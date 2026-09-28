package com.design.umlviewer.domain.model;

import java.util.List;

/**
 * Domain value object (Level 0) representing the Screaming Architecture score and domain cohesion
 * diagnostics adhering to Robert C. Martin Clean Architecture Ch. 21.
 */
public record ScreamingMetric(
    double score,
    int domainPackageCount,
    int technicalPackageCount,
    int totalPackageCount,
    String classification,
    List<String> domainPackages,
    List<String> technicalPackages,
    List<String> frameworkGravityHotspots) {

  public ScreamingMetric {
    if (domainPackages == null) domainPackages = List.of();
    if (technicalPackages == null) technicalPackages = List.of();
    if (frameworkGravityHotspots == null) frameworkGravityHotspots = List.of();
  }

  public static ScreamingMetric empty() {
    return new ScreamingMetric(1.0, 0, 0, 0, "PACKAGE_BY_FEATURE", List.of(), List.of(), List.of());
  }
}
