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

    Path srcDir = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcDir);
    Path serviceFile = srcDir.resolve("OrderService.java");
    Files.writeString(serviceFile, "package com.example;\npublic class OrderService {}\n");

    MailboxGateway mailbox = new FileMailboxService();
    ArchitectureCompiler dummyCompiler =
        (root, proposal) ->
            new ArchitectureGraph("Test", false, null, List.of(), List.of(), List.of());

    VerifyAndRollbackRefactorUseCase useCase =
        new VerifyAndRollbackRefactorUseCase(dummyCompiler, mailbox);

    // 1. Capture snapshot (both policy.json and source tree)
    String snapshotId = useCase.createPreRefactorSnapshot(tempDir.toString());
    assertNotNull(snapshotId);
    assertTrue(snapshotId.startsWith("snap-"));

    // 2. Modify policy and source file, and create a new agent port file to simulate bad agent edit
    Files.writeString(policyPath, "{\"title\": \"Corrupted Policy by Agent\"}");
    Files.writeString(serviceFile, "package com.example;\npublic class OrderService { BROKEN }\n");
    Path roguePortFile = srcDir.resolve("OrderRepoPort.java");
    Files.writeString(roguePortFile, "package com.example;\npublic interface OrderRepoPort {}\n");
    assertTrue(Files.exists(roguePortFile));

    // 3. Rollback
    boolean rolledBack = useCase.rollbackToSnapshot(tempDir.toString(), snapshotId);
    assertTrue(rolledBack);
    assertEquals("{\"title\": \"Original Baseline Policy\"}", Files.readString(policyPath));
    assertEquals(
        "package com.example;\npublic class OrderService {}\n", Files.readString(serviceFile));
    assertFalse(
        Files.exists(roguePortFile),
        "Newly created agent files not in pre-refactor snapshot must be removed on rollback");

    Path trashedPortFile =
        tempDir
            .resolve(".archlens/trash")
            .resolve(snapshotId)
            .resolve("src/main/java/com/example/OrderRepoPort.java");
    assertTrue(
        Files.exists(trashedPortFile),
        "Removed post-snapshot files must be moved to recoverable .archlens/trash/<snapshotId>/ (#228)");
    assertEquals(
        "package com.example;\npublic interface OrderRepoPort {}\n",
        Files.readString(trashedPortFile));

    // 4. Verify viewer mailbox ACK includes trashed file report
    MailboxEnvelope viewerMailbox = mailbox.readMailbox(tempDir.toString(), false);
    assertFalse(viewerMailbox.queue().isEmpty());
    MailboxEnvelope.MailboxCommand ack = viewerMailbox.queue().get(0);
    assertEquals("ROLLBACK_COMPLETE", ack.op());
    assertEquals(".archlens/trash/" + snapshotId, ack.payload().get("trashDir"));
    assertEquals(
        List.of("src/main/java/com/example/OrderRepoPort.java"), ack.payload().get("trashedFiles"));

    // 5. Repeat rollback when .archlens/trash already exists inside rootPath
    assertTrue(useCase.rollbackToSnapshot(tempDir.toString(), snapshotId));
  }

  private static VerifyAndRollbackRefactorUseCase rollbackUseCase() {
    ArchitectureCompiler dummyCompiler =
        (root, proposal) ->
            new ArchitectureGraph("Test", false, null, List.of(), List.of(), List.of());
    return new VerifyAndRollbackRefactorUseCase(dummyCompiler, new FileMailboxService());
  }

  @Test
  void rollbackRejectsMalformedAndTraversingSnapshotIdsWithoutDeletingFiles(@TempDir Path tempDir)
      throws IOException {
    Path project = Files.createDirectories(tempDir.resolve("project"));
    Path source = Files.createDirectories(project.resolve("src"));
    Path keep = Files.writeString(source.resolve("Keep.java"), "class Keep {}");
    // A directory elsewhere that looks like a snapshot with an empty manifest
    Path fake = Files.createDirectories(tempDir.resolve("fake/files"));
    Files.writeString(tempDir.resolve("fake/manifest.txt"), "");

    VerifyAndRollbackRefactorUseCase useCase = rollbackUseCase();
    for (String bad :
        new String[] {"../../../fake", "../../fake", "snap-1-0a1b2c3d/../../../fake", "x"}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> useCase.rollbackToSnapshot(project.toString(), bad),
          "should reject " + bad);
    }
    assertTrue(Files.exists(keep), "no file may be deleted for a rejected snapshot id");
    assertFalse(useCase.rollbackToSnapshot(project.toString(), null));
    assertFalse(useCase.rollbackToSnapshot(project.toString(), "snap-1-0a1b2c3d"));
    assertTrue(Files.exists(keep));
  }

  @Test
  void rollbackRejectsSnapshotDirectorySymlinkedOutsideSnapshots(@TempDir Path tempDir)
      throws IOException {
    Path project = Files.createDirectories(tempDir.resolve("project"));
    Path keep =
        Files.writeString(Files.createDirectories(project.resolve("src")).resolve("K.java"), "x");
    Path fake = Files.createDirectories(tempDir.resolve("fake/files"));
    Files.writeString(tempDir.resolve("fake/manifest.txt"), "");
    Path snapshots = Files.createDirectories(project.resolve(".archlens/snapshots"));
    try {
      Files.createSymbolicLink(snapshots.resolve("snap-1-0a1b2c3d"), fake.getParent());
    } catch (UnsupportedOperationException | IOException e) {
      return;
    }
    assertFalse(rollbackUseCase().rollbackToSnapshot(project.toString(), "snap-1-0a1b2c3d"));
    assertTrue(Files.exists(keep));
  }

  @Test
  void rollbackAbortsBeforeDeletingWhenManifestHasUnsafePaths(@TempDir Path tempDir)
      throws IOException {
    Path project = Files.createDirectories(tempDir.resolve("project"));
    Path keep =
        Files.writeString(Files.createDirectories(project.resolve("src")).resolve("K.java"), "x");
    Path snap = Files.createDirectories(project.resolve(".archlens/snapshots/snap-1-0a1b2c3d"));
    Files.createDirectories(snap.resolve("files"));
    Files.writeString(snap.resolve("manifest.txt"), "src/K.java\n../../outside.java\n");

    assertThrows(
        IllegalArgumentException.class,
        () -> rollbackUseCase().rollbackToSnapshot(project.toString(), "snap-1-0a1b2c3d"));
    assertTrue(Files.exists(keep));
  }

  @Test
  void rollbackWithNoPostSnapshotFilesAndSymlinkedTrashGuards(@TempDir Path tempDir)
      throws IOException {
    Path project = Files.createDirectories(tempDir.resolve("project"));
    Path src = Files.createDirectories(project.resolve("src"));
    Path base = Files.writeString(src.resolve("Base.java"), "class Base {}");
    VerifyAndRollbackRefactorUseCase useCase = rollbackUseCase();
    String snapId = useCase.createPreRefactorSnapshot(project.toString());

    // 1. Clean rollback with zero post-snapshot files -> trashDir is empty
    Files.writeString(base, "class Base { int x; }");
    VerifyAndRollbackRefactorUseCase.RollbackResult clean =
        useCase.rollback(project.toString(), snapId);
    assertTrue(clean.restored());
    assertEquals("", clean.trashDir());
    assertEquals(List.of(), clean.trashedFiles());
    assertEquals("class Base {}", Files.readString(base));

    // 2. Symlinked post-snapshot source file is not moved as a regular file
    Path outsideSource = Files.writeString(tempDir.resolve("Outside.java"), "class Outside {}");
    boolean symlinksSupported = true;
    try {
      Files.createSymbolicLink(src.resolve("Linked.java"), outsideSource);
    } catch (UnsupportedOperationException | IOException e) {
      symlinksSupported = false;
    }
    if (symlinksSupported) {
      VerifyAndRollbackRefactorUseCase.RollbackResult withLink =
          useCase.rollback(project.toString(), snapId);
      assertTrue(withLink.restored());
      assertEquals(List.of(), withLink.trashedFiles());
      assertTrue(Files.exists(outsideSource));
      Files.deleteIfExists(src.resolve("Linked.java"));

      // 3. Symlinked .archlens/trash pointing outside project root must be rejected
      Path outsideTrash = Files.createDirectories(tempDir.resolve("outside-trash"));
      Files.writeString(src.resolve("NewWork.java"), "class NewWork {}");
      Files.createSymbolicLink(project.resolve(".archlens/trash"), outsideTrash);
      assertThrows(
          IllegalArgumentException.class, () -> useCase.rollback(project.toString(), snapId));
      assertFalse(Files.exists(outsideTrash.resolve(snapId)));
      assertTrue(Files.exists(src.resolve("NewWork.java")));
    }
  }

  @Test
  void buildWrapperMustBeProjectLocalRegularFile(@TempDir Path tempDir) throws IOException {
    Path project = Files.createDirectories(tempDir.resolve("project")).toRealPath();
    Path outside = Files.writeString(tempDir.resolve("evil.sh"), "#!/bin/sh\n");
    outside.toFile().setExecutable(true);
    Path local = Files.writeString(project.resolve("mvnw"), "#!/bin/sh\n");
    local.toFile().setExecutable(true);
    assertTrue(VerifyAndRollbackRefactorUseCase.isProjectLocalExecutable(project, local));
    Files.delete(local);
    try {
      Files.createSymbolicLink(project.resolve("mvnw"), outside);
    } catch (UnsupportedOperationException | IOException e) {
      return;
    }
    assertFalse(
        VerifyAndRollbackRefactorUseCase.isProjectLocalExecutable(
            project, project.resolve("mvnw")));
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

    // Scenario C: Violations did not increase, but new ADP package cycle was introduced -> FAILED
    ArchitectureCompiler cycleCompiler =
        (root, proposal) ->
            new ArchitectureGraph(
                "Test",
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(new io.slixes.archlens.domain.graph.PackageCycle(List.of("a", "b", "a"))),
                null);
    VerifyAndRollbackRefactorUseCase cycleUseCase =
        new VerifyAndRollbackRefactorUseCase(cycleCompiler, mailbox);
    RefactorVerificationResult cycleFail =
        cycleUseCase.verifyRefactoring(tempDir.toString(), 0, 0, "snap-cycle");
    assertFalse(cycleFail.passed());
    assertTrue(cycleFail.summary().contains("package dependency cycle"));
  }

  @Test
  void testVerifyRefactoringWithAutomatedTestRunner(@TempDir Path tempDir) {
    MailboxGateway mailbox = new FileMailboxService();
    ArchitectureCompiler cleanCompiler =
        (root, proposal) ->
            new ArchitectureGraph("Test", false, null, List.of(), List.of(), List.of());

    // 1. Test runner passes -> verification succeeds and includes test command in summary
    VerifyAndRollbackRefactorUseCase passingTestUseCase =
        new VerifyAndRollbackRefactorUseCase(
            cleanCompiler,
            mailbox,
            root ->
                new VerifyAndRollbackRefactorUseCase.TestExecutionOutcome(
                    true, true, "mvn -B -ntp test", 0, "Tests run: 42, Failures: 0"));
    RefactorVerificationResult passedWithTests =
        passingTestUseCase.verifyRefactoring(tempDir.toString(), 2, 0, "snap-pass", true);
    assertTrue(passedWithTests.passed());
    assertEquals(2, passedWithTests.violationDelta());
    assertTrue(passedWithTests.summary().contains("test suite passed: mvn -B -ntp test"));

    // 2. Test runner fails -> verification fails even though AST violations == 0
    VerifyAndRollbackRefactorUseCase failingTestUseCase =
        new VerifyAndRollbackRefactorUseCase(
            cleanCompiler,
            mailbox,
            root ->
                new VerifyAndRollbackRefactorUseCase.TestExecutionOutcome(
                    true, false, "mvn -B -ntp test", 1, "OrderServiceTest > shouldSave FAILED"));
    RefactorVerificationResult failedOnTests =
        failingTestUseCase.verifyRefactoring(tempDir.toString(), 2, 0, "snap-fail", true);
    assertFalse(failedOnTests.passed());
    assertTrue(failedOnTests.summary().contains("Project test suite failed"));
    assertTrue(failedOnTests.summary().contains("OrderServiceTest > shouldSave FAILED"));
  }

  @Test
  void testDetectPolyglotTestCommands(@TempDir Path tempDir) throws IOException {
    VerifyAndRollbackRefactorUseCase useCase = new VerifyAndRollbackRefactorUseCase();

    Path mavenDir = tempDir.resolve("maven-proj");
    Files.createDirectories(mavenDir);
    Files.writeString(mavenDir.resolve("pom.xml"), "<project/>");
    assertEquals(List.of("mvn", "-B", "-ntp", "test"), useCase.detectTestCommand(mavenDir));

    Path gradleDir = tempDir.resolve("gradle-proj");
    Files.createDirectories(gradleDir);
    Files.writeString(gradleDir.resolve("build.gradle.kts"), "plugins {}");
    assertEquals(List.of("gradle", "test"), useCase.detectTestCommand(gradleDir));

    Path rustDir = tempDir.resolve("rust-proj");
    Files.createDirectories(rustDir);
    Files.writeString(rustDir.resolve("Cargo.toml"), "[package]");
    assertEquals(List.of("cargo", "test"), useCase.detectTestCommand(rustDir));

    Path goDir = tempDir.resolve("go-proj");
    Files.createDirectories(goDir);
    Files.writeString(goDir.resolve("go.mod"), "module example.com/app");
    assertEquals(List.of("go", "test", "./..."), useCase.detectTestCommand(goDir));

    Path pyDir = tempDir.resolve("py-proj");
    Files.createDirectories(pyDir);
    Files.writeString(pyDir.resolve("pyproject.toml"), "[project]");
    assertEquals(List.of("pytest"), useCase.detectTestCommand(pyDir));

    Path nodeDir = tempDir.resolve("node-proj");
    Files.createDirectories(nodeDir);
    Files.writeString(nodeDir.resolve("package.json"), "{}");
    assertEquals(List.of("npm", "test"), useCase.detectTestCommand(nodeDir));
  }
}
