package com.design.umlviewer.scanner;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClojureAstScannerTest {

  private final ClojureAstScanner scanner = new ClojureAstScanner();

  @Test
  void testLanguageMetadataAndSupports(@TempDir Path tempDir) throws IOException {
    assertEquals("clojure", scanner.languageId());
    assertFalse(scanner.supports(tempDir.toString(), null));

    Files.createFile(tempDir.resolve("deps.edn"));
    assertTrue(scanner.supports(tempDir.toString(), null));

    ArchitecturePolicy clojurePolicy =
        new ArchitecturePolicy(
            "Clojure App",
            "src",
            "app",
            true,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "clojure");
    assertTrue(scanner.supports(tempDir.toString(), clojurePolicy));
  }

  @Test
  void testScanWithEdnManifestAndSourceFiles(@TempDir Path tempDir) throws IOException {
    Path examplesDir = tempDir.resolve("examples");
    Path srcDir = tempDir.resolve("src/uml_viewer/core");
    Files.createDirectories(examplesDir);
    Files.createDirectories(srcDir);

    // Write sample uml-viewer.edn
    String ednContent =
        """
        {:nodes
         [{:id :core.user
           :name "User"
           :ns "uml-viewer.core.user"
           :level 0
           :stereotype :class}
          {:id :gateway.repo
           :name "Repo"
           :ns "uml-viewer.gateway.repo"
           :level 1
           :stereotype :interface}
          {:id :foreign.lib
           :name "Lib"
           :foreign true}]
         :edges
         [{:from :core.user, :to :gateway.repo, :kind :implements}
          {:from :core.user, :to :foreign.lib, :kind :association}]}
        """;
    Files.writeString(examplesDir.resolve("uml-viewer.edn"), ednContent);

    // Write corresponding .clj source file
    String cljContent =
        """
        (ns uml-viewer.core.user)

        (defn calculate-score
          "Calculates user score."
          [a b]
          (+ a b))

        (def max-limit 100)
        """;
    Files.writeString(srcDir.resolve("user.clj"), cljContent);

    LanguageScanner.ScanResult result =
        scanner.scanProject(tempDir.toString(), "src", "uml-viewer");
    assertFalse(result.classes().isEmpty());
    assertEquals(3, result.classes().size());
    assertEquals(2, result.edges().size());

    var userClass = result.classes().stream().filter(c -> c.id().equals("core.user")).findFirst();
    assertTrue(userClass.isPresent());
    assertFalse(userClass.get().methods().isEmpty());
    assertTrue(
        userClass.get().methods().stream().anyMatch(m -> m.name().equals("calculate-score")));
    assertFalse(userClass.get().fields().isEmpty());
    assertTrue(userClass.get().fields().stream().anyMatch(f -> f.name().equals("max-limit")));

    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.IMPLEMENTS
                        && e.from().equals("core.user")
                        && e.to().equals("gateway.repo")));
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.ASSOCIATION
                        && e.from().equals("core.user")
                        && e.to().equals("foreign.lib")));
  }

  @Test
  void testScanFallbackSourceTreeWithoutEdn(@TempDir Path tempDir) throws IOException {
    Path srcDir = tempDir.resolve("src/services");
    Files.createDirectories(srcDir);

    String cljContent =
        """
        (ns services.payment)

        (defn process-payment [amount]
          (println amount))
        """;
    Files.writeString(srcDir.resolve("payment.clj"), cljContent);

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), "src", "services");
    assertFalse(result.classes().isEmpty());
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("payment")));
  }

  @Test
  void testUncleBobLocalWorkspaceIfPresent() throws IOException {
    String umlViewerPath = "/Users/fady/workspace/labs/uml-viewer";
    if (new File(umlViewerPath).exists()) {
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
      assertTrue(scanner.supports(umlViewerPath, clojurePolicy));
      LanguageScanner.ScanResult result = scanner.scanProject(umlViewerPath, "src", "uml-viewer");
      assertFalse(result.classes().isEmpty());
      assertFalse(result.edges().isEmpty());
    }
  }
}
