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

    // Verify RustLexicalScanner scans Cargo workspace members when root/src does not exist
    RustLexicalScanner rustScanner = new RustLexicalScanner();
    LanguageScanner.ScanResult rustResult = rustScanner.scanProject(tempDir.toString(), "src", "");
    assertTrue(rustResult.classes().stream().anyMatch(c -> c.name().equals("Invoice")));
    assertTrue(rustResult.classes().stream().anyMatch(c -> c.name().equals("InvoiceController")));
  }

  @Test
  void testCrossLanguageBoundaryTracingInTypeScriptLexicalScanner(@TempDir Path tempDir)
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
    Files.writeString(
        javaDir.resolve("FakeAdminResource.java"),
        """
        package com.acme.delivery;
        import jakarta.ws.rs.Path;
        @Path("/api/admin")
        public class FakeAdminResource {}
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
            const docQuoted = "fetch('/api/admin')";
            const docTemplate = `
              await fetch('/api/admin');
            `;
            const order = await fetch(`/api/orders/${orderId}`);
            const payment = await axios.post('/api/payments/charge', { orderId });
            return { order, payment, docQuoted, docTemplate };
          }
        }
        """);

    TypeScriptLexicalScanner tsScanner = new TypeScriptLexicalScanner();
    LanguageScanner.ScanResult scanResult =
        tsScanner.scanProject(tempDir.toString(), "frontend/src", "");

    assertTrue(
        scanResult.edges().stream()
            .anyMatch(e ->
                e.from().endsWith("CheckoutClient")
                    && e.to().equals("com.acme.delivery.OrderResource")
                    && "HTTP /api/orders".equals(e.label())));
    assertTrue(
        scanResult.edges().stream()
            .anyMatch(e ->
                e.from().endsWith("CheckoutClient")
                    && e.to().equals("services.payments.routes")
                    && "HTTP /api/payments/charge".equals(e.label())));
    assertFalse(
        scanResult.edges().stream().anyMatch(e -> e.to().contains("FakeAdminResource")),
        "Expected zero HTTP route edges from quoted or multiline-template fetch examples, got: "
            + scanResult.edges());
  }

  @Test
  void testScannersCountSkippedUnreadableOrMalformedFilesWithoutAbortingScan(@TempDir Path tempDir)
      throws IOException {
    byte[] invalidUtf8 = new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xC0, (byte) 0xAF};

    // 1. Java: 1 valid + 1 unparseable + 1 IOException via custom parser
    Path javaRoot = tempDir.resolve("java-proj");
    Path javaSrc = javaRoot.resolve("src/main/java/com/acme");
    Files.createDirectories(javaSrc);
    Files.writeString(javaSrc.resolve("Good.java"), "package com.acme; public class Good {}");
    Files.writeString(javaSrc.resolve("BadSyntax.java"), "package com.acme; public class {{{");

    JavaAstScanner javaScanner = new JavaAstScanner();
    LanguageScanner.ScanResult javaRes = javaScanner.scanProject(javaRoot.toString(), null, "");
    assertEquals(1, javaRes.classes().size());
    assertEquals(1, javaRes.skippedFiles());

    JavaAstScanner ioThrowingJavaScanner =
        new JavaAstScanner(
            (JavaAstScanner.JavaParserFunction)
                p -> {
                  throw new IOException("disk read error");
                });
    LanguageScanner.ScanResult javaAllFailed =
        ioThrowingJavaScanner.scanProject(javaRoot.toString(), null, "");
    assertEquals(0, javaAllFailed.classes().size());
    assertEquals(2, javaAllFailed.skippedFiles());

    // 2. Python: 1 valid + 1 invalid UTF-8 (IOException)
    Path pyRoot = tempDir.resolve("py-proj");
    Path pySrc = pyRoot.resolve("src/domain");
    Files.createDirectories(pySrc);
    Files.writeString(pyRoot.resolve("pyproject.toml"), "[project]\nname='demo'\n");
    Files.writeString(pySrc.resolve("good.py"), "class Order:\n    pass\n");
    Files.write(pySrc.resolve("corrupt.py"), invalidUtf8);

    PythonLexicalScanner pyScanner = new PythonLexicalScanner();
    LanguageScanner.ScanResult pyRes = pyScanner.scanProject(pyRoot.toString(), "src", "");
    assertEquals(1, pyRes.classes().size());
    assertEquals(1, pyRes.skippedFiles());

    // 3. TypeScript: 1 valid + 1 invalid UTF-8 (IOException) + corrupt tsconfig.json + corrupt
    // backend route
    Path tsRoot = tempDir.resolve("ts-proj");
    Path tsSrc = tsRoot.resolve("src/domain");
    Path tsBackend = tsRoot.resolve("backend");
    Files.createDirectories(tsSrc);
    Files.createDirectories(tsBackend);
    Files.write(tsRoot.resolve("tsconfig.json"), invalidUtf8);
    Files.writeString(
        tsSrc.resolve("good.ts"),
        "export class Invoice { load() { return fetch('/api/invoices'); } }\n");
    Files.write(tsSrc.resolve("corrupt.ts"), invalidUtf8);
    Files.write(tsBackend.resolve("BrokenRoute.java"), invalidUtf8);

    TypeScriptLexicalScanner tsScanner = new TypeScriptLexicalScanner();
    LanguageScanner.ScanResult tsRes = tsScanner.scanProject(tsRoot.toString(), "src", "");
    assertEquals(1, tsRes.classes().size());
    assertEquals(2, tsRes.skippedFiles());

    // 4. Go: 1 valid + 1 test file + 1 invalid UTF-8 (IOException) + corrupt go.mod
    Path goRoot = tempDir.resolve("go-proj");
    Path goSrc = goRoot.resolve("pkg/orders");
    Files.createDirectories(goSrc);
    Files.write(goRoot.resolve("go.mod"), invalidUtf8);
    Files.writeString(goSrc.resolve("good.go"), "package orders\ntype Order struct {\n}\n");
    Files.writeString(goSrc.resolve("good_test.go"), "package orders\n");
    Files.write(goSrc.resolve("corrupt.go"), invalidUtf8);

    GoLexicalScanner goScanner = new GoLexicalScanner();
    LanguageScanner.ScanResult goRes = goScanner.scanProject(goRoot.toString(), null, "");
    assertEquals(1, goRes.classes().size());
    assertEquals(1, goRes.skippedFiles());

    // 5. Rust: 1 valid + 1 invalid UTF-8 (IOException)
    Path rsRoot = tempDir.resolve("rs-proj");
    Path rsSrc = rsRoot.resolve("src/domain");
    Files.createDirectories(rsSrc);
    Files.writeString(rsRoot.resolve("Cargo.toml"), "[package]\nname = \"demo\"\n");
    Files.writeString(rsSrc.resolve("good.rs"), "pub struct Ledger;\n");
    Files.write(rsSrc.resolve("corrupt.rs"), invalidUtf8);

    RustLexicalScanner rsScanner = new RustLexicalScanner();
    LanguageScanner.ScanResult rsRes = rsScanner.scanProject(rsRoot.toString(), "src", "");
    assertEquals(1, rsRes.classes().size());
    assertEquals(1, rsRes.skippedFiles());

    // 6. Corrupt workspace manifests and backend route files log & count without throwing
    Path wsCorrupt = tempDir.resolve("ws-corrupt");
    Files.createDirectories(wsCorrupt.resolve("backend"));
    Files.write(wsCorrupt.resolve("settings.gradle.kts"), invalidUtf8);
    Files.write(wsCorrupt.resolve("Cargo.toml"), invalidUtf8);
    Files.write(wsCorrupt.resolve("pnpm-workspace.yaml"), invalidUtf8);
    Files.write(wsCorrupt.resolve("package.json"), invalidUtf8);
    Files.write(wsCorrupt.resolve("backend/BrokenResource.java"), invalidUtf8);

    assertTrue(FileScannerUtil.discoverWorkspaceModules(wsCorrupt).isEmpty());
    assertTrue(FileScannerUtil.indexBackendRestRoutes(wsCorrupt).isEmpty());
    java.util.concurrent.atomic.AtomicInteger routeSkips =
        new java.util.concurrent.atomic.AtomicInteger();
    assertTrue(FileScannerUtil.indexBackendRestRoutes(wsCorrupt, routeSkips).isEmpty());
    assertEquals(1, routeSkips.get());

    assertEquals(2, FileScannerUtil.findFiles(pySrc, 2, p -> p.toString().endsWith(".py")).size());
    Files.writeString(pySrc.resolve("test_order.py"), "def test_ok(): pass\n");
    Files.writeString(pySrc.resolve("NOTES.txt"), "not python\n");
    assertEquals(1, pyScanner.scanProject(pyRoot.toString(), "src", "").skippedFiles());
  }

  @Test
  void testGraphAndScanResultClampNegativeSkippedFilesAndHandleEmptyWorkspaces(
      @TempDir Path tempDir) throws IOException {
    assertEquals(
        0,
        new io.slixes.archlens.domain.graph.ArchitectureGraph(
                "T", false, null, List.of(), List.of(), List.of(), List.of(), null, -5)
            .skippedFiles());
    assertEquals(0, new LanguageScanner.ScanResult(List.of(), List.of(), -3).skippedFiles());

    Path emptyWs = tempDir.resolve("empty-ws");
    Files.createDirectories(emptyWs.resolve("src/main/java"));
    Files.createDirectories(emptyWs.resolve("packages/web"));
    Files.createDirectories(emptyWs.resolve("crates/core"));
    Files.writeString(emptyWs.resolve("pnpm-workspace.yaml"), "packages:\n  - 'packages/*'\n");
    Files.writeString(emptyWs.resolve("Cargo.toml"), "[workspace]\nmembers = [\"crates/*\"]\n");

    assertEquals(0, new JavaAstScanner().scanProject(emptyWs.toString(), null, "").skippedFiles());
    assertEquals(
        0,
        new TypeScriptLexicalScanner()
            .scanProject(emptyWs.toString(), "missing-src", "")
            .skippedFiles());
    assertEquals(
        0,
        new RustLexicalScanner().scanProject(emptyWs.toString(), "missing-src", "").skippedFiles());

    Path twoJavaRoot = tempDir.resolve("two-java");
    Path twoJavaSrc = twoJavaRoot.resolve("src/main/java/com/acme");
    Files.createDirectories(twoJavaSrc);
    Files.writeString(twoJavaSrc.resolve("A.java"), "package com.acme; class A {}");
    Files.writeString(twoJavaSrc.resolve("B.java"), "package com.acme; class B {}");
    assertEquals(
        2,
        new JavaAstScanner((JavaAstScanner.JavaParserFunction) null)
            .scanProject(twoJavaRoot.toString(), null, "")
            .skippedFiles());
    assertEquals(
        2,
        new JavaAstScanner((JavaAstScanner.JavaParserFunction) p -> null)
            .scanProject(twoJavaRoot.toString(), null, "")
            .skippedFiles());
  }

  @Test
  void testPosixUnreadableDirectoryWalkLogsAndCountsSkips(@TempDir Path tempDir)
      throws IOException {
    Path lockedWs = tempDir.resolve("locked-ws");
    Path lockedSub = lockedWs.resolve("packages/locked");
    Files.createDirectories(lockedSub);
    Files.writeString(lockedWs.resolve("pnpm-workspace.yaml"), "packages:\n  - 'packages/*'\n");
    Path packagesParent = lockedWs.resolve("packages");
    try {
      java.util.Set<java.nio.file.attribute.PosixFilePermission> noPerms = java.util.Set.of();
      Files.setPosixFilePermissions(lockedSub, noPerms);
      java.util.concurrent.atomic.AtomicInteger walkSkips =
          new java.util.concurrent.atomic.AtomicInteger();
      assertTrue(
          FileScannerUtil.findFiles(lockedWs, 4, p -> p.toString().endsWith(".java")).isEmpty());
      assertEquals(1, FileScannerUtil.findFiles(lockedWs, 4, null, walkSkips).size());
      assertTrue(
          FileScannerUtil.findFiles(lockedWs, 4, p -> p.toString().endsWith(".java"), walkSkips)
              .isEmpty());
      assertTrue(FileScannerUtil.findDirectories(lockedWs, 4, p -> false).isEmpty());
      if (!Files.isReadable(lockedSub)) {
        assertTrue(walkSkips.get() >= 2);
      }

      Files.setPosixFilePermissions(
          lockedSub, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
      Files.setPosixFilePermissions(packagesParent, noPerms);
      if (!Files.isReadable(packagesParent)) {
        assertTrue(FileScannerUtil.discoverWorkspaceModules(lockedWs).isEmpty());
      }
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX filesystem
    } finally {
      try {
        Files.setPosixFilePermissions(
            packagesParent, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        Files.setPosixFilePermissions(
            lockedSub, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
      } catch (Exception ignored) {
      }
    }
  }

  @Test
  void testStripCommentsAndRawStringsAndInsideStringLiteral() {
    assertTrue(FileScannerUtil.stripCommentsAndRawStrings(null, false).isEmpty());
    assertTrue(FileScannerUtil.stripCommentsAndRawStrings("", true).isEmpty());

    String rustCode =
        """
        /* outer /* nested use crate::bad::Inner; */ still comment */
        // line comment use crate::bad::Line;
        pub fn demo<'a>(input: &'a str) -> char {
            let q = '"';
            let esc = '\\'';
            let s = "escaped \\" quote\nand use crate::bad::Quoted;";
            let bytes = br##"use crate::bad::RawBytes; "# still raw"##;
            let unclosed = r#"tail
        """;
    List<String> rustLines = FileScannerUtil.stripCommentsAndRawStrings(rustCode, true);
    assertEquals(rustCode.split("\\R", -1).length, rustLines.size());
    assertFalse(String.join("\n", rustLines).contains("crate::bad"));
    assertTrue(String.join("\n", rustLines).contains("pub fn demo<'a>"));

    String goCode =
        """
        package domain // comment
        import (
            "github.com/acme/real" // "github.com/acme/commented"
        )
        const ch = '\\''
        const msg = "escaped \\" text // not comment"
        const raw = `
        import "github.com/acme/fake"
        `
        """;
    List<String> goLines = FileScannerUtil.stripCommentsAndRawStrings(goCode, false);
    assertEquals(10, goLines.size());
    String joinedGo = String.join("\n", goLines);
    assertTrue(joinedGo.contains("\"github.com/acme/real\""));
    assertFalse(joinedGo.contains("github.com/acme/commented"));
    assertFalse(joinedGo.contains("github.com/acme/fake"));

    assertFalse(FileScannerUtil.isInsideStringLiteral(null, 5));
    assertFalse(FileScannerUtil.isInsideStringLiteral("import('a')", 0));
    assertTrue(FileScannerUtil.isInsideStringLiteral("const s = \"a\\\"b require('x')\"", 16));
    assertFalse(
        FileScannerUtil.isInsideStringLiteral("const s = 'ok'; const m = require('x')", 26));
    assertTrue(FileScannerUtil.isInsideStringLiteral("const t = `import('x')`", 11));
  }
}
