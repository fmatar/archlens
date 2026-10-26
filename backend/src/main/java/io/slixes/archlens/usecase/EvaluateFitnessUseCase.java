package io.slixes.archlens.usecase;

import io.slixes.archlens.domain.fitness.ArchitectureFitnessCalculator;
import io.slixes.archlens.domain.fitness.FitnessEvaluation;
import io.slixes.archlens.domain.fitness.FitnessThresholds;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;

/**
 * Level 1 Application Use Case orchestrating architectural fitness evaluation against project graph
 * and configured policy invariants.
 */
@ApplicationScoped
public class EvaluateFitnessUseCase {

  @Inject ArchitectureCompiler architectureCompiler;
  @Inject ArchitectureFitnessCalculator fitnessCalculator;

  public EvaluateFitnessUseCase() {}

  public EvaluateFitnessUseCase(
      ArchitectureCompiler architectureCompiler, ArchitectureFitnessCalculator fitnessCalculator) {
    this.architectureCompiler = architectureCompiler;
    this.fitnessCalculator = fitnessCalculator;
  }

  public FitnessEvaluation evaluateCurrent(String projectRoot, String proposalId)
      throws IOException {
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    ArchitectureGraph graph = architectureCompiler.compileGraph(root, proposalId);
    ArchitecturePolicy policy = architectureCompiler.loadPolicy(root);
    FitnessThresholds thresholds =
        (policy != null && policy.fitness() != null)
            ? policy.fitness()
            : FitnessThresholds.defaultThresholds();

    return fitnessCalculator.evaluate(graph, thresholds);
  }

  public FitnessEvaluation evaluateGraph(ArchitectureGraph graph, ArchitecturePolicy policy) {
    FitnessThresholds thresholds =
        (policy != null && policy.fitness() != null)
            ? policy.fitness()
            : FitnessThresholds.defaultThresholds();
    return fitnessCalculator.evaluate(graph, thresholds);
  }
}
