package com.design.umlviewer.scanner;

import static org.junit.jupiter.api.Assertions.*;

import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RustAstScannerTest {

  private final RustAstScanner scanner = new RustAstScanner();

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
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.IMPLEMENTS
                        && e.from().endsWith("PostgresRepo")
                        && e.to().equals("OrderRepository")));

    // Use edge
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.DEPENDENCY
                        && e.from().equals("adapters.postgres")
                        && e.to().equals("domain.OrderRepository")));
  }
}
