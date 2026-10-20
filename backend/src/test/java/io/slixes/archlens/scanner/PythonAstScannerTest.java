package io.slixes.archlens.scanner;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.DependencyEdge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonAstScannerTest {

  private final PythonAstScanner scanner = new PythonAstScanner();

  @Test
  void testLanguageMetadataAndSupports(@TempDir Path tempDir) throws IOException {
    assertEquals("python", scanner.languageId());

    // Non-existent or empty dir
    assertFalse(scanner.supports(tempDir.toString(), null));

    // Dir with pyproject.toml
    Files.createFile(tempDir.resolve("pyproject.toml"));
    assertTrue(scanner.supports(tempDir.toString(), null));

    // Policy with explicit lang "python"
    assertTrue(
        scanner.supports(
            tempDir.toString(),
            new io.slixes.archlens.domain.policy.ArchitecturePolicy(
                "Test", null, null, false, List.of(), List.of(), List.of(), List.of(), List.of(),
                "python")));
  }

  @Test
  void testScanProjectWithImportsAndClasses(@TempDir Path tempDir) throws IOException {
    // Missing directory returns empty
    LanguageScanner.ScanResult emptyResult =
        scanner.scanProject(tempDir.toString(), "nonexistent", "");
    assertTrue(emptyResult.classes().isEmpty());
    assertTrue(emptyResult.edges().isEmpty());

    // Create Python package structure
    Path corePkg = tempDir.resolve("core");
    Path agentPkg = tempDir.resolve("agent");
    Files.createDirectories(corePkg);
    Files.createDirectories(agentPkg);

    // core/entity.py
    Files.writeString(
        corePkg.resolve("entity.py"),
        """
        # Core domain entities
        class BaseEntity:
            def __init__(self, id: str):
                self.id = id

        class Order(BaseEntity):
            def calculate_total(self):
                return 100
        """);

    // agent/service.py
    Files.writeString(
        agentPkg.resolve("service.py"),
        """
        from core.entity import Order, BaseEntity
        import os, sys

        class OrderService:
            def process(self, order: Order):
                pass
        """);

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), ".", "");

    assertFalse(result.classes().isEmpty());
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("Order")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("BaseEntity")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderService")));

    // Inheritance edge: Order inherits BaseEntity
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.INHERITANCE
                        && e.from().endsWith("Order")
                        && e.to().equals("BaseEntity")));

    // Dependency edge: agent.service -> core.entity
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.DEPENDENCY
                        && e.from().equals("agent.service")
                        && e.to().startsWith("core.entity")));
  }

  @Test
  void testSupportsWithTaskfileAndPythonFiles(@TempDir Path tempDir) throws IOException {
    Files.createFile(tempDir.resolve("Taskfile.yml"));
    // Before creating python file -> false
    assertFalse(scanner.supports(tempDir.toString(), null));

    // After creating a python file -> true
    Files.createFile(tempDir.resolve("main.py"));
    assertTrue(scanner.supports(tempDir.toString(), null));

    // Subdirectory python file
    Path sub = tempDir.resolve("mysub");
    Files.createDirectories(sub);
    Files.createFile(sub.resolve("helper.py"));
    assertTrue(scanner.supports(tempDir.toString(), null));
  }

  @Test
  void testInitFilesAndScriptModules(@TempDir Path tempDir) throws IOException {
    Path pkg = tempDir.resolve("mypackage");
    Files.createDirectories(pkg);

    // __init__.py package module
    Files.writeString(
        pkg.resolve("__init__.py"),
        """
        __version__ = "1.0.0"
        def get_version():
            return __version__
        """);

    // Standalone script without class
    Files.writeString(
        tempDir.resolve("standalone_script.py"),
        """
        import mypackage
        from mypackage import get_version

        def main():
            pass
        """);

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), ".", "");
    assertFalse(result.classes().isEmpty());
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("standalone_script")));
    assertTrue(result.classes().stream().anyMatch(c -> c.id().equals("mypackage")));
  }

  @Test
  void testExclusionOfNonProductionDirectoriesAndFiles(@TempDir Path tempDir) throws IOException {
    Path srcDir = tempDir.resolve("app");
    Path testDir = tempDir.resolve("tests");
    Path nodeModulesDir = tempDir.resolve("node_modules/pkg");
    Path venvDir = tempDir.resolve(".venv/lib");
    Files.createDirectories(srcDir);
    Files.createDirectories(testDir);
    Files.createDirectories(nodeModulesDir);
    Files.createDirectories(venvDir);

    // Production source
    Files.writeString(srcDir.resolve("service.py"), "class Service:\n    pass\n");
    // Test in tests dir
    Files.writeString(testDir.resolve("test_service.py"), "class TestService:\n    pass\n");
    // Test file in src dir
    Files.writeString(srcDir.resolve("test_helper.py"), "class TestHelper:\n    pass\n");
    // Python file in node_modules
    Files.writeString(nodeModulesDir.resolve("script.py"), "class ModuleClass:\n    pass\n");
    // Python file in .venv
    Files.writeString(venvDir.resolve("dep.py"), "class DepClass:\n    pass\n");

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), ".", "");
    assertEquals(1, result.classes().size());
    assertEquals("Service", result.classes().get(0).name());
  }

  @Test
  void testPolicyOmitRespected(@TempDir Path tempDir) throws IOException {
    Path srcDir = tempDir.resolve("app");
    Path migrationsDir = tempDir.resolve("migrations");
    Files.createDirectories(srcDir);
    Files.createDirectories(migrationsDir);

    Files.writeString(srcDir.resolve("model.py"), "class User:\n    pass\n");
    Files.writeString(migrationsDir.resolve("m001.py"), "class Migration001:\n    pass\n");

    io.slixes.archlens.domain.policy.ArchitecturePolicy policy =
        new io.slixes.archlens.domain.policy.ArchitecturePolicy(
            "Test",
            ".",
            "",
            true,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("migrations"));

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), ".", "", policy);
    assertEquals(1, result.classes().size());
    assertEquals("User", result.classes().get(0).name());
  }

  @Test
  void testNestedMultiLineRelativeAndBarrelReExportImports(@TempDir Path tempDir)
      throws IOException {
    // src/myapp/{domain,application,adapters/persistence}
    Path domainDir = tempDir.resolve("src/myapp/domain");
    Path appDir = tempDir.resolve("src/myapp/application");
    Path adaptersDir = tempDir.resolve("src/myapp/adapters");
    Path persistenceDir = adaptersDir.resolve("persistence");
    Files.createDirectories(domainDir);
    Files.createDirectories(appDir);
    Files.createDirectories(persistenceDir);

    Files.writeString(tempDir.resolve("src/myapp/__init__.py"), "");
    Files.writeString(
        domainDir.resolve("__init__.py"),
        """
        from . import (
            order,
        )
        """);

    // Multi-line docstring with fake import (should be ignored) + nested function import with #
    // noqa
    Files.writeString(
        domainDir.resolve("order.py"),
        """
        \"\"\"
        Example docstring that must not be parsed as an import:
        from fake.pkg import FakeClass
        import fake_direct
        \"\"\"
        import importlib

        class Order:
            async def save_audit(self):
                import myapp.adapters.persistence.sql_repo as repo  # noqa: PLC0415
                dyn = importlib.import_module("myapp.adapters.http_client")
                return repo, dyn
        """);

    // Barrel re-export in adapters/__init__.py + multi-line parenthesized & backslash imports
    Files.writeString(
        adaptersDir.resolve("__init__.py"),
        """
        from .persistence.sql_repo import (
            SqlOrderRepository,  # primary SQL adapter
        )
        """);

    Files.writeString(
        persistenceDir.resolve("sql_repo.py"),
        """
        from ...domain.order \\
            import Order

        class SqlOrderRepository:
            def save(self, order: Order):
                pass
        """);

    Files.writeString(
        adaptersDir.resolve("http_client.py"),
        """
        class HttpClient:
            pass
        """);

    // Application use case importing via adapters/__init__.py barrel + multi-line parenthesized
    Files.writeString(
        appDir.resolve("place_order.py"),
        """
        from typing import TYPE_CHECKING
        from myapp.domain.order import (
            Order,
        )
        if TYPE_CHECKING:
            from myapp.adapters import SqlOrderRepository  # type: ignore[import]

        class PlaceOrderUseCase:
            def execute(self, order: Order):
                pass
        """);

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), "src", "myapp");

    // Docstring fake imports must not appear
    assertTrue(result.edges().stream().noneMatch(e -> e.to().contains("fake")));

    // 1. __init__.py relative multi-line import: myapp.domain -> myapp.domain.order
    assertTrue(
        result.edges().stream()
            .anyMatch(e -> e.from().equals("myapp.domain") && e.to().equals("myapp.domain.order")));

    // 2. Nested function import with inline # noqa comment & dynamic import_module:
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.from().equals("myapp.domain.order")
                        && e.to().equals("myapp.adapters.persistence.sql_repo")));
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.from().equals("myapp.domain.order")
                        && e.to().equals("myapp.adapters.http_client")));

    // 3. Backslash continuation + 3-dot relative import: sql_repo -> myapp.domain.order
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.from().equals("myapp.adapters.persistence.sql_repo")
                        && e.to().equals("myapp.domain.order")));

    // 4. Barrel re-export through adapters/__init__.py under TYPE_CHECKING:
    // place_order imports SqlOrderRepository from myapp.adapters -> resolves to sql_repo!
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.from().equals("myapp.application.place_order")
                        && e.to().equals("myapp.adapters.persistence.sql_repo")));

    // 5. Verify end-to-end GraphCompiler, Cycle Detection, and Dossier generation on Python graph
    io.slixes.archlens.domain.policy.ArchitecturePolicy policy =
        new io.slixes.archlens.domain.policy.ArchitecturePolicy(
            "MyApp Python",
            "src",
            "myapp",
            true,
            List.of("domain", "application", "adapters"),
            List.of(List.of("domain"), List.of("application"), List.of("adapters")),
            List.of(),
            List.of(),
            List.of(),
            "python");
    Files.createDirectories(tempDir.resolve(".archlens"));
    new com.fasterxml.jackson.databind.ObjectMapper()
        .writeValue(tempDir.resolve(".archlens/policy.json").toFile(), policy);

    LanguageScannerRegistry registry = new LanguageScannerRegistry(List.of(scanner));
    io.slixes.archlens.engine.GraphCompiler compiler =
        new io.slixes.archlens.engine.GraphCompiler();
    try {
      java.lang.reflect.Field f =
          io.slixes.archlens.engine.GraphCompiler.class.getDeclaredField("scannerRegistry");
      f.setAccessible(true);
      f.set(compiler, registry);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    io.slixes.archlens.domain.graph.ArchitectureGraph graph =
        compiler.compileGraph(tempDir.toString(), null);
    assertFalse(graph.edges().stream().filter(DependencyEdge::isViolating).toList().isEmpty());
    assertFalse(
        graph.cycles().isEmpty(),
        "PackageCycleDetector should detect cycle between myapp.domain and myapp.adapters.persistence");

    String dossier =
        new io.slixes.archlens.domain.dossier.ArchitecturalDossierGenerator()
            .generate(graph, policy);
    assertFalse(
        dossier.contains("Unknown Layer"),
        "Dossier must resolve Python module edge endpoints to their concentric ring levels");
  }
}
