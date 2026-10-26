package io.slixes.archlens.domain.mailbox;

/** Result record for verifying architectural refactoring operations. */
public record RefactorVerificationResult(
    boolean passed,
    int initialViolations,
    int remainingViolations,
    int violationDelta,
    String summary,
    boolean canRollback,
    String snapshotId) {

  public static RefactorVerificationResult ofSuccess(
      int initial, int remaining, String snapshotId) {
    int delta = initial - remaining;
    return new RefactorVerificationResult(
        true,
        initial,
        remaining,
        delta,
        "Refactoring verified: violations reduced by " + delta,
        snapshotId != null && !snapshotId.isBlank(),
        snapshotId);
  }

  public static RefactorVerificationResult ofFailure(
      int initial, int remaining, String reason, String snapshotId) {
    int delta = initial - remaining;
    return new RefactorVerificationResult(
        false,
        initial,
        remaining,
        delta,
        "Refactoring verification failed: " + reason,
        snapshotId != null && !snapshotId.isBlank(),
        snapshotId);
  }
}
