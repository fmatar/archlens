package io.slixes.archlens.scanner;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import io.slixes.archlens.engine.GraphCompiler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonAstScannerRealProjectTest {

  @TempDir Path tempDir;

  @Test
  void testScanEfmAgentNvidiaV2() throws IOException {
    Files.createDirectories(tempDir.resolve(".archlens"));
    Files.writeString(
        tempDir.resolve(".archlens/policy.json"),
        """
        {
          "title": "EFM Agent NVIDIA v2 Clean Architecture",
          "src": ".",
          "lang": "python",
          "order": ["core", "agent", "fastapi_server"],
          "levels": [["core"], ["agent"], ["fastapi_server"]],
          "omit": ["tests", "node_modules", "frontend_web", "migrations"],
          "proposals": [
            {
              "id": "clean-arch-v1",
              "name": "Clean Architecture Rings",
              "layers": [
                {"id": "domain-core", "label": "Domain Core", "packages": ["core"], "classes": []},
                {"id": "app-usecases", "label": "Application", "packages": ["agent"], "classes": []},
                {"id": "adapters-api", "label": "Adapters", "packages": ["fastapi_server"], "classes": []},
                {"id": "infra-layer", "label": "Infrastructure", "packages": ["infra"], "classes": []}
              ],
              "omit": []
            }
          ]
        }
        """);

    Files.createDirectories(tempDir.resolve("core"));
    Files.writeString(
        tempDir.resolve("core/domain_model.py"),
        "class ForecastModel:\n    def predict(self) -> float:\n        return 1.0\n");

    Files.createDirectories(tempDir.resolve("agent"));
    Files.writeString(
        tempDir.resolve("agent/orchestrator.py"),
        "from core.domain_model import ForecastModel\nclass AgentOrchestrator:\n    def run(self):\n        return ForecastModel().predict()\n");

    Files.createDirectories(tempDir.resolve("fastapi_server"));
    Files.writeString(
        tempDir.resolve("fastapi_server/routes.py"),
        "from agent.orchestrator import AgentOrchestrator\nclass ForecastRouter:\n    def handle(self):\n        return AgentOrchestrator().run()\n");

    Files.createDirectories(tempDir.resolve("infra"));
    Files.writeString(tempDir.resolve("infra/db.py"), "class DbClient:\n    pass\n");

    // Excluded noise directories
    Files.createDirectories(tempDir.resolve("tests"));
    Files.writeString(tempDir.resolve("tests/test_agent.py"), "class TestAgent:\n    pass\n");
    Files.createDirectories(tempDir.resolve("frontend_web"));
    Files.writeString(tempDir.resolve("frontend_web/app.py"), "class WebApp:\n    pass\n");
    Files.createDirectories(tempDir.resolve("migrations"));
    Files.writeString(tempDir.resolve("migrations/v1.py"), "class MigrationV1:\n    pass\n");

    File targetDir = tempDir.toFile();

    PythonAstScanner scanner = new PythonAstScanner();
    LanguageScannerRegistry registry = new LanguageScannerRegistry(List.of(scanner));

    GraphCompiler compiler = new GraphCompiler();
    try {
      java.lang.reflect.Field field = GraphCompiler.class.getDeclaredField("scannerRegistry");
      field.setAccessible(true);
      field.set(compiler, registry);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    ArchitecturePolicy policy = compiler.loadPolicy(targetDir.getAbsolutePath());
    assertNotNull(policy);
    assertEquals("EFM Agent NVIDIA v2 Clean Architecture", policy.title());
    assertEquals("python", policy.lang());

    ArchitectureGraph graph = compiler.compileGraph(targetDir.getAbsolutePath(), null);
    assertNotNull(graph);
    assertFalse(graph.components().isEmpty());

    // Verify core, agent, fastapi_server components are mapped
    List<String> compIds = graph.components().stream().map(ComponentNode::id).toList();
    assertTrue(
        compIds.stream()
            .anyMatch(
                id ->
                    id.startsWith("core")
                        || id.startsWith("agent")
                        || id.startsWith("fastapi_server")));

    // Verify test directories and omitted paths are excluded
    assertTrue(compIds.stream().noneMatch(id -> id.contains("tests") || id.contains(".tests.")));
    assertTrue(compIds.stream().noneMatch(id -> id.contains("node_modules")));
    assertTrue(compIds.stream().noneMatch(id -> id.contains("frontend_web")));
    assertTrue(compIds.stream().noneMatch(id -> id.contains("migrations")));

    // Verify component count is concise and lightweight (far below 228)
    assertTrue(
        graph.components().size() <= 40,
        "Components should be concise production modules, got: " + graph.components().size());

    // Verify edges were extracted
    assertFalse(graph.edges().isEmpty());

    // Verify clean-arch-v1 proposal produces 4 clean concentric rings
    ArchitectureGraph propGraph =
        compiler.compileGraph(targetDir.getAbsolutePath(), "clean-arch-v1");
    assertNotNull(propGraph);
    assertTrue(propGraph.isProposal());
    assertEquals("clean-arch-v1", propGraph.activeProposalId());
    assertEquals(4, propGraph.components().size());
  }
}
