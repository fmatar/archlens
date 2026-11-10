package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.dossier.MermaidDiagramGenerator;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.ArchitectureCompiler;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportDiagramUseCaseTest {

  private ArchitectureCompiler sampleCompiler() {
    return new ArchitectureCompiler() {
      @Override
      public ArchitectureGraph compileGraph(String projectRoot, String proposalId) {
        ClassNode appClass =
            new ClassNode(
                "com.example.app.OrderService",
                "OrderService",
                "com.example.app",
                "OrderService.java",
                ClassNode.Stereotype.CLASS,
                false,
                1,
                null,
                90.0,
                2,
                5,
                0,
                0,
                List.of(),
                List.of());
        ClassNode infraClass =
            new ClassNode(
                "com.example.infra.SqlOrderRepo",
                "SqlOrderRepo",
                "com.example.infra",
                "SqlOrderRepo.java",
                ClassNode.Stereotype.CLASS,
                false,
                3,
                null,
                85.0,
                3,
                4,
                0,
                0,
                List.of(),
                List.of());
        ComponentNode appComp =
            new ComponentNode(
                "com.example.app", "app", 1, null, 90.0, List.of(), List.of(appClass));
        ComponentNode infraComp =
            new ComponentNode(
                "com.example.infra", "infra", 3, null, 85.0, List.of(), List.of(infraClass));
        DependencyEdge edge =
            new DependencyEdge(
                "com.example.app.OrderService",
                "com.example.infra.SqlOrderRepo",
                DependencyEdge.Kind.DEPENDENCY,
                "violates L1 -> L3",
                true);
        return new ArchitectureGraph(
            "Sample", false, proposalId, List.of(appComp, infraComp), List.of(edge), List.of());
      }

      @Override
      public ArchitecturePolicy loadPolicy(String projectRoot) {
        return new ArchitecturePolicy(
            "Sample",
            "src",
            "com.example",
            true,
            List.of("app", "infra"),
            List.of(List.of(), List.of("app"), List.of(), List.of("infra")),
            List.of(),
            List.of(),
            List.of());
      }
    };
  }

  @Test
  void testExportComponentRingsAndClassUmlAndDipDiagram() throws IOException {
    ExportDiagramUseCase useCase =
        new ExportDiagramUseCase(sampleCompiler(), new MermaidDiagramGenerator());

    String rings = useCase.execute(".", null, "COMPONENT_RINGS", null, false, null, null);
    assertTrue(rings.startsWith("flowchart TB"));
    assertTrue(rings.contains("Ring_L1"));
    assertTrue(rings.contains("VIOLATION"));

    String uml = useCase.execute(".", null, "CLASS_UML", null, true, null, null);
    assertTrue(uml.startsWith("classDiagram"));
    assertTrue(uml.contains("OrderService"));
    assertTrue(uml.contains("SqlOrderRepo"));

    String dipAuto = useCase.execute(".", null, "DIP_REFACTOR", null, false, null, null);
    assertTrue(dipAuto.startsWith("flowchart TB"));
    assertTrue(dipAuto.contains("subgraph Before"));
    assertTrue(dipAuto.contains("subgraph After"));
    assertTrue(dipAuto.contains("OrderService"));
    assertTrue(dipAuto.contains("SqlOrderRepoPort"));

    String dipExplicit =
        useCase.execute(
            ".",
            null,
            "DIP_REFACTOR",
            null,
            false,
            "com.example.app.BillingUseCase",
            "com.example.infra.StripeGateway");
    assertTrue(dipExplicit.contains("BillingUseCase"));
    assertTrue(dipExplicit.contains("StripeGatewayPort"));
  }
}
