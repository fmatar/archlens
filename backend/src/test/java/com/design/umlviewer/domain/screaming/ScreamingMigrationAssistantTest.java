package com.design.umlviewer.domain.screaming;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.graph.ClassNode;
import com.design.umlviewer.domain.graph.ComponentNode;
import com.design.umlviewer.domain.graph.CrapScore;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreamingMigrationAssistantTest {

  private ClassNode createClass(String id, String name, String pkg) {
    return new ClassNode(
        id,
        name,
        pkg,
        "src/" + pkg.replace('.', '/') + "/" + name + ".java",
        ClassNode.Stereotype.CLASS,
        false,
        1,
        new CrapScore(0, 0, 0),
        100.0,
        1,
        1,
        0,
        0,
        Collections.emptyList(),
        Collections.emptyList());
  }

  @Test
  void shouldProposeMigrationForLayeredArchitecture() {
    ComponentNode controllers =
        new ComponentNode(
            "controllers",
            "controllers",
            2,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(
                createClass("c1", "OrderController", "com.app.controllers"),
                createClass("c2", "InvoiceController", "com.app.controllers")));

    ComponentNode services =
        new ComponentNode(
            "services",
            "services",
            1,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(
                createClass("s1", "OrderService", "com.app.services"),
                createClass("s2", "InvoiceService", "com.app.services")));

    ComponentNode repositories =
        new ComponentNode(
            "repositories",
            "repositories",
            2,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(
                createClass("r1", "OrderRepository", "com.app.repositories"),
                createClass("r2", "InvoiceRepository", "com.app.repositories")));

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Layered App",
            false,
            null,
            List.of(controllers, services, repositories),
            Collections.emptyList(),
            Collections.emptyList());

    ScreamingMigrationAssistant assistant = new ScreamingMigrationAssistant();
    ScreamingMigrationProposal proposal = assistant.generateProposal(graph);

    assertNotNull(proposal);
    assertEquals(0.0, proposal.currentScore(), 0.01);
    assertEquals("PACKAGE_BY_LAYER", proposal.currentClassification());
    assertEquals(1.0, proposal.projectedScore(), 0.01);
    assertEquals("PACKAGE_BY_FEATURE", proposal.projectedClassification());

    assertEquals(2, proposal.clusters().size());

    FeatureCluster orderCluster =
        proposal.clusters().stream()
            .filter(c -> c.featureName().equalsIgnoreCase("Order"))
            .findFirst()
            .orElseThrow();
    assertEquals("com.app.order", orderCluster.proposedPackageName());
    assertEquals(3, orderCluster.classCount());
    assertTrue(orderCluster.classNames().contains("OrderController"));
    assertTrue(orderCluster.classNames().contains("OrderService"));
    assertTrue(orderCluster.classNames().contains("OrderRepository"));

    FeatureCluster invoiceCluster =
        proposal.clusters().stream()
            .filter(c -> c.featureName().equalsIgnoreCase("Invoice"))
            .findFirst()
            .orElseThrow();
    assertEquals("com.app.invoice", invoiceCluster.proposedPackageName());
    assertEquals(3, invoiceCluster.classCount());

    assertEquals(6, proposal.stagedClassMoves().size());
    assertEquals("com.app.order", proposal.stagedClassMoves().get("c1"));
    assertEquals("com.app.order", proposal.stagedClassMoves().get("s1"));
    assertEquals("com.app.order", proposal.stagedClassMoves().get("r1"));
    assertEquals("com.app.invoice", proposal.stagedClassMoves().get("c2"));
    assertEquals("com.app.invoice", proposal.stagedClassMoves().get("s2"));
    assertEquals("com.app.invoice", proposal.stagedClassMoves().get("r2"));

    assertTrue(proposal.unclusteredClasses().isEmpty());
  }

  @Test
  void shouldLeaveUnclusteredUtilityClasses() {
    ComponentNode services =
        new ComponentNode(
            "services",
            "services",
            1,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(
                createClass("s1", "OrderService", "com.app.services"),
                createClass("u1", "GlobalDateHelper", "com.app.services")));

    ComponentNode controllers =
        new ComponentNode(
            "controllers",
            "controllers",
            2,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(createClass("c1", "OrderController", "com.app.controllers")));

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "App with Utils",
            false,
            null,
            List.of(services, controllers),
            Collections.emptyList(),
            Collections.emptyList());

    ScreamingMigrationAssistant assistant = new ScreamingMigrationAssistant();
    ScreamingMigrationProposal proposal = assistant.generateProposal(graph);

    assertNotNull(proposal);
    assertEquals(1, proposal.clusters().size());
    assertEquals("Order", proposal.clusters().get(0).featureName());

    assertEquals(1, proposal.unclusteredClasses().size());
    assertTrue(proposal.unclusteredClasses().contains("GlobalDateHelper"));
  }

  @Test
  void shouldHandleAlreadyFeatureBasedArchitecture() {
    ComponentNode orders =
        new ComponentNode(
            "order",
            "order",
            0,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(createClass("o1", "Order", "com.app.order")));

    ComponentNode billing =
        new ComponentNode(
            "billing",
            "billing",
            0,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(createClass("b1", "Invoice", "com.app.billing")));

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Feature App",
            false,
            null,
            List.of(orders, billing),
            Collections.emptyList(),
            Collections.emptyList());

    ScreamingMigrationAssistant assistant = new ScreamingMigrationAssistant();
    ScreamingMigrationProposal proposal = assistant.generateProposal(graph);

    assertNotNull(proposal);
    assertEquals(1.0, proposal.currentScore(), 0.01);
    assertEquals("PACKAGE_BY_FEATURE", proposal.currentClassification());
    assertTrue(proposal.clusters().isEmpty());
    assertTrue(proposal.stagedClassMoves().isEmpty());
  }
}
