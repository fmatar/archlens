package io.slixes.archlens.domain.screaming;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.ScreamingMetric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Domain Service (Level 0) evaluating Robert C. Martin's Screaming Architecture principle (Clean
 * Architecture Chapter 21: "Screaming Architecture").
 *
 * <p>Scores systems based on whether package structures scream their business domain intent
 * (Package-by-Feature) versus horizontal technical layers (Package-by-Layer anti-pattern).
 */
public class ScreamingArchitectureAnalyzer {

  public static final Set<String> TECHNICAL_MARKERS =
      Set.of(
          "controller",
          "controllers",
          "service",
          "services",
          "dao",
          "daos",
          "repository",
          "repositories",
          "dto",
          "dtos",
          "model",
          "models",
          "entity",
          "entities",
          "util",
          "utils",
          "helper",
          "helpers",
          "common",
          "infra",
          "infrastructure",
          "adapter",
          "adapters",
          "resource",
          "resources",
          "api",
          "endpoint",
          "endpoints",
          "view",
          "views",
          "handler",
          "handlers");

  private static final List<String> FRAMEWORK_PREFIXES =
      List.of(
          "jakarta.",
          "javax.",
          "org.springframework.",
          "io.quarkus.",
          "com.google.inject.",
          "org.hibernate.",
          "io.micronaut.");

  public ScreamingMetric analyze(ArchitectureGraph graph) {
    if (graph == null) {
      return ScreamingMetric.empty();
    }
    return analyze(graph.components(), graph.edges());
  }

  public ScreamingMetric analyze(List<ComponentNode> components, List<DependencyEdge> edges) {
    if (components == null || components.isEmpty()) {
      return ScreamingMetric.empty();
    }

    List<String> domainPackages = new ArrayList<>();
    List<String> technicalPackages = new ArrayList<>();

    for (ComponentNode comp : components) {
      String id = comp.id() != null ? comp.id().toLowerCase(Locale.ROOT) : "";
      String leaf = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id;

      if (isTechnical(id, leaf)) {
        technicalPackages.add(comp.id());
      } else {
        domainPackages.add(comp.id());
      }
    }

    int domainCount = domainPackages.size();
    int techCount = technicalPackages.size();
    int totalCount = domainCount + techCount;

    if (totalCount == 0) {
      return ScreamingMetric.empty();
    }

    // Detect Framework Gravity Hotspots: Level 0 domain entities leaking framework dependencies
    Set<String> frameworkHotspots = new HashSet<>();
    Set<String> domainClassIds = new HashSet<>();
    for (ComponentNode comp : components) {
      if (comp.level() != null && comp.level() == 0 && comp.classes() != null) {
        for (ClassNode cls : comp.classes()) {
          domainClassIds.add(cls.id());
        }
      }
    }

    if (edges != null && !domainClassIds.isEmpty()) {
      for (DependencyEdge edge : edges) {
        if (domainClassIds.contains(edge.from()) && isFrameworkTarget(edge.to())) {
          // Identify which component owns this class
          for (ComponentNode comp : components) {
            if (comp.classes() != null
                && comp.classes().stream().anyMatch(c -> c.id().equals(edge.from()))) {
              frameworkHotspots.add(comp.id());
            }
          }
        }
      }
    }

    List<String> hotspotsList = new ArrayList<>(frameworkHotspots);
    double rawRatio = (double) domainCount / totalCount;
    double penalty = hotspotsList.size() * 0.05;
    double finalScore = Math.max(0.0, Math.min(1.0, rawRatio - penalty));
    double roundedScore = Math.round(finalScore * 100.0) / 100.0;

    String classification;
    if (roundedScore >= 0.75) {
      classification = "PACKAGE_BY_FEATURE";
    } else if (roundedScore >= 0.40) {
      classification = "HYBRID";
    } else {
      classification = "PACKAGE_BY_LAYER";
    }

    return new ScreamingMetric(
        roundedScore,
        domainCount,
        techCount,
        totalCount,
        classification,
        domainPackages,
        technicalPackages,
        hotspotsList);
  }

  public static boolean isTechnicalPackage(String packageName) {
    if (packageName == null || packageName.isBlank()) {
      return false;
    }
    String id = packageName.toLowerCase(Locale.ROOT);
    String leaf = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id;
    if (TECHNICAL_MARKERS.contains(leaf) || TECHNICAL_MARKERS.contains(id)) {
      return true;
    }
    String[] parts = id.split("\\.");
    for (String part : parts) {
      if (TECHNICAL_MARKERS.contains(part)) {
        return true;
      }
    }
    return false;
  }

  public static ScreamingMetric calculateScore(List<String> packageNames) {
    if (packageNames == null || packageNames.isEmpty()) {
      return ScreamingMetric.empty();
    }
    List<String> domainPackages = new ArrayList<>();
    List<String> technicalPackages = new ArrayList<>();
    for (String pkg : packageNames) {
      if (isTechnicalPackage(pkg)) {
        technicalPackages.add(pkg);
      } else {
        domainPackages.add(pkg);
      }
    }
    int domainCount = domainPackages.size();
    int techCount = technicalPackages.size();
    int totalCount = domainCount + techCount;
    if (totalCount == 0) {
      return ScreamingMetric.empty();
    }
    double rawRatio = (double) domainCount / totalCount;
    double roundedScore = Math.round(rawRatio * 100.0) / 100.0;
    String classification;
    if (roundedScore >= 0.75) {
      classification = "PACKAGE_BY_FEATURE";
    } else if (roundedScore >= 0.40) {
      classification = "HYBRID";
    } else {
      classification = "PACKAGE_BY_LAYER";
    }
    return new ScreamingMetric(
        roundedScore,
        domainCount,
        techCount,
        totalCount,
        classification,
        domainPackages,
        technicalPackages,
        Collections.emptyList());
  }

  private boolean isTechnical(String fullId, String leaf) {
    return isTechnicalPackage(fullId) || TECHNICAL_MARKERS.contains(leaf);
  }

  private boolean isFrameworkTarget(String target) {
    if (target == null) return false;
    for (String prefix : FRAMEWORK_PREFIXES) {
      if (target.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
