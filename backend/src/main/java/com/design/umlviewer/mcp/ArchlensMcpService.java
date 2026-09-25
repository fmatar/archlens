package com.design.umlviewer.mcp;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.resource.DiagramResource;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Map;

@ApplicationScoped
public class ArchlensMcpService {

  @Inject DiagramResource diagramResource;

  public ArchlensMcpService() {}

  public ArchlensMcpService(DiagramResource diagramResource) {
    this.diagramResource = diagramResource;
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
    return diagramResource.getGraph(projectRoot, null);
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
    return diagramResource.getLlmDossier(
        projectRoot, proposalId == null || proposalId.isBlank() ? null : proposalId);
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
    return diagramResource.listSnapshots(projectRoot);
  }

  @Tool(
      description =
          "Retrieve the Clean Architecture model for a specific historical Git release tag or pre-compiled snapshot ID.")
  public ArchitectureGraph getSnapshot(
      @ToolArg(description = "Snapshot identifier or Git release tag name (e.g. v1.0.0)")
          String snapshotId,
      @ToolArg(
              description =
                  "Absolute or relative path to the target project directory. Defaults to current directory.",
              defaultValue = ".")
          String projectRoot)
      throws IOException {
    return diagramResource.getSnapshot(snapshotId, projectRoot);
  }
}
