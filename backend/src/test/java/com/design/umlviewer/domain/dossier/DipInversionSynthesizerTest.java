package com.design.umlviewer.domain.dossier;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.model.MethodNode;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DipInversionSynthesizerTest {

  private DipInversionSynthesizer synthesizer;
  private ArchitecturePolicy policy;
  private ArchitectureGraph graph;

  @BeforeEach
  void setUp() {
    synthesizer = new DipInversionSynthesizer();

    policy =
        new ArchitecturePolicy(
            "Test Shop",
            "src/main/java",
            "com.example",
            true,
            List.of("domain", "application", "adapters", "infrastructure"),
            List.of(
                List.of("domain"),
                List.of("application"),
                List.of("adapters"),
                List.of("infrastructure")),
            List.of(),
            List.of(),
            List.of());

    MethodNode saveMethod =
        new MethodNode("save", List.of("Order"), "void", false, 1, 1.0, 1.0, 1, 0, 0, 15);
    MethodNode findMethod =
        new MethodNode("findById", List.of("String"), "Order", false, 1, 1.0, 1.0, 1, 0, 0, 20);

    ClassNode callerClass =
        new ClassNode(
            "com.example.application.OrderService",
            "OrderService",
            "com.example.application",
            "src/main/java/com/example/application/OrderService.java",
            ClassNode.Stereotype.CLASS,
            false,
            1,
            null,
            1.0,
            2,
            4,
            0,
            0,
            List.of(),
            List.of());

    ClassNode adapterClass =
        new ClassNode(
            "com.example.adapters.PostgresOrderRepository",
            "PostgresOrderRepository",
            "com.example.adapters",
            "src/main/java/com/example/adapters/PostgresOrderRepository.java",
            ClassNode.Stereotype.CLASS,
            false,
            2,
            null,
            0.9,
            3,
            6,
            0,
            0,
            List.of(),
            List.of(saveMethod, findMethod));

    ComponentNode appComp =
        new ComponentNode(
            "com.example.application",
            "application",
            1,
            null,
            1.0,
            List.of(),
            List.of(callerClass));
    ComponentNode adapterComp =
        new ComponentNode(
            "com.example.adapters", "adapters", 2, null, 0.9, List.of(), List.of(adapterClass));

    DependencyEdge violatingEdge =
        new DependencyEdge(
            "com.example.application.OrderService",
            "com.example.adapters.PostgresOrderRepository",
            DependencyEdge.Kind.DEPENDENCY,
            null,
            true);

    graph =
        new ArchitectureGraph(
            "Test Shop",
            false,
            null,
            List.of(appComp, adapterComp),
            List.of(violatingEdge),
            List.of());
  }

  @Test
  @DisplayName("Should synthesize a complete DIP inversion plan for an outward violation")
  void shouldSynthesizeInversionPlanForDirectOutwardViolation() {
    DipInversionPlan plan =
        synthesizer.synthesize(
            graph,
            policy,
            "com.example.application.OrderService",
            "com.example.adapters.PostgresOrderRepository");

    assertNotNull(plan);
    assertEquals("com.example.application.OrderService", plan.fromClass());
    assertEquals("com.example.adapters.PostgresOrderRepository", plan.toClass());
    assertEquals(1, plan.fromLevel());
    assertEquals(2, plan.toLevel());

    // Port interface derivation
    assertTrue(
        plan.portName().contains("Repository") || plan.portName().endsWith("Port"),
        "Port name should be descriptive: " + plan.portName());
    assertEquals("com.example.application.ports", plan.portPackage());
    assertTrue(plan.portFilePath().endsWith(plan.portName() + ".java"));

    // Interface code content
    assertTrue(plan.portInterfaceCode().contains("public interface " + plan.portName()));
    assertTrue(
        plan.portInterfaceCode().contains("void save(Order arg0);")
            || plan.portInterfaceCode().contains("save"));
    assertTrue(
        plan.portInterfaceCode().contains("Order findById(String arg0);")
            || plan.portInterfaceCode().contains("findById"));

    // Adapter refactor preview
    assertTrue(
        plan.adapterRefactorPreview().contains("implements " + plan.portName()),
        "Adapter preview must implement synthesized port");

    // Caller refactor preview
    assertTrue(
        plan.callerRefactorPreview().contains(plan.portName()),
        "Caller preview must depend on port interface");

    // Surgical prompt
    assertTrue(plan.surgicalPrompt().contains("Dependency Inversion Principle"));
    assertTrue(plan.surgicalPrompt().contains(plan.portName()));
  }

  @Test
  @DisplayName(
      "Should handle target with no methods gracefully by synthesizing placeholder contract")
  void shouldHandleTargetWithNoMethodsGracefully() {
    ClassNode emptyAdapter =
        new ClassNode(
            "com.example.adapters.EmptyGateway",
            "EmptyGateway",
            "com.example.adapters",
            "src/main/java/com/example/adapters/EmptyGateway.java",
            ClassNode.Stereotype.CLASS,
            false,
            2,
            null,
            1.0,
            1,
            2,
            0,
            0,
            List.of(),
            List.of());

    ComponentNode emptyComp =
        new ComponentNode(
            "com.example.adapters", "adapters", 2, null, 1.0, List.of(), List.of(emptyAdapter));

    ArchitectureGraph g =
        new ArchitectureGraph(
            "Test",
            false,
            null,
            List.of(graph.components().get(0), emptyComp),
            List.of(),
            List.of());

    DipInversionPlan plan =
        synthesizer.synthesize(
            g, policy, "com.example.application.OrderService", "com.example.adapters.EmptyGateway");

    assertNotNull(plan);
    assertTrue(plan.portInterfaceCode().contains("public interface"));
    assertNotNull(plan.targetMethods());
  }

  @Test
  @DisplayName("Should fall back cleanly if classes are not found in the graph")
  void shouldFallbackCleanlyWhenClassesNotFound() {
    DipInversionPlan plan =
        synthesizer.synthesize(
            graph,
            policy,
            "com.example.unknown.CustomService",
            "com.example.unknown.ThirdPartyClient");

    assertNotNull(plan);
    assertEquals("com.example.unknown.CustomService", plan.fromClass());
    assertEquals("com.example.unknown.ThirdPartyClient", plan.toClass());
    assertNotNull(plan.portName());
    assertNotNull(plan.portInterfaceCode());
    assertNotNull(plan.surgicalPrompt());
  }
}
