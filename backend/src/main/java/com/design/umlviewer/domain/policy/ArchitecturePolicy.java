package com.design.umlviewer.domain.policy;

import java.util.List;

public record ArchitecturePolicy(
    String title,
    String src,
    String prefix,
    boolean hierarchical,
    List<String> order,
    List<List<String>> levels,
    List<String> foreign,
    List<Proposal> proposals,
    List<String> omit
) {}
