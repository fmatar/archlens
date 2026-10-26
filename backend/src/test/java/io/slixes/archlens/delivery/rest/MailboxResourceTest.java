package io.slixes.archlens.delivery.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.mailbox.MailboxEnvelope;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.mailbox.fs.FileMailboxService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailboxResourceTest {

  @Test
  void testMailboxEndpoints(@TempDir Path tempDir) throws IOException {
    MailboxGateway gateway = new FileMailboxService();
    MailboxResource resource = new MailboxResource(gateway);

    MailboxEnvelope envelope = resource.getToAgentMailbox(tempDir.toString());
    assertNotNull(envelope);
    assertTrue(envelope.queue().isEmpty());

    MailboxEnvelope.MailboxCommand cmd =
        resource.sendToAgent(
            tempDir.toString(),
            Map.of(
                "op", "REGEN", "target", Map.of("path", "src"), "payload", Map.of("key", "val")));

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

    // Initial verify call with empty baseline
    io.slixes.archlens.domain.mailbox.RefactorVerificationResult res =
        resource.verifyRefactoring(
            tempDir.toString(), Map.of("baselineViolations", 0, "snapshotId", "snap-test"));
    assertNotNull(res);
    assertTrue(res.passed());

    // Rollback non-existent snapshot
    Map<String, Object> rollbackRes =
        resource.rollbackRefactoring(tempDir.toString(), Map.of("snapshotId", "missing"));
    assertNotNull(rollbackRes);
    assertEquals(false, rollbackRes.get("success"));
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
}
