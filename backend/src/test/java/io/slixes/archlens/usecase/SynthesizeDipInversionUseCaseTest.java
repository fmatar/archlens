package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.dossier.DipInversionPlan;
import io.slixes.archlens.domain.dossier.DipInversionSynthesizer;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SynthesizeDipInversionUseCaseTest {

  @Test
  void testSynthesizeDipInversionUseCaseExecution() throws IOException {
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

    DipInversionSynthesizer synthesizer = new DipInversionSynthesizer();
    SynthesizeDipInversionUseCase useCase =
        new SynthesizeDipInversionUseCase(mockCompiler, synthesizer);

    DipInversionPlan plan =
        useCase.execute(
            ".", "prop-1", "com.example.OrderService", "com.example.PostgresRepository");

    assertNotNull(plan);
    assertEquals("com.example.OrderService", plan.fromClass());
    assertEquals("com.example.PostgresRepository", plan.toClass());
    assertEquals("PostgresRepositoryPort", plan.portName());
  }
}
