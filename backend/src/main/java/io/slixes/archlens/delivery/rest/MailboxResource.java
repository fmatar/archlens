package io.slixes.archlens.delivery.rest;

import io.slixes.archlens.domain.mailbox.MailboxEnvelope;
import io.slixes.archlens.domain.mailbox.MailboxGateway;
import io.slixes.archlens.usecase.OpenedProjectRegistry;
import io.slixes.archlens.usecase.VerifyAndRollbackRefactorUseCase;
import io.slixes.archlens.usecase.WorkspacePathResolver;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;

/**
 * REST endpoint managing the bidirectional mailbox queue between workbench UI and companion agents.
 */
@Path("/api/mailbox")
@ApplicationScoped
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MailboxResource {

  private static final String DEFAULT_PROJECT_ROOT = ".";

  @Inject MailboxGateway mailboxService;

  @Inject VerifyAndRollbackRefactorUseCase verifyAndRollbackUseCase;

  @Inject io.slixes.archlens.usecase.SyncAdrUseCase syncAdrUseCase;

  @Inject OpenedProjectRegistry openedProjects;

  @Inject SessionTokenService sessionTokens;

  public MailboxResource() {}

  public MailboxResource(MailboxGateway mailboxService) {
    this.mailboxService = mailboxService;
    this.verifyAndRollbackUseCase = null;
    this.syncAdrUseCase = null;
  }

  public MailboxResource(
      MailboxGateway mailboxService, VerifyAndRollbackRefactorUseCase verifyAndRollbackUseCase) {
    this.mailboxService = mailboxService;
    this.verifyAndRollbackUseCase = verifyAndRollbackUseCase;
    this.syncAdrUseCase = null;
  }

  public MailboxResource(
      MailboxGateway mailboxService,
      VerifyAndRollbackRefactorUseCase verifyAndRollbackUseCase,
      io.slixes.archlens.usecase.SyncAdrUseCase syncAdrUseCase) {
    this.mailboxService = mailboxService;
    this.verifyAndRollbackUseCase = verifyAndRollbackUseCase;
    this.syncAdrUseCase = syncAdrUseCase;
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
    String root = requireOpenedProject(projectRoot);
    Map<String, Object> safeRequest = request != null ? request : Map.of();
    String op = (String) safeRequest.getOrDefault("op", "COMMAND");
    @SuppressWarnings("unchecked")
    Map<String, Object> target = (Map<String, Object>) safeRequest.get("target");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) safeRequest.get("payload");
    Map<String, Object> mutablePayload =
        payload != null ? new java.util.HashMap<>(payload) : new java.util.HashMap<>();
    mutablePayload.put("origin", "workbench-session");
    if (sessionTokens != null) {
      mutablePayload.put("sessionFingerprint", sessionTokens.tokenFingerprint());
    }

    // Capture pre-refactor snapshot if starting an apply or invert proposal
    if (("APPLY_PROPOSAL".equalsIgnoreCase(op) || "INVERT_DEPENDENCY".equalsIgnoreCase(op))
        && verifyAndRollbackUseCase != null) {
      try {
        String snapshotId = verifyAndRollbackUseCase.createPreRefactorSnapshot(root);
        mutablePayload.putIfAbsent("snapshotId", snapshotId);
      } catch (IOException ignored) {
      }
    }

    return mailboxService.appendCommand(root, true, op, target, mutablePayload);
  }

  @POST
  @Path("/verify")
  public io.slixes.archlens.domain.mailbox.RefactorVerificationResult verifyRefactoring(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request) {
    String root = requireOpenedProject(projectRoot);
    Map<String, Object> safeRequest = request != null ? request : Map.of();
    int baseline = ((Number) safeRequest.getOrDefault("baselineViolations", 0)).intValue();
    int baselineCycles =
        safeRequest.containsKey("baselineCycles")
                && safeRequest.get("baselineCycles") instanceof Number n
            ? n.intValue()
            : -1;
    String snapshotId = (String) safeRequest.getOrDefault("snapshotId", "");
    boolean runTests = Boolean.TRUE.equals(safeRequest.get("runTests"));
    if (runTests && !Boolean.TRUE.equals(safeRequest.get("confirmRunTests"))) {
      throw jsonError(
          Response.Status.BAD_REQUEST,
          "Executing project test suites requires explicit confirmRunTests=true opt-in");
    }
    return verifyAndRollbackUseCase.verifyRefactoring(
        root, baseline, baselineCycles, snapshotId, runTests);
  }

  @POST
  @Path("/rollback")
  public Map<String, Object> rollbackRefactoring(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request)
      throws IOException {
    String snapshotId = (String) request.get("snapshotId");
    String root = requireOpenedProject(projectRoot);
    VerifyAndRollbackRefactorUseCase.RollbackResult result;
    try {
      result = verifyAndRollbackUseCase.rollback(root, snapshotId);
    } catch (IllegalArgumentException e) {
      throw jsonError(Response.Status.BAD_REQUEST, e.getMessage());
    }
    return Map.of(
        "success",
        result.restored(),
        "snapshotId",
        snapshotId != null ? snapshotId : "",
        "trashDir",
        result.trashDir(),
        "trashedFiles",
        result.trashedFiles());
  }

  /** Registry of projects opened in this session; lazily created outside of CDI (unit tests). */
  public OpenedProjectRegistry getOpenedProjects() {
    if (openedProjects == null) {
      openedProjects = new OpenedProjectRegistry();
    }
    return openedProjects;
  }

  /**
   * Normalizes {@code projectRoot} and rejects it with 403 unless it is a project opened in this
   * workbench session. Verification can run the project's test suite and rollback deletes files, so
   * both are confined to projects the user actually opened.
   */
  String requireOpenedProject(String projectRoot) {
    String root = WorkspacePathResolver.normalizeRoot(projectRoot);
    if (!getOpenedProjects().isOpened(root)) {
      throw jsonError(
          Response.Status.FORBIDDEN,
          "Project root is not opened in this workbench session; open it before verifying or"
              + " rolling back");
    }
    return root;
  }

  private static WebApplicationException jsonError(Response.Status status, String message) {
    return new WebApplicationException(
        Response.status(status)
            .type(MediaType.APPLICATION_JSON)
            .entity(
                Map.of("success", false, "error", message != null ? message : status.toString()))
            .build());
  }

  @POST
  @Path("/sync-adr")
  public Map<String, Object> syncAdr(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      Map<String, Object> request)
      throws IOException {
    String root = requireOpenedProject(projectRoot);
    if (syncAdrUseCase == null) {
      return Map.of("success", false, "error", "SyncAdrUseCase is not configured");
    }

    String type = (String) request.getOrDefault("type", "DIP_INVERSION");

    if ("PACKAGE_REORGANIZATION".equalsIgnoreCase(type)) {
      String title =
          (String) request.getOrDefault("title", "Clean Architecture Package Reorganization");
      String context =
          (String)
              request.getOrDefault(
                  "context", "Reorganized packages according to Clean Architecture layers.");
      String decision =
          (String) request.getOrDefault("decision", "Adopt concentric layer hierarchy.");
      String consequences =
          (String) request.getOrDefault("consequences", "Eliminated illegal outward dependencies.");
      var doc =
          syncAdrUseCase.recordPackageReorganization(root, title, context, decision, consequences);
      return Map.of("success", true, "filename", doc.filename(), "index", doc.index());
    } else {
      String fromClass = (String) request.getOrDefault("sourceClass", "");
      String toClass = (String) request.getOrDefault("targetClass", "");
      String portName = (String) request.getOrDefault("interfacePortName", "Port");
      String reason =
          (String) request.getOrDefault("reason", "Invert outward Clean Architecture dependency");
      var doc = syncAdrUseCase.recordDipInversion(root, fromClass, toClass, portName, reason);
      return Map.of("success", true, "filename", doc.filename(), "index", doc.index());
    }
  }
}
