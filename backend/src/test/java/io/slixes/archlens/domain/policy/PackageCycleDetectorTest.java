package io.slixes.archlens.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.*;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PackageCycleDetectorTest {

  @Test
  @DisplayName("detectCycles returns empty list for null or empty graph")
  void testEmptyGraphReturnsNoCycles() {
    assertTrue(PackageCycleDetector.detectCycles(null).isEmpty());
    ArchitectureGraph empty =
        new ArchitectureGraph("Empty", false, null, List.of(), List.of(), List.of());
    assertTrue(PackageCycleDetector.detectCycles(empty).isEmpty());
  }

  @Test
  @DisplayName("detectCycles returns empty list for acyclic package dependencies")
  void testAcyclicDependencies() {
    ClassNode classA = createClass("com.app.service.OrderService", "com.app.service");
    ClassNode classB = createClass("com.app.repository.OrderRepository", "com.app.repository");
    ClassNode classC = createClass("com.app.model.Order", "com.app.model");

    ComponentNode compService =
        new ComponentNode(
            "service", "Service", 1, null, 0.8, List.of("com.app.service"), List.of(classA));
    ComponentNode compRepo =
        new ComponentNode(
            "repo", "Repo", 2, null, 0.8, List.of("com.app.repository"), List.of(classB));
    ComponentNode compModel =
        new ComponentNode(
            "model", "Model", 0, null, 0.8, List.of("com.app.model"), List.of(classC));

    // A -> B -> C (Acyclic)
    DependencyEdge edge1 = createEdge(classA.id(), classB.id());
    DependencyEdge edge2 = createEdge(classB.id(), classC.id());

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Acyclic App",
            false,
            null,
            List.of(compService, compRepo, compModel),
            List.of(edge1, edge2),
            List.of());

    List<PackageCycle> cycles = PackageCycleDetector.detectCycles(graph);
    assertTrue(cycles.isEmpty(), "Expected no package cycles in an acyclic architecture");
  }

  @Test
  @DisplayName("detectCycles detects direct cycle (A -> B -> A)")
  void testDirectCycleDetection() {
    ClassNode classA = createClass("com.pkg.a.ClassA", "com.pkg.a");
    ClassNode classB = createClass("com.pkg.b.ClassB", "com.pkg.b");

    ComponentNode compA =
        new ComponentNode("compA", "CompA", 1, null, 0.8, List.of("com.pkg.a"), List.of(classA));
    ComponentNode compB =
        new ComponentNode("compB", "CompB", 2, null, 0.8, List.of("com.pkg.b"), List.of(classB));

    // A -> B and B -> A
    DependencyEdge edge1 = createEdge(classA.id(), classB.id());
    DependencyEdge edge2 = createEdge(classB.id(), classA.id());

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Direct Cycle App",
            false,
            null,
            List.of(compA, compB),
            List.of(edge1, edge2),
            List.of());

    List<PackageCycle> cycles = PackageCycleDetector.detectCycles(graph);
    assertEquals(1, cycles.size());
    PackageCycle cycle = cycles.get(0);
    assertEquals(3, cycle.packages().size()); // [A, B, A]
    assertEquals(cycle.packages().get(0), cycle.packages().get(cycle.packages().size() - 1));
    assertTrue(
        cycle.formattedPath().contains("com.pkg.a") && cycle.formattedPath().contains("com.pkg.b"));
  }

  @Test
  @DisplayName("detectCycles detects transitive 3-tier cycle (A -> B -> C -> A)")
  void testTransitiveCycleDetection() {
    ClassNode classA = createClass("com.tier.a.ClassA", "com.tier.a");
    ClassNode classB = createClass("com.tier.b.ClassB", "com.tier.b");
    ClassNode classC = createClass("com.tier.c.ClassC", "com.tier.c");

    ComponentNode compA =
        new ComponentNode("compA", "CompA", 1, null, 0.8, List.of("com.tier.a"), List.of(classA));
    ComponentNode compB =
        new ComponentNode("compB", "CompB", 1, null, 0.8, List.of("com.tier.b"), List.of(classB));
    ComponentNode compC =
        new ComponentNode("compC", "CompC", 1, null, 0.8, List.of("com.tier.c"), List.of(classC));

    // A -> B -> C -> A
    DependencyEdge edge1 = createEdge(classA.id(), classB.id());
    DependencyEdge edge2 = createEdge(classB.id(), classC.id());
    DependencyEdge edge3 = createEdge(classC.id(), classA.id());

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Transitive Cycle App",
            false,
            null,
            List.of(compA, compB, compC),
            List.of(edge1, edge2, edge3),
            List.of());

    List<PackageCycle> cycles = PackageCycleDetector.detectCycles(graph);
    assertEquals(1, cycles.size());
    PackageCycle cycle = cycles.get(0);
    assertEquals(4, cycle.packages().size()); // [A, B, C, A]
    assertEquals(cycle.packages().get(0), cycle.packages().get(cycle.packages().size() - 1));
  }

  @Test
  @DisplayName("detectCycles ignores intra-package dependencies")
  void testIgnoresIntraPackageDependencies() {
    ClassNode classA1 = createClass("com.pkg.same.Class1", "com.pkg.same");
    ClassNode classA2 = createClass("com.pkg.same.Class2", "com.pkg.same");

    ComponentNode comp =
        new ComponentNode(
            "comp", "Comp", 1, null, 0.8, List.of("com.pkg.same"), List.of(classA1, classA2));

    // Class1 -> Class2 within same package
    DependencyEdge edge1 = createEdge(classA1.id(), classA2.id());
    DependencyEdge edge2 = createEdge(classA2.id(), classA1.id());

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Intra-Package App", false, null, List.of(comp), List.of(edge1, edge2), List.of());

    List<PackageCycle> cycles = PackageCycleDetector.detectCycles(graph);
    assertTrue(
        cycles.isEmpty(), "Intra-package dependencies should not be reported as package cycles");
  }

  private DependencyEdge createEdge(String from, String to) {
    return new DependencyEdge(from, to, DependencyEdge.Kind.DEPENDENCY, "uses", false);
  }

  private ClassNode createClass(String id, String packageName) {
    return new ClassNode(
        id,
        id.substring(id.lastIndexOf('.') + 1),
        packageName,
        "/path/" + id.replace('.', '/') + ".java",
        ClassNode.Stereotype.CLASS,
        false,
        1,
        null,
        0.8,
        5,
        1,
        0,
        0,
        List.of(),
        List.of());
  }
}
