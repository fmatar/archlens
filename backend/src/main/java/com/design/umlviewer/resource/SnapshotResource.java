package com.design.umlviewer.resource;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.engine.ArchitectureCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** REST endpoint managing historical snapshots and baseline architecture models. */
@Path("/api/snapshots")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SnapshotResource {

  private static final String DEFAULT_PROJECT_ROOT = ".";

  @Inject ArchitectureCompiler graphCompiler;

  @Inject ObjectMapper mapper = new ObjectMapper();

  public SnapshotResource() {}

  public SnapshotResource(ArchitectureCompiler graphCompiler, ObjectMapper mapper) {
    this.graphCompiler = graphCompiler;
    this.mapper = mapper != null ? mapper : new ObjectMapper();
  }

  @GET
  public Map<String, Object> listSnapshots(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    File snapshotsDir = new File(root, ".archlens/snapshots");
    List<Map<String, Object>> snapshots = new ArrayList<>();

    if (snapshotsDir.exists() && snapshotsDir.isDirectory()) {
      File[] files = snapshotsDir.listFiles((dir, name) -> name.endsWith(".json"));
      if (files != null) {
        java.util.Arrays.sort(
            files, java.util.Comparator.comparingLong(File::lastModified).reversed());
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
                  new java.text.SimpleDateFormat("yyyy-MM-dd")
                      .format(new java.util.Date(f.lastModified()))));
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
          try (java.io.BufferedReader reader =
              new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
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
          boolean finished = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
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

    return Map.of("snapshots", snapshots);
  }

  @GET
  @Path("/{snapshotId}")
  public ArchitectureGraph getSnapshot(
      @PathParam("snapshotId") String snapshotId,
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot)
      throws IOException {
    if (snapshotId == null
        || snapshotId.isBlank()
        || snapshotId.contains("..")
        || snapshotId.contains("/")
        || snapshotId.contains("\\")) {
      throw new BadRequestException(
          "Invalid snapshot ID: path traversal characters are forbidden.");
    }
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    File baseDir = new File(root, ".archlens");
    File snapshotFile = new File(root, ".archlens/snapshots/" + snapshotId + ".json");
    if (snapshotFile.exists() && snapshotFile.isFile()) {
      if (!snapshotFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
        throw new BadRequestException("Invalid snapshot path traversal.");
      }
      return mapper.readValue(snapshotFile, ArchitectureGraph.class);
    }

    File cacheFile = new File(root, ".archlens/cache/" + snapshotId + ".json");
    if (cacheFile.exists() && cacheFile.isFile()) {
      if (!cacheFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
        throw new BadRequestException("Invalid snapshot path traversal.");
      }
      return mapper.readValue(cacheFile, ArchitectureGraph.class);
    }

    return graphCompiler != null ? graphCompiler.compileGraph(root, null) : null;
  }
}
