package io.slixes.archlens.domain.fitness;

import java.util.List;

/** Historical architectural fitness regression trend. */
public record FitnessHistoryTrend(
    String trendDirection, double scoreDelta, List<HistoricalFitnessSnapshot> history) {}
