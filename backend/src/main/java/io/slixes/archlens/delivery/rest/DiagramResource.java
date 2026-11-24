package io.slixes.archlens.delivery.rest;

import io.slixes.archlens.domain.dossier.DipInversionPlan;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.PackageCycle;
import io.slixes.archlens.domain.graph.ScreamingMetric;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.domain.screaming.ScreamingMigrationProposal;
import io.slixes.archlens.engine.ArchitectureCompiler;
import io.slixes.archlens.engine.ProjectFileWatcher;
import io.slixes.archlens.usecase.ExportDiagramUseCase;
import io.slixes.archlens.usecase.ExportDossierUseCase;
import io.slixes.archlens.usecase.OpenedProjectRegistry;
import io.slixes.archlens.usecase.ProjectPathGuard;
import io.slixes.archlens.usecase.ProposeScreamingMigrationUseCase;
import io.slixes.archlens.usecase.SavePolicyUseCase;
import io.slixes.archlens.usecase.SynthesizeDipInversionUseCase;
import io.slixes.archlens.usecase.WorkspacePathResolver;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
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

  private static final Logger LOG = Logger.getLogger(DiagramResource.class);
  private static final String DEFAULT_PROJECT_ROOT = ".";

  private ArchitectureCompiler graphCompiler;
  private ExportDossierUseCase exportDossierUseCase;
  private ExportDiagramUseCase exportDiagramUseCase;
  private SynthesizeDipInversionUseCase synthesizeDipUseCase;
  private ProposeScreamingMigrationUseCase proposeScreamingMigrationUseCase;
  private SavePolicyUseCase savePolicyUseCase;
  private OpenedProjectRegistry openedProjects;
  private ProjectFileWatcher fileWatcher;

  private final BroadcastProcessor<Map<String, Object>> eventProcessor =
      BroadcastProcessor.create();

  DiagramResource() {}

  @Inject
  public DiagramResource(
      ArchitectureCompiler graphCompiler,
      ExportDossierUseCase exportDossierUseCase,
      ExportDiagramUseCase exportDiagramUseCase,
      SynthesizeDipInversionUseCase synthesizeDipUseCase,
      ProposeScreamingMigrationUseCase proposeScreamingMigrationUseCase,
      SavePolicyUseCase savePolicyUseCase,
      OpenedProjectRegistry openedProjects,
      ProjectFileWatcher fileWatcher) {
    this.graphCompiler = graphCompiler;
    this.exportDossierUseCase = exportDossierUseCase;
    this.exportDiagramUseCase = exportDiagramUseCase;
    this.synthesizeDipUseCase = synthesizeDipUseCase;
    this.proposeScreamingMigrationUseCase = proposeScreamingMigrationUseCase;
    this.savePolicyUseCase = savePolicyUseCase;
    this.openedProjects = openedProjects;
    this.fileWatcher = fileWatcher;
  }

  String normalizeRoot(String root) {
    return WorkspacePathResolver.normalizeRoot(root);
  }

  @GET
  @Path("/graph")
  @RunOnVirtualThread
  public ArchitectureGraph getGraph(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    String root = normalizeRoot(projectRoot);
    return graphCompiler.compileGraph(root, proposalId);
  }

  @GET
  @Path("/diagram")
  @RunOnVirtualThread
  public ArchitectureGraph getDiagram(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return getGraph(projectRoot, proposalId);
  }

  @GET
  @Path("/diagram/llm-dossier")
  @Produces(MediaType.TEXT_PLAIN)
  @RunOnVirtualThread
  public String getLlmDossier(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return exportDossierUseCase.execute(normalizeRoot(projectRoot), proposalId);
  }

  @GET
  @Path("/llm-dossier")
  @Produces(MediaType.TEXT_PLAIN)
  @RunOnVirtualThread
  public String getLlmDossierAlias(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return getLlmDossier(projectRoot, proposalId);
  }

  @GET
  @Path("/diagram/export")
  @Produces(MediaType.TEXT_PLAIN)
  @RunOnVirtualThread
  public String getDiagramExport(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId,
      @QueryParam("type") @DefaultValue("COMPONENT_RINGS") String diagramType,
      @QueryParam("componentId") String componentId,
      @QueryParam("violationsOnly") @DefaultValue("false") boolean violationsOnly,
      @QueryParam("from") String fromClass,
      @QueryParam("to") String toClass)
      throws IOException {
    return exportDiagramUseCase.execute(
        normalizeRoot(projectRoot),
        proposalId,
        diagramType,
        componentId,
        violationsOnly,
        fromClass,
        toClass);
  }

  @GET
  @Path("/graph/export")
  @Produces(MediaType.TEXT_PLAIN)
  @RunOnVirtualThread
  public String getGraphExportAlias(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId,
      @QueryParam("type") @DefaultValue("COMPONENT_RINGS") String diagramType,
      @QueryParam("componentId") String componentId,
      @QueryParam("violationsOnly") @DefaultValue("false") boolean violationsOnly,
      @QueryParam("from") String fromClass,
      @QueryParam("to") String toClass)
      throws IOException {
    return getDiagramExport(
        projectRoot, proposalId, diagramType, componentId, violationsOnly, fromClass, toClass);
  }

  @GET
  @Path("/violations/invert-plan")
  @RunOnVirtualThread
  public DipInversionPlan getInvertPlan(
      @QueryParam("from") String fromClass,
      @QueryParam("to") String toClass,
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    if (fromClass == null || fromClass.isBlank() || toClass == null || toClass.isBlank()) {
      throw new BadRequestException("Parameters 'from' and 'to' must not be blank.");
    }
    return synthesizeDipUseCase.execute(normalizeRoot(projectRoot), proposalId, fromClass, toClass);
  }

  @GET
  @Path("/policy")
  @RunOnVirtualThread
  public ArchitecturePolicy getPolicy(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    return graphCompiler.loadPolicy(normalizeRoot(projectRoot));
  }

  @POST
  @Path("/policy")
  @RunOnVirtualThread
  public ArchitectureGraph savePolicy(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      ArchitecturePolicy policy)
      throws IOException {
    String root = normalizeRoot(projectRoot);
    if (!openedProjects.isOpened(root)) {
      throw MailboxResource.jsonError(
          Response.Status.FORBIDDEN,
          "Project root is not opened in this workbench session; open it before saving policy");
    }
    ArchitectureGraph updated = savePolicyUseCase.execute(root, policy);
    eventProcessor.onNext(
        Map.of("event", "graph-update", "type", "policy", "timestamp", System.currentTimeMillis()));
    return updated;
  }

  @GET
  @Path("/cycles")
  @RunOnVirtualThread
  public List<PackageCycle> getCycles(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    ArchitectureGraph graph = getGraph(projectRoot, proposalId);
    return graph.cycles() != null ? graph.cycles() : List.of();
  }

  @GET
  @Path("/screaming")
  @RunOnVirtualThread
  public ScreamingMetric getScreamingMetric(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    ArchitectureGraph graph = getGraph(projectRoot, proposalId);
    return graph.screamingMetric() != null ? graph.screamingMetric() : ScreamingMetric.empty();
  }

  @GET
  @Path("/screaming/migration-proposal")
  @RunOnVirtualThread
  public ScreamingMigrationProposal getScreamingMigrationProposal(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot,
      @QueryParam("proposalId") String proposalId)
      throws IOException {
    return proposeScreamingMigrationUseCase.execute(normalizeRoot(projectRoot), proposalId);
  }

  @GET
  @Path("/events")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  public Multi<Map<String, Object>> streamEvents(
      @QueryParam("projectRoot") @DefaultValue(DEFAULT_PROJECT_ROOT) String projectRoot) {
    String root = normalizeRoot(projectRoot);
    ensureWatcherRunning(root);
    Multi<Map<String, Object>> heartbeat =
        Multi.createFrom()
            .ticks()
            .every(Duration.ofSeconds(10))
            .map(tick -> Map.of("event", "ping", "tick", tick));
    return Multi.createBy().merging().streams(heartbeat, eventProcessor);
  }

  public Multi<Map<String, Object>> streamEvents() {
    return streamEvents(DEFAULT_PROJECT_ROOT);
  }

  public void ensureWatcherRunning(String projectRoot) {
    String normalized = WorkspacePathResolver.normalizeRoot(projectRoot);
    if (!isAuthorizedWatchRoot(normalized)) {
      return;
    }
    java.nio.file.Path desiredRoot = Paths.get(normalized);
    Thread.ofVirtual()
        .name("archlens-watcher-starter")
        .start(() -> {
          try {
            fileWatcher.start(
                desiredRoot,
                path ->
                    eventProcessor.onNext(
                        Map.of(
                            "event", "graph-update",
                            "path", path.toString(),
                            "timestamp", System.currentTimeMillis())));
          } catch (Exception e) {
            LOG.debugf(e, "Error starting file watcher for root: %s", projectRoot);
          }
        });
  }

  private boolean isAuthorizedWatchRoot(String normalizedRoot) {
    return ProjectPathGuard.tryCanonicalRoot(normalizedRoot)
        .map(target ->
            openedProjects.isOpened(target.toString())
                || ProjectPathGuard.tryCanonicalRoot(WorkspacePathResolver.normalizeRoot("."))
                    .map(target::equals)
                    .orElse(false))
        .orElse(false);
  }

  public void notifyGraphUpdate(Map<String, Object> payload) {
    eventProcessor.onNext(payload);
  }
}
