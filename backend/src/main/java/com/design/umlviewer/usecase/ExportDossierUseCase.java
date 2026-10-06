package com.design.umlviewer.usecase;

import com.design.umlviewer.domain.dossier.DossierGenerator;
import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;

/**
 * Level 1 Application Use Case orchestrating Clean Architecture graph compilation, policy
 * evaluation, and LLM refactoring prompt dossier synthesis.
 */
@ApplicationScoped
public class ExportDossierUseCase {

  @Inject ArchitectureCompiler architectureCompiler;

  @Inject DossierGenerator dossierGenerator;

  public ExportDossierUseCase() {}

  public ExportDossierUseCase(
      ArchitectureCompiler architectureCompiler, DossierGenerator dossierGenerator) {
    this.architectureCompiler = architectureCompiler;
    this.dossierGenerator = dossierGenerator;
  }

  public String execute(String projectRoot, String proposalId) throws IOException {
    ArchitectureGraph graph = architectureCompiler.compileGraph(projectRoot, proposalId);
    ArchitecturePolicy policy = architectureCompiler.loadPolicy(projectRoot);
    return dossierGenerator.generate(graph, policy);
  }
}
