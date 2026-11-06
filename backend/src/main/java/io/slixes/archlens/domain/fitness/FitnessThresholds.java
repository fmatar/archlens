package io.slixes.archlens.domain.fitness;

/** Immutable configuration thresholds governing architectural fitness invariants. */
public record FitnessThresholds(
    int maxViolations,
    int maxCycles,
    double minScreamingScore,
    double maxMainSequenceDistance,
    int maxAfferentCoupling,
    int maxEfferentCoupling) {

  public FitnessThresholds {
    if (minScreamingScore <= 0.0) {
      minScreamingScore = 0.70;
    }
    if (maxMainSequenceDistance <= 0.0) {
      maxMainSequenceDistance = 0.35;
    }
    if (maxAfferentCoupling <= 0) {
      maxAfferentCoupling = 50;
    }
    if (maxEfferentCoupling <= 0) {
      maxEfferentCoupling = 30;
    }
  }

  public static FitnessThresholds defaultThresholds() {
    return new FitnessThresholds(0, 0, 0.70, 0.35, 50, 30);
  }
}
