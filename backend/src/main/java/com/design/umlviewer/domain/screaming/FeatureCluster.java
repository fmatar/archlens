package com.design.umlviewer.domain.screaming;

import java.util.List;

/**
 * Represents an automatically identified domain feature cluster grouping related classes across
 * disparate technical layers.
 */
public record FeatureCluster(
    String featureName,
    String proposedPackageName,
    List<String> classNames,
    List<String> sourcePackages,
    int classCount) {}
