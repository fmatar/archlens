package io.slixes.archlens.usecase;

import io.slixes.archlens.domain.dossier.DipInversionPlan;
import io.slixes.archlens.domain.dossier.DipInversionSynthesizer;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;

/**
 * Level 1 Application Use Case orchestrating automated Dependency Inversion Principle (DIP)
 * interface port synthesis and caller refactoring plans for illegal outward dependencies.
 */
@ApplicationScoped
public class SynthesizeDipInversionUseCase {

  @Inject ArchitectureCompiler architectureCompiler;

  @Inject DipInversionSynthesizer dipSynthesizer;

  public SynthesizeDipInversionUseCase() {}

  public SynthesizeDipInversionUseCase(
      ArchitectureCompiler architectureCompiler, DipInversionSynthesizer dipSynthesizer) {
    this.architectureCompiler = architectureCompiler;
    this.dipSynthesizer = dipSynthesizer != null ? dipSynthesizer : new DipInversionSynthesizer();
  }

  public DipInversionPlan execute(
      String projectRoot, String proposalId, String fromClass, String toClass) throws IOException {
    ArchitectureGraph graph = architectureCompiler.compileGraph(projectRoot, proposalId);
    ArchitecturePolicy policy = architectureCompiler.loadPolicy(projectRoot);
    return dipSynthesizer.synthesize(graph, policy, fromClass, toClass);
  }
}
