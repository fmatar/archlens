package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.mailbox.MailboxEnvelope;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.domain.mailbox.RefactorVerificationResult;
import io.slixes.archlens.engine.ArchitectureCompiler;
import io.slixes.archlens.mailbox.fs.FileMailboxService;
import io.slixes.archlens.usecase.OpenedProjectRegistry;
import io.slixes.archlens.usecase.SyncAdrUseCase;
import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase;
import io.slixes.archlens.verification.runner.ProcessTestRunnerGateway;
import io.slixes.archlens.workspace.fs.LocalFilesystemGateway;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailboxResourceTest {

  private static VerifyAndRollbackRefactorUseCase newVerifyUseCase(MailboxGateway gateway) {
    ArchitectureCompiler dummyCompiler =
        (root, proposal) ->
            new ArchitectureGraph("Test", false, null, List.of(), List.of(), List.of());
    return new VerifyAndRollbackRefactorUseCase(
        dummyCompiler, gateway, new LocalFilesystemGateway(), new ProcessTestRunnerGateway());
  }

  private static MailboxResource verifyResource() {
    MailboxGateway gateway = new FileMailboxService();
    return new MailboxResource(
        gateway,
        newVerifyUseCase(gateway),
        new SyncAdrUseCase(),
        new OpenedProjectRegistry(),
        new SessionTokenService(true, "test-session-token-1234567890", "127.0.0.1", 8088));
  }

  @Test
  void testMailboxEndpoints(@TempDir Path tempDir) throws IOException {
    MailboxResource resource = verifyResource();
    resource.getOpenedProjects().markOpened(tempDir.toString());

    MailboxEnvelope envelope = resource.getToAgentMailbox(tempDir.toString());
    assertNotNull(envelope);
    assertTrue(envelope.queue().isEmpty());

    MailboxEnvelope.MailboxCommand cmd =
        resource.sendToAgent(
            tempDir.toString(),
            Map.of(
                "op",
                "APPLY_PROPOSAL",
                "target",
                Map.of("path", "src"),
                "payload",
                Map.of("key", "val", "origin", "spoofed")));
    assertEquals("workbench-session", cmd.payload().get("origin"));
    assertNotNull(cmd.payload().get("sessionFingerprint"));
    assertNotNull(cmd.payload().get("snapshotId"));
    assertEquals("APPLY_PROPOSAL", cmd.op());
    assertEquals(1, cmd.id());

    MailboxEnvelope.MailboxCommand defaultCmd = resource.sendToAgent(tempDir.toString(), Map.of());
    assertEquals(2, defaultCmd.id());
    assertEquals("COMMAND", defaultCmd.op());

    MailboxEnvelope updated = resource.getToAgentMailbox(tempDir.toString());
    assertEquals(2, updated.queue().size());
  }

  @Test
  void testVerifyAndRollbackEndpoints(@TempDir Path tempDir) throws IOException {
    MailboxGateway gateway = new FileMailboxService();
    VerifyAndRollbackRefactorUseCase verifyUseCase = newVerifyUseCase(gateway);

    MailboxResource resource =
        new MailboxResource(gateway, verifyUseCase, null, new OpenedProjectRegistry(), null);
    resource.getOpenedProjects().markOpened(tempDir.toString());

    RefactorVerificationResult res =
        resource.verifyRefactoring(
            tempDir.toString(),
            Map.of("baselineViolations", 0, "baselineCycles", 0, "snapshotId", "snap-test"));
    assertNotNull(res);
    assertTrue(res.passed());

    Map<String, Object> nullReqRes = resource.rollbackRefactoring(tempDir.toString(), null);
    assertEquals(false, nullReqRes.get("success"));
    assertEquals("", nullReqRes.get("snapshotId"));

    Map<String, Object> rollbackRes =
        resource.rollbackRefactoring(
            tempDir.toString(), Map.of("snapshotId", "snap-1700000000000-deadbeef"));
    assertNotNull(rollbackRes);
    assertEquals(false, rollbackRes.get("success"));
    assertEquals(List.of(), rollbackRes.get("trashedFiles"));

    Path srcDir = Files.createDirectories(tempDir.resolve("src"));
    Files.writeString(srcDir.resolve("Base.java"), "class Base {}");
    String snapId = verifyUseCase.createPreRefactorSnapshot(tempDir.toString());
    Path extra = Files.writeString(srcDir.resolve("Draft.java"), "class Draft {}");

    Map<String, Object> restoredRes =
        resource.rollbackRefactoring(tempDir.toString(), Map.of("snapshotId", snapId));
    assertEquals(true, restoredRes.get("success"));
    assertEquals(".archlens/trash/" + snapId, restoredRes.get("trashDir"));
    assertEquals(List.of("src/Draft.java"), restoredRes.get("trashedFiles"));
    assertFalse(Files.exists(extra));
    assertEquals(
        "class Draft {}",
        Files.readString(tempDir.resolve(".archlens/trash/" + snapId + "/src/Draft.java")));

    Map<String, Object> unconfiguredAdr =
        resource.syncAdr(tempDir.toString(), Map.of("type", "DIP_INVERSION"));
    assertEquals(false, unconfiguredAdr.get("success"));
  }

  private static int statusOf(WebApplicationException e) {
    return e.getResponse().getStatus();
  }

  @Test
  void verifyRejectsProjectRootThatWasNotOpened(@TempDir Path tempDir) {
    MailboxResource resource = verifyResource();
    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () ->
                resource.verifyRefactoring(
                    tempDir.toString(), Map.of("baselineViolations", 0, "runTests", true)));
    assertEquals(403, statusOf(ex));
  }

  @Test
  void verifyRejectsSiblingOfOpenedProject(@TempDir Path tempDir) throws IOException {
    Path opened = Files.createDirectories(tempDir.resolve("opened"));
    Path other = Files.createDirectories(tempDir.resolve("other"));
    MailboxResource resource = verifyResource();
    resource.getOpenedProjects().markOpened(opened.toString());

    assertNotNull(resource.verifyRefactoring(opened.toString(), Map.of("baselineViolations", 0)));
    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> resource.verifyRefactoring(other.toString(), Map.of("baselineViolations", 0)));
    assertEquals(403, statusOf(ex));
    WebApplicationException traversal =
        assertThrows(
            WebApplicationException.class,
            () ->
                resource.verifyRefactoring(
                    opened.resolve("../other").toString(), Map.of("baselineViolations", 0)));
    assertEquals(403, statusOf(traversal));
  }

  @Test
  void rollbackRejectsUnopenedProjectAndInvalidSnapshotIds(@TempDir Path tempDir)
      throws IOException {
    MailboxResource resource = verifyResource();
    WebApplicationException unopened =
        assertThrows(
            WebApplicationException.class,
            () ->
                resource.rollbackRefactoring(
                    tempDir.toString(), Map.of("snapshotId", "snap-1700000000000-deadbeef")));
    assertEquals(403, statusOf(unopened));

    resource.getOpenedProjects().markOpened(tempDir.toString());
    for (String bad : new String[] {"../../etc", "snap-1/../../x", "missing", "SNAP-1-DEADBEEF"}) {
      WebApplicationException ex =
          assertThrows(
              WebApplicationException.class,
              () -> resource.rollbackRefactoring(tempDir.toString(), Map.of("snapshotId", bad)),
              "snapshotId must be rejected: " + bad);
      assertEquals(400, statusOf(ex));
    }
  }

  @Test
  void testSyncAdrEndpoint(@TempDir Path tempDir) throws IOException {
    MailboxResource resource = verifyResource();
    resource.getOpenedProjects().markOpened(tempDir.toString());

    Map<String, Object> adrRes =
        resource.syncAdr(
            tempDir.toString(),
            Map.of(
                "type",
                "DIP_INVERSION",
                "sourceClass",
                "com.example.OrderService",
                "targetClass",
                "com.example.PostgresRepo",
                "interfacePortName",
                "OrderRepoPort"));

    assertNotNull(adrRes);
    assertEquals(true, adrRes.get("success"));
    assertEquals(1, adrRes.get("index"));
    assertNotNull(adrRes.get("filename"));

    Map<String, Object> reorgRes =
        resource.syncAdr(tempDir.toString(), Map.of("type", "PACKAGE_REORGANIZATION"));
    assertEquals(true, reorgRes.get("success"));
    assertEquals(2, reorgRes.get("index"));
  }

  @Test
  void rejectsUnopenedCommandAndUnconfirmedRunTests(@TempDir Path tempDir) {
    MailboxResource resource = verifyResource();

    WebApplicationException unopenedSend =
        assertThrows(
            WebApplicationException.class,
            () -> resource.sendToAgent(tempDir.toString(), Map.of("op", "REGEN")));
    assertEquals(403, statusOf(unopenedSend));

    WebApplicationException unopenedSync =
        assertThrows(
            WebApplicationException.class,
            () -> resource.syncAdr(tempDir.toString(), Map.of("type", "DIP_INVERSION")));
    assertEquals(403, statusOf(unopenedSync));

    resource.getOpenedProjects().markOpened(tempDir.toString());
    WebApplicationException unconfirmedTests =
        assertThrows(
            WebApplicationException.class,
            () ->
                resource.verifyRefactoring(
                    tempDir.toString(), Map.of("baselineViolations", 0, "runTests", true)));
    assertEquals(400, statusOf(unconfirmedTests));

    WebApplicationException stringConfirmed =
        assertThrows(
            WebApplicationException.class,
            () ->
                resource.verifyRefactoring(
                    tempDir.toString(),
                    Map.of("baselineViolations", 0, "runTests", true, "confirmRunTests", "true")));
    assertEquals(400, statusOf(stringConfirmed));
  }
}
