package com.design.umlviewer.domain.fitness;

/** Historical architectural fitness point representing a snapshot or git tag. */
public record HistoricalFitnessSnapshot(
    String snapshotId,
    String label,
    String date,
    double fitnessScore,
    String grade,
    int violationsCount,
    int cyclesCount,
    double screamingScore,
    double maxDistance) {}
