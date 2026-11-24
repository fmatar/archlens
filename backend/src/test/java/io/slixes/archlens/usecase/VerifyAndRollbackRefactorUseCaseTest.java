package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.PackageCycle;
import io.slixes.archlens.domain.mailbox.MailboxEnvelope;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.domain.mailbox.RefactorVerificationResult;
import io.slixes.archlens.engine.ArchitectureCompiler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerifyAndRollbackRefactorUseCaseTest {

  private static final class InMemoryMailbox implements MailboxGateway {
    final List<MailboxEnvelope.MailboxCommand> viewerEvents = new ArrayList<>();

    @Override
    public MailboxEnvelope readMailbox(String projectRoot, boolean toAgent) {
      return new MailboxEnvelope(viewerEvents.size(), List.copyOf(viewerEvents));
    }

    @Override
    public MailboxEnvelope.MailboxCommand appendCommand(
        String projectRoot,
        boolean toAgent,
        String op,
        Map<String, Object> target,
        Map<String, Object> payload) {
      MailboxEnvelope.MailboxCommand cmd =
          new MailboxEnvelope.MailboxCommand(viewerEvents.size() + 1L, op, target, payload);
      if (!toAgent) {
        viewerEvents.add(cmd);
      }
      return cmd;
    }

    @Override
    public MailboxEnvelope.MailboxCommand popOldest(String projectRoot, boolean toAgent) {
      return viewerEvents.isEmpty() ? null : viewerEvents.remove(0);
    }
  }

  private static final FilesystemGateway STUB_FS =
      new FilesystemGateway() {
        @Override
        public Map<String, Object> listProjects() {
          return Map.of();
        }

        @Override
        public Map<String, Object> listDirectories(String rawPath, List<Path> allowedRoots) {
          return Map.of();
        }

        @Override
        public Map<String, Object> pickDirectory() {
          return Map.of();
        }

        @Override
        public Map<String, Object> readConfinedSourceCode(
            String filePath, int line, String projectRoot) {
          return Map.of();
        }

        @Override
        public String createPreRefactorSnapshot(String projectRoot) {
          return "snap-1700000000000-01234567";
        }

        @Override
        public VerifyAndRollbackRefactorUseCase.RollbackResult restoreSnapshot(
            String projectRoot, String snapshotId) {
          if (snapshotId == null || snapshotId.isBlank()) {
            return new VerifyAndRollbackRefactorUseCase.RollbackResult(false, "", List.of());
          }
          return new VerifyAndRollbackRefactorUseCase.RollbackResult(
              true, ".archlens/trash/" + snapshotId, List.of("src/Extra.java"));
        }
      };

  @Test
  void delegatesSnapshotAndRollbackAndAppendsMailboxAck() throws IOException {
    InMemoryMailbox mailbox = new InMemoryMailbox();
    ArchitectureCompiler cleanCompiler =
        (root, proposal) ->
            new ArchitectureGraph("Test", false, null, List.of(), List.of(), List.of());

    TestRunnerGateway lambdaGateway =
        root ->
            new VerifyAndRollbackRefactorUseCase.TestExecutionOutcome(false, true, "none", 0, "");
    assertEquals(List.of(), lambdaGateway.detectTestCommand(Path.of("/virtual/project")));

    VerifyAndRollbackRefactorUseCase useCase =
        new VerifyAndRollbackRefactorUseCase(
            cleanCompiler,
            mailbox,
            STUB_FS,
            new TestRunnerGateway() {
              @Override
              public VerifyAndRollbackRefactorUseCase.TestExecutionOutcome runTests(
                  String projectRoot) {
                return new VerifyAndRollbackRefactorUseCase.TestExecutionOutcome(
                    false, true, "none", 0, "");
              }

              @Override
              public List<String> detectTestCommand(Path rootPath) {
                return List.of("mvn", "test");
              }
            });

    String snapId = useCase.createPreRefactorSnapshot("/virtual/project");
    assertEquals("snap-1700000000000-01234567", snapId);
    assertEquals(List.of("mvn", "test"), useCase.detectTestCommand(Path.of("/virtual/project")));

    assertFalse(useCase.rollbackToSnapshot("/virtual/project", ""));
    assertTrue(mailbox.viewerEvents.isEmpty());

    assertTrue(useCase.rollbackToSnapshot("/virtual/project", snapId));
    assertEquals(1, mailbox.viewerEvents.size());
    assertEquals("ROLLBACK_COMPLETE", mailbox.viewerEvents.get(0).op());
    assertEquals(
        ".archlens/trash/" + snapId, mailbox.viewerEvents.get(0).payload().get("trashDir"));
  }

  @Test
  void evaluatesViolationsCyclesTestOutcomesAndNullCollaborators() throws IOException {
    InMemoryMailbox mailbox = new InMemoryMailbox();

    ArchitectureCompiler twoViolations =
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
        new VerifyAndRollbackRefactorUseCase(twoViolations, mailbox, STUB_FS, null);
    assertEquals(List.of(), useCase.detectTestCommand(Path.of("/virtual/project")));

    RefactorVerificationResult passed = useCase.verifyRefactoring("/virtual/project", 5, "snap-1");
    assertTrue(passed.passed());
    assertEquals(3, passed.violationDelta());

    RefactorVerificationResult failedViolations =
        useCase.verifyRefactoring("/virtual/project", 1, "snap-1");
    assertFalse(failedViolations.passed());
    assertTrue(failedViolations.summary().contains("New outward Clean Architecture violations"));

    ArchitectureCompiler cycleCompiler =
        (root, proposal) ->
            new ArchitectureGraph(
                "Test",
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(new PackageCycle(List.of("a", "b", "a"))),
                null);
    VerifyAndRollbackRefactorUseCase cycleUseCase =
        new VerifyAndRollbackRefactorUseCase(cycleCompiler, mailbox, STUB_FS, null);
    RefactorVerificationResult cycleFail =
        cycleUseCase.verifyRefactoring("/virtual/project", 0, 0, "snap-cycle");
    assertFalse(cycleFail.passed());
    assertTrue(cycleFail.summary().contains("package dependency cycle"));

    ArchitectureCompiler cleanCompiler =
        (root, proposal) ->
            new ArchitectureGraph("Test", false, null, List.of(), List.of(), List.of());

    VerifyAndRollbackRefactorUseCase passingTests =
        new VerifyAndRollbackRefactorUseCase(
            cleanCompiler,
            mailbox,
            STUB_FS,
            root ->
                new VerifyAndRollbackRefactorUseCase.TestExecutionOutcome(
                    true, true, "mvn -B -ntp test", 0, "OK"));
    RefactorVerificationResult okWithTests =
        passingTests.verifyRefactoring("/virtual/project", 2, 0, "snap-pass", true);
    assertTrue(okWithTests.passed());
    assertTrue(okWithTests.summary().contains("test suite passed: mvn -B -ntp test"));

    VerifyAndRollbackRefactorUseCase failingTests =
        new VerifyAndRollbackRefactorUseCase(
            cleanCompiler,
            null,
            STUB_FS,
            root ->
                new VerifyAndRollbackRefactorUseCase.TestExecutionOutcome(
                    true, false, "mvn -B -ntp test", 1, "OrderTest FAILED"));
    RefactorVerificationResult failWithTests =
        failingTests.verifyRefactoring("/virtual/project", 2, 0, "snap-fail", true);
    assertFalse(failWithTests.passed());
    assertTrue(failWithTests.summary().contains("OrderTest FAILED"));
    assertTrue(failingTests.rollback("/virtual/project", "snap-ok").restored());
  }
}
