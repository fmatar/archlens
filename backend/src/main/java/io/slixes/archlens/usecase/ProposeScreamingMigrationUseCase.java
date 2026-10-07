package io.slixes.archlens.usecase;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.screaming.ScreamingMigrationAssistant;
import io.slixes.archlens.domain.screaming.ScreamingMigrationProposal;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;

/**
 * Level 1 Application Use Case orchestrating automated Package-by-Feature domain migration
 * proposals and Screaming Architecture Score (SAS) optimization.
 */
@ApplicationScoped
public class ProposeScreamingMigrationUseCase {

  @Inject ArchitectureCompiler architectureCompiler;

  @Inject ScreamingMigrationAssistant migrationAssistant;

  public ProposeScreamingMigrationUseCase() {}

  public ProposeScreamingMigrationUseCase(
      ArchitectureCompiler architectureCompiler, ScreamingMigrationAssistant migrationAssistant) {
    this.architectureCompiler = architectureCompiler;
    this.migrationAssistant =
        migrationAssistant != null ? migrationAssistant : new ScreamingMigrationAssistant();
  }

  public ScreamingMigrationProposal execute(String projectRoot, String proposalId)
      throws IOException {
    ArchitectureGraph graph = architectureCompiler.compileGraph(projectRoot, proposalId);
    ScreamingMigrationAssistant assistant =
        migrationAssistant != null ? migrationAssistant : new ScreamingMigrationAssistant();
    return assistant.generateProposal(graph);
  }
}
