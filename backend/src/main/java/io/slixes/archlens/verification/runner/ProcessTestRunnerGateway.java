package io.slixes.archlens.verification.runner;

import io.slixes.archlens.usecase.TestRunnerGateway;
import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase.TestExecutionOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ring 2 process execution adapter implementing {@link TestRunnerGateway} for polyglot test runner
 * detection and bounded process-tree execution.
 */
@ApplicationScoped
public class ProcessTestRunnerGateway implements TestRunnerGateway {

  public static final Duration TEST_TIMEOUT = Duration.ofSeconds(120);
  private static final int OUTPUT_SUMMARY_CHARS = 240;

  @Override
  public TestExecutionOutcome runTests(String projectRoot) {
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    Path rootPath = root.toPath().toAbsolutePath().normalize();
    List<String> cmd = detectTestCommand(rootPath);
    if (cmd.isEmpty()) {
      return new TestExecutionOutcome(false, true, "none", 0, "No test runner manifest detected");
    }
    return runTestCommand(cmd, rootPath, TEST_TIMEOUT);
  }

  @Override
  public List<String> detectTestCommand(Path rootPath) {
    if (rootPath == null || !Files.exists(rootPath)) {
      return List.of();
    }
    if (Files.exists(rootPath.resolve("pom.xml"))) {
      Path mvnw = rootPath.resolve("mvnw");
      if (isProjectLocalExecutable(rootPath, mvnw)) {
        return List.of("./mvnw", "-B", "-ntp", "test");
      }
      return List.of("mvn", "-B", "-ntp", "test");
    }
    if (Files.exists(rootPath.resolve("build.gradle"))
        || Files.exists(rootPath.resolve("build.gradle.kts"))) {
      Path gradlew = rootPath.resolve("gradlew");
      if (isProjectLocalExecutable(rootPath, gradlew)) {
        return List.of("./gradlew", "test");
      }
      return List.of("gradle", "test");
    }
    if (Files.exists(rootPath.resolve("Cargo.toml"))) {
      return List.of("cargo", "test");
    }
    if (Files.exists(rootPath.resolve("go.mod"))) {
      return List.of("go", "test", "./...");
    }
    if (Files.exists(rootPath.resolve("pyproject.toml"))
        || Files.exists(rootPath.resolve("pytest.ini"))
        || Files.exists(rootPath.resolve("setup.py"))) {
      return List.of("pytest");
    }
    if (Files.exists(rootPath.resolve("package.json"))) {
      return List.of("npm", "test");
    }
    return List.of();
  }

  /**
   * A build wrapper is only trusted when it is a regular, executable file physically inside the
   * project root (not a symlink pointing elsewhere).
   */
  public static boolean isProjectLocalExecutable(Path rootPath, Path wrapper) {
    try {
      return Files.isRegularFile(wrapper, LinkOption.NOFOLLOW_LINKS)
          && Files.isExecutable(wrapper)
          && wrapper.toRealPath().startsWith(rootPath.toRealPath());
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Runs {@code cmd} in {@code dir} and enforces {@code timeout} (#235). Output goes to a temp file
   * instead of being read on this thread, so a hung test suite that keeps stdout open cannot block
   * before the timeout is checked. On timeout the whole process tree is killed.
   */
  public static TestExecutionOutcome runTestCommand(List<String> cmd, Path dir, Duration timeout) {
    String cmdLabel = String.join(" ", cmd);
    Path outputFile = null;
    Process proc = null;
    try {
      outputFile = Files.createTempFile("archlens-verify-", ".log");
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(dir.toFile());
      pb.redirectErrorStream(true);
      pb.redirectOutput(outputFile.toFile());
      proc = pb.start();
      boolean finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        destroyProcessTree(proc);
        return new TestExecutionOutcome(
            true,
            false,
            cmdLabel,
            -1,
            "Test execution timed out after " + timeout.toSeconds() + "s");
      }
      int exitCode = proc.exitValue();
      String summary = tail(outputFile, OUTPUT_SUMMARY_CHARS);
      return new TestExecutionOutcome(true, exitCode == 0, cmdLabel, exitCode, summary);
    } catch (IOException | InterruptedException | RuntimeException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      if (proc != null && proc.isAlive()) {
        destroyProcessTree(proc);
      }
      return new TestExecutionOutcome(
          true, false, cmdLabel, -1, "Failed to invoke test runner: " + e.getMessage());
    } finally {
      if (outputFile != null) {
        try {
          Files.deleteIfExists(outputFile);
        } catch (IOException e) {
          outputFile.toFile().deleteOnExit();
        }
      }
    }
  }

  private static void destroyProcessTree(Process proc) {
    proc.descendants().forEach(ProcessHandle::destroyForcibly);
    proc.destroyForcibly();
    try {
      proc.waitFor(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Last {@code maxChars} characters of a (possibly large) log file, prefixed with "..." if cut.
   */
  public static String tail(Path file, int maxChars) throws IOException {
    long size = Files.size(file);
    long readFrom = Math.max(0, size - (long) maxChars * 4);
    byte[] bytes = new byte[(int) (size - readFrom)];
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
      raf.seek(readFrom);
      raf.readFully(bytes);
    }
    String raw = new String(bytes, StandardCharsets.UTF_8).trim();
    boolean cut = readFrom > 0 || raw.length() > maxChars;
    if (raw.length() > maxChars) {
      raw = raw.substring(raw.length() - maxChars);
    }
    return cut ? "..." + raw : raw;
  }
}
