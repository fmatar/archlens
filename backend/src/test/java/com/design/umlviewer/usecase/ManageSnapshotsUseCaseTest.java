package com.design.umlviewer.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.ArchitectureCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManageSnapshotsUseCaseTest {

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
}
