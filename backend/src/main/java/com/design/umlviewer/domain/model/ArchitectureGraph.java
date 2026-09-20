package com.design.umlviewer.domain.model;

import java.util.List;

public record ArchitectureGraph(
    String title,
    boolean isProposal,
    String activeProposalId,
    List<ComponentNode> components,
    List<DependencyEdge> edges,
    List<ClassNode> unassigned
) {}
