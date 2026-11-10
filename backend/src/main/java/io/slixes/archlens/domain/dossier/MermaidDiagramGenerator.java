package io.slixes.archlens.domain.dossier;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.FieldNode;
import io.slixes.archlens.domain.graph.MethodNode;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure Domain Service (Ring L0) synthesizing Mermaid diagrams across Concentric Ring Component
 * Topology, Class & Interface UML, and Before/After DIP Port Refactoring views.
 */
@ApplicationScoped
public class MermaidDiagramGenerator {

  private static final List<String> RING_TITLES =
      List.of(
          "Ring L0: Domain Core",
          "Ring L1: Application",
          "Ring L2: Adapters",
          "Ring L3: Infrastructure");

  /**
   * Generates a Concentric Ring Component topology flowchart TB diagram with ring subgraphs and red
   * linkStyle highlights for outward Dependency Rule violations.
   */
  public String generateComponentRings(ArchitectureGraph graph, ArchitecturePolicy policy) {
    if (graph == null || graph.components() == null || graph.components().isEmpty()) {
      return "flowchart TB\n  Empty[\"No Architecture Components Found\"]\n";
    }

    StringBuilder sb = new StringBuilder("flowchart TB\n");
    Map<String, ComponentNode> classToComponent = indexClassToComponent(graph.components());
    Map<Integer, List<ComponentNode>> byLevel = groupComponentsByLevel(graph.components());

    for (Map.Entry<Integer, List<ComponentNode>> entry : byLevel.entrySet()) {
      int level = entry.getKey();
      sb.append("  subgraph Ring_L")
          .append(level)
          .append("[\"")
          .append(ringTitle(level))
          .append("\"]\n");
      for (ComponentNode comp : entry.getValue()) {
        int classCount = comp.classes() != null ? comp.classes().size() : 0;
        sb.append("    ")
            .append(sanitizeId("comp_" + comp.id()))
            .append("[\"")
            .append(escapeLabel(comp.label()))
            .append(" (")
            .append(classCount)
            .append(" classes)\"]\n");
      }
      sb.append("  end\n");
    }

    appendComponentEdgesAndStyles(sb, graph.edges(), classToComponent);
    return sb.toString();
  }

  /**
   * Generates a native Mermaid classDiagram honoring optional package focus or violationsOnly
   * filtering.
   */
  public String generateClassUml(
      ArchitectureGraph graph, String focusedComponentId, boolean violationsOnly) {
    if (graph == null || graph.components() == null || graph.components().isEmpty()) {
      return "classDiagram\n  class Empty\n";
    }

    StringBuilder sb = new StringBuilder("classDiagram\n");
    List<DependencyEdge> edges = filterClassEdges(graph, focusedComponentId, violationsOnly);
    Set<String> activeClassIds =
        collectActiveClassIds(graph, edges, focusedComponentId, violationsOnly);

    Map<String, String> classIdToAlias = new HashMap<>();
    for (ComponentNode comp : graph.components()) {
      if (comp.classes() == null) continue;
      for (ClassNode cls : comp.classes()) {
        if (!activeClassIds.contains(cls.id())) continue;
        String alias = sanitizeId(cls.name() + "_" + Math.abs(cls.id().hashCode() % 1000));
        classIdToAlias.put(cls.id(), alias);
        classIdToAlias.putIfAbsent(cls.name(), alias);
        appendClassDefinition(sb, cls, alias);
      }
    }

    appendClassRelations(sb, edges, classIdToAlias);
    return sb.toString();
  }

  /**
   * Generates a side-by-side Before vs. After Dependency Inversion Principle (DIP) refactoring
   * flowchart TB diagram suitable for embedding in ADRs and PR descriptions.
   */
  public String generateDipRefactor(
      String sourceClass,
      String targetClass,
      String portName,
      Integer sourceLevel,
      Integer targetLevel) {
    String srcSimple = simpleName(sourceClass, "CallerService");
    String tgtSimple = simpleName(targetClass, "ConcreteAdapter");
    String portSimple =
        (portName != null && !portName.isBlank())
            ? simpleName(portName, tgtSimple + "Port")
            : tgtSimple + "Port";
    int sLvl = sourceLevel != null ? sourceLevel : 1;
    int tLvl = targetLevel != null ? targetLevel : Math.max(sLvl + 1, 3);

    StringBuilder sb = new StringBuilder("flowchart TB\n");
    sb.append("  subgraph Before[\"❌ Before: Outward Dependency Violation\"]\n");
    sb.append("    direction TB\n");
    sb.append("    B_Src[\"")
        .append(escapeLabel(srcSimple))
        .append(" (Ring L")
        .append(sLvl)
        .append(")\"] -->|\"❌ Violates L")
        .append(sLvl)
        .append(" → L")
        .append(tLvl)
        .append("\"| B_Tgt[\"")
        .append(escapeLabel(tgtSimple))
        .append(" (Ring L")
        .append(tLvl)
        .append(")\"]\n");
    sb.append("  end\n");

    sb.append("  subgraph After[\"✅ After: DIP Port Inversion\"]\n");
    sb.append("    direction TB\n");
    sb.append("    A_Src[\"")
        .append(escapeLabel(srcSimple))
        .append(" (Ring L")
        .append(sLvl)
        .append(")\"] -->|\"depends on\"| A_Port[\"<<interface>> ")
        .append(escapeLabel(portSimple))
        .append(" (Ring L")
        .append(sLvl)
        .append(")\"]\n");
    sb.append("    A_Tgt[\"")
        .append(escapeLabel(tgtSimple))
        .append(" (Ring L")
        .append(tLvl)
        .append(")\"] -.->|\"implements\"| A_Port\n");
    sb.append("  end\n");
    sb.append("  linkStyle 0 stroke:#ef4444,stroke-width:2.5px,color:#fca5a5\n");
    sb.append("  linkStyle 1 stroke:#10b981,stroke-width:2px,color:#6ee7b7\n");
    sb.append("  linkStyle 2 stroke:#10b981,stroke-width:2px,color:#6ee7b7\n");
    return sb.toString();
  }

  private Map<String, ComponentNode> indexClassToComponent(List<ComponentNode> components) {
    Map<String, ComponentNode> map = new HashMap<>();
    for (ComponentNode comp : components) {
      map.put(comp.id(), comp);
      if (comp.classes() == null) continue;
      for (ClassNode cls : comp.classes()) {
        map.put(cls.id(), comp);
        map.putIfAbsent(cls.name(), comp);
      }
    }
    return map;
  }

  private Map<Integer, List<ComponentNode>> groupComponentsByLevel(List<ComponentNode> components) {
    Map<Integer, List<ComponentNode>> byLevel = new TreeMap<>();
    for (ComponentNode comp : components) {
      int level = comp.level() != null ? comp.level() : 3;
      byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(comp);
    }
    for (List<ComponentNode> list : byLevel.values()) {
      list.sort(Comparator.comparing(ComponentNode::id));
    }
    return byLevel;
  }

  private void appendComponentEdgesAndStyles(
      StringBuilder sb, List<DependencyEdge> edges, Map<String, ComponentNode> classToComponent) {
    if (edges == null || edges.isEmpty()) return;

    Map<String, int[]> bundled = new LinkedHashMap<>();
    for (DependencyEdge edge : edges) {
      ComponentNode fromComp = classToComponent.get(edge.from());
      ComponentNode toComp = classToComponent.get(edge.to());
      if (fromComp == null || toComp == null || fromComp.id().equals(toComp.id())) continue;
      String key = fromComp.id() + "->" + toComp.id();
      int[] counts = bundled.computeIfAbsent(key, k -> new int[] {0, 0});
      counts[0]++;
      if (edge.isViolating()) counts[1]++;
    }

    List<Integer> violatingIndices = new ArrayList<>();
    List<Integer> conformingIndices = new ArrayList<>();
    int edgeIndex = 0;

    for (Map.Entry<String, int[]> entry : bundled.entrySet()) {
      String[] parts = entry.getKey().split("->", 2);
      String fromId = sanitizeId("comp_" + parts[0]);
      String toId = sanitizeId("comp_" + parts[1]);
      int total = entry.getValue()[0];
      int violations = entry.getValue()[1];
      if (violations > 0) {
        sb.append("  ")
            .append(fromId)
            .append(" -->|\"❌ VIOLATION (")
            .append(violations)
            .append("/")
            .append(total)
            .append(")\"| ")
            .append(toId)
            .append("\n");
        violatingIndices.add(edgeIndex);
      } else {
        sb.append("  ")
            .append(fromId)
            .append(" -->|\"")
            .append(total)
            .append(" refs\"| ")
            .append(toId)
            .append("\n");
        conformingIndices.add(edgeIndex);
      }
      edgeIndex++;
    }

    for (int idx : violatingIndices) {
      sb.append("  linkStyle ")
          .append(idx)
          .append(" stroke:#ef4444,stroke-width:2.5px,color:#fca5a5\n");
    }
    for (int idx : conformingIndices) {
      sb.append("  linkStyle ").append(idx).append(" stroke:#64748b,stroke-width:1.5px\n");
    }
  }

  private List<DependencyEdge> filterClassEdges(
      ArchitectureGraph graph, String focusedComponentId, boolean violationsOnly) {
    List<DependencyEdge> allEdges = graph.edges() != null ? graph.edges() : List.of();
    Map<String, ComponentNode> classToComp = indexClassToComponent(graph.components());
    List<DependencyEdge> result = new ArrayList<>();
    for (DependencyEdge edge : allEdges) {
      if (violationsOnly && !edge.isViolating()) continue;
      if (focusedComponentId != null && !focusedComponentId.isBlank()) {
        ComponentNode fc = classToComp.get(edge.from());
        ComponentNode tc = classToComp.get(edge.to());
        boolean matches =
            (fc != null && focusedComponentId.equals(fc.id()))
                || (tc != null && focusedComponentId.equals(tc.id()));
        if (!matches) continue;
      }
      result.add(edge);
    }
    return result;
  }

  private Set<String> collectActiveClassIds(
      ArchitectureGraph graph,
      List<DependencyEdge> filteredEdges,
      String focusedComponentId,
      boolean violationsOnly) {
    Set<String> ids = new LinkedHashSet<>();
    for (DependencyEdge e : filteredEdges) {
      ids.add(e.from());
      ids.add(e.to());
    }
    if (!violationsOnly) {
      for (ComponentNode comp : graph.components()) {
        if (focusedComponentId != null
            && !focusedComponentId.isBlank()
            && !focusedComponentId.equals(comp.id())) {
          continue;
        }
        if (comp.classes() != null) {
          for (ClassNode cls : comp.classes()) {
            ids.add(cls.id());
          }
        }
      }
    }
    return ids;
  }

  private void appendClassDefinition(StringBuilder sb, ClassNode cls, String alias) {
    sb.append("  class ")
        .append(alias)
        .append("[\"")
        .append(escapeLabel(cls.name()))
        .append("\"] {\n");
    if (cls.stereotype() != null && cls.stereotype() != ClassNode.Stereotype.CLASS) {
      sb.append("    <<").append(cls.stereotype().name().toLowerCase(Locale.ROOT)).append(">>\n");
    }
    if (cls.fields() != null) {
      for (FieldNode f : cls.fields()) {
        sb.append("    ")
            .append(f.isPrivate() ? "-" : "+")
            .append(sanitizeType(f.type()))
            .append(" ")
            .append(sanitizeMember(f.name()))
            .append("\n");
      }
    }
    if (cls.methods() != null) {
      for (MethodNode m : cls.methods()) {
        sb.append("    ")
            .append(m.isPrivate() ? "-" : "+")
            .append(sanitizeMember(m.name()))
            .append("() ")
            .append(sanitizeType(m.returnType()))
            .append("\n");
      }
    }
    sb.append("  }\n");
  }

  private void appendClassRelations(
      StringBuilder sb, List<DependencyEdge> edges, Map<String, String> classIdToAlias) {
    for (DependencyEdge edge : edges) {
      String fromAlias = classIdToAlias.get(edge.from());
      String toAlias = classIdToAlias.get(edge.to());
      if (fromAlias == null || toAlias == null || fromAlias.equals(toAlias)) continue;
      String arrow = relationArrow(edge.kind());
      String label =
          edge.isViolating() ? "VIOLATION" : edge.label() != null ? edge.label() : "uses";
      sb.append("  ")
          .append(fromAlias)
          .append(" ")
          .append(arrow)
          .append(" ")
          .append(toAlias)
          .append(" : ")
          .append(escapeLabel(label))
          .append("\n");
    }
  }

  private String relationArrow(DependencyEdge.Kind kind) {
    if (kind == DependencyEdge.Kind.IMPLEMENTS) return "..|>";
    if (kind == DependencyEdge.Kind.INHERITANCE) return "--|>";
    return "-->";
  }

  private String ringTitle(int level) {
    if (level >= 0 && level < RING_TITLES.size()) {
      return RING_TITLES.get(level);
    }
    return "Ring L" + level;
  }

  private String simpleName(String fqcn, String fallback) {
    if (fqcn == null || fqcn.isBlank()) return fallback;
    int dot = fqcn.lastIndexOf('.');
    return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
  }

  private String sanitizeId(String raw) {
    if (raw == null || raw.isBlank()) return "node";
    return raw.replaceAll("[^a-zA-Z0-9_]", "_");
  }

  private String sanitizeType(String raw) {
    if (raw == null || raw.isBlank()) return "void";
    return raw.replaceAll("[^a-zA-Z0-9_<>]", "");
  }

  private String sanitizeMember(String raw) {
    if (raw == null || raw.isBlank()) return "member";
    return raw.replaceAll("[^a-zA-Z0-9_]", "");
  }

  private String escapeLabel(String raw) {
    if (raw == null) return "";
    return raw.replace("\"", "'").replace("\n", " ");
  }
}
