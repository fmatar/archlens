package com.design.umlviewer.domain.screaming;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.model.ScreamingMetric;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Intelligent refactoring assistant (Level 0 Domain) that analyzes technical layered architectures
 * and automatically synthesizes package-by-feature domain clusters.
 */
@ApplicationScoped
public class ScreamingMigrationAssistant {

  private static final List<String> TECHNICAL_SUFFIXES =
      List.of(
          "Controllers",
          "Controller",
          "Resources",
          "Resource",
          "Endpoints",
          "Endpoint",
          "Services",
          "ServiceImpl",
          "Service",
          "Repositories",
          "Repository",
          "Repos",
          "Repo",
          "Daos",
          "Dao",
          "Dtos",
          "Dto",
          "Entities",
          "Entity",
          "Models",
          "Model",
          "Views",
          "View",
          "Handlers",
          "Handler",
          "Validators",
          "Validator",
          "Adapters",
          "Adapter",
          "Helpers",
          "Helper",
          "Utils",
          "Util");

  private static final List<String> TECHNICAL_PREFIXES =
      List.of("Default", "Abstract", "Base", "Postgres", "Jpa", "Mongo", "Sql", "Http");

  public ScreamingMigrationProposal generateProposal(ArchitectureGraph graph) {
    if (graph == null || graph.components() == null || graph.components().isEmpty()) {
      return new ScreamingMigrationProposal(
          1.0,
          1.0,
          "PACKAGE_BY_FEATURE",
          "PACKAGE_BY_FEATURE",
          Collections.emptyList(),
          Collections.emptyMap(),
          Collections.emptyList());
    }

    ScreamingMetric currentMetric =
        (graph.screamingMetric() != null && graph.screamingMetric().totalPackageCount() > 0)
            ? graph.screamingMetric()
            : new ScreamingArchitectureAnalyzer().analyze(graph);

    Set<String> technicalPkgSet =
        currentMetric.technicalPackages().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

    if (technicalPkgSet.isEmpty()) {
      return new ScreamingMigrationProposal(
          currentMetric.score(),
          currentMetric.score(),
          currentMetric.classification(),
          currentMetric.classification(),
          Collections.emptyList(),
          Collections.emptyMap(),
          Collections.emptyList());
    }

    List<ClassNode> technicalClasses = new ArrayList<>();
    Map<String, Set<String>> pkgToClasses = new HashMap<>();

    for (ComponentNode comp : graph.components()) {
      for (ClassNode cls : comp.classes()) {
        String pkgLower = cls.packageName() != null ? cls.packageName().toLowerCase() : "";
        boolean isTech =
            technicalPkgSet.contains(pkgLower)
                || technicalPkgSet.contains(comp.label().toLowerCase())
                || ScreamingArchitectureAnalyzer.isTechnicalPackage(pkgLower)
                || ScreamingArchitectureAnalyzer.isTechnicalPackage(comp.label());

        if (isTech) {
          technicalClasses.add(cls);
          pkgToClasses.computeIfAbsent(cls.packageName(), k -> new HashSet<>()).add(cls.id());
        }
      }
    }

    String commonPrefix = findCommonBasePackage(technicalClasses);

    Map<String, List<ClassNode>> tokenToClasses = new LinkedHashMap<>();
    for (ClassNode cls : technicalClasses) {
      String token = extractFeatureToken(cls.name());
      if (token != null && token.length() >= 3) {
        tokenToClasses.computeIfAbsent(token, k -> new ArrayList<>()).add(cls);
      }
    }

    List<FeatureCluster> clusters = new ArrayList<>();
    Map<String, String> stagedClassMoves = new LinkedHashMap<>();
    Set<String> clusteredClassIds = new HashSet<>();

    for (Map.Entry<String, List<ClassNode>> entry : tokenToClasses.entrySet()) {
      String token = entry.getKey();
      List<ClassNode> classes = entry.getValue();

      Set<String> sourcePkgs =
          classes.stream().map(ClassNode::packageName).collect(Collectors.toSet());

      // Valid cluster: at least 2 classes or spanning at least 2 distinct technical packages
      if (classes.size() >= 2 || sourcePkgs.size() >= 2) {
        String featureName = Character.toUpperCase(token.charAt(0)) + token.substring(1);
        String proposedPackage =
            commonPrefix.isEmpty() ? token.toLowerCase() : commonPrefix + "." + token.toLowerCase();

        List<String> classNames = classes.stream().map(ClassNode::name).toList();
        List<String> sourcePackageList = new ArrayList<>(sourcePkgs);
        Collections.sort(sourcePackageList);

        FeatureCluster cluster =
            new FeatureCluster(
                featureName, proposedPackage, classNames, sourcePackageList, classes.size());

        clusters.add(cluster);

        for (ClassNode cls : classes) {
          stagedClassMoves.put(cls.id(), proposedPackage);
          clusteredClassIds.add(cls.id());
        }
      }
    }

    clusters.sort(Comparator.comparing(FeatureCluster::featureName));

    List<String> unclustered = new ArrayList<>();
    for (ClassNode cls : technicalClasses) {
      if (!clusteredClassIds.contains(cls.id())) {
        unclustered.add(cls.name());
      }
    }
    Collections.sort(unclustered);

    // Calculate projected score
    Set<String> projectedPackages = new HashSet<>();
    for (ComponentNode comp : graph.components()) {
      for (ClassNode cls : comp.classes()) {
        String targetPkg = stagedClassMoves.getOrDefault(cls.id(), cls.packageName());
        if (targetPkg != null && !targetPkg.isBlank()) {
          projectedPackages.add(targetPkg);
        }
      }
    }

    ScreamingMetric projectedMetric =
        ScreamingArchitectureAnalyzer.calculateScore(new ArrayList<>(projectedPackages));

    return new ScreamingMigrationProposal(
        currentMetric.score(),
        projectedMetric.score(),
        currentMetric.classification(),
        projectedMetric.classification(),
        clusters,
        stagedClassMoves,
        unclustered);
  }

  static String extractFeatureToken(String className) {
    if (className == null || className.isBlank()) {
      return null;
    }

    String candidate = className;

    for (String prefix : TECHNICAL_PREFIXES) {
      if (candidate.startsWith(prefix) && candidate.length() > prefix.length() + 2) {
        candidate = candidate.substring(prefix.length());
        break;
      }
    }

    for (String suffix : TECHNICAL_SUFFIXES) {
      if (candidate.endsWith(suffix) && candidate.length() > suffix.length()) {
        candidate = candidate.substring(0, candidate.length() - suffix.length());
        break;
      }
    }

    // Clean any trailing numbers or underscores
    candidate = candidate.replaceAll("[0-9_]+$", "");

    if (candidate.length() < 3
        || TECHNICAL_PREFIXES.contains(candidate)
        || TECHNICAL_SUFFIXES.contains(candidate)
        || ScreamingArchitectureAnalyzer.isTechnicalPackage(candidate)) {
      return null;
    }

    return candidate;
  }

  private String findCommonBasePackage(List<ClassNode> classes) {
    Set<String> packages =
        classes.stream()
            .map(ClassNode::packageName)
            .filter(Objects::nonNull)
            .filter(p -> !p.isBlank())
            .collect(Collectors.toSet());

    if (packages.isEmpty()) {
      return "";
    }

    String common = null;
    for (String pkg : packages) {
      // Strip technical segment if it's the leaf
      String parentPkg = pkg;
      int lastDot = pkg.lastIndexOf('.');
      if (lastDot > 0) {
        String leaf = pkg.substring(lastDot + 1);
        if (ScreamingArchitectureAnalyzer.isTechnicalPackage(leaf)) {
          parentPkg = pkg.substring(0, lastDot);
        }
      }

      if (common == null) {
        common = parentPkg;
      } else {
        common = commonPrefix(common, parentPkg);
      }
    }

    return common != null ? common : "";
  }

  private String commonPrefix(String a, String b) {
    String[] partsA = a.split("\\.");
    String[] partsB = b.split("\\.");
    List<String> matched = new ArrayList<>();
    for (int i = 0; i < Math.min(partsA.length, partsB.length); i++) {
      if (partsA[i].equalsIgnoreCase(partsB[i])) {
        matched.add(partsA[i]);
      } else {
        break;
      }
    }
    return String.join(".", matched);
  }
}
