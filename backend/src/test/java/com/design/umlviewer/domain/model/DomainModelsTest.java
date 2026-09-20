package com.design.umlviewer.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DomainModelsTest {

  @Test
  void testCrapScore() {
    CrapScore zero = CrapScore.zero();
    assertEquals(0.0, zero.mu());
    assertEquals(0.0, zero.max());
    assertEquals(0.0, zero.sigma());

    CrapScore score = new CrapScore(4.5, 12.0, 1.2);
    assertEquals(4.5, score.mu());
    assertEquals(12.0, score.max());
    assertEquals(1.2, score.sigma());
  }

  @Test
  void testMethodNode() {
    MethodNode method =
        new MethodNode(
            "calculate", List.of("int x", "int y"), "int", false, 3, 0.9, 4.2, 5, 1, 0, 42);
    assertEquals("calculate", method.name());
    assertEquals(List.of("int x", "int y"), method.args());
    assertEquals("int", method.returnType());
    assertFalse(method.isPrivate());
    assertEquals(3, method.cc());
    assertEquals(0.9, method.coverage());
    assertEquals(4.2, method.crap());
    assertEquals(5, method.killed());
    assertEquals(1, method.survived());
    assertEquals(0, method.uncovered());
    assertEquals(42, method.line());
  }

  @Test
  void testFieldNode() {
    FieldNode field = new FieldNode("id", "String", true);
    assertEquals("id", field.name());
    assertEquals("String", field.type());
    assertTrue(field.isPrivate());
  }

  @Test
  void testClassNode() {
    FieldNode field = new FieldNode("count", "int", true);
    MethodNode method =
        new MethodNode("getCount", List.of(), "int", false, 1, 1.0, 1.0, 1, 0, 0, 10);
    ClassNode classNode =
        new ClassNode(
            "com.test.MyClass",
            "MyClass",
            "com.test",
            "/path/MyClass.java",
            ClassNode.Stereotype.CLASS,
            false,
            0,
            CrapScore.zero(),
            1.0,
            1,
            1,
            0,
            0,
            List.of(field),
            List.of(method));

    assertEquals("com.test.MyClass", classNode.id());
    assertEquals("MyClass", classNode.name());
    assertEquals("com.test", classNode.packageName());
    assertEquals("/path/MyClass.java", classNode.filePath());
    assertEquals(ClassNode.Stereotype.CLASS, classNode.stereotype());
    assertFalse(classNode.isForeign());
    assertEquals(0, classNode.level());
    assertEquals(1, classNode.fields().size());
    assertEquals(1, classNode.methods().size());
    assertEquals(ClassNode.Stereotype.RECORD, ClassNode.Stereotype.valueOf("RECORD"));
    assertEquals(ClassNode.Stereotype.ENUM, ClassNode.Stereotype.valueOf("ENUM"));
    assertEquals(ClassNode.Stereotype.ABSTRACT, ClassNode.Stereotype.valueOf("ABSTRACT"));
    assertEquals(ClassNode.Stereotype.INTERFACE, ClassNode.Stereotype.valueOf("INTERFACE"));
  }

  @Test
  void testComponentNode() {
    ComponentNode component =
        new ComponentNode(
            "domain",
            "Domain Layer",
            0,
            CrapScore.zero(),
            0.95,
            List.of("com.test.domain"),
            List.of());
    assertEquals("domain", component.id());
    assertEquals("Domain Layer", component.label());
    assertEquals(0, component.level());
    assertEquals(0.95, component.mutationScore());
    assertEquals(1, component.childPackageIds().size());
    assertTrue(component.classes().isEmpty());
  }

  @Test
  void testDependencyEdge() {
    DependencyEdge edge =
        new DependencyEdge("A", "B", DependencyEdge.Kind.DEPENDENCY, "uses", false);
    assertEquals("A", edge.from());
    assertEquals("B", edge.to());
    assertEquals(DependencyEdge.Kind.DEPENDENCY, edge.kind());
    assertEquals("uses", edge.label());
    assertFalse(edge.isViolating());

    DependencyEdge violating = edge.withViolating(true);
    assertTrue(violating.isViolating());
    assertEquals(DependencyEdge.Kind.INHERITANCE, DependencyEdge.Kind.valueOf("INHERITANCE"));
    assertEquals(DependencyEdge.Kind.IMPLEMENTS, DependencyEdge.Kind.valueOf("IMPLEMENTS"));
    assertEquals(DependencyEdge.Kind.ASSOCIATION, DependencyEdge.Kind.valueOf("ASSOCIATION"));
    assertEquals(DependencyEdge.Kind.AGGREGATION, DependencyEdge.Kind.valueOf("AGGREGATION"));
    assertEquals(DependencyEdge.Kind.COMPOSITION, DependencyEdge.Kind.valueOf("COMPOSITION"));
  }

  @Test
  void testArchitectureGraph() {
    ArchitectureGraph graph =
        new ArchitectureGraph("Test Architecture", false, null, List.of(), List.of(), List.of());
    assertEquals("Test Architecture", graph.title());
    assertFalse(graph.isProposal());
    assertNull(graph.activeProposalId());
    assertTrue(graph.components().isEmpty());
    assertTrue(graph.edges().isEmpty());
    assertTrue(graph.unassigned().isEmpty());
  }
}
