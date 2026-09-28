package com.design.umlviewer.usecase;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.engine.ArchitectureCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Level 1 Application Use Case coordinating historical snapshot discovery, git tag discovery, and
 * baseline architecture model retrieval.
 */
@ApplicationScoped
public class ManageSnapshotsUseCase {

  @Inject ArchitectureCompiler architectureCompiler;

  @Inject ObjectMapper mapper = new ObjectMapper();

  public ManageSnapshotsUseCase() {}

  public ManageSnapshotsUseCase(ArchitectureCompiler architectureCompiler, ObjectMapper mapper) {
    this.architectureCompiler = architectureCompiler;
    this.mapper = mapper != null ? mapper : new ObjectMapper();
  }

  public List<Map<String, Object>> listSnapshots(String projectRoot) {
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    File snapshotsDir = new File(root, ".archlens/snapshots");
    List<Map<String, Object>> snapshots = new ArrayList<>();

    if (snapshotsDir.exists() && snapshotsDir.isDirectory()) {
      File[] files = snapshotsDir.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : files) {
          String name = f.getName();
          String id = name.substring(0, name.length() - 5);
          snapshots.add(
              Map.of(
                  "id",
                  id,
                  "label",
                  id,
                  "tag",
                  id,
                  "date",
                  new SimpleDateFormat("yyyy-MM-dd").format(new Date(f.lastModified()))));
        }
      }
    }

    if (snapshots.isEmpty()) {
      File gitDir = new File(root, ".git");
      if (gitDir.exists()) {
        try {
          Process process =
              new ProcessBuilder("git", "tag", "-l", "--sort=-creatordate")
                  .directory(new File(root))
                  .start();
          try (BufferedReader reader =
              new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 10) {
              String tag = line.trim();
              if (!tag.isEmpty()) {
                snapshots.add(Map.of("id", tag, "label", tag, "tag", tag));
                count++;
              }
            }
          }
          boolean finished = process.waitFor(2, TimeUnit.SECONDS);
          if (!finished) {
            process.destroyForcibly();
          }
        } catch (IOException ignored) {
          // Gracefully continue with available list
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }

    return snapshots;
  }

  public ArchitectureGraph getSnapshot(String snapshotId, String projectRoot) throws IOException {
    if (snapshotId == null
        || snapshotId.isBlank()
        || snapshotId.contains("..")
        || snapshotId.contains("/")
        || snapshotId.contains("\\")) {
      throw new IllegalArgumentException(
          "Invalid snapshot ID: path traversal characters are forbidden.");
    }
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    File baseDir = new File(root, ".archlens");
    File snapshotFile = new File(root, ".archlens/snapshots/" + snapshotId + ".json");
    if (snapshotFile.exists() && snapshotFile.isFile()) {
      if (!snapshotFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
        throw new IllegalArgumentException("Invalid snapshot path traversal.");
      }
      return mapper.readValue(snapshotFile, ArchitectureGraph.class);
    }

    File cacheFile = new File(root, ".archlens/cache/" + snapshotId + ".json");
    if (cacheFile.exists() && cacheFile.isFile()) {
      if (!cacheFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
        throw new IllegalArgumentException("Invalid snapshot path traversal.");
      }
      return mapper.readValue(cacheFile, ArchitectureGraph.class);
    }

    return architectureCompiler != null ? architectureCompiler.compileGraph(root, null) : null;
  }
}
