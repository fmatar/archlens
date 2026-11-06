package io.slixes.archlens.mcp;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.slixes.archlens.domain.dossier.DipInversionPlan;
import io.slixes.archlens.domain.fitness.FitnessEvaluation;
import io.slixes.archlens.domain.fitness.FitnessHistoryTrend;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.domain.screaming.ScreamingMigrationProposal;
import io.slixes.archlens.engine.GraphCompiler;
import io.slixes.archlens.usecase.SyncAdrUseCase;
import io.smallrye.common.annotation.RunOnVirtualThread;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchlensMcpServiceTest {

  private ArchlensMcpService mcpService;

  @BeforeEach
  void setUp() {
    GraphCompiler compiler =
        new GraphCompiler() {
          @Override
          public ArchitecturePolicy loadPolicy(String root) {
            return new ArchitecturePolicy(
                "McpApp", "src", "com.mcp", true, List.of(), List.of(), List.of(), List.of(),
                List.of());
          }

          @Override
          public ArchitectureGraph compileGraph(String root, String proposalId) {
            return new ArchitectureGraph(
                "McpGraph", false, proposalId, List.of(), List.of(), List.of());
          }
        };

    mcpService = new ArchlensMcpService(compiler);
  }

  @Test
  void testHexagonalDecouplingAndVirtualThreadAnnotations() {
    boolean hasRestCoupling =
        Arrays.stream(ArchlensMcpService.class.getDeclaredFields())
            .anyMatch(f -> f.getType().getName().contains("delivery.rest"));
    assertFalse(
        hasRestCoupling,
        "ArchlensMcpService (Ring 3 MCP Adapter) must not depend on delivery.rest controllers");
    assertTrue(
        ArchlensMcpService.class.isAnnotationPresent(RunOnVirtualThread.class),
        "ArchlensMcpService must be annotated with @RunOnVirtualThread for Java 25 virtual thread execution");
  }

  @Test
  void testInspectArchitecture(@TempDir Path tempDir) throws Exception {
    ArchitectureGraph graph = mcpService.inspectArchitecture(tempDir.toString());
    assertNotNull(graph);
    assertEquals("McpGraph", graph.title());
  }

  @Test
  void testExportLlmDossier(@TempDir Path tempDir) throws IOException {
    String dossier = mcpService.exportLlmDossier(tempDir.toString(), "");
    assertNotNull(dossier);
    assertTrue(dossier.contains("# Clean Architecture Optimization Dossier — McpGraph"));
    assertTrue(dossier.contains("Actionable LLM Refactoring Instructions"));
  }

  @Test
  void testListSnapshots(@TempDir Path tempDir) {
    Map<String, Object> result = mcpService.listSnapshots(tempDir.toString());
    assertNotNull(result);
    assertTrue(result.containsKey("snapshots"));
  }

  @Test
  void testGetSnapshot(@TempDir Path tempDir) throws IOException {
    ArchitectureGraph fallback = mcpService.getSnapshot("test-release", tempDir.toString());
    assertNotNull(fallback);
    assertEquals("McpGraph", fallback.title());
  }

  @Test
  void testSynthesizeDipInversion(@TempDir Path tempDir) throws IOException {
    DipInversionPlan plan =
        mcpService.synthesizeDipInversion(
            "com.example.OrderService", "com.example.PaymentGateway", tempDir.toString());
    assertNotNull(plan);
    assertEquals("com.example.OrderService", plan.fromClass());
    assertEquals("com.example.PaymentGateway", plan.toClass());
    assertEquals("PaymentGatewayPort", plan.portName());
    assertNotNull(plan.portInterfaceCode());
    assertNotNull(plan.surgicalPrompt());
  }

  @Test
  void testEvaluateFitnessAndHistoryTools(@TempDir Path tempDir) throws IOException {
    FitnessEvaluation evaluation = mcpService.evaluateFitness(tempDir.toString(), "");
    assertNotNull(evaluation);
    assertTrue(evaluation.fitnessScore() >= 0.0);

    FitnessHistoryTrend trend = mcpService.getFitnessHistory(tempDir.toString());
    assertNotNull(trend);
    assertNotNull(trend.history());
  }

  @Test
  void testProposeScreamingMigrationAndPolicyTools(@TempDir Path tempDir) throws IOException {
    ScreamingMigrationProposal proposal =
        mcpService.proposeScreamingMigration(tempDir.toString(), "");
    assertNotNull(proposal);

    ArchitecturePolicy policy = mcpService.getPolicy(tempDir.toString());
    assertNotNull(policy);
    assertEquals("McpApp", policy.title());
  }

  @Test
  void testSyncDipAdrTool(@TempDir Path tempDir) throws IOException {
    SyncAdrUseCase.AdrDocument adr =
        mcpService.syncDipAdr(
            tempDir.toString(),
            "com.example.OrderService",
            "com.example.PaymentGateway",
            "PaymentGatewayPort",
            "Eliminate outward adapter coupling");
    assertNotNull(adr);
    assertTrue(adr.title().contains("PaymentGatewayPort"));
  }

  @Test
  void testMcpResourcesAndPrompts(@TempDir Path tempDir) throws IOException {
    TextResourceContents llmsResource = mcpService.readLlmsDossierResource();
    assertNotNull(llmsResource);
    assertEquals("archlens://llms.txt", llmsResource.uri());
    assertTrue(llmsResource.text().contains("Clean Architecture Optimization Dossier"));

    TextResourceContents policyResource = mcpService.readPolicyResource();
    assertNotNull(policyResource);
    assertEquals("archlens://policy", policyResource.uri());
    assertTrue(policyResource.text().contains("McpApp"));

    TextResourceContents fitnessResource = mcpService.readFitnessResource();
    assertNotNull(fitnessResource);
    assertEquals("archlens://fitness", fitnessResource.uri());
    assertTrue(fitnessResource.text().contains("fitnessScore"));

    PromptMessage reviewPrompt = mcpService.architecturalReviewPrompt(tempDir.toString());
    assertNotNull(reviewPrompt);
    assertNotNull(reviewPrompt.content());

    PromptMessage dipPrompt =
        mcpService.dipRefactoringPrompt(
            "com.example.OrderService", "com.example.PaymentGateway", tempDir.toString());
    assertNotNull(dipPrompt);
    assertNotNull(dipPrompt.content());
  }
}
