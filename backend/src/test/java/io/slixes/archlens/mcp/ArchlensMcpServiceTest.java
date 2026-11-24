package io.slixes.archlens.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.slixes.archlens.domain.dossier.ArchitecturalDossierGenerator;
import io.slixes.archlens.domain.dossier.DipInversionPlan;
import io.slixes.archlens.domain.dossier.DipInversionSynthesizer;
import io.slixes.archlens.domain.dossier.MermaidDiagramGenerator;
import io.slixes.archlens.domain.fitness.ArchitectureFitnessCalculator;
import io.slixes.archlens.domain.fitness.FitnessEvaluation;
import io.slixes.archlens.domain.fitness.FitnessHistoryTrend;
import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.domain.screaming.ScreamingMigrationAssistant;
import io.slixes.archlens.domain.screaming.ScreamingMigrationProposal;
import io.slixes.archlens.engine.GraphCompiler;
import io.slixes.archlens.usecase.EvaluateFitnessUseCase;
import io.slixes.archlens.usecase.ExportDiagramUseCase;
import io.slixes.archlens.usecase.ExportDossierUseCase;
import io.slixes.archlens.usecase.GetFitnessHistoryUseCase;
import io.slixes.archlens.usecase.ManageSnapshotsUseCase;
import io.slixes.archlens.usecase.OpenedProjectRegistry;
import io.slixes.archlens.usecase.ProposeScreamingMigrationUseCase;
import io.slixes.archlens.usecase.SyncAdrUseCase;
import io.slixes.archlens.usecase.SynthesizeDipInversionUseCase;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

    ObjectMapper mapper = new ObjectMapper();
    ManageSnapshotsUseCase snapshots = new ManageSnapshotsUseCase(compiler, mapper);
    ArchitectureFitnessCalculator fitnessCalculator = new ArchitectureFitnessCalculator();
    mcpService =
        new ArchlensMcpService(
            compiler,
            new ExportDossierUseCase(compiler, new ArchitecturalDossierGenerator()),
            new ExportDiagramUseCase(compiler, new MermaidDiagramGenerator()),
            snapshots,
            new SynthesizeDipInversionUseCase(compiler, new DipInversionSynthesizer()),
            new EvaluateFitnessUseCase(compiler, fitnessCalculator),
            new GetFitnessHistoryUseCase(snapshots, compiler, fitnessCalculator),
            new ProposeScreamingMigrationUseCase(compiler, new ScreamingMigrationAssistant()),
            new SyncAdrUseCase(),
            new OpenedProjectRegistry(),
            mapper);
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

    String mermaidRings =
        mcpService.exportArchitectureDiagram(tempDir.toString(), "COMPONENT_RINGS", "", false);
    assertTrue(mermaidRings.startsWith("flowchart TB"));

    String mermaidUml =
        mcpService.exportArchitectureDiagram(tempDir.toString(), "CLASS_UML", "", true);
    assertTrue(mermaidUml.startsWith("classDiagram"));
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
  void testSyncDipAdrRequiresActiveWorkspaceOrOpenedProject(@TempDir Path tempDir)
      throws IOException {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            mcpService.syncDipAdr(
                tempDir.toString(),
                "com.example.OrderService",
                "com.example.PaymentGateway",
                "PaymentGatewayPort",
                "Eliminate outward adapter coupling"));

    assertNotNull(mcpService.requireAuthorizedWriteRoot("."));
    assertNotNull(mcpService.getOpenedProjects());

    mcpService.getOpenedProjects().markOpened(tempDir.toString());
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
    assertEquals("archlens://llms.txt", llmsResource.uri());
    assertTrue(llmsResource.text().contains("Clean Architecture Optimization Dossier"));

    TextResourceContents policyResource = mcpService.readPolicyResource();
    assertEquals("archlens://policy", policyResource.uri());
    assertTrue(policyResource.text().contains("McpApp"));

    TextResourceContents fitnessResource = mcpService.readFitnessResource();
    assertEquals("archlens://fitness", fitnessResource.uri());
    assertTrue(fitnessResource.text().contains("fitnessScore"));

    PromptMessage reviewPrompt = mcpService.architecturalReviewPrompt(tempDir.toString());
    assertNotNull(reviewPrompt.content());

    PromptMessage dipPrompt =
        mcpService.dipRefactoringPrompt(
            "com.example.OrderService", "com.example.PaymentGateway", tempDir.toString());
    assertNotNull(dipPrompt.content());
  }

  @Test
  void testDeclarativeBeanValidationAnnotationsOnMcpToolAndPromptParameters() throws Exception {
    Method getSnapshot =
        ArchlensMcpService.class.getMethod("getSnapshot", String.class, String.class);
    assertTrue(getSnapshot.getParameters()[0].isAnnotationPresent(NotBlank.class));

    Method synthesizeDip =
        ArchlensMcpService.class.getMethod(
            "synthesizeDipInversion", String.class, String.class, String.class);
    assertTrue(synthesizeDip.getParameters()[0].isAnnotationPresent(NotBlank.class));
    assertTrue(synthesizeDip.getParameters()[1].isAnnotationPresent(NotBlank.class));

    Method dipPrompt =
        ArchlensMcpService.class.getMethod(
            "dipRefactoringPrompt", String.class, String.class, String.class);
    assertTrue(dipPrompt.getParameters()[0].isAnnotationPresent(NotBlank.class));
    assertTrue(dipPrompt.getParameters()[1].isAnnotationPresent(NotBlank.class));

    Method exportDiagram =
        ArchlensMcpService.class.getMethod(
            "exportArchitectureDiagram", String.class, String.class, String.class, boolean.class);
    assertTrue(exportDiagram.isAnnotationPresent(Tool.class));
    assertTrue(
        Arrays.stream(exportDiagram.getParameters())
            .allMatch(p -> p.isAnnotationPresent(ToolArg.class)));
  }

  @Test
  void testDualTransportMcpStdioExtensionAndProfileConfiguration() throws Exception {
    Class<?> stdioHandler =
        Class.forName("io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler");
    assertNotNull(stdioHandler);

    Properties props = new Properties();
    try (InputStream in =
        ArchlensMcpService.class.getClassLoader().getResourceAsStream("application.properties")) {
      assertNotNull(in);
      props.load(in);
    }

    assertEquals("false", props.getProperty("quarkus.mcp.server.stdio.enabled"));
    assertEquals("false", props.getProperty("quarkus.mcp.server.stdio.null-system-out"));
    assertEquals("false", props.getProperty("quarkus.log.console.stderr"));

    assertEquals("true", props.getProperty("%mcp-stdio.quarkus.mcp.server.stdio.enabled"));
    assertEquals("true", props.getProperty("%mcp-stdio.quarkus.mcp.server.stdio.null-system-out"));
    assertEquals("true", props.getProperty("%mcp-stdio.quarkus.log.console.stderr"));
    assertEquals("false", props.getProperty("%mcp-stdio.quarkus.banner.enabled"));
    assertEquals("0", props.getProperty("%mcp-stdio.quarkus.http.port"));
  }
}
