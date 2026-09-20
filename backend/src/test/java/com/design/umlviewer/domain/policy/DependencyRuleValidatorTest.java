package com.design.umlviewer.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.DependencyEdge;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyRuleValidatorTest {

  @Test
  void testCleanArchitectureViolation() {
    // levels: 0 -> domain, 1 -> application, 2 -> adapters
    List<List<String>> levels =
        List.of(List.of("domain"), List.of("application"), List.of("adapters"));
    DependencyRuleValidator validator = DependencyRuleValidator.fromLevels(levels);

    // adapters -> domain (outer -> inner: legal)
    DependencyEdge legalEdge =
        new DependencyEdge(
            "com.design.adapters.OrderController",
            "com.design.domain.Order",
            DependencyEdge.Kind.DEPENDENCY,
            null,
            false);
    assertFalse(validator.evaluate(legalEdge).isViolating(), "Outer depending on inner is legal");

    // domain -> adapters (inner -> outer: ILLEGAL / VIOLATING)
    DependencyEdge violatingEdge =
        new DependencyEdge(
            "com.design.domain.Order",
            "com.design.adapters.OrderRepositoryImpl",
            DependencyEdge.Kind.DEPENDENCY,
            null,
            false);
    assertTrue(
        validator.evaluate(violatingEdge).isViolating(),
        "Inner depending on outer is a Clean Architecture violation");

    // implements is polymorphic and never violating
    DependencyEdge implEdge =
        new DependencyEdge(
            "com.design.adapters.OrderRepositoryImpl",
            "com.design.domain.OrderRepository",
            DependencyEdge.Kind.IMPLEMENTS,
            null,
            false);
    assertFalse(
        validator.evaluate(implEdge).isViolating(), "Interface implementation is not violating");
  }
}
