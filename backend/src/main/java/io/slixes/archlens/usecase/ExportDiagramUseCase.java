package io.slixes.archlens.usecase;

import io.slixes.archlens.domain.dossier.DiagramExportType;
import io.slixes.archlens.domain.dossier.MermaidDiagramGenerator;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.List;

/**
 * Level 1 Application Use Case orchestrating architecture graph compilation and Mermaid diagram
 * export across Concentric Rings, Class UML, and Before/After DIP Refactoring views.
 */
@ApplicationScoped
public class ExportDiagramUseCase {

  @Inject ArchitectureCompiler architectureCompiler;

  @Inject MermaidDiagramGenerator mermaidGenerator;

  public ExportDiagramUseCase() {}

  public ExportDiagramUseCase(
      ArchitectureCompiler architectureCompiler, MermaidDiagramGenerator mermaidGenerator) {
    this.architectureCompiler = architectureCompiler;
    this.mermaidGenerator =
        mermaidGenerator != null ? mermaidGenerator : new MermaidDiagramGenerator();
  }

  private MermaidDiagramGenerator generator() {
    if (mermaidGenerator == null) {
      mermaidGenerator = new MermaidDiagramGenerator();
    }
    return mermaidGenerator;
  }

  public String execute(
      String projectRoot,
      String proposalId,
      String rawDiagramType,
      String focusedComponentId,
      boolean violationsOnly,
      String fromClass,
      String toClass)
      throws IOException {
    ArchitectureGraph graph = architectureCompiler.compileGraph(projectRoot, proposalId);
    ArchitecturePolicy policy = architectureCompiler.loadPolicy(projectRoot);
    DiagramExportType exportType = DiagramExportType.fromString(rawDiagramType);

    if (exportType == DiagramExportType.CLASS_UML) {
      return generator().generateClassUml(graph, focusedComponentId, violationsOnly);
    }
    if (exportType == DiagramExportType.DIP_REFACTOR) {
      return resolveAndGenerateDip(graph, fromClass, toClass);
    }
    return generator().generateComponentRings(graph, policy);
  }

  private String resolveAndGenerateDip(
      ArchitectureGraph graph, String explicitFrom, String explicitTo) {
    String src = explicitFrom;
    String tgt = explicitTo;
    if ((src == null || src.isBlank() || tgt == null || tgt.isBlank()) && graph != null) {
      DependencyEdge firstViolation = findFirstViolatingEdge(graph.edges());
      if (firstViolation != null) {
        src = firstViolation.from();
        tgt = firstViolation.to();
      }
    }
    String safeSrc = (src != null && !src.isBlank()) ? src : "InnerDomainService";
    String safeTgt = (tgt != null && !tgt.isBlank()) ? tgt : "OuterInfrastructureAdapter";
    int dot = safeTgt.lastIndexOf('.');
    String tgtSimple = dot >= 0 ? safeTgt.substring(dot + 1) : safeTgt;
    String portName = tgtSimple + "Port";

    Integer srcLevel = findClassLevel(graph, safeSrc, 1);
    Integer tgtLevel = findClassLevel(graph, safeTgt, 3);
    return generator().generateDipRefactor(safeSrc, safeTgt, portName, srcLevel, tgtLevel);
  }

  private DependencyEdge findFirstViolatingEdge(List<DependencyEdge> edges) {
    if (edges == null) return null;
    for (DependencyEdge e : edges) {
      if (e.isViolating()) return e;
    }
    return edges.isEmpty() ? null : edges.getFirst();
  }

  private Integer findClassLevel(ArchitectureGraph graph, String classId, int fallback) {
    if (graph == null || graph.components() == null) return fallback;
    for (ComponentNode comp : graph.components()) {
      if (comp.classes() == null) continue;
      for (ClassNode cls : comp.classes()) {
        if (classId.equals(cls.id()) || classId.equals(cls.name())) {
          return comp.level() != null ? comp.level() : fallback;
        }
      }
    }
    return fallback;
  }
}
