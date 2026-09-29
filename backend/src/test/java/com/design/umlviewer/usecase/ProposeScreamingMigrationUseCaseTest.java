package com.design.umlviewer.usecase;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.model.CrapScore;
import com.design.umlviewer.domain.model.ScreamingMetric;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.domain.screaming.ScreamingMigrationAssistant;
import com.design.umlviewer.domain.screaming.ScreamingMigrationProposal;
import com.design.umlviewer.engine.ArchitectureCompiler;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposeScreamingMigrationUseCaseTest {

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
  void testProposeScreamingMigrationUseCaseExecution() throws IOException {
    ComponentNode controllers =
        new ComponentNode(
            "controllers",
            "controllers",
            2,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(createClass("c1", "OrderController", "com.app.controllers")));

    ComponentNode services =
        new ComponentNode(
            "services",
            "services",
            1,
            new CrapScore(0, 0, 0),
            100.0,
            Collections.emptyList(),
            List.of(createClass("s1", "OrderService", "com.app.services")));

    ArchitectureCompiler mockCompiler =
        new ArchitectureCompiler() {
          @Override
          public ArchitectureGraph compileGraph(String projectRoot, String proposalId) {
            return new ArchitectureGraph(
                "TestProject",
                false,
                proposalId,
                List.of(controllers, services),
                List.of(),
                List.of(),
                List.of(),
                ScreamingMetric.empty());
          }

          @Override
          public ArchitecturePolicy loadPolicy(String projectRoot) {
            return null;
          }
        };

    ScreamingMigrationAssistant assistant = new ScreamingMigrationAssistant();
    ProposeScreamingMigrationUseCase useCase =
        new ProposeScreamingMigrationUseCase(mockCompiler, assistant);

    ScreamingMigrationProposal proposal = useCase.execute(".", "prop-1");

    assertNotNull(proposal);
    assertEquals("PACKAGE_BY_LAYER", proposal.currentClassification());
    assertEquals("PACKAGE_BY_FEATURE", proposal.projectedClassification());
    assertEquals(1, proposal.clusters().size());
    assertEquals("Order", proposal.clusters().get(0).featureName());
    assertEquals("com.app.order", proposal.clusters().get(0).proposedPackageName());
    assertEquals("com.app.order", proposal.stagedClassMoves().get("c1"));
    assertEquals("com.app.order", proposal.stagedClassMoves().get("s1"));
  }
}
