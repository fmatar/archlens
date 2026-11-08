package io.slixes.archlens.scanner;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileScannerUtilTest {

  @Test
  void testIsIgnoredDirectory() {
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of(".git")));
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of(".archlens")));
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of(".uml-viewer")));
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of("target")));
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of("build")));
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of("node_modules")));
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of(".venv")));
    assertTrue(FileScannerUtil.isIgnoredDirectory(Path.of("__pycache__")));

    assertFalse(FileScannerUtil.isIgnoredDirectory(Path.of("src")));
    assertFalse(FileScannerUtil.isIgnoredDirectory(Path.of("app")));
    assertFalse(FileScannerUtil.isIgnoredDirectory(Path.of("main")));
    assertFalse(FileScannerUtil.isIgnoredDirectory(Path.of("java")));
  }

  @Test
  void testFindFilesIgnoresHiddenAndBuildDirectories(@TempDir Path tempDir) throws IOException {
    // 1. Create valid source file
    Path srcDir = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcDir);
    Path validJava = srcDir.resolve("App.java");
    Files.writeString(validJava, "public class App {}");

    // 2. Create ignored build and git directories
    Path gitDir = tempDir.resolve(".git");
    Files.createDirectories(gitDir);
    Path targetDir = tempDir.resolve("target");
    Files.createDirectories(targetDir);
    Files.writeString(targetDir.resolve("App.class"), "bytecode");

    // 3. Attempt creating a Unix domain socket inside .git to simulate fsmonitor--daemon.ipc
    Path socketPath = gitDir.resolve("fsmonitor--daemon.ipc");
    ServerSocketChannel serverChannel = null;
    try {
      UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
      serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      serverChannel.bind(address);
    } catch (Throwable ignored) {
      // Fallback: create regular file if unix domain sockets are unsupported on OS
      if (!Files.exists(socketPath)) {
        Files.writeString(socketPath, "socket-placeholder");
      }
    }

    try {
      List<Path> found = FileScannerUtil.findFiles(tempDir, p -> p.toString().endsWith(".java"));
      assertEquals(1, found.size());
      assertEquals(validJava, found.get(0));
    } finally {
      if (serverChannel != null) {
        try {
          serverChannel.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  @Test
  void testFindDirectoriesPruning(@TempDir Path tempDir) throws IOException {
    // Create multi-module layout
    Path modA = tempDir.resolve("core/src/main/java");
    Path modB = tempDir.resolve("api/src/main/java");
    Path gitDir = tempDir.resolve(".git/nested");
    Path targetDir = tempDir.resolve("target/generated-sources");

    Files.createDirectories(modA);
    Files.createDirectories(modB);
    Files.createDirectories(gitDir);
    Files.createDirectories(targetDir);

    List<Path> dirs =
        FileScannerUtil.findDirectories(
            tempDir, 6, p -> p.endsWith(Path.of("src", "main", "java")));

    assertEquals(2, dirs.size());
    assertTrue(dirs.contains(modA));
    assertTrue(dirs.contains(modB));
  }

  @Test
  void testDiscoverWorkspaceModulesAcrossGradleCargoPnpmAndNpm(@TempDir Path tempDir)
      throws IOException {
    // 1. Gradle settings.gradle.kts
    Path gradleModA = tempDir.resolve("services/orders");
    Path gradleModB = tempDir.resolve("services/billing");
    Files.createDirectories(gradleModA);
    Files.createDirectories(gradleModB);
    Files.writeString(
        tempDir.resolve("settings.gradle.kts"),
        """
        rootProject.name = "enterprise-monorepo"
        include(":services:orders", ":services:billing")
        """);

    // 2. Cargo.toml [workspace] with glob
    Path cargoCrateA = tempDir.resolve("crates/domain-core/src");
    Path cargoCrateB = tempDir.resolve("crates/http-adapter/src");
    Files.createDirectories(cargoCrateA);
    Files.createDirectories(cargoCrateB);
    Files.writeString(
        tempDir.resolve("Cargo.toml"),
        """
        [workspace]
        members = ["crates/*"]
        """);
    Files.writeString(
        cargoCrateA.resolve("lib.rs"),
        """
        pub struct Invoice {
            pub id: String,
        }
        """);
    Files.writeString(
        cargoCrateB.resolve("lib.rs"),
        """
        pub struct InvoiceController;
        """);

    // 3. pnpm-workspace.yaml & package.json workspaces
    Path webApp = tempDir.resolve("apps/portal");
    Path uiPkg = tempDir.resolve("packages/ui-kit");
    Files.createDirectories(webApp);
    Files.createDirectories(uiPkg);
    Files.writeString(
        tempDir.resolve("pnpm-workspace.yaml"),
        """
        packages:
          - 'apps/*'
        """);
    Files.writeString(
        tempDir.resolve("package.json"),
        """
        {
          "name": "root",
          "workspaces": ["packages/*"]
        }
        """);

    List<Path> modules = FileScannerUtil.discoverWorkspaceModules(tempDir);
    assertTrue(modules.contains(gradleModA));
    assertTrue(modules.contains(gradleModB));
    assertTrue(modules.contains(tempDir.resolve("crates/domain-core")));
    assertTrue(modules.contains(tempDir.resolve("crates/http-adapter")));
    assertTrue(modules.contains(webApp));
    assertTrue(modules.contains(uiPkg));

    // Verify RustAstScanner scans Cargo workspace members when root/src does not exist
    RustAstScanner rustScanner = new RustAstScanner();
    LanguageScanner.ScanResult rustResult = rustScanner.scanProject(tempDir.toString(), "src", "");
    assertTrue(rustResult.classes().stream().anyMatch(c -> c.name().equals("Invoice")));
    assertTrue(rustResult.classes().stream().anyMatch(c -> c.name().equals("InvoiceController")));
  }

  @Test
  void testCrossLanguageBoundaryTracingInTypeScriptAstScanner(@TempDir Path tempDir)
      throws IOException {
    // 1. Backend Java JAX-RS resource
    Path javaDir = tempDir.resolve("backend/src/main/java/com/acme/delivery");
    Files.createDirectories(javaDir);
    Files.writeString(
        javaDir.resolve("OrderResource.java"),
        """
        package com.acme.delivery;
        import jakarta.ws.rs.Path;
        @Path("/api/orders")
        public class OrderResource {}
        """);

    // 2. Backend Python FastAPI router
    Path pyDir = tempDir.resolve("services/payments");
    Files.createDirectories(pyDir);
    Files.writeString(
        pyDir.resolve("routes.py"),
        """
        @app.post("/api/payments/charge")
        def charge_payment():
            pass
        """);

    // 3. Frontend TypeScript client calling both backend routes
    Path tsDir = tempDir.resolve("frontend/src/api");
    Files.createDirectories(tsDir);
    Files.writeString(
        tsDir.resolve("checkoutClient.ts"),
        """
        export class CheckoutClient {
          async checkout(orderId: string) {
            const order = await fetch(`/api/orders/${orderId}`);
            const payment = await axios.post('/api/payments/charge', { orderId });
            return { order, payment };
          }
        }
        """);

    TypeScriptAstScanner tsScanner = new TypeScriptAstScanner();
    LanguageScanner.ScanResult scanResult =
        tsScanner.scanProject(tempDir.toString(), "frontend/src", "");

    assertTrue(
        scanResult.edges().stream()
            .anyMatch(
                e ->
                    e.from().endsWith("CheckoutClient")
                        && e.to().equals("com.acme.delivery.OrderResource")
                        && "HTTP /api/orders".equals(e.label())));
    assertTrue(
        scanResult.edges().stream()
            .anyMatch(
                e ->
                    e.from().endsWith("CheckoutClient")
                        && e.to().equals("services.payments.routes")
                        && "HTTP /api/payments/charge".equals(e.label())));
  }
}
