package io.slixes.archlens.delivery.rest;

import io.slixes.archlens.domain.mailbox.MailboxEnvelope;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase;
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

  @Inject VerifyAndRollbackRefactorUseCase verifyAndRollbackUseCase;

  public MailboxResource() {}

  public MailboxResource(MailboxGateway mailboxService) {
    this.mailboxService = mailboxService;
    this.verifyAndRollbackUseCase = null;
  }

  public MailboxResource(
      MailboxGateway mailboxService, VerifyAndRollbackRefactorUseCase verifyAndRollbackUseCase) {
    this.mailboxService = mailboxService;
    this.verifyAndRollbackUseCase = verifyAndRollbackUseCase;
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

    // Capture pre-refactor snapshot if starting an apply or invert proposal
    if (("APPLY_PROPOSAL".equalsIgnoreCase(op) || "INVERT_DEPENDENCY".equalsIgnoreCase(op))
        && verifyAndRollbackUseCase != null) {
      try {
        String snapshotId =
            verifyAndRollbackUseCase.createPreRefactorSnapshot(
                WorkspacePathResolver.normalizeRoot(projectRoot));
        if (payload != null && !payload.containsKey("snapshotId")) {
          payload = new java.util.HashMap<>(payload);
          payload.put("snapshotId", snapshotId);
        }
      } catch (IOException ignored) {
      }
    }

    return mailboxService.appendCommand(
        WorkspacePathResolver.normalizeRoot(projectRoot), true, op, target, payload);
  }

  @POST
  @Path("/verify")
  public io.slixes.archlens.domain.mailbox.RefactorVerificationResult verifyRefactoring(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request) {
    int baseline = ((Number) request.getOrDefault("baselineViolations", 0)).intValue();
    String snapshotId = (String) request.getOrDefault("snapshotId", "");
    return verifyAndRollbackUseCase.verifyRefactoring(
        WorkspacePathResolver.normalizeRoot(projectRoot), baseline, snapshotId);
  }

  @POST
  @Path("/rollback")
  public Map<String, Object> rollbackRefactoring(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request)
      throws IOException {
    String snapshotId = (String) request.get("snapshotId");
    boolean success =
        verifyAndRollbackUseCase.rollbackToSnapshot(
            WorkspacePathResolver.normalizeRoot(projectRoot), snapshotId);
    return Map.of("success", success, "snapshotId", snapshotId != null ? snapshotId : "");
  }
}
