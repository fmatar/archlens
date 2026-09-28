package com.design.umlviewer.resource;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.adapter.mailbox.FileMailboxService;
import com.design.umlviewer.domain.mailbox.MailboxEnvelope;
import com.design.umlviewer.domain.mailbox.MailboxGateway;
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
}
