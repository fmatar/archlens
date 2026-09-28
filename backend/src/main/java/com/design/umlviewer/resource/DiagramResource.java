package com.design.umlviewer.resource;

import com.design.umlviewer.domain.dossier.ArchitecturalDossierGenerator;
import com.design.umlviewer.domain.dossier.DipInversionPlan;
import com.design.umlviewer.domain.dossier.DipInversionSynthesizer;
import com.design.umlviewer.domain.dossier.DossierGenerator;
import com.design.umlviewer.domain.mailbox.MailboxEnvelope;
import com.design.umlviewer.domain.mailbox.MailboxGateway;
import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.ArchitectureCompiler;
import com.design.umlviewer.usecase.ExportDossierUseCase;
import com.design.umlviewer.usecase.SynthesizeDipInversionUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * REST endpoint serving architecture graph compilation, policy evaluation, LLM refactoring
 * dossiers, DIP inversion synthesis, and live SSE event streams.
 */
@Path("/api")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DiagramResource {

  private static final String DEFAULT_PROJECT_ROOT = ".";

  @Inject ArchitectureCompiler graphCompiler;

  @Inject MailboxGateway mailboxService;

  @Inject DossierGenerator dossierGenerator;

  @Inject DipInversionSynthesizer dipSynthesizer;

  @Inject ExportDossierUseCase exportDossierUseCase;

  @Inject SynthesizeDipInversionUseCase synthesizeDipUseCase;

  @Inject ObjectMapper mapper = new ObjectMapper();

  @Inject ProjectFilesystemResource filesystemResource;

  @Inject SnapshotResource snapshotResource;

  @Inject MailboxResource mailboxResource;

  public DiagramResource() {
    this.filesystemResource = new ProjectFilesystemResource();
  }

  public DiagramResource(
      ArchitectureCompiler graphCompiler,
      MailboxGateway mailboxService,
      DossierGenerator dossierGenerator,
      ObjectMapper mapper,
      DipInversionSynthesizer dipSynthesizer) {
    this.graphCompiler = graphCompiler;
    this.mailboxService = mailboxService;
    this.dossierGenerator =
        dossierGenerator != null ? dossierGenerator : new ArchitecturalDossierGenerator();
    this.mapper = mapper != null ? mapper : new ObjectMapper();
    this.dipSynthesizer = dipSynthesizer != null ? dipSynthesizer : new DipInversionSynthesizer();
    this.exportDossierUseCase = new ExportDossierUseCase(this.graphCompiler, this.dossierGenerator);
    this.synthesizeDipUseCase =
        new SynthesizeDipInversionUseCase(this.graphCompiler, this.dipSynthesizer);
    this.filesystemResource = new ProjectFilesystemResource();
    this.snapshotResource = new SnapshotResource(graphCompiler, this.mapper);
    this.mailboxResource = new MailboxResource(mailboxService);
  }

  public DiagramResource(
      ArchitectureCompiler graphCompiler,
      MailboxGateway mailboxService,
      DossierGenerator dossierGenerator,
      ObjectMapper mapper) {
    this(graphCompiler, mailboxService, dossierGenerator, mapper, new DipInversionSynthesizer());
  }

  String normalizeRoot(String root) {
    return WorkspacePathResolver.normalizeRoot(root);
  }

  private ProjectFilesystemResource getFilesystemResource() {
    if (filesystemResource == null) {
      filesystemResource = new ProjectFilesystemResource();
    }
    return filesystemResource;
  }

  private SnapshotResource getSnapshotResource() {
    if (snapshotResource == null) {
      snapshotResource = new SnapshotResource(graphCompiler, mapper);
    }
    return snapshotResource;
  }

  private MailboxResource getMailboxResource() {
    if (mailboxResource == null) {
      mailboxResource = new MailboxResource(mailboxService);
    }
    return mailboxResource;
  }

  private ExportDossierUseCase getExportDossierUseCase() {
    if (exportDossierUseCase == null) {
      exportDossierUseCase =
          new ExportDossierUseCase(
              graphCompiler,
              dossierGenerator != null ? dossierGenerator : new ArchitecturalDossierGenerator());
    }
    return exportDossierUseCase;
  }

  private SynthesizeDipInversionUseCase getSynthesizeDipUseCase() {
    if (synthesizeDipUseCase == null) {
      synthesizeDipUseCase =
          new SynthesizeDipInversionUseCase(
              graphCompiler,
              dipSynthesizer != null ? dipSynthesizer : new DipInversionSynthesizer());
    }
    return synthesizeDipUseCase;
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
  @Path("/diagram")
  public ArchitectureGraph getDiagram(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return getGraph(projectRoot, proposalId);
  }

  @GET
  @Path("/diagram/llm-dossier")
  @Produces(MediaType.TEXT_PLAIN)
  public String getLlmDossier(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return getExportDossierUseCase().execute(normalizeRoot(projectRoot), proposalId);
  }

  @GET
  @Path("/llm-dossier")
  @Produces(MediaType.TEXT_PLAIN)
  public String getLlmDossierAlias(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return getLlmDossier(projectRoot, proposalId);
  }

  @GET
  @Path("/violations/invert-plan")
  public DipInversionPlan getInvertPlan(
      @QueryParam("from") String fromClass,
      @QueryParam("to") String toClass,
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    if (fromClass == null || fromClass.isBlank() || toClass == null || toClass.isBlank()) {
      throw new BadRequestException("Parameters 'from' and 'to' must not be blank.");
    }
    return getSynthesizeDipUseCase()
        .execute(normalizeRoot(projectRoot), proposalId, fromClass, toClass);
  }

  @GET
  @Path("/policy")
  public ArchitecturePolicy getPolicy(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return graphCompiler.loadPolicy(normalizeRoot(projectRoot));
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

  // --- Backwards-compatible delegates for Java consumers and unit tests ---

  public Map<String, Object> listProjects() {
    return getFilesystemResource().listProjects();
  }

  public Map<String, Object> listDirectories(String rawPath) {
    return getFilesystemResource().listDirectories(rawPath);
  }

  public Map<String, Object> pickDirectory() {
    return getFilesystemResource().pickDirectory();
  }

  public Map<String, Object> getSourceCode(String filePath, int line) {
    return getFilesystemResource().getSourceCode(filePath, line);
  }

  public Map<String, Object> getSourceCode(String filePath, int line, String projectRoot) {
    return getFilesystemResource().getSourceCode(filePath, line, projectRoot);
  }

  public Map<String, Object> listSnapshots(String projectRoot) {
    return getSnapshotResource().listSnapshots(projectRoot);
  }

  public ArchitectureGraph getSnapshot(String snapshotId, String projectRoot) throws IOException {
    return getSnapshotResource().getSnapshot(snapshotId, projectRoot);
  }

  public MailboxEnvelope getToAgentMailbox(String projectRoot) {
    return getMailboxResource().getToAgentMailbox(projectRoot);
  }

  public MailboxEnvelope.MailboxCommand sendToAgent(String projectRoot, Map<String, Object> request)
      throws IOException {
    return getMailboxResource().sendToAgent(projectRoot, request);
  }
}
