package io.slixes.archlens.domain.fitness;

import java.util.List;
import java.util.Map;

/** Composite evaluation of an architecture against all fitness invariants. */
public record FitnessEvaluation(
    double fitnessScore,
    boolean overallPassed,
    String grade,
    int passedRuleCount,
    int totalRuleCount,
    List<FitnessRule> rules,
    Map<String, Object> summaryMetrics) {}
