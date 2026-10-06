package com.design.umlviewer.domain.dossier;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.graph.ClassNode;
import com.design.umlviewer.domain.graph.ComponentNode;
import com.design.umlviewer.domain.graph.DependencyEdge;
import com.design.umlviewer.domain.graph.MethodNode;
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
  @DisplayName("Should synthesize a complete DIP inversion plan with exact assertions")
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

    // Deterministic exact assertions (Craftsmanship standard)
    assertEquals("PostgresOrderRepositoryPort", plan.portName());
    assertEquals("com.example.application.ports", plan.portPackage());
    assertEquals(
        "src/main/java/com/example/application/ports/PostgresOrderRepositoryPort.java",
        plan.portFilePath());

    // Interface code content
    assertTrue(plan.portInterfaceCode().contains("public interface PostgresOrderRepositoryPort {"));
    assertTrue(plan.portInterfaceCode().contains("void save(Order arg0);"));
    assertTrue(plan.portInterfaceCode().contains("Order findById(String arg0);"));

    // Adapter refactor preview
    assertTrue(
        plan.adapterRefactorPreview()
            .contains(
                "public class PostgresOrderRepository implements PostgresOrderRepositoryPort"));

    // Caller refactor preview
    assertTrue(
        plan.callerRefactorPreview()
            .contains("private final PostgresOrderRepositoryPort postgresOrderRepositoryPort;"));
    assertTrue(
        plan.callerRefactorPreview()
            .contains(
                "public OrderService(PostgresOrderRepositoryPort postgresOrderRepositoryPort) {"));

    // Surgical prompt
    assertTrue(plan.surgicalPrompt().contains("Dependency Inversion Principle"));
    assertTrue(plan.surgicalPrompt().contains("PostgresOrderRepositoryPort"));
    assertTrue(plan.surgicalPrompt().contains("com.example.application.ports"));
  }

  @Test
  @DisplayName("Should derive port names according to Impl and Port suffixes")
  void shouldDerivePortNamesAccordingToSuffixes() {
    // 1. Target ends with Impl -> strips Impl and appends Port
    DipInversionPlan implPlan =
        synthesizer.synthesize(
            graph,
            policy,
            "com.example.application.OrderService",
            "com.example.adapters.OrderDaoImpl");
    assertEquals("OrderDaoPort", implPlan.portName());

    // 2. Target ends with Port -> preserves existing name
    DipInversionPlan portPlan =
        synthesizer.synthesize(
            graph,
            policy,
            "com.example.application.OrderService",
            "com.example.adapters.PaymentPort");
    assertEquals("PaymentPort", portPlan.portName());

    // 3. Regular concretion -> appends Port
    DipInversionPlan clientPlan =
        synthesizer.synthesize(
            graph,
            policy,
            "com.example.application.OrderService",
            "com.example.adapters.StripeGateway");
    assertEquals("StripeGatewayPort", clientPlan.portName());
  }

  @Test
  @DisplayName("Should derive port package when caller is already in a port or ports subpackage")
  void shouldDerivePortPackageWhenAlreadyInPortSubpackage() {
    // 1. Caller in .port subpackage -> does not append duplicate .ports
    DipInversionPlan portSubPlan =
        synthesizer.synthesize(
            graph, policy, "com.example.domain.port.OrderService", "com.example.adapters.OrderDao");
    assertEquals("com.example.domain.port", portSubPlan.portPackage());

    // 2. Caller in .ports subpackage -> does not append duplicate .ports
    DipInversionPlan portsSubPlan =
        synthesizer.synthesize(
            graph,
            policy,
            "com.example.domain.ports.OrderService",
            "com.example.adapters.OrderDao");
    assertEquals("com.example.domain.ports", portsSubPlan.portPackage());

    // 3. Caller in default package without package declaration -> falls back to "ports"
    DipInversionPlan defaultPkgPlan =
        synthesizer.synthesize(
            null, policy, "StandaloneOrderService", "com.example.adapters.OrderDao");
    assertEquals("ports", defaultPkgPlan.portPackage());
  }

  @Test
  @DisplayName(
      "Should derive file path when filePath does not contain /java/ or when node is missing")
  void shouldDeriveFilePathForAlternativeDirectoryLayouts() {
    // Caller node with non-standard source path without /java/
    ClassNode nonStandardCaller =
        new ClassNode(
            "com.example.app.BillingService",
            "BillingService",
            "com.example.app",
            "backend/src/com/example/app/BillingService.java",
            ClassNode.Stereotype.CLASS,
            false,
            1,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());

    ComponentNode comp =
        new ComponentNode(
            "com.example.app", "app", 1, null, 1.0, List.of(), List.of(nonStandardCaller));
    ArchitectureGraph altGraph =
        new ArchitectureGraph("Alt", false, null, List.of(comp), List.of(), List.of());

    DipInversionPlan plan =
        synthesizer.synthesize(
            altGraph,
            policy,
            "com.example.app.BillingService",
            "com.example.adapters.StripeClient");
    assertEquals("backend/src/com/example/app/ports/StripeClientPort.java", plan.portFilePath());

    // Caller with no node and no filePath -> defaults to src/main/java
    DipInversionPlan fallbackPlan =
        synthesizer.synthesize(
            altGraph,
            policy,
            "com.example.app.MissingService",
            "com.example.adapters.StripeClient");
    assertEquals(
        "src/main/java/com/example/app/ports/StripeClientPort.java", fallbackPlan.portFilePath());
  }

  @Test
  @DisplayName("Should filter out private methods and handle multi-arg and void return methods")
  void shouldFilterPrivateMethodsAndHandleComplexSignatures() {
    MethodNode privateMethod =
        new MethodNode("internalHelper", List.of(), "void", true, 1, 1.0, 1.0, 1, 0, 0, 10);
    MethodNode multiArgMethod =
        new MethodNode(
            "calculateTax",
            List.of("BigDecimal", "String", "boolean"),
            "BigDecimal",
            false,
            2,
            1.0,
            1.0,
            2,
            0,
            0,
            15);
    MethodNode blankReturnMethod =
        new MethodNode("resetState", List.of(), "", false, 1, 1.0, 1.0, 1, 0, 0, 20);
    MethodNode nullArgsMethod =
        new MethodNode("ping", null, "boolean", false, 1, 1.0, 1.0, 1, 0, 0, 25);

    ClassNode complexTarget =
        new ClassNode(
            "com.example.adapters.TaxCalculatorImpl",
            "TaxCalculatorImpl",
            "com.example.adapters",
            "src/main/java/com/example/adapters/TaxCalculatorImpl.java",
            ClassNode.Stereotype.CLASS,
            false,
            2,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of(privateMethod, multiArgMethod, blankReturnMethod, nullArgsMethod));

    ComponentNode comp =
        new ComponentNode(
            "com.example.adapters", "adapters", 2, null, 1.0, List.of(), List.of(complexTarget));
    ArchitectureGraph complexGraph =
        new ArchitectureGraph("Complex", false, null, List.of(comp), List.of(), List.of());

    DipInversionPlan plan =
        synthesizer.synthesize(
            complexGraph,
            policy,
            "com.example.application.OrderService",
            "com.example.adapters.TaxCalculatorImpl");

    // Private method must NOT be in the interface
    assertFalse(plan.portInterfaceCode().contains("internalHelper"));

    // Multi-arg method formatted with comma separation
    assertTrue(
        plan.portInterfaceCode()
            .contains("BigDecimal calculateTax(BigDecimal arg0, String arg1, boolean arg2);"));

    // Blank return type defaults to void
    assertTrue(plan.portInterfaceCode().contains("void resetState();"));

    // Null args handled cleanly
    assertTrue(plan.portInterfaceCode().contains("boolean ping();"));
  }

  @Test
  @DisplayName("Should resolve level via policy pattern when node level is null")
  void shouldResolveLevelViaPolicyPatterns() {
    // 1. Level resolved via policy pattern: startsWith (e.g. "domain.Order")
    DipInversionPlan startsWithPlan =
        synthesizer.synthesize(null, policy, "domain.Order", "adapters.PostgresDao");
    assertEquals(0, startsWithPlan.fromLevel());
    assertEquals(2, startsWithPlan.toLevel());

    // 2. Level resolved via policy pattern: contains with slash (e.g.
    // "src/application/OrderService.java")
    DipInversionPlan slashPlan =
        synthesizer.synthesize(
            null, policy, "src/application/OrderService.java", "src/external/Database.java");
    assertEquals(1, slashPlan.fromLevel());
    assertNull(slashPlan.toLevel()); // external is not in policy.levels()
  }

  @Test
  @DisplayName("Should find class node from unassigned list or by package name matching")
  void shouldFindClassNodeFromUnassignedOrPackageMatching() {
    ClassNode unassignedClass =
        new ClassNode(
            "com.example.unassigned.LegacyGateway",
            "LegacyGateway",
            "com.example.unassigned",
            "src/main/java/com/example/unassigned/LegacyGateway.java",
            ClassNode.Stereotype.CLASS,
            false,
            3,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());

    ClassNode matchingByPkgAndName =
        new ClassNode(
            "alt-id-123",
            "SpecialService",
            "com.example.special",
            "src/main/java/com/example/special/SpecialService.java",
            ClassNode.Stereotype.CLASS,
            false,
            1,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());

    ComponentNode specialComp =
        new ComponentNode(
            "com.example.special",
            "special",
            1,
            null,
            1.0,
            List.of(),
            List.of(matchingByPkgAndName));

    ArchitectureGraph g =
        new ArchitectureGraph(
            "Test", false, null, List.of(specialComp), List.of(), List.of(unassignedClass));

    // Matched by packageName + "." + name
    DipInversionPlan p1 =
        synthesizer.synthesize(
            g,
            policy,
            "com.example.special.SpecialService",
            "com.example.unassigned.LegacyGateway");
    assertEquals("com.example.special.SpecialService", p1.fromClass());
    assertEquals(1, p1.fromLevel());

    // Matched from unassigned by name or id
    assertEquals("com.example.unassigned.LegacyGateway", p1.toClass());
    assertEquals(3, p1.toLevel());
  }

  @Test
  @DisplayName(
      "Should generate valid previews for classes in default package without package header")
  void shouldGeneratePreviewsForDefaultPackageClasses() {
    DipInversionPlan defaultPkgPlan =
        synthesizer.synthesize(null, null, "StandaloneCaller", "StandaloneAdapter");

    assertEquals("StandaloneCaller", defaultPkgPlan.fromClass());
    assertEquals("StandaloneAdapter", defaultPkgPlan.toClass());
    assertEquals("StandaloneAdapterPort", defaultPkgPlan.portName());
    assertEquals("ports", defaultPkgPlan.portPackage());

    // Previews should not declare blank "package ;"
    assertFalse(defaultPkgPlan.adapterRefactorPreview().contains("package ;"));
    assertFalse(defaultPkgPlan.callerRefactorPreview().contains("package ;"));
    assertTrue(
        defaultPkgPlan
            .adapterRefactorPreview()
            .contains("public class StandaloneAdapter implements StandaloneAdapterPort"));
    assertTrue(defaultPkgPlan.callerRefactorPreview().contains("public class StandaloneCaller {"));
  }

  @Test
  @DisplayName("Should handle null, blank, and empty inputs gracefully")
  void shouldHandleNullAndEmptyInputsGracefully() {
    DipInversionPlan emptyPlan = synthesizer.synthesize(null, null, null, null);
    assertNotNull(emptyPlan);
    assertEquals("", emptyPlan.fromClass());
    assertEquals("", emptyPlan.toClass());
    assertEquals("Port", emptyPlan.portName());
    assertEquals("ports", emptyPlan.portPackage());
    assertTrue(emptyPlan.targetMethods().isEmpty());
    assertTrue(emptyPlan.portInterfaceCode().contains("// TODO: Declare contract methods"));

    DipInversionPlan blankPlan = synthesizer.synthesize(null, null, "   ", "   ");
    assertNotNull(blankPlan);
  }

  @Test
  @DisplayName("Should cover remaining edge cases including null fields and name matchers")
  void shouldCoverRemainingEdgeCaseBranches() {
    // 1. Level matching via inner package dot notation: .domain.
    DipInversionPlan dotPlan =
        synthesizer.synthesize(
            null, policy, "com.example.domain.entities.Order", "com.example.adapters.OrderRepo");
    assertEquals(0, dotPlan.fromLevel());

    // 2. Caller node with null level, null/blank package name, and null/no-src filePath
    ClassNode bareCaller =
        new ClassNode(
            "BareCaller",
            "BareCaller",
            null, // null packageName
            null, // null filePath
            ClassNode.Stereotype.CLASS,
            false,
            null, // null level
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());

    // 3. Target node with null methods and null return type
    MethodNode nullReturnMethod =
        new MethodNode("doSomething", List.of("int"), null, false, 1, 1.0, 1.0, 1, 0, 0, 10);
    ClassNode targetWithNullReturn =
        new ClassNode(
            "TargetClass",
            "TargetClass",
            "com.example.adapters",
            "custom/path/TargetClass.java", // filePath without src/
            ClassNode.Stereotype.CLASS,
            false,
            2,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of(nullReturnMethod));

    ClassNode targetWithNullMethods =
        new ClassNode(
            "com.example.adapters.NoMethodsTarget",
            "NoMethodsTarget",
            "com.example.adapters",
            "src/main/java/com/example/adapters/NoMethodsTarget.java",
            ClassNode.Stereotype.CLASS,
            false,
            2,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            null); // null methods list

    // 4. Graph with null components and unassigned matching by name
    ClassNode unassignedMatchedByName =
        new ClassNode(
            "unassigned-guid-1",
            "MatchedByNameService",
            "com.example.unassigned",
            "src/main/java/com/example/unassigned/MatchedByNameService.java",
            ClassNode.Stereotype.CLASS,
            false,
            1,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());

    ArchitectureGraph sparseGraph =
        new ArchitectureGraph(
            "Sparse", false, null, null, List.of(), List.of(unassignedMatchedByName));

    DipInversionPlan pSparse =
        synthesizer.synthesize(
            sparseGraph, policy, "MatchedByNameService", "com.example.adapters.NoMethodsTarget");
    assertEquals("MatchedByNameService", pSparse.fromClass());

    // 5. Component with null classes and matching by class name without package
    ComponentNode nullClassesComp =
        new ComponentNode("null.pkg", "null-comp", 1, null, 1.0, List.of(), null);
    ClassNode classNoPackage =
        new ClassNode(
            "id-no-pkg",
            "ClassWithNoPackage",
            "", // blank packageName
            "src/custom/ClassWithNoPackage.java", // has src/ but no /java/
            ClassNode.Stereotype.CLASS,
            false,
            null,
            null,
            1.0,
            1,
            1,
            0,
            0,
            List.of(),
            List.of());
    ComponentNode validComp =
        new ComponentNode(
            "valid.pkg", "valid-comp", 1, null, 1.0, List.of(), List.of(classNoPackage));

    ArchitectureGraph mixedGraph =
        new ArchitectureGraph(
            "Mixed",
            false,
            null,
            List.of(nullClassesComp, validComp),
            List.of(),
            null); // null unassigned

    DipInversionPlan pMixed =
        synthesizer.synthesize(
            mixedGraph, policy, "ClassWithNoPackage", "com.example.adapters.TargetClass");
    assertEquals("ports", pMixed.portPackage());
    assertTrue(pMixed.portFilePath().startsWith("src/"));

    // 6. Test bareCaller with null fields
    ComponentNode bareComp =
        new ComponentNode("bare", "bare", 1, null, 1.0, List.of(), List.of(bareCaller));
    ArchitectureGraph bareGraph =
        new ArchitectureGraph("Bare", false, null, List.of(bareComp), List.of(), List.of());
    DipInversionPlan pBare =
        synthesizer.synthesize(bareGraph, null, "BareCaller", "com.example.adapters.TargetClass");
    assertEquals("ports", pBare.portPackage());
    assertEquals("src/main/java/ports/TargetClassPort.java", pBare.portFilePath());

    // 7. Test target with null methods list
    ComponentNode nullMethodsComp =
        new ComponentNode(
            "nm",
            "nm",
            2,
            null,
            1.0,
            List.of(),
            List.of(targetWithNullMethods, targetWithNullReturn));
    ArchitectureGraph nmGraph =
        new ArchitectureGraph("NM", false, null, List.of(nullMethodsComp), List.of(), List.of());
    DipInversionPlan pNM =
        synthesizer.synthesize(
            nmGraph, policy, "BareCaller", "com.example.adapters.NoMethodsTarget");
    assertTrue(pNM.targetMethods().isEmpty());

    // 8. Test target with null return type method
    DipInversionPlan pNullRet =
        synthesizer.synthesize(nmGraph, policy, "BareCaller", "TargetClass");
    assertEquals(1, pNullRet.targetMethods().size());
    assertTrue(pNullRet.targetMethods().get(0).startsWith("void doSomething(int arg0);"));
  }
}
