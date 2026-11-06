package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.fitness.ArchitectureFitnessCalculator;
import io.slixes.archlens.domain.fitness.FitnessEvaluation;
import io.slixes.archlens.domain.fitness.FitnessThresholds;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ScreamingMetric;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluateFitnessUseCaseTest {

  private EvaluateFitnessUseCase useCase;
  private ArchitectureGraph stubGraph;
  private ArchitecturePolicy stubPolicy;

  @BeforeEach
  void setUp() {
    stubGraph =
        new ArchitectureGraph(
            "Test",
            false,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new ScreamingMetric(
                0.80, 8, 2, 10, "PACKAGE_BY_FEATURE", List.of(), List.of(), List.of()));

    stubPolicy =
        new ArchitecturePolicy(
            "Test Policy",
            "src",
            "com.app",
            true,
            List.of("domain"),
            List.of(List.of("domain")),
            List.of(),
            List.of(),
            List.of(),
            "java",
            new FitnessThresholds(0, 0, 0.75, 0.35, 50, 30));

    ArchitectureCompiler stubCompiler =
        new ArchitectureCompiler() {
          @Override
          public ArchitectureGraph compileGraph(String projectRoot, String proposalId)
              throws IOException {
            return stubGraph;
          }

          @Override
          public ArchitecturePolicy loadPolicy(String projectRoot) {
            return stubPolicy;
          }
        };

    useCase = new EvaluateFitnessUseCase(stubCompiler, new ArchitectureFitnessCalculator());
  }

  @Test
  @DisplayName("Should evaluate current project fitness using configured policy thresholds")
  void shouldEvaluateCurrentFitness() throws IOException {
    FitnessEvaluation eval = useCase.evaluateCurrent(".", null);
    assertNotNull(eval);
    assertTrue(eval.overallPassed());
    assertEquals("A", eval.grade());
    assertEquals(4, eval.totalRuleCount());
  }

  @Test
  @DisplayName("Should evaluate direct graph and policy")
  void shouldEvaluateDirectGraphAndPolicy() {
    FitnessEvaluation eval = useCase.evaluateGraph(stubGraph, stubPolicy);
    assertNotNull(eval);
    assertTrue(eval.overallPassed());
  }
}
