package io.slixes.archlens.domain.graph;

import java.util.List;

public record ArchitectureGraph(
    String title,
    boolean isProposal,
    String activeProposalId,
    List<ComponentNode> components,
    List<DependencyEdge> edges,
    List<ClassNode> unassigned,
    List<PackageCycle> cycles,
    ScreamingMetric screamingMetric,
    int skippedFiles) {

  public ArchitectureGraph(
      String title,
      boolean isProposal,
      String activeProposalId,
      List<ComponentNode> components,
      List<DependencyEdge> edges,
      List<ClassNode> unassigned) {
    this(
        title,
        isProposal,
        activeProposalId,
        components,
        edges,
        unassigned,
        List.of(),
        ScreamingMetric.empty(),
        0);
  }

  public ArchitectureGraph(
      String title,
      boolean isProposal,
      String activeProposalId,
      List<ComponentNode> components,
      List<DependencyEdge> edges,
      List<ClassNode> unassigned,
      List<PackageCycle> cycles) {
    this(
        title,
        isProposal,
        activeProposalId,
        components,
        edges,
        unassigned,
        cycles,
        ScreamingMetric.empty(),
        0);
  }

  public ArchitectureGraph(
      String title,
      boolean isProposal,
      String activeProposalId,
      List<ComponentNode> components,
      List<DependencyEdge> edges,
      List<ClassNode> unassigned,
      List<PackageCycle> cycles,
      ScreamingMetric screamingMetric) {
    this(
        title,
        isProposal,
        activeProposalId,
        components,
        edges,
        unassigned,
        cycles,
        screamingMetric,
        0);
  }

  public ArchitectureGraph {
    if (cycles == null) {
      cycles = List.of();
    }
    if (screamingMetric == null) {
      screamingMetric = ScreamingMetric.empty();
    }
    if (skippedFiles < 0) {
      skippedFiles = 0;
    }
  }
}
