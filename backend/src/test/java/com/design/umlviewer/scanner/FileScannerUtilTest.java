package com.design.umlviewer.scanner;

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
}
