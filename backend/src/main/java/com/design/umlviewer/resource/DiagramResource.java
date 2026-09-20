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

  private static final String DEFAULT_PROJECT_ROOT = "..";

  @GET
  @Path("/graph")
  public ArchitectureGraph getGraph(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return graphCompiler.compileGraph(projectRoot, proposalId);
  }

  @GET
  @Path("/policy")
  public ArchitecturePolicy getPolicy(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return graphCompiler.loadPolicy(projectRoot);
  }

  @GET
  @Path("/mailbox/to-agent")
  public MailboxEnvelope getToAgentMailbox(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return mailboxService.readMailbox(projectRoot, true);
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

    return mailboxService.appendCommand(projectRoot, true, op, target, payload);
  }

  @GET
  @Path("/source")
  public Map<String, Object> getSourceCode(
      @QueryParam("filePath") String filePath, @QueryParam("line") @DefaultValue("1") int line)
      throws IOException {
    File f = new File(filePath);
    if (!f.exists() || !f.isFile()) {
      return Map.of("error", "File not found: " + filePath);
    }
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
