package com.design.umlviewer.metrics;

import com.design.umlviewer.domain.model.CrapScore;
import java.util.List;

/** Metric computation interface for Change Risk Anti-Patterns (CRAP) scores. */
public interface CrapCalculator {

  /** Calculates CRAP score for an individual method based on cyclomatic complexity and coverage. */
  double calculateMethodCrap(int cyclomaticComplexity, double coverage);

  /** Aggregates a list of method CRAP scores into mean, max, and variance distribution. */
  CrapScore aggregate(List<Double> craps);
}
