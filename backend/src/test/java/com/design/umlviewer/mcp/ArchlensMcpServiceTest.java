package com.design.umlviewer.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.dossier.ArchitecturalDossierGenerator;
import com.design.umlviewer.domain.mailbox.FileMailboxService;
import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.GraphCompiler;
import com.design.umlviewer.resource.DiagramResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchlensMcpServiceTest {

  private ArchlensMcpService mcpService;
  private DiagramResource diagramResource;

  @BeforeEach
  void setUp() {
    GraphCompiler compiler =
        new GraphCompiler() {
          @Override
          public ArchitecturePolicy loadPolicy(String root) {
            return new ArchitecturePolicy(
                "McpApp", "src", "com.mcp", true, List.of(), List.of(), List.of(), List.of(),
                List.of());
          }

          @Override
          public ArchitectureGraph compileGraph(String root, String proposalId) {
            return new ArchitectureGraph(
                "McpGraph", false, proposalId, List.of(), List.of(), List.of());
          }
        };

    diagramResource =
        new DiagramResource(
            compiler,
            new FileMailboxService(),
            new ArchitecturalDossierGenerator(),
            new ObjectMapper());
    mcpService = new ArchlensMcpService(diagramResource);
  }

  @Test
  void testInspectArchitecture(@TempDir Path tempDir) throws Exception {
    ArchitectureGraph graph = mcpService.inspectArchitecture(tempDir.toString());
    assertNotNull(graph);
    assertEquals("McpGraph", graph.title());
  }

  @Test
  void testExportLlmDossier(@TempDir Path tempDir) throws IOException {
    String dossier = mcpService.exportLlmDossier(tempDir.toString(), "");
    assertNotNull(dossier);
    assertTrue(dossier.contains("# Clean Architecture Optimization Dossier — McpGraph"));
    assertTrue(dossier.contains("Actionable LLM Refactoring Instructions"));
  }

  @Test
  void testListSnapshots(@TempDir Path tempDir) {
    Map<String, Object> result = mcpService.listSnapshots(tempDir.toString());
    assertNotNull(result);
    assertTrue(result.containsKey("snapshots"));
  }

  @Test
  void testGetSnapshot(@TempDir Path tempDir) throws IOException {
    ArchitectureGraph fallback = mcpService.getSnapshot("test-release", tempDir.toString());
    assertNotNull(fallback);
    assertEquals("McpGraph", fallback.title());
  }
}
