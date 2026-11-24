package io.slixes.archlens.usecase;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.domain.mailbox.RefactorVerificationResult;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Use case orchestrating transactional refactoring verification and workspace source/policy
 * snapshot rollbacks through {@link FilesystemGateway} and {@link TestRunnerGateway} ports.
 */
@ApplicationScoped
public class VerifyAndRollbackRefactorUseCase {

  private static final Logger LOG = Logger.getLogger(VerifyAndRollbackRefactorUseCase.class);

  public record RollbackResult(boolean restored, String trashDir, List<String> trashedFiles) {}

  /** Outcome of running the target project's automated test suite during refactor verification. */
  public record TestExecutionOutcome(
      boolean executed, boolean passed, String command, int exitCode, String outputSummary) {}

  private ArchitectureCompiler compiler;
  private MailboxGateway mailboxGateway;
  private FilesystemGateway filesystemGateway;
  private TestRunnerGateway testRunnerGateway;

  VerifyAndRollbackRefactorUseCase() {}

  @Inject
  public VerifyAndRollbackRefactorUseCase(
      ArchitectureCompiler compiler,
      MailboxGateway mailboxGateway,
      FilesystemGateway filesystemGateway,
      TestRunnerGateway testRunnerGateway) {
    this.compiler = compiler;
    this.mailboxGateway = mailboxGateway;
    this.filesystemGateway = filesystemGateway;
    this.testRunnerGateway = testRunnerGateway;
  }

  /**
   * Captures an isolated pre-refactor snapshot of both `.archlens/policy.json` and the project's
   * source code files so agent refactorings can be atomically rolled back.
   */
  public String createPreRefactorSnapshot(String projectRoot) throws IOException {
    return filesystemGateway.createPreRefactorSnapshot(projectRoot);
  }

  /**
   * Verifies that an executed refactoring operation reduced or maintained outward violations
   * without introducing new Clean Architecture violations or ADP package cycles.
   */
  public RefactorVerificationResult verifyRefactoring(
      String projectRoot, int baselineViolations, String snapshotId) {
    return verifyRefactoring(projectRoot, baselineViolations, -1, snapshotId, false);
  }

  /**
   * Verifies that an executed refactoring operation reduced or maintained outward violations and
   * did not introduce new ADP package dependency cycles.
   */
  public RefactorVerificationResult verifyRefactoring(
      String projectRoot, int baselineViolations, int baselineCycles, String snapshotId) {
    return verifyRefactoring(projectRoot, baselineViolations, baselineCycles, snapshotId, false);
  }

  /**
   * Verifies that an executed refactoring operation reduced or maintained outward violations, did
   * not introduce new ADP package dependency cycles, and optionally passes the project's automated
   * test suite before acknowledging completion in `.archlens/to-viewer.json`.
   */
  public RefactorVerificationResult verifyRefactoring(
      String projectRoot,
      int baselineViolations,
      int baselineCycles,
      String snapshotId,
      boolean runTests) {
    ArchitectureGraph currentGraph;
    try {
      currentGraph = compiler.compileGraph(projectRoot, null);
    } catch (IOException e) {
      return RefactorVerificationResult.ofFailure(
          baselineViolations,
          baselineViolations,
          "Failed to compile graph during verification: " + e.getMessage(),
          snapshotId);
    }
    int currentViolations =
        (int) currentGraph.edges().stream().filter(e -> e.isViolating()).count();

    if (currentViolations > baselineViolations) {
      RefactorVerificationResult failure =
          RefactorVerificationResult.ofFailure(
              baselineViolations,
              currentViolations,
              "New outward Clean Architecture violations introduced (current: "
                  + currentViolations
                  + ", baseline: "
                  + baselineViolations
                  + ")",
              snapshotId);
      recordVerificationTelemetry(projectRoot, failure);
      return failure;
    }

    int currentCycles = currentGraph.cycles() != null ? currentGraph.cycles().size() : 0;
    if (baselineCycles >= 0 && currentCycles > baselineCycles) {
      RefactorVerificationResult cycleFailure =
          RefactorVerificationResult.ofFailure(
              baselineViolations,
              currentViolations,
              "New package dependency cycle (ADP) introduced (current: "
                  + currentCycles
                  + ", baseline: "
                  + baselineCycles
                  + ")",
              snapshotId);
      recordVerificationTelemetry(projectRoot, cycleFailure);
      return cycleFailure;
    }

    if (runTests && testRunnerGateway != null) {
      TestExecutionOutcome testOutcome = testRunnerGateway.runTests(projectRoot);
      if (testOutcome.executed() && !testOutcome.passed()) {
        RefactorVerificationResult testFailure =
            RefactorVerificationResult.ofFailure(
                baselineViolations,
                currentViolations,
                "Project test suite failed ("
                    + testOutcome.command()
                    + ", exit code "
                    + testOutcome.exitCode()
                    + "): "
                    + testOutcome.outputSummary(),
                snapshotId);
        recordVerificationTelemetry(projectRoot, testFailure);
        return testFailure;
      }
      if (testOutcome.executed() && testOutcome.passed()) {
        int delta = baselineViolations - currentViolations;
        RefactorVerificationResult verifiedWithTests =
            new RefactorVerificationResult(
                true,
                baselineViolations,
                currentViolations,
                delta,
                "Refactoring verified: violations reduced by "
                    + delta
                    + " (test suite passed: "
                    + testOutcome.command()
                    + ")",
                snapshotId != null && !snapshotId.isBlank(),
                snapshotId);
        recordVerificationTelemetry(projectRoot, verifiedWithTests);
        return verifiedWithTests;
      }
    }

    RefactorVerificationResult success =
        RefactorVerificationResult.ofSuccess(baselineViolations, currentViolations, snapshotId);
    recordVerificationTelemetry(projectRoot, success);
    return success;
  }

  /** Delegates build-tool test command discovery to the configured {@link TestRunnerGateway}. */
  public List<String> detectTestCommand(Path rootPath) {
    return testRunnerGateway != null ? testRunnerGateway.detectTestCommand(rootPath) : List.of();
  }

  private void recordVerificationTelemetry(
      String projectRoot, RefactorVerificationResult verification) {
    if (mailboxGateway == null) {
      return;
    }
    try {
      mailboxGateway.appendCommand(
          projectRoot,
          false,
          "VERIFY_COMPLETE",
          Map.of("snapshotId", verification.snapshotId() != null ? verification.snapshotId() : ""),
          Map.of(
              "status",
              verification.passed() ? "SUCCESS" : "FAILED",
              "passed",
              verification.passed(),
              "violationDelta",
              verification.violationDelta(),
              "remainingViolations",
              verification.remainingViolations(),
              "summary",
              verification.summary()));
    } catch (IOException e) {
      LOG.warnf(
          "Failed to append verification telemetry to mailbox for %s: %s",
          projectRoot, e.getMessage());
    }
  }

  /**
   * Rolls back both `.archlens/policy.json` and modified/created source files to the captured
   * pre-refactor snapshot.
   */
  public boolean rollbackToSnapshot(String projectRoot, String snapshotId) throws IOException {
    return rollback(projectRoot, snapshotId).restored();
  }

  /**
   * Rolls back `.archlens/policy.json` and modified source files, moving any post-snapshot source
   * files into `.archlens/trash/<snapshotId>/` so uncommitted work stays recoverable (#228).
   */
  public RollbackResult rollback(String projectRoot, String snapshotId) throws IOException {
    RollbackResult result = filesystemGateway.restoreSnapshot(projectRoot, snapshotId);
    if (result.restored() && mailboxGateway != null) {
      mailboxGateway.appendCommand(
          projectRoot,
          false,
          "ROLLBACK_COMPLETE",
          Map.of("snapshotId", snapshotId),
          Map.of(
              "status",
              "SUCCESS",
              "message",
              "Workspace restored to snapshot " + snapshotId,
              "trashDir",
              result.trashDir(),
              "trashedFiles",
              result.trashedFiles()));
    }
    return result;
  }
}
