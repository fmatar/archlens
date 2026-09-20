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

class GoAstScannerTest {

  private final GoAstScanner scanner = new GoAstScanner();

  @Test
  void testLanguageMetadataAndSupports(@TempDir Path tempDir) throws IOException {
    assertEquals("go", scanner.languageId());
    assertFalse(scanner.supports(tempDir.toString(), null));

    Files.createFile(tempDir.resolve("go.mod"));
    assertTrue(scanner.supports(tempDir.toString(), null));

    assertTrue(
        scanner.supports(
            tempDir.toString(),
            new ArchitecturePolicy(
                "Go", null, null, false, List.of(), List.of(), List.of(), List.of(), List.of(),
                "go")));
  }

  @Test
  void testScanGoProject(@TempDir Path tempDir) throws IOException {
    // Write go.mod
    Files.writeString(
        tempDir.resolve("go.mod"), "module github.com/example/myproject\n\ngo 1.22\n");

    Path domain = tempDir.resolve("internal/domain");
    Path adapters = tempDir.resolve("internal/adapters");
    Files.createDirectories(domain);
    Files.createDirectories(adapters);

    // internal/domain/order.go
    Files.writeString(
        domain.resolve("order.go"),
        """
        package domain

        type OrderRepository interface {
            Save(order *Order) error
        }

        type Order struct {
            ID string
        }
        """);

    // internal/adapters/handler.go
    Files.writeString(
        adapters.resolve("handler.go"),
        """
        package adapters

        import (
            "fmt"
            "github.com/example/myproject/internal/domain"
        )

        type OrderHandler struct {
            Repo domain.OrderRepository
        }

        func (h *OrderHandler) Handle() {
            fmt.Println("handling")
        }
        """);

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), ".", "");
    assertFalse(result.classes().isEmpty());

    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("Order")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderRepository")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderHandler")));

    // Dependency edge
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.DEPENDENCY
                        && e.from().equals("internal.adapters")
                        && e.to().equals("internal.domain")));
  }
}
