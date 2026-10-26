package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.engine.GraphCompiler;
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotResourceTest {

  @Test
  void testSnapshotResourceLifecycle(@TempDir Path tempDir) throws IOException {
    GraphCompiler compiler =
        new GraphCompiler() {
          @Override
          public ArchitectureGraph compileGraph(String root, String proposalId) {
            return new ArchitectureGraph(
                "CompiledRoot", false, proposalId, List.of(), List.of(), List.of());
          }
        };

    SnapshotResource resource = new SnapshotResource(compiler, new ObjectMapper());

    // Empty snapshots initially
    Map<String, Object> initial = resource.listSnapshots(tempDir.toString());
    assertNotNull(initial);
    assertTrue(initial.containsKey("snapshots"));

    // Create a snapshot file
    Path snapshotsDir = tempDir.resolve(".archlens/snapshots");
    Files.createDirectories(snapshotsDir);
    Files.writeString(
        snapshotsDir.resolve("snap-01.json"),
        "{\"title\":\"SnapshotApp\",\"isProposal\":false,\"activeProposalId\":null,\"components\":[],\"edges\":[],\"unassigned\":[]}");

    Map<String, Object> populated = resource.listSnapshots(tempDir.toString());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list = (List<Map<String, Object>>) populated.get("snapshots");
    assertEquals(1, list.size());
    assertEquals("snap-01", list.get(0).get("id"));

    // Read snapshot
    ArchitectureGraph graph = resource.getSnapshot("snap-01", tempDir.toString());
    assertNotNull(graph);
    assertEquals("SnapshotApp", graph.title());

    // Path traversal rejection
    assertThrows(
        BadRequestException.class, () -> resource.getSnapshot("../evil", tempDir.toString()));
    assertThrows(
        BadRequestException.class, () -> resource.getSnapshot("sub/dir", tempDir.toString()));
    assertThrows(BadRequestException.class, () -> resource.getSnapshot("  ", tempDir.toString()));

    // Fallback compilation
    ArchitectureGraph fallback = resource.getSnapshot("nonexistent", tempDir.toString());
    assertEquals("CompiledRoot", fallback.title());
  }
}
