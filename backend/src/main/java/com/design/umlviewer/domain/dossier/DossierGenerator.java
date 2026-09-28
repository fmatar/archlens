package com.design.umlviewer.domain.dossier;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;

/**
 * Interface port for synthesizing Clean Architecture LLM prompt dossiers. Prescribes concrete
 * Dependency Inversion Principle (DIP) instructions and ring compliance metrics.
 */
public interface DossierGenerator {

  /**
   * Generates a markdown dossier from the compiled ArchitectureGraph and policy rules.
   *
   * @param graph the compiled ArchitectureGraph
   * @param policy the active ArchitecturePolicy
   * @return markdown formatted LLM prompt dossier
   */
  String generate(ArchitectureGraph graph, ArchitecturePolicy policy);
}
