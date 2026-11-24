package io.slixes.archlens.verification.runner;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase;
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

@DisabledOnOs(OS.WINDOWS)
class ProcessTestRunnerGatewayTest {

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void hungSuiteThatKeepsStdoutOpenTimesOutWithinTheTimeout(@TempDir Path dir) {
    long start = System.nanoTime();
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome outcome =
        ProcessTestRunnerGateway.runTestCommand(
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
        ProcessTestRunnerGateway.runTestCommand(
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
  void passingAndFailingSuitesReportExitCodeAndOutput(@TempDir Path dir) throws IOException {
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome ok =
        ProcessTestRunnerGateway.runTestCommand(
            List.of("sh", "-c", "echo all-green"), dir, Duration.ofSeconds(10));
    assertTrue(ok.passed());
    assertEquals(0, ok.exitCode());
    assertEquals("all-green", ok.outputSummary());

    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome failed =
        ProcessTestRunnerGateway.runTestCommand(
            List.of("sh", "-c", "echo boom 1>&2; exit 3"), dir, Duration.ofSeconds(10));
    assertFalse(failed.passed());
    assertEquals(3, failed.exitCode());
    assertEquals("boom", failed.outputSummary(), "stderr is captured too");

    // Exercise runTests() end-to-end with a lightweight ./gradlew test wrapper
    Path gradleProj = Files.createDirectories(dir.resolve("gradle-e2e")).toRealPath();
    Files.writeString(gradleProj.resolve("build.gradle"), "// gradle");
    Path gradlew = Files.writeString(gradleProj.resolve("gradlew"), "#!/bin/sh\necho gradle-ok\n");
    gradlew.toFile().setExecutable(true);
    ProcessTestRunnerGateway gateway = new ProcessTestRunnerGateway();
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome e2eOutcome =
        gateway.runTests(gradleProj.toString());
    assertTrue(e2eOutcome.executed());
    assertTrue(e2eOutcome.passed());
    assertEquals("./gradlew test", e2eOutcome.command());
  }

  @Test
  void missingRunnerIsReportedAsFailureAndTailTruncatesOutput(@TempDir Path dir)
      throws IOException {
    VerifyAndRollbackRefactorUseCase.TestExecutionOutcome outcome =
        ProcessTestRunnerGateway.runTestCommand(
            List.of("definitely-not-a-real-binary-xyz"), dir, Duration.ofSeconds(5));
    assertFalse(outcome.passed());
    assertTrue(outcome.outputSummary().startsWith("Failed to invoke test runner"));

    Path log = dir.resolve("out.log");
    Files.writeString(log, "x".repeat(10_000) + "THE-END");
    String tail = ProcessTestRunnerGateway.tail(log, 20);
    assertTrue(tail.startsWith("..."));
    assertTrue(tail.endsWith("THE-END"));
    assertEquals(23, tail.length());
    Files.writeString(log, "short\n");
    assertEquals("short", ProcessTestRunnerGateway.tail(log, 20));
  }

  @Test
  void detectsPolyglotTestCommandsAndValidatesLocalExecutableWrappers(@TempDir Path tempDir)
      throws IOException {
    ProcessTestRunnerGateway gateway = new ProcessTestRunnerGateway();
    assertEquals(List.of(), gateway.detectTestCommand(null));
    assertEquals(List.of(), gateway.detectTestCommand(tempDir.resolve("missing")));

    Path existingEmpty = Files.createDirectories(tempDir.resolve("empty-existing"));
    assertEquals(List.of(), gateway.detectTestCommand(existingEmpty));
    assertFalse(gateway.runTests(existingEmpty.toString()).executed());

    Path mavenDir = Files.createDirectories(tempDir.resolve("maven-proj")).toRealPath();
    Files.writeString(mavenDir.resolve("pom.xml"), "<project/>");
    assertEquals(List.of("mvn", "-B", "-ntp", "test"), gateway.detectTestCommand(mavenDir));

    Path nonExecMvnw = Files.writeString(mavenDir.resolve("mvnw"), "#!/bin/sh\n");
    nonExecMvnw.toFile().setExecutable(false);
    assertFalse(ProcessTestRunnerGateway.isProjectLocalExecutable(mavenDir, nonExecMvnw));
    assertFalse(
        ProcessTestRunnerGateway.isProjectLocalExecutable(
            mavenDir, mavenDir.resolve("does-not-exist")));

    nonExecMvnw.toFile().setExecutable(true);
    assertTrue(ProcessTestRunnerGateway.isProjectLocalExecutable(mavenDir, nonExecMvnw));
    assertEquals(List.of("./mvnw", "-B", "-ntp", "test"), gateway.detectTestCommand(mavenDir));

    Path outside = Files.writeString(tempDir.resolve("evil.sh"), "#!/bin/sh\n");
    outside.toFile().setExecutable(true);
    Files.delete(nonExecMvnw);
    try {
      Files.createSymbolicLink(mavenDir.resolve("mvnw"), outside);
      assertFalse(
          ProcessTestRunnerGateway.isProjectLocalExecutable(mavenDir, mavenDir.resolve("mvnw")));
    } catch (UnsupportedOperationException | IOException ignored) {
      // Symlinks unsupported
    }

    Path gradleDir = Files.createDirectories(tempDir.resolve("gradle-proj"));
    Files.writeString(gradleDir.resolve("build.gradle.kts"), "plugins {}");
    assertEquals(List.of("gradle", "test"), gateway.detectTestCommand(gradleDir));

    Path rustDir = Files.createDirectories(tempDir.resolve("rust-proj"));
    Files.writeString(rustDir.resolve("Cargo.toml"), "[package]");
    assertEquals(List.of("cargo", "test"), gateway.detectTestCommand(rustDir));

    Path goDir = Files.createDirectories(tempDir.resolve("go-proj"));
    Files.writeString(goDir.resolve("go.mod"), "module example.com/app");
    assertEquals(List.of("go", "test", "./..."), gateway.detectTestCommand(goDir));

    Path pyToml = Files.createDirectories(tempDir.resolve("py-toml"));
    Files.writeString(pyToml.resolve("pyproject.toml"), "[project]");
    assertEquals(List.of("pytest"), gateway.detectTestCommand(pyToml));

    Path pyIni = Files.createDirectories(tempDir.resolve("py-ini"));
    Files.writeString(pyIni.resolve("pytest.ini"), "[pytest]");
    assertEquals(List.of("pytest"), gateway.detectTestCommand(pyIni));

    Path pySetup = Files.createDirectories(tempDir.resolve("py-setup"));
    Files.writeString(pySetup.resolve("setup.py"), "from setuptools import setup");
    assertEquals(List.of("pytest"), gateway.detectTestCommand(pySetup));

    Path nodeDir = Files.createDirectories(tempDir.resolve("node-proj"));
    Files.writeString(nodeDir.resolve("package.json"), "{}");
    assertEquals(List.of("npm", "test"), gateway.detectTestCommand(nodeDir));
  }
}
