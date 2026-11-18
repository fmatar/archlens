package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.mailbox.MailboxEnvelope;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.mailbox.fs.FileMailboxService;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailboxResourceTest {

  @Test
  void testMailboxEndpoints(@TempDir Path tempDir) throws IOException {
    MailboxGateway gateway = new FileMailboxService();
    MailboxResource resource = new MailboxResource(gateway);
    resource.getOpenedProjects().markOpened(tempDir.toString());

    MailboxEnvelope envelope = resource.getToAgentMailbox(tempDir.toString());
    assertNotNull(envelope);
    assertTrue(envelope.queue().isEmpty());

    MailboxEnvelope.MailboxCommand cmd =
        resource.sendToAgent(
            tempDir.toString(),
            Map.of(
                "op",
                "REGEN",
                "target",
                Map.of("path", "src"),
                "payload",
                Map.of("key", "val", "origin", "spoofed")));
    assertEquals("workbench-session", cmd.payload().get("origin"));

    assertNotNull(cmd);
    assertEquals("REGEN", cmd.op());
    assertEquals(1, cmd.id());

    MailboxEnvelope updated = resource.getToAgentMailbox(tempDir.toString());
    assertEquals(1, updated.queue().size());
    assertEquals("REGEN", updated.queue().get(0).op());
  }

  @Test
  void testVerifyAndRollbackEndpoints(@TempDir Path tempDir) throws IOException {
    MailboxGateway gateway = new FileMailboxService();
    io.slixes.archlens.engine.ArchitectureCompiler dummyCompiler =
        (root, proposal) ->
            new io.slixes.archlens.domain.graph.ArchitectureGraph(
                "Test", false, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
    io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase verifyUseCase =
        new io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase(dummyCompiler, gateway);

    MailboxResource resource = new MailboxResource(gateway, verifyUseCase);
    resource.getOpenedProjects().markOpened(tempDir.toString());

    // Initial verify call with empty baseline
    io.slixes.archlens.domain.mailbox.RefactorVerificationResult res =
        resource.verifyRefactoring(
            tempDir.toString(), Map.of("baselineViolations", 0, "snapshotId", "snap-test"));
    assertNotNull(res);
    assertTrue(res.passed());

    // Rollback non-existent snapshot
    Map<String, Object> rollbackRes =
        resource.rollbackRefactoring(
            tempDir.toString(), Map.of("snapshotId", "snap-1700000000000-deadbeef"));
    assertNotNull(rollbackRes);
    assertEquals(false, rollbackRes.get("success"));
  }

  private static MailboxResource verifyResource() {
    MailboxGateway gateway = new FileMailboxService();
    io.slixes.archlens.engine.ArchitectureCompiler dummyCompiler =
        (root, proposal) ->
            new io.slixes.archlens.domain.graph.ArchitectureGraph(
                "Test", false, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
    return new MailboxResource(
        gateway,
        new io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase(dummyCompiler, gateway));
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
    MailboxGateway gateway = new FileMailboxService();
    io.slixes.archlens.engine.ArchitectureCompiler dummyCompiler =
        (root, proposal) ->
            new io.slixes.archlens.domain.graph.ArchitectureGraph(
                "Test", false, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
    io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase verifyUseCase =
        new io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase(dummyCompiler, gateway);
    io.slixes.archlens.usecase.SyncAdrUseCase syncAdrUseCase =
        new io.slixes.archlens.usecase.SyncAdrUseCase();

    MailboxResource resource = new MailboxResource(gateway, verifyUseCase, syncAdrUseCase);
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
  }

  @Test
  void rejectsUnopenedCommandAndUnconfirmedRunTests(@TempDir Path tempDir) throws IOException {
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
