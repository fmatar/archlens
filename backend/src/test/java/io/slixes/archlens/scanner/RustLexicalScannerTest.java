package io.slixes.archlens.scanner;

import static org.junit.jupiter.api.Assertions.*;

import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RustLexicalScannerTest {

  private final RustLexicalScanner scanner = new RustLexicalScanner();

  @Test
  void testLanguageMetadataAndSupports(@TempDir Path tempDir) throws IOException {
    assertEquals("rust", scanner.languageId());
    assertFalse(scanner.supports(tempDir.toString(), null));

    Files.createFile(tempDir.resolve("Cargo.toml"));
    assertTrue(scanner.supports(tempDir.toString(), null));

    assertTrue(
        scanner.supports(
            tempDir.toString(),
            new ArchitecturePolicy(
                "Rust", null, null, false, List.of(), List.of(), List.of(), List.of(), List.of(),
                "rust")));
  }

  @Test
  void testScanRustProject(@TempDir Path tempDir) throws IOException {
    Path src = tempDir.resolve("src");
    Path domain = src.resolve("domain");
    Path adapters = src.resolve("adapters");
    Files.createDirectories(domain);
    Files.createDirectories(adapters);

    // domain/mod.rs
    Files.writeString(
        domain.resolve("mod.rs"),
        """
        pub trait OrderRepository {
            fn save(&self);
        }

        pub struct Order {
            id: String,
        }
        """);

    // adapters/postgres.rs
    Files.writeString(
        adapters.resolve("postgres.rs"),
        """
        use crate::domain::OrderRepository;

        pub struct PostgresRepo;

        impl OrderRepository for PostgresRepo {
            fn save(&self) {}
        }
        """);

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), "src", "");
    assertFalse(result.classes().isEmpty());

    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("Order")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderRepository")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("PostgresRepo")));

    // Implementation edge
    assertTrue(
        result.edges().stream()
            .anyMatch(e ->
                e.kind() == DependencyEdge.Kind.IMPLEMENTS
                    && e.from().endsWith("PostgresRepo")
                    && e.to().equals("OrderRepository")));

    // Use edge
    assertTrue(
        result.edges().stream()
            .anyMatch(e ->
                e.kind() == DependencyEdge.Kind.DEPENDENCY
                    && e.from().equals("adapters.postgres")
                    && e.to().equals("domain.OrderRepository")));
  }

  @Test
  void testIgnoresUseStatementsInsideBlockCommentsAndStrings(@TempDir Path tempDir)
      throws IOException {
    Files.writeString(tempDir.resolve("Cargo.toml"), "[package]\nname = \"clean\"\n");
    Path domain = tempDir.resolve("src/domain");
    Files.createDirectories(domain);

    Files.writeString(
        domain.resolve("mod.rs"),
        """
        pub(crate) use crate::model::{
            Money,
            currency::{Currency as Curr, self},
        };
        /*
        use crate::adapters::PostgresRepo;
        */
        pub struct OrderService;

        pub fn doc_snippet() -> &'static str {
            r#"
        use crate::infra::HttpClient;
            "#
        }
        """
            + "\nuse a::"
            + "{".repeat(12_000)
            + "b"
            + "}".repeat(12_000)
            + ";\n");

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), "src", "");
    assertTrue(
        result.edges().stream().anyMatch(e -> e.to().equals("model.Money")),
        "Expected grouped pub(crate) use edge to model.Money, got: " + result.edges());
    assertTrue(
        result.edges().stream().anyMatch(e -> e.to().equals("model.currency.Currency")),
        "Expected nested grouped use edge to model.currency.Currency, got: " + result.edges());
    assertTrue(
        result.edges().stream().anyMatch(e -> e.to().equals("model.currency")),
        "Expected self grouped use edge to model.currency, got: " + result.edges());
    assertFalse(
        result.edges().stream()
            .anyMatch(e -> e.to().contains("PostgresRepo") || e.to().contains("HttpClient")),
        "Expected zero edges from block comments or raw strings, got: " + result.edges());
  }
}
