package com.design.umlviewer.scanner;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.engine.GraphCompiler;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PythonAstScannerRealProjectTest {

  @Test
  void testScanEfmAgentNvidiaV2() throws IOException {
    File targetDir = new File("/Users/fady/workspace/datarobot/efm-agent-nvidia-v2");
    if (!targetDir.exists()) {
      return; // Skip if environment does not have the path
    }

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

    // Verify edges were extracted
    assertFalse(graph.edges().isEmpty());
  }
}
