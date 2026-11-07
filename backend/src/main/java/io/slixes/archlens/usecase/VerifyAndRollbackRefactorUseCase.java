package io.slixes.archlens.usecase;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.domain.mailbox.RefactorVerificationResult;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Use case orchestrating transactional refactoring verification and workspace source/policy
 * snapshot rollbacks.
 */
@ApplicationScoped
public class VerifyAndRollbackRefactorUseCase {

  private static final String SNAPSHOTS_DIR = ".archlens/snapshots";
  private static final String MANIFEST_FILE = "manifest.txt";
  private static final String FILES_BACKUP_DIR = "files";

  private static final Set<String> IGNORED_DIRS =
      Set.of(
          ".git",
          ".archlens",
          ".uml-viewer",
          ".agent",
          ".agents",
          ".github",
          ".claude",
          ".cursor",
          ".vscode",
          "node_modules",
          "target",
          "dist",
          "build",
          "out",
          "coverage",
          "__pycache__",
          ".venv",
          "venv",
          ".idea",
          ".settings",
          ".gradle",
          ".next",
          ".svelte-kit");

  private static final Set<String> SOURCE_EXTENSIONS =
      Set.of(
          ".java", ".py", ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".svelte", ".go", ".rs",
          ".clj", ".cljs", ".cljc", ".edn", ".kt");

  @Inject ArchitectureCompiler compiler;
  @Inject MailboxGateway mailboxGateway;

  private TestSuiteExecutor testExecutor = this::executeDetectedTestSuite;

  /** Outcome of running the target project's automated test suite during refactor verification. */
  public record TestExecutionOutcome(
      boolean executed, boolean passed, String command, int exitCode, String outputSummary) {}

  /** Strategy interface for executing a project's test suite during closed-loop verification. */
  @FunctionalInterface
  public interface TestSuiteExecutor {
    TestExecutionOutcome runTests(String projectRoot);
  }

  public VerifyAndRollbackRefactorUseCase() {}

  public VerifyAndRollbackRefactorUseCase(
      ArchitectureCompiler compiler, MailboxGateway mailboxGateway) {
    this.compiler = compiler;
    this.mailboxGateway = mailboxGateway;
  }

  public VerifyAndRollbackRefactorUseCase(
      ArchitectureCompiler compiler,
      MailboxGateway mailboxGateway,
      TestSuiteExecutor testExecutor) {
    this.compiler = compiler;
    this.mailboxGateway = mailboxGateway;
    if (testExecutor != null) {
      this.testExecutor = testExecutor;
    }
  }

  /**
   * Captures an isolated pre-refactor snapshot of both `.archlens/policy.json` and the project's
   * source code files so agent refactorings can be atomically rolled back.
   */
  public String createPreRefactorSnapshot(String projectRoot) throws IOException {
    String snapshotId =
        "snap-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    Path rootPath = root.toPath().toAbsolutePath().normalize();
    File snapDir = new File(root, SNAPSHOTS_DIR + "/" + snapshotId);
    Path snapPath = snapDir.toPath();
    Files.createDirectories(snapPath);

    File policy = new File(root, ".archlens/policy.json");
    if (policy.exists()) {
      Files.copy(
          policy.toPath(), snapPath.resolve("policy.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    Path filesBackupRoot = snapPath.resolve(FILES_BACKUP_DIR);
    Files.createDirectories(filesBackupRoot);
    List<String> trackedFiles = collectSourceFiles(rootPath);
    for (String relPath : trackedFiles) {
      Path sourceFile = rootPath.resolve(relPath);
      Path backupDest = filesBackupRoot.resolve(relPath);
      if (backupDest.getParent() != null) {
        Files.createDirectories(backupDest.getParent());
      }
      Files.copy(sourceFile, backupDest, StandardCopyOption.REPLACE_EXISTING);
    }
    Files.write(snapPath.resolve(MANIFEST_FILE), trackedFiles, StandardCharsets.UTF_8);

    return snapshotId;
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

    if (runTests && testExecutor != null) {
      TestExecutionOutcome testOutcome = testExecutor.runTests(projectRoot);
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

  /**
   * Detects the appropriate test runner command for a target project root across Maven, Gradle,
   * npm, Cargo, Go, and Python.
   */
  public List<String> detectTestCommand(Path rootPath) {
    if (rootPath == null || !Files.exists(rootPath)) {
      return List.of();
    }
    if (Files.exists(rootPath.resolve("pom.xml"))) {
      Path mvnw = rootPath.resolve("mvnw");
      if (Files.exists(mvnw) && Files.isExecutable(mvnw)) {
        return List.of("./mvnw", "-B", "-ntp", "test");
      }
      return List.of("mvn", "-B", "-ntp", "test");
    }
    if (Files.exists(rootPath.resolve("build.gradle"))
        || Files.exists(rootPath.resolve("build.gradle.kts"))) {
      Path gradlew = rootPath.resolve("gradlew");
      if (Files.exists(gradlew) && Files.isExecutable(gradlew)) {
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

  private TestExecutionOutcome executeDetectedTestSuite(String projectRoot) {
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    Path rootPath = root.toPath().toAbsolutePath().normalize();
    List<String> cmd = detectTestCommand(rootPath);
    if (cmd.isEmpty()) {
      return new TestExecutionOutcome(false, true, "none", 0, "No test runner manifest detected");
    }
    String cmdLabel = String.join(" ", cmd);
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(rootPath.toFile());
      pb.redirectErrorStream(true);
      Process proc = pb.start();
      byte[] outputBytes = proc.getInputStream().readAllBytes();
      boolean finished = proc.waitFor(120, TimeUnit.SECONDS);
      if (!finished) {
        proc.destroyForcibly();
        return new TestExecutionOutcome(
            true, false, cmdLabel, -1, "Test execution timed out after 120s");
      }
      int exitCode = proc.exitValue();
      String rawOut = new String(outputBytes, StandardCharsets.UTF_8).trim();
      String summary =
          rawOut.length() > 240 ? "..." + rawOut.substring(rawOut.length() - 240) : rawOut;
      return new TestExecutionOutcome(true, exitCode == 0, cmdLabel, exitCode, summary);
    } catch (Exception e) {
      return new TestExecutionOutcome(
          true, false, cmdLabel, -1, "Failed to invoke test runner: " + e.getMessage());
    }
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
    } catch (IOException ignored) {
      // Non-fatal telemetry write
    }
  }

  /**
   * Rolls back both `.archlens/policy.json` and modified/created source files to the captured
   * pre-refactor snapshot.
   */
  public boolean rollbackToSnapshot(String projectRoot, String snapshotId) throws IOException {
    if (snapshotId == null || snapshotId.isBlank()) {
      return false;
    }
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    Path rootPath = root.toPath().toAbsolutePath().normalize();
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

    Path snapPath = snapDir.toPath();
    Path manifestPath = snapPath.resolve(MANIFEST_FILE);
    Path filesBackupRoot = snapPath.resolve(FILES_BACKUP_DIR);
    if (Files.exists(manifestPath) && Files.isDirectory(filesBackupRoot)) {
      List<String> manifestLines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
      Set<String> originalFiles = new HashSet<>();
      for (String line : manifestLines) {
        String relPath = line.trim();
        if (relPath.isEmpty()) {
          continue;
        }
        originalFiles.add(relPath);
        Path backupFile = filesBackupRoot.resolve(relPath);
        Path targetFile = rootPath.resolve(relPath).normalize();
        if (targetFile.startsWith(rootPath) && Files.exists(backupFile)) {
          if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
          }
          Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
      }

      // Remove any newly created source files that did not exist in the pre-refactor snapshot
      List<String> currentSourceFiles = collectSourceFiles(rootPath);
      for (String currentRel : currentSourceFiles) {
        if (!originalFiles.contains(currentRel)) {
          Path rogueFile = rootPath.resolve(currentRel).normalize();
          if (rogueFile.startsWith(rootPath)) {
            Files.deleteIfExists(rogueFile);
          }
        }
      }
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

  private List<String> collectSourceFiles(Path rootPath) throws IOException {
    List<String> result = new ArrayList<>();
    if (!Files.exists(rootPath)) {
      return result;
    }
    Files.walkFileTree(
        rootPath,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(rootPath)) {
              Path fileName = dir.getFileName();
              if (fileName != null && IGNORED_DIRS.contains(fileName.toString())) {
                return FileVisitResult.SKIP_SUBTREE;
              }
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile() && isSourceFile(file)) {
              String rel = rootPath.relativize(file).toString().replace('\\', '/');
              result.add(rel);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
          }
        });
    return result;
  }

  private boolean isSourceFile(Path file) {
    Path namePath = file.getFileName();
    if (namePath == null) {
      return false;
    }
    String name = namePath.toString();
    int dotIdx = name.lastIndexOf('.');
    if (dotIdx < 0) {
      return false;
    }
    return SOURCE_EXTENSIONS.contains(name.substring(dotIdx));
  }
}
