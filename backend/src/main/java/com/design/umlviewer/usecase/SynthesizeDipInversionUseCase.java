package com.design.umlviewer.usecase;

import com.design.umlviewer.domain.dossier.DipInversionPlan;
import com.design.umlviewer.domain.dossier.DipInversionSynthesizer;
import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.ArchitectureCompiler;
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
