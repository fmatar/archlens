package com.design.umlviewer.domain.fitness;

/** Immutable evaluation record for a single architectural fitness invariant rule. */
public record FitnessRule(
    String id,
    String name,
    String description,
    double threshold,
    double actualValue,
    boolean passed,
    String failureMessage) {}
