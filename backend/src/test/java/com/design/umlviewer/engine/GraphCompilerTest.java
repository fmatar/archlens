package com.design.umlviewer.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.metrics.CrapScoreCalculator;
import com.design.umlviewer.scanner.JavaAstScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphCompilerTest {

  @Test
  void testCompileGraphAndPolicy(@TempDir Path tempDir) throws IOException {
    GraphCompiler compiler = new GraphCompiler();
    JavaAstScanner scanner = new JavaAstScanner();

    try {
      java.lang.reflect.Field field1 = JavaAstScanner.class.getDeclaredField("crapCalculator");
      field1.setAccessible(true);
      field1.set(scanner, new CrapScoreCalculator());

      com.design.umlviewer.scanner.LanguageScannerRegistry registry =
          new com.design.umlviewer.scanner.LanguageScannerRegistry(java.util.List.of(scanner));

      java.lang.reflect.Field field2 = GraphCompiler.class.getDeclaredField("scannerRegistry");
      field2.setAccessible(true);
      field2.set(compiler, registry);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // 1. Default policy when missing
    ArchitecturePolicy defaultPolicy = compiler.loadPolicy(tempDir.toString());
    assertEquals(tempDir.toFile().getName(), defaultPolicy.title());

    // 2. Write custom policy
    Path umlDir = tempDir.resolve(".uml-viewer");
    Files.createDirectories(umlDir);
    String policyJson =
        """
                {
                  "title": "My Custom Architecture",
                  "src": "src",
                  "prefix": "com.myapp",
                  "hierarchical": true,
                  "levels": [["domain"], ["service"]],
                  "proposals": [
                    {
                      "id": "prop1",
                      "name": "Proposal 1",
                      "layers": [
                        {"id": "core", "label": "Core Layer", "packages": ["domain"]}
                      ],
                      "omit": []
                    }
                  ]
                }
                """;
    Files.writeString(umlDir.resolve("policy.json"), policyJson);

    ArchitecturePolicy loadedPolicy = compiler.loadPolicy(tempDir.toString());
    assertEquals("My Custom Architecture", loadedPolicy.title());
    assertEquals(1, loadedPolicy.proposals().size());

    // 3. Create mock java source files
    Path srcDir = tempDir.resolve("src/com/myapp/domain");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("Entity.java"), "package com.myapp.domain; public class Entity {}");

    // 4. Compile real graph
    ArchitectureGraph graph = compiler.compileGraph(tempDir.toString(), null);
    assertNotNull(graph);
    assertEquals("My Custom Architecture", graph.title());
    assertFalse(graph.isProposal());
    assertFalse(graph.components().isEmpty());

    // 5. Compile proposal graph with both package and class matches
    String proposalWithClasses =
        """
        {
          "title": "My Custom Architecture",
          "src": "src",
          "prefix": "com.myapp",
          "hierarchical": true,
          "levels": [["domain"], ["service"]],
          "proposals": [
            {
              "id": "prop1",
              "name": "Proposal 1",
              "layers": [
                {"id": "core", "label": "Core Layer", "packages": ["domain"], "classes": ["Entity"]}
              ],
              "omit": []
            }
          ]
        }
        """;
    Files.writeString(umlDir.resolve("policy.json"), proposalWithClasses);
    ArchitectureGraph propGraph = compiler.compileGraph(tempDir.toString(), "prop1");
    assertNotNull(propGraph);
    assertTrue(propGraph.isProposal());
    assertEquals("prop1", propGraph.activeProposalId());
    assertEquals(1, propGraph.components().size());

    // 6. Test corrupt policy.json fallback
    Files.writeString(umlDir.resolve("policy.json"), "invalid-json-content");
    ArchitecturePolicy fallbackPolicy = compiler.loadPolicy(tempDir.toString());
    assertEquals(tempDir.toFile().getName(), fallbackPolicy.title());

    // 7. Test dynamic common prefix computation
    com.design.umlviewer.domain.model.ClassNode c1 =
        new com.design.umlviewer.domain.model.ClassNode(
            "com.slixes.vanguard.chat.ChatService",
            "ChatService",
            "com.slixes.vanguard.chat",
            "path",
            com.design.umlviewer.domain.model.ClassNode.Stereotype.CLASS,
            false,
            null,
            null,
            1.0,
            1,
            0,
            0,
            0,
            java.util.List.of(),
            java.util.List.of());
    com.design.umlviewer.domain.model.ClassNode c2 =
        new com.design.umlviewer.domain.model.ClassNode(
            "com.slixes.vanguard.project.ProjectService",
            "ProjectService",
            "com.slixes.vanguard.project",
            "path",
            com.design.umlviewer.domain.model.ClassNode.Stereotype.CLASS,
            false,
            null,
            null,
            1.0,
            1,
            0,
            0,
            0,
            java.util.List.of(),
            java.util.List.of());
    assertEquals(
        "com.slixes.vanguard", GraphCompiler.computeCommonPrefix(java.util.List.of(c1, c2)));
  }

  @Test
  void testCompileGraphWithOmitAndSorting(@TempDir Path tempDir) throws IOException {
    GraphCompiler compiler = new GraphCompiler();
    JavaAstScanner scanner = new JavaAstScanner();

    try {
      java.lang.reflect.Field field1 = JavaAstScanner.class.getDeclaredField("crapCalculator");
      field1.setAccessible(true);
      field1.set(scanner, new CrapScoreCalculator());

      com.design.umlviewer.scanner.LanguageScannerRegistry registry =
          new com.design.umlviewer.scanner.LanguageScannerRegistry(java.util.List.of(scanner));

      java.lang.reflect.Field field2 = GraphCompiler.class.getDeclaredField("scannerRegistry");
      field2.setAccessible(true);
      field2.set(compiler, registry);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    Path umlDir = tempDir.resolve(".uml-viewer");
    Files.createDirectories(umlDir);
    String policyJson =
        """
        {
          "title": "Ordered Architecture",
          "src": "src",
          "prefix": "com.example",
          "hierarchical": true,
          "order": ["domain", "service"],
          "levels": [["domain"], ["service"]],
          "omit": ["omitted_pkg"]
        }
        """;
    Files.writeString(umlDir.resolve("policy.json"), policyJson);

    Path srcDomain = tempDir.resolve("src/com/example/domain");
    Path srcService = tempDir.resolve("src/com/example/service");
    Path srcOmitted = tempDir.resolve("src/com/example/omitted_pkg");
    Files.createDirectories(srcDomain);
    Files.createDirectories(srcService);
    Files.createDirectories(srcOmitted);

    Files.writeString(
        srcDomain.resolve("DomainModel.java"),
        "package com.example.domain; public class DomainModel {}");
    Files.writeString(
        srcService.resolve("AppService.java"),
        "package com.example.service; public class AppService {}");
    Files.writeString(
        srcOmitted.resolve("Ignored.java"),
        "package com.example.omitted_pkg; public class Ignored {}");

    ArchitectureGraph graph = compiler.compileGraph(tempDir.toString(), null);
    assertNotNull(graph);
    // Ignored package must be filtered out
    assertTrue(graph.components().stream().noneMatch(c -> c.id().contains("omitted_pkg")));
    assertEquals(2, graph.components().size());
    // Components must follow declared order
    assertEquals("domain", graph.components().get(0).id());
    assertEquals("service", graph.components().get(1).id());
  }

  @Test
  void testArchlensPolicyPriorityOverLegacyUmlViewer(@TempDir Path tempDir) throws IOException {
    Path legacyDir = tempDir.resolve(".uml-viewer");
    Path primaryDir = tempDir.resolve(".archlens");
    Files.createDirectories(legacyDir);
    Files.createDirectories(primaryDir);

    Files.writeString(
        legacyDir.resolve("policy.json"),
        """
        {"title": "Legacy Policy", "src": "src", "prefix": "com.test", "hierarchical": true, "levels": []}
        """);

    Files.writeString(
        primaryDir.resolve("policy.json"),
        """
        {"title": "Primary Archlens Policy", "src": "src", "prefix": "com.test", "hierarchical": true, "levels": []}
        """);

    GraphCompiler compiler = new GraphCompiler();
    ArchitecturePolicy policy = compiler.loadPolicy(tempDir.toString());
    assertNotNull(policy);
    assertEquals("Primary Archlens Policy", policy.title());
  }
}
