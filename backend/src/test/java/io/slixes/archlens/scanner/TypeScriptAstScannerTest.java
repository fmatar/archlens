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

  @Test
  void testTypeScriptOmitAndImportResolution(@TempDir Path tempDir) throws IOException {
    Path src = tempDir.resolve("src");
    Path components = src.resolve("components");
    Path ignoredTests = src.resolve("tests");
    Files.createDirectories(components);
    Files.createDirectories(ignoredTests);

    Files.writeString(
        components.resolve("button.tsx"),
        """
        import { Util } from '@/utils/helper';
        import { Theme } from '~/styles/theme';

        export class Button {
          label: string;
        }
        """);

    Files.writeString(
        ignoredTests.resolve("button.test.ts"),
        """
        export class ButtonTest {}
        """);

    ArchitecturePolicy policy =
        new ArchitecturePolicy(
            "TS App",
            "src",
            "app",
            true,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("tests"));

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), "src", "", policy);
    assertEquals(1, result.classes().size());
    assertEquals("Button", result.classes().get(0).name());
    assertTrue(result.edges().stream().anyMatch(e -> e.to().equals("utils.helper")));
    assertTrue(result.edges().stream().anyMatch(e -> e.to().equals("styles.theme")));
  }

  @Test
  void testSvelteComponentAndImportScanning(@TempDir Path tempDir) throws IOException {
    Path src = tempDir.resolve("src");
    Path lib = src.resolve("lib");
    Path components = lib.resolve("components");
    Path services = lib.resolve("services");
    Path stores = lib.resolve("stores");
    Files.createDirectories(components);
    Files.createDirectories(services);
    Files.createDirectories(stores);

    Files.writeString(
        services.resolve("OrderService.ts"),
        """
        export class OrderService {
          processOrder() {}
        }
        """);

    Files.writeString(
        stores.resolve("orderStore.svelte.ts"),
        """
        export const orderStore = {};
        """);

    Files.writeString(
        components.resolve("OrderCard.svelte"),
        """
        <script lang="ts">
          import { OrderService } from '#lib/services/OrderService';
          import { orderStore } from '#lib/stores/orderStore.svelte';
        </script>
        <div>Order</div>
        """);

    ArchitecturePolicy policy =
        new ArchitecturePolicy(
            "Svelte App", "src", "", true, List.of(), List.of(), List.of(), List.of(), List.of());

    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), "src", "", policy);
    assertEquals(3, result.classes().size());
    assertTrue(result.classes().stream().anyMatch(c -> c.id().equals("lib.components.OrderCard")));
    assertTrue(
        result.classes().stream()
            .anyMatch(c -> c.id().equals("lib.services.OrderService.OrderService")));
    assertTrue(result.classes().stream().anyMatch(c -> c.id().equals("lib.stores.orderStore")));

    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.from().equals("lib.components.OrderCard")
                        && e.to().equals("lib.services.OrderService")));
    assertTrue(
        result.edges().stream()
            .anyMatch(
                e ->
                    e.from().equals("lib.components.OrderCard")
                        && e.to().equals("lib.stores.orderStore.svelte")));
  }

  @Test
  void testTypeScriptMonorepoWorkspaceResolution(@TempDir Path tempDir) throws IOException {
    // Scaffold Turborepo / pnpm workspace: apps/web/src and packages/ui/src
    Path webSrc = tempDir.resolve("apps/web/src/routes");
    Path uiSrc = tempDir.resolve("packages/ui/src/components");
    Files.createDirectories(webSrc);
    Files.createDirectories(uiSrc);

    Files.writeString(
        uiSrc.resolve("Button.svelte"),
        """
        <script lang="ts">
          export let label: string;
        </script>
        <button>{label}</button>
        """);

    Files.writeString(
        webSrc.resolve("page.svelte"),
        """
        <script lang="ts">
          import Button from '@workspace/ui/components/Button.svelte';
        </script>
        <Button label="Submit" />
        """);

    // Scan from monorepo root
    LanguageScanner.ScanResult result = scanner.scanProject(tempDir.toString(), null, "");
    assertEquals(2, result.classes().size());
    assertTrue(result.classes().stream().anyMatch(c -> c.id().contains("Button")));
    assertTrue(result.classes().stream().anyMatch(c -> c.id().contains("page")));

    // Edge from web route to ui component
    assertTrue(
        result.edges().stream()
            .anyMatch(e -> e.from().contains("page") && e.to().contains("ui.components.Button")));
  }
}
