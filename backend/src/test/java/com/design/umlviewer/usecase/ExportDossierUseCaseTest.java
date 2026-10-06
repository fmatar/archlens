package com.design.umlviewer.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.dossier.DossierGenerator;
import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.ArchitectureCompiler;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportDossierUseCaseTest {

  @Test
  void testExportDossierUseCaseExecution() throws IOException {
    ArchitectureCompiler mockCompiler =
        new ArchitectureCompiler() {
          @Override
          public ArchitectureGraph compileGraph(String projectRoot, String proposalId) {
            return new ArchitectureGraph(
                "TestProject", false, proposalId, List.of(), List.of(), List.of());
          }

          @Override
          public ArchitecturePolicy loadPolicy(String projectRoot) {
            return new ArchitecturePolicy(
                "TestPolicy",
                "src",
                "com",
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
          }
        };

    DossierGenerator mockGenerator =
        (graph, policy) -> "# Dossier for " + graph.title() + " under " + policy.title();

    ExportDossierUseCase useCase = new ExportDossierUseCase(mockCompiler, mockGenerator);
    String result = useCase.execute(".", "prop-1");

    assertNotNull(result);
    assertEquals("# Dossier for TestProject under TestPolicy", result);
  }
}
