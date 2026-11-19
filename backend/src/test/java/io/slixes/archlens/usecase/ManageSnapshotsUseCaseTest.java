package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import io.slixes.archlens.engine.GraphCompiler;
import io.slixes.archlens.metrics.CrapScoreCalculator;
import io.slixes.archlens.scanner.JavaAstScanner;
import io.slixes.archlens.scanner.LanguageScannerRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManageSnapshotsUseCaseTest {

  static GraphCompiler createRealJavaCompiler() {
    try {
      JavaAstScanner scanner = new JavaAstScanner();
      Field f1 = JavaAstScanner.class.getDeclaredField("crapCalculator");
      f1.setAccessible(true);
      f1.set(scanner, new CrapScoreCalculator());

      GraphCompiler compiler = new GraphCompiler();
      Field f2 = GraphCompiler.class.getDeclaredField("scannerRegistry");
      f2.setAccessible(true);
      f2.set(compiler, new LanguageScannerRegistry(List.of(scanner)));
      return compiler;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  static String runGit(Path repo, String... args) throws Exception {
    String[] cmd = new String[args.length + 1];
    cmd[0] = "git";
    System.arraycopy(args, 0, cmd, 1, args.length);
    ProcessBuilder pb = new ProcessBuilder(cmd).directory(repo.toFile());
    pb.environment().put("GIT_AUTHOR_NAME", "Test");
    pb.environment().put("GIT_AUTHOR_EMAIL", "test@example.com");
    pb.environment().put("GIT_COMMITTER_NAME", "Test");
    pb.environment().put("GIT_COMMITTER_EMAIL", "test@example.com");
    Process p = pb.start();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(p.waitFor(10, TimeUnit.SECONDS), "git timed out");
    assertEquals(0, p.exitValue(), "git failed: " + err);
    return out;
  }

  static Path initTwoTagJavaRepo(Path tempDir) throws Exception {
    Path repo = Files.createDirectories(tempDir.resolve("git-repo"));
    runGit(repo, "init");

    Path domainDir = Files.createDirectories(repo.resolve("src/main/java/com/demo/domain"));
    Path adapterDir = Files.createDirectories(repo.resolve("src/main/java/com/demo/adapter"));

    Files.writeString(
        adapterDir.resolve("PaymentAdapter.java"),
        "package com.demo.adapter;\npublic class PaymentAdapter {}\n");
    Files.writeString(
        domainDir.resolve("Order.java"),
        "package com.demo.domain;\nimport com.demo.adapter.PaymentAdapter;\n"
            + "public class Order { PaymentAdapter p; }\n");

    runGit(repo, "add", ".");
    runGit(repo, "commit", "-m", "v0.1.0 with outward violation and no policy");
    runGit(repo, "tag", "v0.1.0");

    // Fix violation in v0.2.0 and add .archlens/policy.json
    Files.writeString(
        domainDir.resolve("Order.java"), "package com.demo.domain;\npublic class Order {}\n");
    Path archlensDir = Files.createDirectories(repo.resolve(".archlens"));
    Files.writeString(
        archlensDir.resolve("policy.json"),
        "{\"title\":\"DemoApp\",\"src\":\"src/main/java\",\"prefix\":\"com.demo\","
            + "\"hierarchical\":true,\"levels\":[[\"domain\"],[\"adapter\"]]}");

    runGit(repo, "add", ".");
    runGit(repo, "commit", "-m", "v0.2.0 clean architecture");
    runGit(repo, "tag", "v0.2.0");
    return repo;
  }

  @Test
  void testManageSnapshotsUseCaseLifecycle(@TempDir Path tempDir) throws IOException {
    ArchitectureCompiler mockCompiler =
        new ArchitectureCompiler() {
          @Override
          public ArchitectureGraph compileGraph(String root, String proposalId) {
            return new ArchitectureGraph(
                "CompiledSnapshotProject", false, proposalId, List.of(), List.of(), List.of());
          }

          @Override
          public ArchitecturePolicy loadPolicy(String projectRoot) {
            return null;
          }
        };

    ManageSnapshotsUseCase useCase = new ManageSnapshotsUseCase(mockCompiler, new ObjectMapper());

    // Initially empty
    List<Map<String, Object>> initial = useCase.listSnapshots(tempDir.toString());
    assertNotNull(initial);
    assertTrue(initial.isEmpty());

    // Populate a snapshot
    Path snapshotsDir = tempDir.resolve(".archlens/snapshots");
    Files.createDirectories(snapshotsDir);
    Files.writeString(
        snapshotsDir.resolve("baseline-01.json"),
        "{\"title\":\"BaselineSnapshot\",\"isProposal\":false,\"activeProposalId\":null,\"components\":[],\"edges\":[],\"unassigned\":[]}");

    List<Map<String, Object>> populated = useCase.listSnapshots(tempDir.toString());
    assertEquals(1, populated.size());
    assertEquals("baseline-01", populated.get(0).get("id"));

    // Fetch snapshot
    ArchitectureGraph loaded = useCase.getSnapshot("baseline-01", tempDir.toString());
    assertNotNull(loaded);
    assertEquals("BaselineSnapshot", loaded.title());

    // Check path traversal defenses
    assertThrows(
        IllegalArgumentException.class,
        () -> useCase.getSnapshot("../traversal", tempDir.toString()));
    assertThrows(
        IllegalArgumentException.class, () -> useCase.getSnapshot("sub/dir", tempDir.toString()));
    assertThrows(IllegalArgumentException.class, () -> useCase.getSnapshot("", tempDir.toString()));

    // Fallback compilation
    ArchitectureGraph fallback = useCase.getSnapshot("not-cached", tempDir.toString());
    assertNotNull(fallback);
    assertEquals("CompiledSnapshotProject", fallback.title());
  }

  @Test
  void testGitTagExtractionPolicyFallbackCacheAndOptionGuard(@TempDir Path tempDir)
      throws Exception {
    Path repo = initTwoTagJavaRepo(tempDir);
    Path snapshotsDir = Files.createDirectories(repo.resolve(".archlens/snapshots"));
    Files.writeString(
        snapshotsDir.resolve("manual-01.json"),
        "{\"title\":\"ManualSnap\",\"isProposal\":false,\"activeProposalId\":null,\"components\":[],\"edges\":[],\"unassigned\":[]}");

    ManageSnapshotsUseCase useCase =
        new ManageSnapshotsUseCase(createRealJavaCompiler(), new ObjectMapper());

    List<Map<String, Object>> listed = useCase.listSnapshots(repo.toString());
    List<String> ids = listed.stream().map(m -> (String) m.get("id")).toList();
    assertTrue(ids.contains("manual-01"), "Should include saved .archlens/snapshots/*.json");
    assertTrue(ids.contains("v0.1.0"), "Should also merge git tags even when snapshots exist");
    assertTrue(ids.contains("v0.2.0"), "Should include v0.2.0 git tag");
    for (Map<String, Object> item : listed) {
      String date = (String) item.get("date");
      assertNotNull(date, "Every snapshot/tag entry must include a YYYY-MM-DD date");
      assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2}"), "Unexpected date format: " + date);
    }

    // Reject option-like snapshot IDs before spawning git
    assertThrows(IllegalArgumentException.class, () -> useCase.getSnapshot("-o", repo.toString()));
    assertThrows(
        IllegalArgumentException.class,
        () -> useCase.getSnapshot("--output=/tmp/x", repo.toString()));

    // Nonexistent tag in a git repo must not poison .archlens/cache/
    useCase.getSnapshot("v99.99.99", repo.toString());
    assertFalse(Files.exists(repo.resolve(".archlens/cache/v99.99.99.json")));

    // Historical v0.1.0 tag must compile the v0.1.0 tree (1 violation), NOT current v0.2.0 (0
    // violations)
    ArchitectureGraph v010 = useCase.getSnapshot("v0.1.0", repo.toString());
    long v010Violations = v010.edges().stream().filter(e -> e.isViolating()).count();
    assertEquals(1L, v010Violations, "v0.1.0 had 1 outward dependency violation");
    assertTrue(
        Files.exists(repo.resolve(".archlens/cache/v0.1.0.json")),
        "Compiled git tag must be cached at .archlens/cache/v0.1.0.json");

    // Second read hits .archlens/cache/v0.1.0.json
    ArchitectureGraph v010Cached = useCase.getSnapshot("v0.1.0", repo.toString());
    assertEquals(1L, v010Cached.edges().stream().filter(e -> e.isViolating()).count());

    // Live git index and tracked working tree must remain completely untouched
    String gitStatus = runGit(repo, "status", "--porcelain", "--untracked-files=no");
    assertEquals("", gitStatus.trim(), "Git index and tracked working tree must not be mutated");
  }
}
