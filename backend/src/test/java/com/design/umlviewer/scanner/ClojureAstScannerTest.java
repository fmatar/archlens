package com.design.umlviewer.scanner;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClojureAstScannerTest {

  @Test
  void testSupportsAndScanUncleBobUmlViewer() throws IOException {
    ClojureAstScanner scanner = new ClojureAstScanner();
    assertEquals("clojure", scanner.languageId());

    ArchitecturePolicy clojurePolicy =
        new ArchitecturePolicy(
            "UML viewer",
            "src",
            "uml-viewer",
            true,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String umlViewerPath = "/Users/fady/workspace/labs/uml-viewer";
    if (new File(umlViewerPath).exists()) {
      assertTrue(scanner.supports(umlViewerPath, clojurePolicy));
      LanguageScanner.ScanResult result = scanner.scanProject(umlViewerPath, "src", "uml-viewer");
      assertFalse(result.classes().isEmpty(), "Should scan classes from uml-viewer EDN");
      assertFalse(result.edges().isEmpty(), "Should scan edges from uml-viewer EDN");

      // Verify that methods/functions are extracted from .clj source files
      var classWithMethods =
          result.classes().stream().filter(c -> !c.methods().isEmpty()).findFirst();
      assertTrue(classWithMethods.isPresent(), "Should extract methods/functions from .clj files");
      assertNotNull(classWithMethods.get().filePath());
      assertFalse(classWithMethods.get().filePath().isBlank());
    }
  }
}
