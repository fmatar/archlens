package com.design.umlviewer.scanner;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageScannerRegistryTest {

  @Test
  void testRegistryResolutionAndFallbacks(@TempDir Path tempDir) throws IOException {
    JavaAstScanner javaScanner = new JavaAstScanner();
    PythonAstScanner pythonScanner = new PythonAstScanner();

    LanguageScannerRegistry registry =
        new LanguageScannerRegistry(List.of(javaScanner, pythonScanner));

    assertEquals(2, registry.getAllScanners().size());

    // Find scanner by id
    assertTrue(registry.findScanner("java").isPresent());
    assertTrue(registry.findScanner("python").isPresent());
    assertFalse(registry.findScanner("nonexistent").isPresent());
    assertFalse(registry.findScanner(null).isPresent());
    assertFalse(registry.findScanner("").isPresent());

    // Resolve by explicit policy lang
    ArchitecturePolicy pythonPolicy =
        new ArchitecturePolicy(
            "Py", null, null, false, List.of(), List.of(), List.of(), List.of(), List.of(),
            "python");
    LanguageScanner resolvedPy = registry.resolveScanner(tempDir.toString(), pythonPolicy);
    assertEquals("python", resolvedPy.languageId());

    // Resolve by file detection (pom.xml -> java)
    Files.createFile(tempDir.resolve("pom.xml"));
    ArchitecturePolicy nullPolicy =
        new ArchitecturePolicy(
            "Java", null, null, false, List.of(), List.of(), List.of(), List.of(), List.of(), null);
    LanguageScanner resolvedJava = registry.resolveScanner(tempDir.toString(), nullPolicy);
    assertEquals("java", resolvedJava.languageId());

    // Manual registration
    LanguageScannerRegistry emptyRegistry = new LanguageScannerRegistry();
    emptyRegistry.register(javaScanner);
    assertEquals(1, emptyRegistry.getAllScanners().size());
  }
}
