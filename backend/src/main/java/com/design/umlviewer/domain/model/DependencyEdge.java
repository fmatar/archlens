package com.design.umlviewer.domain.model;

public record DependencyEdge(String from, String to, Kind kind, String label, boolean isViolating) {
  public enum Kind {
    DEPENDENCY,
    IMPLEMENTS,
    INHERITANCE,
    ASSOCIATION,
    AGGREGATION,
    COMPOSITION
  }

  public DependencyEdge withViolating(boolean violating) {
    return new DependencyEdge(from, to, kind, label, violating);
  }
}
