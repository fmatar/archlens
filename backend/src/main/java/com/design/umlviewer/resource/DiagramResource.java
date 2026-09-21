package com.design.umlviewer.resource;

import com.design.umlviewer.domain.mailbox.FileMailboxService;
import com.design.umlviewer.domain.mailbox.MailboxEnvelope;
import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.GraphCompiler;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DiagramResource {

  @Inject GraphCompiler graphCompiler;

  @Inject FileMailboxService mailboxService;

  private static final String DEFAULT_PROJECT_ROOT = ".";

  private String normalizeRoot(String root) {
    if (root == null || root.isBlank()) {
      return DEFAULT_PROJECT_ROOT;
    }
    String normalized = root.trim();
    // Expand home directory shorthand ~
    if (normalized.startsWith("~")) {
      normalized = System.getProperty("user.home") + normalized.substring(1);
    }
    // Map /workspace/labs/ to host /Users/fady/workspace/labs/ if present
    if (normalized.startsWith("/workspace/labs/")) {
      File hostLabs = new File("/Users/fady/workspace/labs");
      if (hostLabs.exists() && hostLabs.isDirectory()) {
        normalized = normalized.replace("/workspace/labs", "/Users/fady/workspace/labs");
      }
    }
    // Transparently map agentlens or unclebob-design to archlens
    if (normalized.contains("agentlens")) {
      normalized = normalized.replace("agentlens", "archlens");
    }
    if (normalized.contains("unclebob-design")) {
      normalized = normalized.replace("unclebob-design", "archlens");
    }
    return normalized;
  }

  @GET
  @Path("/projects")
  public Map<String, Object> listProjects() {
    File cwd;
    try {
      cwd = new File(".").getCanonicalFile();
    } catch (IOException e) {
      cwd = new File(".").getAbsoluteFile();
    }
    String currentName = cwd.getName();

    Map<String, String> current = Map.of("name", currentName + " (Active Workspace)", "path", ".");

    java.util.List<Map<String, String>> discovered = new java.util.ArrayList<>();
    discovered.add(current);

    File parent = cwd.getParentFile();
    if (parent != null && parent.exists() && parent.isDirectory()) {
      File[] siblings = parent.listFiles();
      if (siblings != null) {
        java.util.Arrays.sort(siblings, java.util.Comparator.comparing(File::getName));
        for (File sibling : siblings) {
          if (sibling.isDirectory() && !sibling.getName().startsWith(".") && !sibling.equals(cwd)) {
            boolean isProject =
                new File(sibling, "pom.xml").exists()
                    || new File(sibling, "package.json").exists()
                    || new File(sibling, "go.mod").exists()
                    || new File(sibling, "Cargo.toml").exists()
                    || new File(sibling, "deps.edn").exists()
                    || new File(sibling, ".git").exists()
                    || new File(sibling, "src").exists();
            if (isProject) {
              discovered.add(
                  Map.of(
                      "name", sibling.getName(),
                      "path", sibling.getAbsolutePath()));
            }
          }
        }
      }
    }

    return Map.of(
        "current", current,
        "discovered", discovered);
  }

  @GET
  @Path("/graph")
  public ArchitectureGraph getGraph(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return graphCompiler.compileGraph(normalizeRoot(projectRoot), proposalId);
  }

  @GET
  @Path("/policy")
  public ArchitecturePolicy getPolicy(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return graphCompiler.loadPolicy(normalizeRoot(projectRoot));
  }

  @GET
  @Path("/mailbox/to-agent")
  public MailboxEnvelope getToAgentMailbox(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return mailboxService.readMailbox(normalizeRoot(projectRoot), true);
  }

  @POST
  @Path("/mailbox/to-agent")
  public MailboxEnvelope.MailboxCommand sendToAgent(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request)
      throws IOException {
    String op = (String) request.getOrDefault("op", "COMMAND");
    @SuppressWarnings("unchecked")
    Map<String, Object> target = (Map<String, Object>) request.get("target");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) request.get("payload");

    return mailboxService.appendCommand(normalizeRoot(projectRoot), true, op, target, payload);
  }

  @GET
  @Path("/source")
  public Map<String, Object> getSourceCode(
      @QueryParam("filePath") String filePath, @QueryParam("line") @DefaultValue("1") int line) {
    if (filePath == null || filePath.isBlank()) {
      return Map.of("error", "No filePath provided", "content", "");
    }

    File f = new File(filePath);
    // ponytail: resilient path fallback for renamed repository folders or relative paths
    if (!f.exists() || !f.isFile()) {
      if (filePath.startsWith("/workspace/labs/")) {
        File hostFallback =
            new File(filePath.replace("/workspace/labs", "/Users/fady/workspace/labs"));
        if (hostFallback.exists() && hostFallback.isFile()) {
          f = hostFallback;
        }
      }
      if (filePath.contains("agentlens")) {
        File fallback = new File(filePath.replace("agentlens", "archlens"));
        if (fallback.exists() && fallback.isFile()) {
          f = fallback;
        }
      }
      if (filePath.contains("unclebob-design")) {
        File fallback = new File(filePath.replace("unclebob-design", "archlens"));
        if (fallback.exists() && fallback.isFile()) {
          f = fallback;
        }
      }
      if (!f.exists() || !f.isFile()) {
        File rel = new File(".", filePath);
        if (rel.exists() && rel.isFile()) {
          f = rel;
        }
      }
      if (!f.exists() || !f.isFile()) {
        File parentRel = new File("..", filePath);
        if (parentRel.exists() && parentRel.isFile()) {
          f = parentRel;
        }
      }
    }

    if (!f.exists() || !f.isFile()) {
      return Map.of(
          "error", "File not found: " + filePath,
          "filePath", filePath,
          "content", "// File could not be loaded on server: " + filePath);
    }

    try {
      String content = Files.readString(f.toPath());
      return Map.of(
          "fileName",
          f.getName(),
          "filePath",
          f.getAbsolutePath(),
          "content",
          content,
          "targetLine",
          line);
    } catch (Exception e) {
      return Map.of(
          "error", "Failed to read file: " + e.getMessage(),
          "filePath", filePath,
          "content", "// Error reading file: " + e.getMessage());
    }
  }

  @GET
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  public Multi<Map<String, Object>> streamEvents() {
    // SSE heartbeat / live-reload pulse
    return Multi.createFrom()
        .ticks()
        .every(Duration.ofSeconds(2))
        .map(tick -> Map.of("event", "ping", "tick", tick));
  }
}
