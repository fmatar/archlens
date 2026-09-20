package com.design.umlviewer.domain.mailbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileMailboxServiceTest {

    private final FileMailboxService service = new FileMailboxService();

    @Test
    void testMailboxQueueOperations(@TempDir Path tempDir) throws IOException {
        String root = tempDir.toString();

        // 1. Initial read on non-existing directory returns empty envelope
        MailboxEnvelope initial = service.readMailbox(root, true);
        assertEquals(1, initial.nextId());
        assertTrue(initial.queue().isEmpty());

        // 2. Append command to agent outbox
        MailboxEnvelope.MailboxCommand cmd1 = service.appendCommand(
                root,
                true,
                "REFRESH_CRAP",
                Map.of("id", "com.test.MyClass"),
                Map.of("target", "class")
        );
        assertEquals(1, cmd1.id());
        assertEquals("REFRESH_CRAP", cmd1.op());
        assertEquals("com.test.MyClass", cmd1.target().get("id"));

        // 3. Append second command
        MailboxEnvelope.MailboxCommand cmd2 = service.appendCommand(
                root,
                true,
                "REGEN",
                Map.of(),
                Map.of()
        );
        assertEquals(2, cmd2.id());

        // Read again and verify
        MailboxEnvelope afterAppends = service.readMailbox(root, true);
        assertEquals(3, afterAppends.nextId());
        assertEquals(2, afterAppends.queue().size());

        // 4. Pop oldest command (FIFO)
        MailboxEnvelope.MailboxCommand popped1 = service.popOldest(root, true);
        assertNotNull(popped1);
        assertEquals(1, popped1.id());
        assertEquals("REFRESH_CRAP", popped1.op());

        // 5. Pop second command
        MailboxEnvelope.MailboxCommand popped2 = service.popOldest(root, true);
        assertNotNull(popped2);
        assertEquals(2, popped2.id());

        // 6. Pop when queue is empty returns null
        MailboxEnvelope.MailboxCommand emptyPop = service.popOldest(root, true);
        assertNull(emptyPop);

        // Verify viewer inbox as well
        MailboxEnvelope viewerBox = service.readMailbox(root, false);
        assertEquals(1, viewerBox.nextId());

        // 7. Verify handling of corrupted JSON file (triggers catch block)
        File corruptedFile = new File(new File(root, ".uml-viewer"), "to-agent.json");
        java.nio.file.Files.writeString(corruptedFile.toPath(), "invalid-non-json-content");
        MailboxEnvelope recovered = service.readMailbox(root, true);
        assertEquals(1, recovered.nextId());
        assertTrue(recovered.queue().isEmpty());
    }
}
