package io.slixes.archlens.engine;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import java.io.IOException;

/**
 * Interface port defining the core architecture graph compilation and policy retrieval contract.
 * Decouples delivery mechanisms (REST resources, MCP server, CLI) from concrete scanner
 * orchestration.
 */
public interface ArchitectureCompiler {

  /**
   * Compiles the architecture graph for a given project root and optional proposal ID.
   *
   * @param projectRoot the project root directory
   * @param proposalId optional active proposal ID
   * @return the compiled ArchitectureGraph model
   * @throws IOException if source reading or AST parsing fails
   */
  ArchitectureGraph compileGraph(String projectRoot, String proposalId) throws IOException;

  /**
   * Loads the active ArchitecturePolicy for a given project root.
   *
   * @param projectRoot the project root directory
   * @return the resolved ArchitecturePolicy
   */
  ArchitecturePolicy loadPolicy(String projectRoot);
}
