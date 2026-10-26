package io.slixes.archlens.usecase;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.domain.mailbox.RefactorVerificationResult;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Use case orchestrating transactional refactoring verification and worktree snapshot rollbacks.
 */
@ApplicationScoped
public class VerifyAndRollbackRefactorUseCase {

  private static final String SNAPSHOTS_DIR = ".archlens/snapshots";

  @Inject ArchitectureCompiler compiler;
  @Inject MailboxGateway mailboxGateway;

  public VerifyAndRollbackRefactorUseCase() {}

  public VerifyAndRollbackRefactorUseCase(
      ArchitectureCompiler compiler, MailboxGateway mailboxGateway) {
    this.compiler = compiler;
    this.mailboxGateway = mailboxGateway;
  }

  /** Captures an isolated pre-refactor snapshot of the workspace configuration and policy. */
  public String createPreRefactorSnapshot(String projectRoot) throws IOException {
    String snapshotId =
        "snap-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    File snapDir = new File(root, SNAPSHOTS_DIR + "/" + snapshotId);
    Files.createDirectories(snapDir.toPath());

    File policy = new File(root, ".archlens/policy.json");
    if (policy.exists()) {
      Files.copy(
          policy.toPath(),
          snapDir.toPath().resolve("policy.json"),
          StandardCopyOption.REPLACE_EXISTING);
    }
    return snapshotId;
  }

  /**
   * Verifies that an executed refactoring operation reduced or maintained outward violations
   * without regressions.
   */
  public RefactorVerificationResult verifyRefactoring(
      String projectRoot, int baselineViolations, String snapshotId) {
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
      return RefactorVerificationResult.ofFailure(
          baselineViolations,
          currentViolations,
          "New outward Clean Architecture violations introduced (current: "
              + currentViolations
              + ", baseline: "
              + baselineViolations
              + ")",
          snapshotId);
    }

    return RefactorVerificationResult.ofSuccess(baselineViolations, currentViolations, snapshotId);
  }

  /** Rolls back workspace policy to the captured pre-refactor snapshot. */
  public boolean rollbackToSnapshot(String projectRoot, String snapshotId) throws IOException {
    if (snapshotId == null || snapshotId.isBlank()) {
      return false;
    }
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    File snapDir = new File(root, SNAPSHOTS_DIR + "/" + snapshotId);
    if (!snapDir.exists()) {
      return false;
    }

    File backupPolicy = new File(snapDir, "policy.json");
    if (backupPolicy.exists()) {
      File destPolicy = new File(root, ".archlens/policy.json");
      Files.createDirectories(destPolicy.getParentFile().toPath());
      Files.copy(backupPolicy.toPath(), destPolicy.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // Acknowledge rollback in viewer mailbox
    mailboxGateway.appendCommand(
        projectRoot,
        false,
        "ROLLBACK_COMPLETE",
        Map.of("snapshotId", snapshotId),
        Map.of("status", "SUCCESS", "message", "Workspace restored to snapshot " + snapshotId));

    return true;
  }
}
