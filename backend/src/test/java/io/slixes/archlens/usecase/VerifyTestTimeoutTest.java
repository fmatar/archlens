package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** #235: the verify test-runner timeout must actually fire. */
@DisabledOnOs(OS.WINDOWS)
class VerifyTestTimeoutTest {

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void hungSuiteThatKeepsStdoutOpenTimesOutWithinTheTimeout(@TempDir Path dir) {
    long start = System.nanoTime();
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome outcome =
        VerifyAndRollbackRefactorUseCase.runTestCommand(
            List.of("sh", "-c", "echo started; sleep 60"), dir, Duration.ofSeconds(1));
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertTrue(outcome.executed());
    assertFalse(outcome.passed());
    assertEquals(-1, outcome.exitCode());
    assertEquals("Test execution timed out after 1s", outcome.outputSummary());
    assertTrue(elapsedMs < 8000, "timeout must fire near 1s, took " + elapsedMs + "ms");
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void timeoutKillsTheWholeProcessTree(@TempDir Path dir) throws Exception {
    Path pidFile = dir.resolve("child.pid");
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome outcome =
        VerifyAndRollbackRefactorUseCase.runTestCommand(
            List.of("sh", "-c", "sleep 60 & echo $! > child.pid; wait"),
            dir,
            Duration.ofSeconds(1));
    assertFalse(outcome.passed());
    long childPid = Long.parseLong(Files.readString(pidFile).trim());
    long deadline = System.currentTimeMillis() + 5000;
    boolean alive = true;
    while (alive && System.currentTimeMillis() < deadline) {
      alive = ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false);
      if (alive) {
        Thread.sleep(100);
      }
    }
    assertFalse(alive, "grandchild test process must be killed on timeout");
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void passingAndFailingSuitesReportExitCodeAndOutput(@TempDir Path dir) {
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome ok =
        VerifyAndRollbackRefactorUseCase.runTestCommand(
            List.of("sh", "-c", "echo all-green"), dir, Duration.ofSeconds(10));
    assertTrue(ok.passed());
    assertEquals(0, ok.exitCode());
    assertEquals("all-green", ok.outputSummary());

    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome failed =
        VerifyAndRollbackRefactorUseCase.runTestCommand(
            List.of("sh", "-c", "echo boom 1>&2; exit 3"), dir, Duration.ofSeconds(10));
    assertFalse(failed.passed());
    assertEquals(3, failed.exitCode());
    assertEquals("boom", failed.outputSummary(), "stderr is captured too");
  }

  @Test
  void missingRunnerIsReportedAsFailure(@TempDir Path dir) {
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome outcome =
        VerifyAndRollbackRefactorUseCase.runTestCommand(
            List.of("definitely-not-a-real-binary-xyz"), dir, Duration.ofSeconds(5));
    assertFalse(outcome.passed());
    assertTrue(outcome.outputSummary().startsWith("Failed to invoke test runner"));
  }

  @Test
  void tailKeepsOnlyTheEndOfLargeOutput(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("out.log");
    Files.writeString(log, "x".repeat(10_000) + "THE-END");
    String tail = VerifyAndRollbackRefactorUseCase.tail(log, 20);
    assertTrue(tail.startsWith("..."));
    assertTrue(tail.endsWith("THE-END"));
    assertEquals(23, tail.length());
    Files.writeString(log, "short\n");
    assertEquals("short", VerifyAndRollbackRefactorUseCase.tail(log, 20));
  }
}
