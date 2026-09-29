package com.design.umlviewer.domain.policy;

import com.design.umlviewer.domain.fitness.FitnessThresholds;
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
    List<String> omit,
    String lang,
    FitnessThresholds fitness) {

  public ArchitecturePolicy(
      String title,
      String src,
      String prefix,
      boolean hierarchical,
      List<String> order,
      List<List<String>> levels,
      List<String> foreign,
      List<Proposal> proposals,
      List<String> omit) {
    this(
        title,
        src,
        prefix,
        hierarchical,
        order,
        levels,
        foreign,
        proposals,
        omit,
        null,
        FitnessThresholds.defaultThresholds());
  }

  public ArchitecturePolicy(
      String title,
      String src,
      String prefix,
      boolean hierarchical,
      List<String> order,
      List<List<String>> levels,
      List<String> foreign,
      List<Proposal> proposals,
      List<String> omit,
      String lang) {
    this(
        title,
        src,
        prefix,
        hierarchical,
        order,
        levels,
        foreign,
        proposals,
        omit,
        lang,
        FitnessThresholds.defaultThresholds());
  }

  public ArchitecturePolicy {
    if (fitness == null) {
      fitness = FitnessThresholds.defaultThresholds();
    }
  }
}
