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

class TypeScriptAstScannerTest {

  private final TypeScriptAstScanner scanner = new TypeScriptAstScanner();

  @Test
  void testLanguageMetadataAndSupports(@TempDir Path tempDir) throws IOException {
    assertEquals("typescript", scanner.languageId());
    assertFalse(scanner.supports(tempDir.toString(), null));

    Files.createFile(tempDir.resolve("tsconfig.json"));
    assertTrue(scanner.supports(tempDir.toString(), null));

    assertTrue(
        scanner.supports(
            tempDir.toString(),
            new ArchitecturePolicy(
                "TS",
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "typescript")));
  }

  @Test
  void testScanTypeScriptProject(@TempDir Path tempDir) throws IOException {
    Path src = tempDir.resolve("src");
    Path domain = src.resolve("domain");
    Path services = src.resolve("services");
    Files.createDirectories(domain);
    Files.createDirectories(services);

    // domain/order.ts
    Files.writeString(
        domain.resolve("order.ts"),
        """
        export interface OrderRepository {
          findById(id: string): Order;
        }

        export class Order {
          id: string;
        }
        """);

    // services/orderService.ts
    Files.writeString(
        services.resolve("orderService.ts"),
        """
        import { Order, OrderRepository } from '../domain/order';

        export class OrderService implements OrderRepository {
          findById(id: string): Order {
            return new Order();
          }
        }
        """);

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), "src", "");
    assertFalse(result.classes().isEmpty());

    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("Order")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderRepository")));
    assertTrue(result.classes().stream().anyMatch(c -> c.name().equals("OrderService")));

    // Implementation edge
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.IMPLEMENTS
                        && e.from().endsWith("OrderService")
                        && e.to().equals("OrderRepository")));

    // Dependency edge
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.kind() == DependencyEdge.Kind.DEPENDENCY
                        && e.from().equals("services.orderService")
                        && e.to().equals("domain.order")));
  }
}
