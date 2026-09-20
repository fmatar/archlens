package com.design.umlviewer.domain.mailbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FileMailboxService {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String MAILBOX_DIR = ".uml-viewer";
    private static final String TO_AGENT = "to-agent.json";
    private static final String TO_VIEWER = "to-viewer.json";

    private File getFile(String projectRoot, String fileName) {
        File dir = new File(projectRoot, MAILBOX_DIR);
        if (!dir.exists() && new File(MAILBOX_DIR).exists()) {
            dir = new File(MAILBOX_DIR);
        } else if (!dir.exists() && new File("../" + MAILBOX_DIR).exists()) {
            dir = new File("../" + MAILBOX_DIR);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }

    public synchronized MailboxEnvelope readMailbox(String projectRoot, boolean isToAgent) {
        File file = getFile(projectRoot, isToAgent ? TO_AGENT : TO_VIEWER);
        if (!file.exists()) {
            return new MailboxEnvelope(1, List.of());
        }
        try {
            return mapper.readValue(file, MailboxEnvelope.class);
        } catch (Exception e) {
            return new MailboxEnvelope(1, List.of());
        }
    }

    public synchronized MailboxEnvelope.MailboxCommand appendCommand(
            String projectRoot,
            boolean isToAgent,
            String op,
            Map<String, Object> target,
            Map<String, Object> payload
    ) throws IOException {
        File targetFile = getFile(projectRoot, isToAgent ? TO_AGENT : TO_VIEWER);
        MailboxEnvelope current = readMailbox(projectRoot, isToAgent);

        long newId = current.nextId();
        MailboxEnvelope.MailboxCommand cmd = new MailboxEnvelope.MailboxCommand(newId, op, target, payload);
        List<MailboxEnvelope.MailboxCommand> updatedQueue = new ArrayList<>(current.queue());
        updatedQueue.add(cmd);

        MailboxEnvelope updatedEnvelope = new MailboxEnvelope(newId + 1, updatedQueue);
        atomicWrite(targetFile, updatedEnvelope);
        return cmd;
    }

    public synchronized MailboxEnvelope.MailboxCommand popOldest(String projectRoot, boolean isToAgent) throws IOException {
        File targetFile = getFile(projectRoot, isToAgent ? TO_AGENT : TO_VIEWER);
        MailboxEnvelope current = readMailbox(projectRoot, isToAgent);
        if (current.queue().isEmpty()) {
            return null;
        }

        MailboxEnvelope.MailboxCommand oldest = current.queue().get(0);
        List<MailboxEnvelope.MailboxCommand> remaining = new ArrayList<>(current.queue().subList(1, current.queue().size()));
        MailboxEnvelope updatedEnvelope = new MailboxEnvelope(current.nextId(), remaining);
        atomicWrite(targetFile, updatedEnvelope);
        return oldest;
    }

    private void atomicWrite(File destination, Object content) throws IOException {
        File tmp = new File(destination.getAbsolutePath() + ".tmp");
        mapper.writeValue(tmp, content);
        Files.move(tmp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
