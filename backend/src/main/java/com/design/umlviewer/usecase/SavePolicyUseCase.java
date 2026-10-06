package com.design.umlviewer.usecase;

import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.ArchitectureCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application use case (Level 1) responsible for validating, persisting, and recompiling
 * architectural governance policies (.archlens/policy.json).
 */
@ApplicationScoped
public class SavePolicyUseCase {

  @Inject ArchitectureCompiler graphCompiler;

  @Inject ObjectMapper mapper;

  public SavePolicyUseCase() {
    this.mapper = new ObjectMapper();
  }

  public SavePolicyUseCase(ArchitectureCompiler graphCompiler, ObjectMapper mapper) {
    this.graphCompiler = graphCompiler;
    this.mapper = mapper != null ? mapper : new ObjectMapper();
  }

  public ArchitectureGraph execute(String projectRoot, ArchitecturePolicy policy)
      throws IOException {
    if (policy == null) {
      throw new IllegalArgumentException("ArchitecturePolicy must not be null.");
    }
    if (policy.levels() == null || policy.levels().isEmpty()) {
      throw new IllegalArgumentException("ArchitecturePolicy levels must not be null or empty.");
    }

    String normalizedRoot = WorkspacePathResolver.normalizeRoot(projectRoot);
    Path rootPath = Paths.get(normalizedRoot);

    Path configDir = rootPath.resolve(".archlens");
    if (!Files.exists(configDir) && Files.exists(rootPath.resolve(".uml-viewer"))) {
      configDir = rootPath.resolve(".uml-viewer");
    }

    if (!Files.exists(configDir)) {
      Files.createDirectories(configDir);
    }

    Path policyPath = configDir.resolve("policy.json");
    mapper.writerWithDefaultPrettyPrinter().writeValue(policyPath.toFile(), policy);

    if (graphCompiler != null) {
      return graphCompiler.compileGraph(normalizedRoot, null);
    }
    return null;
  }
}
