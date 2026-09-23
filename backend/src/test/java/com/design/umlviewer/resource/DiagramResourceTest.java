package com.design.umlviewer.resource;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.mailbox.FileMailboxService;
import com.design.umlviewer.domain.mailbox.MailboxEnvelope;
import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.GraphCompiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagramResourceTest {

  @Test
  void testDiagramResourceEndpoints(@TempDir Path tempDir) throws IOException {
    DiagramResource resource = new DiagramResource();
    FileMailboxService mailboxService = new FileMailboxService();

    // Create a mock GraphCompiler
    GraphCompiler compiler =
        new GraphCompiler() {
          @Override
          public ArchitecturePolicy loadPolicy(String root) {
            return new ArchitecturePolicy(
                "Test", "src", "com", true, List.of(), List.of(), List.of(), List.of(), List.of());
          }

          @Override
          public ArchitectureGraph compileGraph(String root, String proposalId) {
            return new ArchitectureGraph(
                "Graph", false, proposalId, List.of(), List.of(), List.of());
          }
        };

    try {
      java.lang.reflect.Field field1 = DiagramResource.class.getDeclaredField("graphCompiler");
      field1.setAccessible(true);
      field1.set(resource, compiler);

      java.lang.reflect.Field field2 = DiagramResource.class.getDeclaredField("mailboxService");
      field2.setAccessible(true);
      field2.set(resource, mailboxService);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Test getGraph
    ArchitectureGraph graph = resource.getGraph(tempDir.toString(), "prop-1");
    assertEquals("Graph", graph.title());
    assertEquals("prop-1", graph.activeProposalId());

    // Test getPolicy
    ArchitecturePolicy policy = resource.getPolicy(tempDir.toString());
    assertEquals("Test", policy.title());

    // Test mailbox endpoints
    MailboxEnvelope initialBox = resource.getToAgentMailbox(tempDir.toString());
    assertTrue(initialBox.queue().isEmpty());

    MailboxEnvelope.MailboxCommand sent =
        resource.sendToAgent(
            tempDir.toString(),
            Map.of("op", "REFRESH", "target", Map.of("id", "c1"), "payload", Map.of()));
    assertEquals(1, sent.id());
    assertEquals("REFRESH", sent.op());

    // Test sendToAgent with default op and missing fields
    MailboxEnvelope.MailboxCommand defaultSent = resource.sendToAgent(tempDir.toString(), Map.of());
    assertEquals(2, defaultSent.id());
    assertEquals("COMMAND", defaultSent.op());

    // Test getSource (file not found)
    Map<String, Object> notFound = resource.getSourceCode("/invalid/path/Test.java", 10);
    assertTrue(notFound.containsKey("error"));

    // Test getSource (existing file)
    Path testFile = tempDir.resolve("Test.java");
    Files.writeString(testFile, "line 1\nline 2\nline 3\n");
    Map<String, Object> source = resource.getSourceCode(testFile.toString(), 2);
    assertEquals("Test.java", source.get("fileName"));
    assertEquals(2, source.get("targetLine"));
    assertTrue(((String) source.get("content")).contains("line 2"));

    // Test getSource (relative path resolved against projectRoot)
    Map<String, Object> relativeSource = resource.getSourceCode("Test.java", 2, tempDir.toString());
    assertEquals("Test.java", relativeSource.get("fileName"));
    assertEquals(2, relativeSource.get("targetLine"));
    assertTrue(((String) relativeSource.get("content")).contains("line 2"));

    // Test events stream
    assertNotNull(resource.streamEvents());

    // Test listProjects
    Map<String, Object> projects = resource.listProjects();
    assertNotNull(projects);
    assertTrue(projects.containsKey("current"));
    assertTrue(projects.containsKey("discovered"));
    @SuppressWarnings("unchecked")
    List<Map<String, String>> discovered = (List<Map<String, String>>) projects.get("discovered");
    assertFalse(discovered.isEmpty());

    // Test listDirectories includes container and native picker capability flags
    Map<String, Object> dirs = resource.listDirectories(tempDir.toString());
    assertNotNull(dirs);
    assertTrue(dirs.containsKey("isContainer"));
    assertTrue(dirs.containsKey("nativePickerSupported"));
    assertTrue(dirs.get("isContainer") instanceof Boolean);
    assertTrue(dirs.get("nativePickerSupported") instanceof Boolean);

    // Test pickDirectory in headless mode
    System.setProperty("test.headless", "true");
    try {
      Map<String, Object> picked = resource.pickDirectory();
      assertNotNull(picked);
      assertEquals(false, picked.get("supported"));
      assertEquals("headless", picked.get("reason"));
    } finally {
      System.clearProperty("test.headless");
    }
  }
}
