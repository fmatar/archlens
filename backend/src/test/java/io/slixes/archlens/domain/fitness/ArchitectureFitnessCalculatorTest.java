package io.slixes.archlens.domain.fitness;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.*;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureFitnessCalculatorTest {

  private ArchitectureFitnessCalculator calculator;
  private FitnessThresholds defaultThresholds;

  private ClassNode createClass(
      String id, String name, String pkg, ClassNode.Stereotype stereotype, int level) {
    return new ClassNode(
        id,
        name,
        pkg,
        "src/" + pkg.replace('.', '/') + "/" + name + ".java",
        stereotype,
        false,
        level,
        new CrapScore(0, 0, 0),
        100.0,
        1,
        1,
        0,
        0,
        Collections.emptyList(),
        Collections.emptyList());
  }

  @BeforeEach
  void setUp() {
    calculator = new ArchitectureFitnessCalculator();
    defaultThresholds = FitnessThresholds.defaultThresholds();
  }

  @Test
  @DisplayName("Should evaluate perfect fitness (Grade A) when all architectural invariants pass")
  void shouldCalculatePerfectFitnessWhenAllInvariantsPass() {
    ClassNode c1 =
        createClass(
            "com.design.model.OrderRepository",
            "OrderRepository",
            "com.design.model",
            ClassNode.Stereotype.INTERFACE,
            0);
    ClassNode c2 =
        createClass(
            "com.design.service.OrderService",
            "OrderService",
            "com.design.service",
            ClassNode.Stereotype.CLASS,
            1);

    ComponentNode comp0 =
        new ComponentNode("domain", "Domain", 0, null, null, List.of(), List.of(c1));
    ComponentNode comp1 =
        new ComponentNode("service", "Service", 1, null, null, List.of(), List.of(c2));

    // Valid inward dependency: Service (Level 1) -> Model (Level 0)
    DependencyEdge validEdge =
        new DependencyEdge(c2.id(), c1.id(), DependencyEdge.Kind.DEPENDENCY, "uses", false);

    ScreamingMetric screamingMetric =
        new ScreamingMetric(
            0.85,
            8,
            1,
            9,
            "PACKAGE_BY_FEATURE",
            List.of("util"),
            List.of("order", "billing"),
            List.of());

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Test Graph",
            false,
            null,
            List.of(comp0, comp1),
            List.of(validEdge),
            List.of(),
            List.of(),
            screamingMetric);

    FitnessEvaluation eval = calculator.evaluate(graph, defaultThresholds);

    assertNotNull(eval);
    assertTrue(eval.overallPassed(), "Evaluation should pass all rules");
    assertEquals("A", eval.grade(), "Grade should be A");
    assertTrue(eval.fitnessScore() >= 0.85, "Fitness score should be high");
    assertEquals(4, eval.totalRuleCount());
    assertEquals(4, eval.passedRuleCount());

    FitnessRule concentricRule =
        eval.rules().stream()
            .filter(r -> r.id().equals("CONCENTRIC_DEPENDENCY_RULE"))
            .findFirst()
            .orElseThrow();
    assertTrue(concentricRule.passed());
    assertEquals(0, (int) concentricRule.actualValue());

    FitnessRule adpRule =
        eval.rules().stream()
            .filter(r -> r.id().equals("ACYCLIC_DEPENDENCIES_RULE"))
            .findFirst()
            .orElseThrow();
    assertTrue(adpRule.passed());
    assertEquals(0, (int) adpRule.actualValue());

    FitnessRule screamingRule =
        eval.rules().stream()
            .filter(r -> r.id().equals("SCREAMING_ARCHITECTURE_RULE"))
            .findFirst()
            .orElseThrow();
    assertTrue(screamingRule.passed());
    assertEquals(0.85, screamingRule.actualValue(), 0.001);
  }

  @Test
  @DisplayName("Should fail concentric rule when outward dependency violations are present")
  void shouldFailConcentricRuleWhenOutwardViolationsExist() {
    ClassNode c1 =
        createClass(
            "com.design.model.Order", "Order", "com.design.model", ClassNode.Stereotype.CLASS, 0);
    ClassNode c2 =
        createClass(
            "com.design.resource.OrderResource",
            "OrderResource",
            "com.design.resource",
            ClassNode.Stereotype.CLASS,
            3);

    ComponentNode comp0 =
        new ComponentNode("domain", "Domain", 0, null, null, List.of(), List.of(c1));
    ComponentNode comp3 =
        new ComponentNode("resource", "Resource", 3, null, null, List.of(), List.of(c2));

    // Illegal outward dependency: Model (Level 0) -> Resource (Level 3)
    DependencyEdge violatingEdge =
        new DependencyEdge(c1.id(), c2.id(), DependencyEdge.Kind.DEPENDENCY, "leaks", true);

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Violating Graph",
            false,
            null,
            List.of(comp0, comp3),
            List.of(violatingEdge),
            List.of(),
            List.of(),
            ScreamingMetric.empty());

    FitnessEvaluation eval = calculator.evaluate(graph, defaultThresholds);

    assertNotNull(eval);
    assertFalse(eval.overallPassed(), "Should fail due to outward violation");
    FitnessRule rule =
        eval.rules().stream()
            .filter(r -> r.id().equals("CONCENTRIC_DEPENDENCY_RULE"))
            .findFirst()
            .orElseThrow();
    assertFalse(rule.passed());
    assertEquals(1.0, rule.actualValue());
    assertTrue(rule.failureMessage().contains("1 outward violation"));
  }

  @Test
  @DisplayName("Should fail acyclic dependencies rule when package cycles exist")
  void shouldFailAcyclicDependenciesRuleWhenCyclesDetected() {
    PackageCycle cycle = new PackageCycle(List.of("service", "dao", "service"));

    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Cycle Graph",
            false,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(cycle),
            ScreamingMetric.empty());

    FitnessEvaluation eval = calculator.evaluate(graph, defaultThresholds);

    assertNotNull(eval);
    assertFalse(eval.overallPassed(), "Should fail due to cycle");
    FitnessRule rule =
        eval.rules().stream()
            .filter(r -> r.id().equals("ACYCLIC_DEPENDENCIES_RULE"))
            .findFirst()
            .orElseThrow();
    assertFalse(rule.passed());
    assertEquals(1.0, rule.actualValue());
    assertTrue(rule.failureMessage().contains("1 package cycle"));
  }

  @Test
  @DisplayName("Should compute historical regression trend correctly")
  void shouldComputeHistoricalRegressionTrend() {
    HistoricalFitnessSnapshot s1 =
        new HistoricalFitnessSnapshot(
            "v1", "v0.0.1-Alpha-12", "2026-09-20", 0.65, "D", 2, 1, 0.40, 0.45);
    HistoricalFitnessSnapshot s2 =
        new HistoricalFitnessSnapshot(
            "v2", "v0.0.1-Alpha-13", "2026-09-24", 0.80, "B", 1, 0, 0.65, 0.28);
    HistoricalFitnessSnapshot s3 =
        new HistoricalFitnessSnapshot(
            "v3", "v0.0.1-Alpha-14", "2026-09-28", 0.94, "A", 0, 0, 0.85, 0.12);

    FitnessHistoryTrend trendImproving = calculator.calculateTrend(List.of(s1, s2, s3));
    assertEquals("IMPROVING", trendImproving.trendDirection());
    assertTrue(trendImproving.scoreDelta() > 0.20);

    FitnessHistoryTrend trendDegrading = calculator.calculateTrend(List.of(s3, s2, s1));
    assertEquals("DEGRADING", trendDegrading.trendDirection());
    assertTrue(trendDegrading.scoreDelta() < -0.20);

    FitnessHistoryTrend trendStable = calculator.calculateTrend(List.of(s2, s2));
    assertEquals("STABLE", trendStable.trendDirection());
    assertEquals(0.0, trendStable.scoreDelta(), 0.001);
  }

  @Test
  @DisplayName("Should handle empty or single history snapshot gracefully")
  void shouldHandleEmptyOrSingleHistoryTrend() {
    FitnessHistoryTrend emptyTrend = calculator.calculateTrend(List.of());
    assertEquals("STABLE", emptyTrend.trendDirection());
    assertEquals(0.0, emptyTrend.scoreDelta(), 0.001);

    HistoricalFitnessSnapshot s1 =
        new HistoricalFitnessSnapshot(
            "v1", "v0.0.1-Alpha-12", "2026-09-20", 0.65, "D", 2, 1, 0.40, 0.45);
    FitnessHistoryTrend singleTrend = calculator.calculateTrend(List.of(s1));
    assertEquals("STABLE", singleTrend.trendDirection());
    assertEquals(0.0, singleTrend.scoreDelta(), 0.001);
  }

  @Test
  @DisplayName("Should fallback to default thresholds when null is provided")
  void shouldFallbackToDefaultThresholdsWhenNullProvided() {
    ArchitectureGraph emptyGraph =
        new ArchitectureGraph("Empty", false, null, List.of(), List.of(), List.of());
    FitnessEvaluation eval = calculator.evaluate(emptyGraph, null);
    assertNotNull(eval);
    assertNotNull(eval.grade());
    assertEquals(4, eval.totalRuleCount());
  }

  @Test
  @DisplayName("Should fail screaming rule when score is below minimum threshold")
  void shouldFailScreamingRuleWhenBelowThreshold() {
    ArchitectureGraph graph =
        new ArchitectureGraph(
            "Layered",
            false,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new ScreamingMetric(
                0.20, 2, 8, 10, "PACKAGE_BY_LAYER", List.of(), List.of(), List.of()));
    FitnessEvaluation eval = calculator.evaluate(graph, defaultThresholds);
    assertFalse(eval.overallPassed());
    FitnessRule rule =
        eval.rules().stream()
            .filter(r -> r.id().equals("SCREAMING_ARCHITECTURE_RULE"))
            .findFirst()
            .orElseThrow();
    assertFalse(rule.passed());
    assertEquals(0.20, rule.actualValue(), 0.001);
  }
}
