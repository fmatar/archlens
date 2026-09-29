package com.design.umlviewer.domain.screaming;

import java.util.List;
import java.util.Map;

/**
 * Migration proposal recommending the reorganization of technical layered packages into
 * package-by-feature domain slices, calculating before-and-after Screaming Architecture Scores
 * (SAS).
 */
public record ScreamingMigrationProposal(
    double currentScore,
    double projectedScore,
    String currentClassification,
    String projectedClassification,
    List<FeatureCluster> clusters,
    Map<String, String> stagedClassMoves,
    List<String> unclusteredClasses) {}
