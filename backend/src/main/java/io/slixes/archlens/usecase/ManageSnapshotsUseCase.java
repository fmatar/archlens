package io.slixes.archlens.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Level 1 Application Use Case coordinating historical snapshot discovery, git tag discovery,
 * isolated historical tag compilation, and baseline architecture model retrieval.
 */
@ApplicationScoped
public class ManageSnapshotsUseCase {

  private static final Pattern VALID_SNAPSHOT_ID =
      Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9_+-]|\\.(?!\\.)){0,127}$");

  @Inject ArchitectureCompiler architectureCompiler;

  @Inject ObjectMapper mapper = new ObjectMapper();

  @Inject OpenedProjectRegistry openedProjects;

  public ManageSnapshotsUseCase() {}

  public ManageSnapshotsUseCase(ArchitectureCompiler architectureCompiler, ObjectMapper mapper) {
    this.architectureCompiler = architectureCompiler;
    this.mapper = mapper != null ? mapper : new ObjectMapper();
  }

  static boolean isValidSnapshotId(String snapshotId) {
    return snapshotId != null && VALID_SNAPSHOT_ID.matcher(snapshotId).matches();
  }

  static void scrubGitEnvironment(Map<String, String> env, Map<String, String> extraEnv) {
    env.remove("GIT_DIR");
    env.remove("GIT_WORK_TREE");
    env.remove("GIT_COMMON_DIR");
    env.remove("GIT_INDEX_FILE");
    if (extraEnv != null) {
      env.putAll(extraEnv);
    }
  }

  boolean canPersistCache(Path canonicalRoot) {
    if (openedProjects == null || openedProjects.isOpened(canonicalRoot.toString())) {
      return true;
    }
    return ProjectPathGuard.tryCanonicalRoot(WorkspacePathResolver.normalizeRoot("."))
        .map(canonicalRoot::equals)
        .orElse(false);
  }

  public List<Map<String, Object>> listSnapshots(String projectRoot) {
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    File snapshotsDir = new File(root, ".archlens/snapshots");
    List<Map<String, Object>> snapshots = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();

    if (snapshotsDir.exists() && snapshotsDir.isDirectory()) {
      File[] files = snapshotsDir.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : files) {
          String name = f.getName();
          String id = name.substring(0, name.length() - 5);
          if (isValidSnapshotId(id) && seenIds.add(id)) {
            String date =
                Instant.ofEpochMilli(f.lastModified())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .toString();
            snapshots.add(Map.of("id", id, "label", id, "tag", id, "date", date));
          }
        }
      }
    }

    if (new File(root, ".git").exists()) {
      appendGitTags(root, snapshots, seenIds);
    }

    return snapshots;
  }

  private static void appendGitTags(
      String root, List<Map<String, Object>> snapshots, Set<String> seenIds) {
    try {
      ProcessBuilder pb =
          new ProcessBuilder(
                  "git",
                  "for-each-ref",
                  "--sort=-version:refname",
                  "--sort=-creatordate",
                  "--count=10",
                  "--format=%(refname:short)|%(creatordate:short)",
                  "refs/tags")
              .directory(new File(root))
              .redirectError(ProcessBuilder.Redirect.DISCARD);
      scrubGitEnvironment(pb.environment(), null);
      Process started = pb.start();
      try {
        CompletableFuture<List<String>> stdoutFuture =
            CompletableFuture.supplyAsync(() -> {
              List<String> out = new ArrayList<>();
              try (BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  out.add(line.trim());
                }
              } catch (IOException ignored) {
                // Stream closed on timeout or exit
              }
              return out;
            });
        if (started.waitFor(2, TimeUnit.SECONDS) && started.exitValue() == 0) {
          List<String> lines = stdoutFuture.get(1, TimeUnit.SECONDS);
          String today = LocalDate.now(ZoneOffset.UTC).toString();
          for (String entry : lines) {
            String[] parts = entry.split("\\|", 2);
            String tag = parts[0].trim();
            String rawDate = parts.length > 1 ? parts[1].trim() : "";
            String date = rawDate.matches("\\d{4}-\\d{2}-\\d{2}") ? rawDate : today;
            if (isValidSnapshotId(tag) && seenIds.add(tag)) {
              snapshots.add(Map.of("id", tag, "label", tag, "tag", tag, "date", date));
            }
          }
        }
      } finally {
        started.destroyForcibly();
      }
    } catch (IOException | ExecutionException | TimeoutException ignored) {
      // Gracefully continue with available list
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public ArchitectureGraph getSnapshot(String snapshotId, String projectRoot) throws IOException {
    if (!isValidSnapshotId(snapshotId)) {
      throw new IllegalArgumentException(
          "Invalid snapshot ID: path traversal and leading option dashes are forbidden.");
    }
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    Path canonicalRoot = ProjectPathGuard.canonicalRoot(root);

    Path snapshotFile = canonicalRoot.resolve(".archlens/snapshots/" + snapshotId + ".json");
    if (Files.isRegularFile(snapshotFile)) {
      SyncAdrUseCase.ensureNoSymlinkOnPath(canonicalRoot, snapshotFile);
      return mapper.readValue(snapshotFile.toFile(), ArchitectureGraph.class);
    }

    Path cacheFile = canonicalRoot.resolve(".archlens/cache/" + snapshotId + ".json");
    if (Files.isRegularFile(cacheFile)) {
      SyncAdrUseCase.ensureNoSymlinkOnPath(canonicalRoot, cacheFile);
      return mapper.readValue(cacheFile.toFile(), ArchitectureGraph.class);
    }

    if (architectureCompiler == null) {
      return null;
    }

    if (Files.exists(canonicalRoot.resolve(".git"))) {
      ArchitectureGraph tagGraph = compileAndCacheGitTag(canonicalRoot, snapshotId);
      if (tagGraph != null) {
        return tagGraph;
      }
    }

    return architectureCompiler.compileGraph(root, null);
  }

  private ArchitectureGraph compileAndCacheGitTag(Path canonicalRoot, String tag)
      throws IOException {
    if (runGitCommand(
            canonicalRoot,
            null,
            "git",
            "rev-parse",
            "--verify",
            "--quiet",
            "refs/tags/" + tag + "^{tree}")
        != 0) {
      return null;
    }

    Path tempDir = Files.createTempDirectory("archlens-tag-");
    try {
      Path worktree = Files.createDirectories(tempDir.resolve("tree"));
      Path canonicalWorktree = worktree.toRealPath();
      Map<String, String> gitEnv = Map.of("GIT_INDEX_FILE", tempDir.resolve("index").toString());

      boolean extracted =
          runGitCommand(canonicalRoot, gitEnv, "git", "read-tree", "refs/tags/" + tag) == 0
              && runGitCommand(
                      canonicalRoot,
                      gitEnv,
                      "git",
                      "-c",
                      "core.symlinks=false",
                      "-c",
                      "core.fsmonitor=false",
                      "checkout-index",
                      "-a",
                      "-f",
                      "--prefix=" + canonicalWorktree + File.separator)
                  == 0;
      if (!extracted) {
        return null;
      }

      sanitizeAndEnsureTagPolicy(canonicalRoot, canonicalWorktree);

      ArchitectureGraph graph =
          architectureCompiler.compileGraph(canonicalWorktree.toString(), null);
      if (graph != null && canPersistCache(canonicalRoot)) {
        Path cacheDir = canonicalRoot.resolve(".archlens/cache");
        SyncAdrUseCase.ensureNoSymlinkOnPath(canonicalRoot, cacheDir);
        Files.createDirectories(cacheDir);
        Path cacheFile = cacheDir.resolve(tag + ".json");
        SyncAdrUseCase.ensureNoSymlinkOnPath(canonicalRoot, cacheFile);
        Path tmpFile = Files.createTempFile(cacheDir, tag + "-", ".tmp");
        try {
          mapper.writeValue(tmpFile.toFile(), graph);
          Files.move(tmpFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
          Files.deleteIfExists(tmpFile);
        }
      }
      return graph;
    } finally {
      deleteRecursively(tempDir);
    }
  }

  private void sanitizeAndEnsureTagPolicy(Path canonicalRoot, Path canonicalWorktree)
      throws IOException {
    Path tagArchlensDir = canonicalWorktree.resolve(".archlens");
    if (Files.exists(tagArchlensDir, LinkOption.NOFOLLOW_LINKS)
        && !Files.isDirectory(tagArchlensDir, LinkOption.NOFOLLOW_LINKS)) {
      Files.deleteIfExists(tagArchlensDir);
    }
    Path tagPolicy = tagArchlensDir.resolve("policy.json");
    SyncAdrUseCase.ensureNoSymlinkOnPath(canonicalWorktree, tagPolicy);
    if (Files.isRegularFile(tagPolicy, LinkOption.NOFOLLOW_LINKS)) {
      ArchitecturePolicy parsed = architectureCompiler.loadPolicy(canonicalWorktree.toString());
      String configuredSrc = parsed != null ? parsed.src() : null;
      if (configuredSrc != null
          && !configuredSrc.isBlank()
          && !ProjectPathGuard.isSafeRelativePath(canonicalWorktree, configuredSrc)) {
        Files.deleteIfExists(tagPolicy);
      }
    }
    Path activePolicy = canonicalRoot.resolve(".archlens/policy.json");
    if (!Files.exists(tagPolicy, LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(activePolicy, LinkOption.NOFOLLOW_LINKS)) {
      SyncAdrUseCase.ensureNoSymlinkOnPath(canonicalRoot, activePolicy);
      Files.createDirectories(tagArchlensDir);
      Files.copy(activePolicy, tagPolicy, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static int runGitCommand(Path workingDir, Map<String, String> extraEnv, String... cmd) {
    try {
      ProcessBuilder pb =
          new ProcessBuilder(cmd)
              .directory(workingDir.toFile())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD);
      scrubGitEnvironment(pb.environment(), extraEnv);
      Process process = pb.start();
      try {
        return process.waitFor(5, TimeUnit.SECONDS) ? process.exitValue() : -1;
      } finally {
        process.destroyForcibly();
      }
    } catch (IOException e) {
      return -1;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private static void deleteRecursively(Path root) {
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.deleteIfExists(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              Files.deleteIfExists(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException ignored) {
      // Best-effort cleanup of ephemeral temp directory
    }
  }
}
