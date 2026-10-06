package com.design.umlviewer.domain.screaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.design.umlviewer.domain.graph.ClassNode;
import com.design.umlviewer.domain.graph.ClassNode.Stereotype;
import com.design.umlviewer.domain.graph.ComponentNode;
import com.design.umlviewer.domain.graph.CrapScore;
import com.design.umlviewer.domain.graph.DependencyEdge;
import com.design.umlviewer.domain.graph.DependencyEdge.Kind;
import com.design.umlviewer.domain.graph.ScreamingMetric;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScreamingArchitectureAnalyzerTest {

  private ScreamingArchitectureAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer = new ScreamingArchitectureAnalyzer();
  }

  @Test
  void testEmptyComponentsReturnsDefaultMetric() {
    ScreamingMetric metric = analyzer.analyze(List.of(), List.of());
    assertEquals(1.0, metric.score());
    assertEquals(0, metric.totalPackageCount());
    assertEquals("PACKAGE_BY_FEATURE", metric.classification());
  }

  @Test
  void testPureDomainFeaturesScoresOne() {
    ComponentNode orderComp = createComponent("com.app.order", "order", 0);
    ComponentNode billingComp = createComponent("com.app.billing", "billing", 0);
    ComponentNode catalogComp = createComponent("com.app.catalog", "catalog", 0);

    ScreamingMetric metric =
        analyzer.analyze(List.of(orderComp, billingComp, catalogComp), List.of());

    assertEquals(1.0, metric.score());
    assertEquals(3, metric.domainPackageCount());
    assertEquals(0, metric.technicalPackageCount());
    assertEquals(3, metric.totalPackageCount());
    assertEquals("PACKAGE_BY_FEATURE", metric.classification());
    assertTrue(metric.domainPackages().contains("order"));
    assertTrue(metric.domainPackages().contains("billing"));
  }

  @Test
  void testPureHorizontalLayersScoresZero() {
    ComponentNode controllers = createComponent("com.app.controllers", "controllers", 2);
    ComponentNode services = createComponent("com.app.services", "services", 1);
    ComponentNode repositories = createComponent("com.app.repositories", "repositories", 2);
    ComponentNode dtos = createComponent("com.app.dtos", "dtos", 1);

    ScreamingMetric metric =
        analyzer.analyze(List.of(controllers, services, repositories, dtos), List.of());

    assertEquals(0.0, metric.score());
    assertEquals(0, metric.domainPackageCount());
    assertEquals(4, metric.technicalPackageCount());
    assertEquals(4, metric.totalPackageCount());
    assertEquals("PACKAGE_BY_LAYER", metric.classification());
    assertTrue(metric.technicalPackages().contains("controllers"));
    assertTrue(metric.technicalPackages().contains("services"));
  }

  @Test
  void testMixedArchitectureClassifiedAsHybrid() {
    ComponentNode order = createComponent("com.app.order", "order", 0);
    ComponentNode billing = createComponent("com.app.billing", "billing", 0);
    ComponentNode common = createComponent("com.app.common", "common", 1);
    ComponentNode utils = createComponent("com.app.utils", "utils", 1);

    ScreamingMetric metric = analyzer.analyze(List.of(order, billing, common, utils), List.of());

    assertEquals(0.5, metric.score(), 0.01);
    assertEquals(2, metric.domainPackageCount());
    assertEquals(2, metric.technicalPackageCount());
    assertEquals(4, metric.totalPackageCount());
    assertEquals("HYBRID", metric.classification());
  }

  @Test
  void testFrameworkGravityDetectsCoreFrameworkLeaking() {
    // Domain core entity (Level 0) having a dependency edge targeting a framework type
    ClassNode domainEntity =
        new ClassNode(
            "com.app.order.OrderEntity",
            "OrderEntity",
            "com.app.order",
            "/src/OrderEntity.java",
            Stereotype.CLASS,
            false,
            0,
            new CrapScore(1.0, 1.0, 0.0),
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());

    ComponentNode order =
        new ComponentNode(
            "order",
            "order",
            0,
            new CrapScore(1.0, 1.0, 0.0),
            1.0,
            List.of("com.app.order"),
            List.of(domainEntity));

    // Dependency from domain entity to jakarta/spring framework
    DependencyEdge frameworkEdge =
        new DependencyEdge(
            "com.app.order.OrderEntity",
            "jakarta.persistence.Entity",
            Kind.DEPENDENCY,
            null,
            false);

    ScreamingMetric metric = analyzer.analyze(List.of(order), List.of(frameworkEdge));

    // Base score is 1.0 (1 domain pkg), but penalized by framework gravity leak
    assertFalse(metric.frameworkGravityHotspots().isEmpty());
    assertTrue(metric.frameworkGravityHotspots().contains("order"));
    assertTrue(metric.score() < 1.0);
  }

  private ComponentNode createComponent(String fullPkg, String label, int level) {
    ClassNode cls =
        new ClassNode(
            fullPkg + ".SampleClass",
            "SampleClass",
            fullPkg,
            "/src/SampleClass.java",
            Stereotype.CLASS,
            false,
            level,
            new CrapScore(1.0, 1.0, 0.0),
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());

    return new ComponentNode(
        label, label, level, new CrapScore(1.0, 1.0, 0.0), 1.0, List.of(fullPkg), List.of(cls));
  }
}
