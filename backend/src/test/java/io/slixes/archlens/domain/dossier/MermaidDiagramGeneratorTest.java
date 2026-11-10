package io.slixes.archlens.domain.dossier;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.graph.CrapScore;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.FieldNode;
import io.slixes.archlens.domain.graph.MethodNode;
import io.slixes.archlens.domain.graph.PackageCycle;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MermaidDiagramGeneratorTest {

  private ArchitectureGraph sampleGraph() {
    ClassNode orderEntity =
        new ClassNode(
            "com.example.domain.Order",
            "Order",
            "com.example.domain",
            "src/main/java/com/example/domain/Order.java",
            ClassNode.Stereotype.RECORD,
            false,
            0,
            new CrapScore(2.1, 3.0, 0.5),
            98.0,
            2,
            5,
            0,
            0,
            List.of(new FieldNode("id", "String", false)),
            List.of(
                new MethodNode(
                    "total", List.of(), "BigDecimal", false, 1, 100.0, 1.5, 2, 0, 0, 10)));

    ClassNode orderPort =
        new ClassNode(
            "com.example.domain.OrderPort",
            "OrderPort",
            "com.example.domain",
            "src/main/java/com/example/domain/OrderPort.java",
            ClassNode.Stereotype.INTERFACE,
            false,
            0,
            new CrapScore(1.0, 1.0, 0.0),
            100.0,
            1,
            2,
            0,
            0,
            List.of(),
            List.of(
                new MethodNode(
                    "save", List.of("Order"), "void", false, 1, 100.0, 1.0, 1, 0, 0, 12)));

    ClassNode orderService =
        new ClassNode(
            "com.example.application.OrderService",
            "OrderService",
            "com.example.application",
            "src/main/java/com/example/application/OrderService.java",
            ClassNode.Stereotype.CLASS,
            false,
            1,
            new CrapScore(4.2, 6.0, 1.1),
            90.0,
            4,
            8,
            0,
            0,
            List.of(new FieldNode("repo", "PostgresOrderRepo", true)),
            List.of(
                new MethodNode(
                    "placeOrder", List.of("Order"), "void", false, 2, 92.0, 3.5, 4, 0, 0, 20)));

    ClassNode postgresRepo =
        new ClassNode(
            "com.example.infrastructure.PostgresOrderRepo",
            "PostgresOrderRepo",
            "com.example.infrastructure",
            "src/main/java/com/example/infrastructure/PostgresOrderRepo.java",
            ClassNode.Stereotype.CLASS,
            false,
            3,
            new CrapScore(8.5, 12.0, 2.0),
            85.0,
            5,
            6,
            1,
            0,
            List.of(),
            List.of(
                new MethodNode(
                    "save", List.of("Order"), "void", false, 2, 85.0, 4.0, 3, 1, 0, 15)));

    ComponentNode domainComp =
        new ComponentNode(
            "com.example.domain",
            "domain",
            0,
            new CrapScore(1.5, 3.0, 0.5),
            99.0,
            List.of(),
            List.of(orderEntity, orderPort));
    ComponentNode appComp =
        new ComponentNode(
            "com.example.application",
            "application",
            1,
            new CrapScore(4.2, 6.0, 1.1),
            90.0,
            List.of(),
            List.of(orderService));
    ComponentNode infraComp =
        new ComponentNode(
            "com.example.infrastructure",
            "infrastructure",
            3,
            new CrapScore(8.5, 12.0, 2.0),
            85.0,
            List.of(),
            List.of(postgresRepo));

    DependencyEdge legalEdge =
        new DependencyEdge(
            "com.example.application.OrderService",
            "com.example.domain.Order",
            DependencyEdge.Kind.DEPENDENCY,
            "uses",
            false);
    DependencyEdge implementsEdge =
        new DependencyEdge(
            "com.example.infrastructure.PostgresOrderRepo",
            "com.example.domain.OrderPort",
            DependencyEdge.Kind.IMPLEMENTS,
            "implements",
            false);
    DependencyEdge violatingEdge =
        new DependencyEdge(
            "com.example.application.OrderService",
            "com.example.infrastructure.PostgresOrderRepo",
            DependencyEdge.Kind.DEPENDENCY,
            "violates L1 -> L3",
            true);

    return new ArchitectureGraph(
        "Order Platform",
        false,
        null,
        List.of(domainComp, appComp, infraComp),
        List.of(legalEdge, implementsEdge, violatingEdge),
        List.of(),
        List.of(
            new PackageCycle(List.of("com.example.application", "com.example.infrastructure"))));
  }

  @Test
  @DisplayName("DiagramExportType parses case-insensitively and defaults to COMPONENT_RINGS")
  void testDiagramExportTypeParsing() {
    assertEquals(DiagramExportType.COMPONENT_RINGS, DiagramExportType.fromString(null));
    assertEquals(DiagramExportType.COMPONENT_RINGS, DiagramExportType.fromString(""));
    assertEquals(DiagramExportType.COMPONENT_RINGS, DiagramExportType.fromString("unknown"));
    assertEquals(
        DiagramExportType.COMPONENT_RINGS, DiagramExportType.fromString("component_rings"));
    assertEquals(DiagramExportType.CLASS_UML, DiagramExportType.fromString("class_uml"));
    assertEquals(DiagramExportType.CLASS_UML, DiagramExportType.fromString("CLASS"));
    assertEquals(DiagramExportType.DIP_REFACTOR, DiagramExportType.fromString("dip_refactor"));
    assertEquals(DiagramExportType.DIP_REFACTOR, DiagramExportType.fromString("dip"));
  }

  @Test
  @DisplayName(
      "Should generate Concentric Ring Component flowchart with subgraphs and red violation linkStyle")
  void testGenerateComponentRings() {
    MermaidDiagramGenerator generator = new MermaidDiagramGenerator();
    ArchitecturePolicy policy =
        new ArchitecturePolicy(
            "Order Platform",
            "src/main/java",
            "com.example",
            true,
            List.of("domain", "application", "infrastructure"),
            List.of(
                List.of("domain"), List.of("application"), List.of(), List.of("infrastructure")),
            List.of(),
            List.of(),
            List.of());

    String mmd = generator.generateComponentRings(sampleGraph(), policy);

    assertNotNull(mmd);
    assertTrue(mmd.startsWith("flowchart TB"));
    assertTrue(mmd.contains("subgraph Ring_L0[\"Ring L0: Domain Core\"]"));
    assertTrue(mmd.contains("subgraph Ring_L1[\"Ring L1: Application\"]"));
    assertTrue(mmd.contains("subgraph Ring_L3[\"Ring L3: Infrastructure\"]"));
    assertTrue(mmd.contains("VIOLATION"));
    assertTrue(mmd.contains("linkStyle"));
    assertTrue(mmd.contains("stroke:#ef4444"));
  }

  @Test
  @DisplayName("Should handle null or empty graph gracefully in generateComponentRings")
  void testGenerateComponentRingsEmpty() {
    MermaidDiagramGenerator generator = new MermaidDiagramGenerator();
    String mmd = generator.generateComponentRings(null, null);
    assertTrue(mmd.startsWith("flowchart TB"));
    assertTrue(mmd.contains("Empty[\"No Architecture Components Found\"]"));
  }

  @Test
  @DisplayName(
      "Should generate native Mermaid classDiagram with stereotypes, members, and filtered relations")
  void testGenerateClassUml() {
    MermaidDiagramGenerator generator = new MermaidDiagramGenerator();
    String mmd = generator.generateClassUml(sampleGraph(), null, false);

    assertNotNull(mmd);
    assertTrue(mmd.startsWith("classDiagram"));
    assertTrue(mmd.contains("<<record>>"));
    assertTrue(mmd.contains("<<interface>>"));
    assertTrue(mmd.contains("+String id"));
    assertTrue(mmd.contains("+total() BigDecimal"));
    assertTrue(mmd.contains("..|>"));
    assertTrue(mmd.contains("-->"));

    String violationsOnlyMmd = generator.generateClassUml(sampleGraph(), null, true);
    assertTrue(violationsOnlyMmd.contains("OrderService"));
    assertTrue(violationsOnlyMmd.contains("PostgresOrderRepo"));
    assertFalse(violationsOnlyMmd.contains("OrderPort"));

    String focusedMmd = generator.generateClassUml(sampleGraph(), "com.example.domain", false);
    assertTrue(focusedMmd.contains("Order"));
    assertTrue(focusedMmd.contains("OrderPort"));
  }

  @Test
  @DisplayName("Should generate Before vs After DIP Refactoring flowchart with extracted port")
  void testGenerateDipRefactor() {
    MermaidDiagramGenerator generator = new MermaidDiagramGenerator();
    String mmd =
        generator.generateDipRefactor(
            "com.example.application.OrderService",
            "com.example.infrastructure.PostgresOrderRepo",
            "PostgresOrderRepoPort",
            1,
            3);

    assertNotNull(mmd);
    assertTrue(mmd.startsWith("flowchart TB"));
    assertTrue(mmd.contains("subgraph Before"));
    assertTrue(mmd.contains("subgraph After"));
    assertTrue(mmd.contains("OrderService"));
    assertTrue(mmd.contains("PostgresOrderRepo"));
    assertTrue(mmd.contains("PostgresOrderRepoPort"));
    assertTrue(mmd.contains("implements"));
    assertTrue(mmd.contains("stroke:#ef4444"));
    assertTrue(mmd.contains("stroke:#10b981"));
  }
}
