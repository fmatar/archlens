package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.mailbox.MailboxEnvelope;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.domain.mailbox.RefactorVerificationResult;
import io.slixes.archlens.engine.ArchitectureCompiler;
import io.slixes.archlens.mailbox.fs.FileMailboxService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyAndRollbackRefactorUseCaseTest {

  @Test
  void testCreateSnapshotAndRollback(@TempDir Path tempDir) throws IOException {
    Path archlensDir = tempDir.resolve(".archlens");
    Files.createDirectories(archlensDir);
    Path policyPath = archlensDir.resolve("policy.json");
    Files.writeString(policyPath, "{\"title\": \"Original Baseline Policy\"}");

    MailboxGateway mailbox = new FileMailboxService();
    ArchitectureCompiler dummyCompiler =
        (root, proposal) ->
            new ArchitectureGraph("Test", false, null, List.of(), List.of(), List.of());

    VerifyAndRollbackRefactorUseCase useCase =
        new VerifyAndRollbackRefactorUseCase(dummyCompiler, mailbox);

    // 1. Capture snapshot
    String snapshotId = useCase.createPreRefactorSnapshot(tempDir.toString());
    assertNotNull(snapshotId);
    assertTrue(snapshotId.startsWith("snap-"));

    // 2. Modify policy to simulate bad agent edit
    Files.writeString(policyPath, "{\"title\": \"Corrupted Policy by Agent\"}");
    assertEquals("{\"title\": \"Corrupted Policy by Agent\"}", Files.readString(policyPath));

    // 3. Rollback
    boolean rolledBack = useCase.rollbackToSnapshot(tempDir.toString(), snapshotId);
    assertTrue(rolledBack);
    assertEquals("{\"title\": \"Original Baseline Policy\"}", Files.readString(policyPath));

    // 4. Verify viewer mailbox ACK
    MailboxEnvelope viewerMailbox = mailbox.readMailbox(tempDir.toString(), false);
    assertFalse(viewerMailbox.queue().isEmpty());
    assertEquals("ROLLBACK_COMPLETE", viewerMailbox.queue().get(0).op());
  }

  @Test
  void testVerifyRefactoringDelta(@TempDir Path tempDir) {
    MailboxGateway mailbox = new FileMailboxService();

    // Compiler simulating 2 violations remaining
    ArchitectureCompiler compiler =
        (root, proposal) ->
            new ArchitectureGraph(
                "Test",
                false,
                null,
                List.of(),
                List.of(
                    new DependencyEdge("a", "b", DependencyEdge.Kind.DEPENDENCY, null, true),
                    new DependencyEdge("c", "d", DependencyEdge.Kind.DEPENDENCY, null, true)),
                List.of());

    VerifyAndRollbackRefactorUseCase useCase =
        new VerifyAndRollbackRefactorUseCase(compiler, mailbox);

    // Scenario A: Baseline had 5 violations -> Now 2 (Delta = -3) -> PASSED
    RefactorVerificationResult successResult =
        useCase.verifyRefactoring(tempDir.toString(), 5, "snap-123");
    assertTrue(successResult.passed());
    assertEquals(3, successResult.violationDelta());
    assertEquals(5, successResult.initialViolations());
    assertEquals(2, successResult.remainingViolations());

    // Scenario B: Baseline had 1 violation -> Now 2 (Regression) -> FAILED
    RefactorVerificationResult failResult =
        useCase.verifyRefactoring(tempDir.toString(), 1, "snap-123");
    assertFalse(failResult.passed());
    assertTrue(
        failResult.summary().contains("New outward Clean Architecture violations introduced"));
  }
}
