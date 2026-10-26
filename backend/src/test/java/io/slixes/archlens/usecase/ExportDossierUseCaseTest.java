package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.dossier.DossierGenerator;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
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
