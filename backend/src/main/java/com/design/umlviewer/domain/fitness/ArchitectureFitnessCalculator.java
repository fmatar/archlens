package com.design.umlviewer.domain.fitness;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.model.DependencyEdge;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

/**
 * Pure domain service calculating architectural fitness invariants, composite Architectural Fitness
 * Index (AFI), and historical regression trends. Fully decoupled from external frameworks and I/O.
 */
@ApplicationScoped
public class ArchitectureFitnessCalculator {

  public FitnessEvaluation evaluate(ArchitectureGraph graph, FitnessThresholds thresholds) {
    if (thresholds == null) {
      thresholds = FitnessThresholds.defaultThresholds();
    }

    // 1. Concentric Dependency Rule
    long violations = 0;
    if (graph.edges() != null) {
      violations = graph.edges().stream().filter(DependencyEdge::isViolating).count();
    }
    boolean concentricPassed = violations <= thresholds.maxViolations();
    double concentricScore = concentricPassed ? 1.0 : Math.max(0.0, 1.0 - (violations * 0.25));
    String concentricMsg =
        concentricPassed
            ? "Concentric Ring Rule satisfied: 0 outward breaches."
            : violations + " outward violation(s) breach concentric dependency rules.";
    FitnessRule concentricRule =
        new FitnessRule(
            "CONCENTRIC_DEPENDENCY_RULE",
            "Concentric Dependency Rule",
            "Inner layers must not depend on outer layers (Zero outward dependencies).",
            thresholds.maxViolations(),
            violations,
            concentricPassed,
            concentricMsg);

    // 2. Acyclic Dependencies Principle (ADP)
    int cycles = (graph.cycles() != null) ? graph.cycles().size() : 0;
    boolean adpPassed = cycles <= thresholds.maxCycles();
    double adpScore = adpPassed ? 1.0 : Math.max(0.0, 1.0 - (cycles * 0.35));
    String adpMsg =
        adpPassed
            ? "Acyclic Dependencies Principle satisfied: 0 package cycles."
            : cycles + " package cycle(s) violate Acyclic Dependencies Principle (ADP).";
    FitnessRule adpRule =
        new FitnessRule(
            "ACYCLIC_DEPENDENCIES_RULE",
            "Acyclic Dependencies Principle (ADP)",
            "Component dependency structure must be a Directed Acyclic Graph (DAG).",
            thresholds.maxCycles(),
            cycles,
            adpPassed,
            adpMsg);

    // 3. Screaming Architecture Score
    double sas = 0.0;
    if (graph.screamingMetric() != null && graph.screamingMetric().totalPackageCount() > 0) {
      sas = graph.screamingMetric().score();
    }
    boolean screamingPassed = sas >= thresholds.minScreamingScore();
    double screamingScore =
        thresholds.minScreamingScore() > 0
            ? Math.min(1.0, sas / thresholds.minScreamingScore())
            : 1.0;
    String screamingMsg =
        screamingPassed
            ? String.format(
                Locale.ROOT,
                "Screaming Architecture Score is %.0f%% (>= %.0f%% threshold).",
                sas * 100,
                thresholds.minScreamingScore() * 100)
            : String.format(
                Locale.ROOT,
                "Screaming Architecture Score %.0f%% is below minimum %.0f%%.",
                sas * 100,
                thresholds.minScreamingScore() * 100);
    FitnessRule screamingRule =
        new FitnessRule(
            "SCREAMING_ARCHITECTURE_RULE",
            "Screaming Architecture Invariant",
            "Architecture must scream domain feature use cases rather than framework layers.",
            thresholds.minScreamingScore(),
            sas,
            screamingPassed,
            screamingMsg);

    // 4. Main Sequence Distance
    double maxDistance = calculateMaxMainSequenceDistance(graph);
    boolean distancePassed = maxDistance <= thresholds.maxMainSequenceDistance();
    double distanceScore =
        thresholds.maxMainSequenceDistance() > 0
            ? Math.max(0.0, 1.0 - (maxDistance / (2.0 * thresholds.maxMainSequenceDistance())))
            : 1.0;
    String distanceMsg =
        distancePassed
            ? String.format(
                Locale.ROOT,
                "Max distance from Main Sequence is %.2f (<= %.2f threshold).",
                maxDistance,
                thresholds.maxMainSequenceDistance())
            : String.format(
                Locale.ROOT,
                "Max distance %.2f exceeds %.2f threshold (Zone of Pain / Uselessness).",
                maxDistance,
                thresholds.maxMainSequenceDistance());
    FitnessRule distanceRule =
        new FitnessRule(
            "MAIN_SEQUENCE_DISTANCE_RULE",
            "Main Sequence Balance Invariant",
            "Components must remain in the balanced corridor (D <= threshold) avoiding Zones of Pain and Uselessness.",
            thresholds.maxMainSequenceDistance(),
            maxDistance,
            distancePassed,
            distanceMsg);

    List<FitnessRule> rules = List.of(concentricRule, adpRule, screamingRule, distanceRule);
    int passedCount = (int) rules.stream().filter(FitnessRule::passed).count();
    boolean overallPassed = passedCount == rules.size();

    double weightedScore =
        (concentricScore * 0.35)
            + (adpScore * 0.25)
            + (screamingScore * 0.20)
            + (distanceScore * 0.20);
    double finalScore = Math.round(weightedScore * 100.0) / 100.0;

    String grade;
    if (finalScore >= 0.90) {
      grade = "A";
    } else if (finalScore >= 0.80) {
      grade = "B";
    } else if (finalScore >= 0.70) {
      grade = "C";
    } else if (finalScore >= 0.60) {
      grade = "D";
    } else {
      grade = "F";
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("violations", violations);
    summary.put("cycles", cycles);
    summary.put("screamingScore", sas);
    summary.put("maxDistance", maxDistance);

    return new FitnessEvaluation(
        finalScore, overallPassed, grade, passedCount, rules.size(), rules, summary);
  }

  public FitnessHistoryTrend calculateTrend(List<HistoricalFitnessSnapshot> history) {
    if (history == null || history.isEmpty()) {
      return new FitnessHistoryTrend("STABLE", 0.0, List.of());
    }
    if (history.size() == 1) {
      return new FitnessHistoryTrend("STABLE", 0.0, history);
    }

    HistoricalFitnessSnapshot oldest = history.get(0);
    HistoricalFitnessSnapshot newest = history.get(history.size() - 1);
    double rawDelta = newest.fitnessScore() - oldest.fitnessScore();
    double scoreDelta = Math.round(rawDelta * 100.0) / 100.0;

    String trend;
    if (scoreDelta > 0.03) {
      trend = "IMPROVING";
    } else if (scoreDelta < -0.03) {
      trend = "DEGRADING";
    } else {
      trend = "STABLE";
    }

    return new FitnessHistoryTrend(trend, scoreDelta, history);
  }

  private double calculateMaxMainSequenceDistance(ArchitectureGraph graph) {
    if (graph.components() == null || graph.components().isEmpty()) {
      return 0.0;
    }

    List<DependencyEdge> edges = graph.edges() != null ? graph.edges() : List.of();
    double maxDistance = 0.0;

    for (ComponentNode comp : graph.components()) {
      List<ClassNode> classes = comp.classes() != null ? comp.classes() : List.of();
      if (classes.isEmpty()) {
        continue;
      }

      Set<String> compClassKeys = new HashSet<>();
      int abstractCount = 0;
      for (ClassNode cls : classes) {
        compClassKeys.add(cls.id());
        compClassKeys.add(cls.name());
        if (cls.packageName() != null) {
          compClassKeys.add(cls.packageName() + "." + cls.name());
        }
        if (cls.stereotype() == ClassNode.Stereotype.INTERFACE
            || cls.stereotype() == ClassNode.Stereotype.ABSTRACT) {
          abstractCount++;
        }
      }

      double abstractness = (double) abstractCount / classes.size();

      int ca = 0;
      int ce = 0;
      for (DependencyEdge edge : edges) {
        boolean fromInside = compClassKeys.contains(edge.from());
        boolean toInside = compClassKeys.contains(edge.to());
        if (!fromInside && toInside) {
          ca++;
        } else if (fromInside && !toInside) {
          ce++;
        }
      }

      double instability = (ca + ce > 0) ? (double) ce / (ca + ce) : 0.0;
      double distance = Math.abs(abstractness + instability - 1.0);
      if (distance > maxDistance) {
        maxDistance = distance;
      }
    }

    return Math.round(maxDistance * 100.0) / 100.0;
  }
}
