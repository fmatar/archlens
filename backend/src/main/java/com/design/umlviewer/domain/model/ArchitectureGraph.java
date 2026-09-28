package com.design.umlviewer.domain.model;

import java.util.List;

public record ArchitectureGraph(
    String title,
    boolean isProposal,
    String activeProposalId,
    List<ComponentNode> components,
    List<DependencyEdge> edges,
    List<ClassNode> unassigned,
    List<PackageCycle> cycles) {

  public ArchitectureGraph(
      String title,
      boolean isProposal,
      String activeProposalId,
      List<ComponentNode> components,
      List<DependencyEdge> edges,
      List<ClassNode> unassigned) {
    this(title, isProposal, activeProposalId, components, edges, unassigned, List.of());
  }

  public ArchitectureGraph {
    if (cycles == null) {
      cycles = List.of();
    }
  }
}
