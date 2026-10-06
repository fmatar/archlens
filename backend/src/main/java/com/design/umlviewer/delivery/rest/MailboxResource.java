package com.design.umlviewer.delivery.rest;

import com.design.umlviewer.domain.mailbox.MailboxEnvelope;
import com.design.umlviewer.domain.mailbox.MailboxGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Map;

/**
 * REST endpoint managing the bidirectional mailbox queue between workbench UI and companion agents.
 */
@Path("/api/mailbox")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MailboxResource {

  private static final String DEFAULT_PROJECT_ROOT = ".";

  @Inject MailboxGateway mailboxService;

  public MailboxResource() {}

  public MailboxResource(MailboxGateway mailboxService) {
    this.mailboxService = mailboxService;
  }

  @GET
  @Path("/to-agent")
  public MailboxEnvelope getToAgentMailbox(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return mailboxService.readMailbox(WorkspacePathResolver.normalizeRoot(projectRoot), true);
  }

  @POST
  @Path("/to-agent")
  public MailboxEnvelope.MailboxCommand sendToAgent(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request)
      throws IOException {
    String op = (String) request.getOrDefault("op", "COMMAND");
    @SuppressWarnings("unchecked")
    Map<String, Object> target = (Map<String, Object>) request.get("target");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) request.get("payload");

    return mailboxService.appendCommand(
        WorkspacePathResolver.normalizeRoot(projectRoot), true, op, target, payload);
  }
}
