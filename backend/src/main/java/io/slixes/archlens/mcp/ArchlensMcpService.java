package io.slixes.archlens.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.slixes.archlens.domain.dossier.ArchitecturalDossierGenerator;
import io.slixes.archlens.domain.dossier.DipInversionPlan;
import io.slixes.archlens.domain.dossier.DipInversionSynthesizer;
import io.slixes.archlens.domain.dossier.MermaidDiagramGenerator;
import io.slixes.archlens.domain.fitness.ArchitectureFitnessCalculator;
import io.slixes.archlens.domain.fitness.FitnessEvaluation;
import io.slixes.archlens.domain.fitness.FitnessHistoryTrend;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.domain.screaming.ScreamingMigrationAssistant;
import io.slixes.archlens.domain.screaming.ScreamingMigrationProposal;
import io.slixes.archlens.engine.ArchitectureCompiler;
import io.slixes.archlens.usecase.EvaluateFitnessUseCase;
import io.slixes.archlens.usecase.ExportDiagramUseCase;
import io.slixes.archlens.usecase.ExportDossierUseCase;
import io.slixes.archlens.usecase.GetFitnessHistoryUseCase;
import io.slixes.archlens.usecase.ManageSnapshotsUseCase;
import io.slixes.archlens.usecase.ProposeScreamingMigrationUseCase;
import io.slixes.archlens.usecase.SyncAdrUseCase;
import io.slixes.archlens.usecase.SynthesizeDipInversionUseCase;
import io.slixes.archlens.usecase.WorkspacePathResolver;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Map;

/**
 * Primary MCP Driving Adapter (Ring 3) exposing Clean Architecture inspection, fitness evaluation,
 * DIP inversion synthesis, MCP resources, and agentic refactoring prompts over Quarkus MCP
 * Streamable HTTP and SSE. Delegates directly to Ring 1 Application Use Cases.
 */
@ApplicationScoped
@RunOnVirtualThread
public class ArchlensMcpService {

  @Inject ArchitectureCompiler architectureCompiler;

  @Inject ExportDossierUseCase exportDossierUseCase;

  @Inject ExportDiagramUseCase exportDiagramUseCase;

  @Inject ManageSnapshotsUseCase manageSnapshotsUseCase;

  @Inject SynthesizeDipInversionUseCase synthesizeDipUseCase;

  @Inject EvaluateFitnessUseCase evaluateFitnessUseCase;

  @Inject GetFitnessHistoryUseCase getFitnessHistoryUseCase;

  @Inject ProposeScreamingMigrationUseCase proposeScreamingMigrationUseCase;

  @Inject SyncAdrUseCase syncAdrUseCase;

  @Inject ObjectMapper objectMapper;

  public ArchlensMcpService() {}

  public ArchlensMcpService(ArchitectureCompiler architectureCompiler) {
    this.architectureCompiler = architectureCompiler;
    this.objectMapper = new ObjectMapper();
    this.exportDossierUseCase =
        new ExportDossierUseCase(architectureCompiler, new ArchitecturalDossierGenerator());
    this.exportDiagramUseCase =
        new ExportDiagramUseCase(architectureCompiler, new MermaidDiagramGenerator());
    this.manageSnapshotsUseCase =
        new ManageSnapshotsUseCase(architectureCompiler, this.objectMapper);
    this.synthesizeDipUseCase =
        new SynthesizeDipInversionUseCase(architectureCompiler, new DipInversionSynthesizer());
    ArchitectureFitnessCalculator fitnessCalculator = new ArchitectureFitnessCalculator();
    this.evaluateFitnessUseCase =
        new EvaluateFitnessUseCase(architectureCompiler, fitnessCalculator);
    this.getFitnessHistoryUseCase =
        new GetFitnessHistoryUseCase(
            this.manageSnapshotsUseCase, architectureCompiler, fitnessCalculator);
    this.proposeScreamingMigrationUseCase =
        new ProposeScreamingMigrationUseCase(
            architectureCompiler, new ScreamingMigrationAssistant());
    this.syncAdrUseCase = new SyncAdrUseCase();
  }

  private ObjectMapper mapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
    }
    return objectMapper;
  }

  @Tool(
      description =
          "Inspect codebase Clean Architecture concentric rings, inward dependency breaches, complexity hotspots, and instability metrics.")
  public ArchitectureGraph inspectArchitecture(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot)
      throws IOException {
    return architectureCompiler.compileGraph(
        WorkspacePathResolver.normalizeRoot(projectRoot), null);
  }

  @Tool(
      description =
          "Export an actionable Clean Architecture LLM prompt dossier diagnosing outward dependency breaches and prescribing concrete Dependency Inversion Principle (DIP) interface ports.")
  public String exportLlmDossier(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot,
      @ToolArg(
              description =
                  "Optional structural proposal ID. Defaults to empty for active architecture.",
              defaultValue = "")
          String proposalId)
      throws IOException {
    String normalizedProposal = proposalId == null || proposalId.isBlank() ? null : proposalId;
    return exportDossierUseCase.execute(
        WorkspacePathResolver.normalizeRoot(projectRoot), normalizedProposal);
  }

  public String exportArchitectureDiagram(
      String projectRoot, String diagramType, String proposalId, boolean violationsOnly)
      throws IOException {
    String normalizedProposal = proposalId == null || proposalId.isBlank() ? null : proposalId;
    if (exportDiagramUseCase == null) {
      exportDiagramUseCase =
          new ExportDiagramUseCase(architectureCompiler, new MermaidDiagramGenerator());
    }
    return exportDiagramUseCase.execute(
        WorkspacePathResolver.normalizeRoot(projectRoot),
        normalizedProposal,
        diagramType,
        null,
        violationsOnly,
        null,
        null);
  }

  @Tool(
      description =
          "List available architecture release snapshots in .archlens/snapshots and historical Git release tags for architectural comparison.")
  public Map<String, Object> listSnapshots(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot) {
    return Map.of(
        "snapshots",
        manageSnapshotsUseCase.listSnapshots(WorkspacePathResolver.normalizeRoot(projectRoot)));
  }

  @Tool(
      description =
          "Retrieve the Clean Architecture model for a specific historical Git release tag or pre-compiled snapshot ID.")
  public ArchitectureGraph getSnapshot(
      @NotBlank @ToolArg(description = "Snapshot identifier or Git release tag name (e.g. v1.0.0)")
          String snapshotId,
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot)
      throws IOException {
    return manageSnapshotsUseCase.getSnapshot(
        snapshotId, WorkspacePathResolver.normalizeRoot(projectRoot));
  }

  @Tool(
      description =
          "Synthesize a targeted Dependency Inversion Principle (DIP) refactoring plan to fix an outward architectural breach, including synthesized Java interface port code, concrete adapter implementation diff, caller injection refactor, and step-by-step instructions.")
  public DipInversionPlan synthesizeDipInversion(
      @NotBlank
          @ToolArg(
              description =
                  "Fully qualified class name of the caller class initiating the outward breach (e.g. com.example.service.OrderService)")
          String fromClass,
      @NotBlank
          @ToolArg(
              description =
                  "Fully qualified class name of the target concrete class to invert into an interface port (e.g. com.example.repo.PostgresOrderRepo)")
          String toClass,
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot)
      throws IOException {
    return synthesizeDipUseCase.execute(
        WorkspacePathResolver.normalizeRoot(projectRoot), null, fromClass, toClass);
  }

  @Tool(
      description =
          "Evaluate the Architectural Fitness Index (AFI 0-100, Grade A-F), concentric ring violations, package cycles, Screaming Architecture Score (SAS), and Robert C. Martin Main Sequence Distance.")
  public FitnessEvaluation evaluateFitness(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot,
      @ToolArg(
              description =
                  "Optional structural proposal ID. Defaults to empty for active architecture.",
              defaultValue = "")
          String proposalId)
      throws IOException {
    String normalizedProposal = proposalId == null || proposalId.isBlank() ? null : proposalId;
    return evaluateFitnessUseCase.evaluateCurrent(
        WorkspacePathResolver.normalizeRoot(projectRoot), normalizedProposal);
  }

  @Tool(
      description =
          "Retrieve the historical Architectural Fitness Index (AFI) trend across Git tags and recorded snapshots.")
  public FitnessHistoryTrend getFitnessHistory(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot) {
    return getFitnessHistoryUseCase.getHistory(WorkspacePathResolver.normalizeRoot(projectRoot));
  }

  @Tool(
      description =
          "Generate an automated Package-by-Feature Screaming Architecture migration proposal to optimize domain cohesion and Screaming Architecture Score (SAS).")
  public ScreamingMigrationProposal proposeScreamingMigration(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot,
      @ToolArg(
              description =
                  "Optional structural proposal ID. Defaults to empty for active architecture.",
              defaultValue = "")
          String proposalId)
      throws IOException {
    String normalizedProposal = proposalId == null || proposalId.isBlank() ? null : proposalId;
    return proposeScreamingMigrationUseCase.execute(
        WorkspacePathResolver.normalizeRoot(projectRoot), normalizedProposal);
  }

  @Tool(
      description =
          "Read the active Clean Architecture concentric layer policy (.archlens/policy.json) for the target workspace.")
  public ArchitecturePolicy getPolicy(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot) {
    return architectureCompiler.loadPolicy(WorkspacePathResolver.normalizeRoot(projectRoot));
  }

  @Tool(
      description =
          "Record an accepted Architecture Decision Record (ADR) in docs/adr/ documenting a Dependency Inversion Principle (DIP) interface port extraction.")
  public SyncAdrUseCase.AdrDocument syncDipAdr(
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot,
      @NotBlank @ToolArg(description = "Source class initiating the outward dependency")
          String sourceClass,
      @NotBlank @ToolArg(description = "Target outer-layer concrete class being inverted")
          String targetClass,
      @NotBlank @ToolArg(description = "Extracted inner-layer interface port name")
          String interfacePortName,
      @ToolArg(
              description = "Architectural rationale for the DIP inversion",
              defaultValue = "Invert outward Clean Architecture dependency")
          String reason)
      throws IOException {
    return syncAdrUseCase.recordDipInversion(
        WorkspacePathResolver.normalizeRoot(projectRoot),
        sourceClass,
        targetClass,
        interfacePortName,
        reason);
  }

  @Resource(
      name = "archlens-llm-dossier",
      uri = "archlens://llms.txt",
      title = "Archlens Clean Architecture & DIP Dossier",
      description =
          "Live Clean Architecture concentric ring topology, inward violation diagnostics, and DIP refactoring dossier.",
      mimeType = "text/markdown")
  public TextResourceContents readLlmsDossierResource() throws IOException {
    String dossier = exportLlmDossier(".", "");
    return new TextResourceContents("archlens://llms.txt", dossier, "text/markdown");
  }

  @Resource(
      name = "archlens-policy",
      uri = "archlens://policy",
      title = "Archlens Concentric Ring Policy (.archlens/policy.json)",
      description = "Active Clean Architecture concentric ring policy and fitness thresholds.",
      mimeType = "application/json")
  public TextResourceContents readPolicyResource() throws IOException {
    ArchitecturePolicy policy = getPolicy(".");
    String json = mapper().writerWithDefaultPrettyPrinter().writeValueAsString(policy);
    return new TextResourceContents("archlens://policy", json, "application/json");
  }

  @Resource(
      name = "archlens-fitness",
      uri = "archlens://fitness",
      title = "Archlens Architectural Fitness Evaluation",
      description =
          "Current Architectural Fitness Index (AFI), Screaming Architecture Score (SAS), and Main Sequence Distance evaluation.",
      mimeType = "application/json")
  public TextResourceContents readFitnessResource() throws IOException {
    FitnessEvaluation evaluation = evaluateFitness(".", "");
    String json = mapper().writerWithDefaultPrettyPrinter().writeValueAsString(evaluation);
    return new TextResourceContents("archlens://fitness", json, "application/json");
  }

  @Prompt(
      name = "architectural-review",
      description =
          "Generate a comprehensive Clean Architecture & Robert C. Martin Main Sequence review prompt for a target workspace.")
  public PromptMessage architecturalReviewPrompt(
      @PromptArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot)
      throws IOException {
    String dossier = exportLlmDossier(projectRoot, "");
    FitnessEvaluation fitness = evaluateFitness(projectRoot, "");
    String promptText =
        "Perform a strict Robert C. Martin Clean Architecture review for project `"
            + projectRoot
            + "` (AFI: "
            + fitness.fitnessScore()
            + "%, Grade: "
            + fitness.grade()
            + ").\n\n"
            + dossier;
    return PromptMessage.withUserRole(new TextContent(promptText));
  }

  @Prompt(
      name = "dip-refactoring-guide",
      description =
          "Synthesize a surgical Dependency Inversion Principle (DIP) prompt to decouple an inner-ring caller from an outer-ring concrete class.")
  public PromptMessage dipRefactoringPrompt(
      @NotBlank @PromptArg(description = "Fully qualified caller class name") String fromClass,
      @NotBlank @PromptArg(description = "Fully qualified target concrete class name")
          String toClass,
      @PromptArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot)
      throws IOException {
    DipInversionPlan plan = synthesizeDipInversion(fromClass, toClass, projectRoot);
    return PromptMessage.withUserRole(new TextContent(plan.surgicalPrompt()));
  }
}
