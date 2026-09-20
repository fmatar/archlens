package com.design.umlviewer.scanner;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.DependencyEdge;
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
            new com.design.umlviewer.domain.policy.ArchitecturePolicy(
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
}
