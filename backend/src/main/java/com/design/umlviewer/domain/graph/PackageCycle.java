package com.design.umlviewer.domain.graph;

import java.util.List;

/**
 * Represents a cyclic dependency loop between packages in violation of the Robert C. Martin Acyclic
 * Dependencies Principle (ADP).
 */
public record PackageCycle(List<String> packages) {

  public PackageCycle {
    if (packages == null) {
      packages = List.of();
    }
  }

  public int length() {
    return packages.size();
  }

  public String formattedPath() {
    return String.join(" -> ", packages);
  }
}
