package com.design.umlviewer.domain.dossier;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitecturalDossierGeneratorTest {

  @Test
  @DisplayName("Should generate conforming report when architecture has zero violations")
  void testConformingReportGeneration() {
    List<List<String>> levels =
        List.of(
            List.of("domain"),
            List.of("application"),
            List.of("adapter"),
            List.of("infrastructure"));
    ArchitecturePolicy policy =
        new ArchitecturePolicy(
            "Sample System",
            "src/main/java",
            "com.example",
            true,
            List.of("domain", "application", "adapter", "infrastructure"),
            levels,
            List.of(),
            List.of(),
            List.of());

    ClassNode domainClass =
        new ClassNode(
            "com.example.domain.Order",
            "Order",
            "com.example.domain",
            "src/main/java/com/example/domain/Order.java",
            ClassNode.Stereotype.CLASS,
            false,
            0,
            null,
            100.0,
            2,
            5,
            0,
            0,
            List.of(),
            List.of());
    ClassNode appClass =
        new ClassNode(
            "com.example.application.OrderService",
            "OrderService",
            "com.example.application",
            "src/main/java/com/example/application/OrderService.java",
            ClassNode.Stereotype.CLASS,
            false,
            1,
            null,
            95.0,
            3,
            10,
            0,
            0,
            List.of(),
            List.of());

    ComponentNode domainComp =
        new ComponentNode(
            "com.example.domain", "domain", 0, null, 100.0, List.of(), List.of(domainClass));
    ComponentNode appComp =
        new ComponentNode(
            "com.example.application", "application", 1, null, 95.0, List.of(), List.of(appClass));

    // Legal inward edge: Application (1) -> Domain (0)
    DependencyEdge legalEdge =
        new DependencyEdge(
            "com.example.application.OrderService",
            "com.example.domain.Order",
            DependencyEdge.Kind.DEPENDENCY,
            "uses",
            false);

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Sample System",
            false,
            null,
            List.of(domainComp, appComp),
            List.of(legalEdge),
            List.of());

    ArchitecturalDossierGenerator generator = new ArchitecturalDossierGenerator();
    String markdown = generator.generate(graph, policy);

    assertNotNull(markdown);
    assertTrue(markdown.contains("# Clean Architecture Optimization Dossier — Sample System"));
    assertTrue(markdown.contains("Clean Architecture Status**: ✅ **Conforming"));
    assertTrue(markdown.contains("Zero outward dependency rule violations detected"));
    assertTrue(markdown.contains("Domain Core"));
    assertTrue(markdown.contains("Actionable LLM Refactoring Instructions"));
  }

  @Test
  @DisplayName("Should detect violations and prescribe concrete DIP refactoring instructions")
  void testViolationsPrescriptions() {
    List<List<String>> levels =
        List.of(
            List.of("domain"),
            List.of("application"),
            List.of("adapter"),
            List.of("infrastructure"));
    ArchitecturePolicy policy =
        new ArchitecturePolicy(
            "Violating App",
            "src/main/java",
            "com.example",
            true,
            List.of("domain", "application", "adapter", "infrastructure"),
            levels,
            List.of(),
            List.of(),
            List.of());

    ClassNode domainClass =
        new ClassNode(
            "com.example.domain.User",
            "User",
            "com.example.domain",
            "src/main/java/com/example/domain/User.java",
            ClassNode.Stereotype.CLASS,
            false,
            0,
            null,
            80.0,
            4,
            8,
            2,
            1,
            List.of(),
            List.of());
    ClassNode infraClass =
        new ClassNode(
            "com.example.infrastructure.DatabaseClient",
            "DatabaseClient",
            "com.example.infrastructure",
            "src/main/java/com/example/infrastructure/DatabaseClient.java",
            ClassNode.Stereotype.CLASS,
            false,
            3,
            null,
            50.0,
            10,
            15,
            5,
            3,
            List.of(),
            List.of());

    ComponentNode domainComp =
        new ComponentNode(
            "com.example.domain", "domain", 0, null, 80.0, List.of(), List.of(domainClass));
    ComponentNode infraComp =
        new ComponentNode(
            "com.example.infrastructure",
            "infrastructure",
            3,
            null,
            50.0,
            List.of(),
            List.of(infraClass));

    // Illegal outward edge: Domain (0) -> Infrastructure (3)
    DependencyEdge violatingEdge =
        new DependencyEdge(
            "com.example.domain.User",
            "com.example.infrastructure.DatabaseClient",
            DependencyEdge.Kind.DEPENDENCY,
            "calls",
            true);

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Violating App",
            false,
            null,
            List.of(domainComp, infraComp),
            List.of(violatingEdge),
            List.of());

    ArchitecturalDossierGenerator generator = new ArchitecturalDossierGenerator();
    String markdown = generator.generate(graph, policy);

    assertNotNull(markdown);
    assertTrue(markdown.contains("1 Dependency Rule Violation Detected"));
    assertTrue(
        markdown.contains(
            "`com.example.domain.User` ➔ `com.example.infrastructure.DatabaseClient`"));
    assertTrue(markdown.contains("Dependency Inversion Principle (DIP)"));
    assertTrue(markdown.contains("Define an interface port"));
    assertTrue(markdown.contains("Invert outward dependencies by creating interface ports"));
  }

  @Test
  @DisplayName("Should handle null graph gracefully")
  void testNullGraphHandling() {
    ArchitecturalDossierGenerator generator = new ArchitecturalDossierGenerator();
    String markdown = generator.generate(null, null);
    assertNotNull(markdown);
    assertTrue(markdown.contains("No architecture graph available"));
  }

  @Test
  @DisplayName("Should handle null policy gracefully with default tiers")
  void testNullPolicyHandling() {
    ArchitecturalDossierGenerator generator = new ArchitecturalDossierGenerator();
    ArchitectureGraph graph =
        new ArchitectureGraph("Null Policy System", false, null, List.of(), List.of(), List.of());
    String markdown = generator.generate(graph, null);
    assertNotNull(markdown);
    assertTrue(markdown.contains("# Clean Architecture Optimization Dossier — Null Policy System"));
    assertTrue(markdown.contains("Domain Core (L0)"));
  }
}
