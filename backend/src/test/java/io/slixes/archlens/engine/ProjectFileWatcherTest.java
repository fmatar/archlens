package io.slixes.archlens.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFileWatcherTest {

  @Test
  @DisplayName("start and stop manages watcher lifecycle cleanly")
  void testLifecycle(@TempDir Path tempDir) {
    ProjectFileWatcher watcher = new ProjectFileWatcher();
    assertFalse(watcher.isRunning());

    watcher.start(tempDir, p -> {});
    assertTrue(watcher.isRunning());

    watcher.stop();
    assertFalse(watcher.isRunning());
  }

  @Test
  @DisplayName("watcher detects Java source file changes and invokes callback")
  void testDetectsSourceChanges(@TempDir Path tempDir) throws Exception {
    ProjectFileWatcher watcher = new ProjectFileWatcher(50); // fast 50ms debounce for tests
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean changed = new AtomicBoolean(false);

    watcher.start(
        tempDir,
        p -> {
          changed.set(true);
          latch.countDown();
        });

    // Create a Java source file
    Path srcFile = tempDir.resolve("TestService.java");
    Files.writeString(srcFile, "public class TestService {}");

    boolean triggered = latch.await(5, TimeUnit.SECONDS);
    watcher.stop();

    assertTrue(triggered, "Expected file watcher to detect Java source file creation");
    assertTrue(changed.get());
  }

  @Test
  @DisplayName("watcher ignores files in target or node_modules directories")
  void testIgnoresExcludedDirectories(@TempDir Path tempDir) throws Exception {
    ProjectFileWatcher watcher = new ProjectFileWatcher(50);
    CountDownLatch latch = new CountDownLatch(1);

    watcher.start(
        tempDir,
        p -> {
          latch.countDown();
        });

    // Create target dir and modify file inside
    Path targetDir = tempDir.resolve("target");
    Files.createDirectories(targetDir);
    Path targetFile = targetDir.resolve("Ignored.java");
    Files.writeString(targetFile, "class Ignored {}");

    boolean triggered = latch.await(300, TimeUnit.MILLISECONDS);
    watcher.stop();

    assertFalse(triggered, "Watcher should ignore events in target/ directory");
  }

  @Test
  @DisplayName("isWatchedExtension identifies source languages and policy")
  void testWatchedExtensions() {
    assertTrue(ProjectFileWatcher.isWatchedFile(Path.of("Service.java")));
    assertTrue(ProjectFileWatcher.isWatchedFile(Path.of("index.ts")));
    assertTrue(ProjectFileWatcher.isWatchedFile(Path.of("app.py")));
    assertTrue(ProjectFileWatcher.isWatchedFile(Path.of("main.go")));
    assertTrue(ProjectFileWatcher.isWatchedFile(Path.of("lib.rs")));
    assertTrue(ProjectFileWatcher.isWatchedFile(Path.of("core.clj")));
    assertTrue(ProjectFileWatcher.isWatchedFile(Path.of("policy.json")));
    assertFalse(ProjectFileWatcher.isWatchedFile(Path.of("image.png")));
    assertFalse(ProjectFileWatcher.isWatchedFile(Path.of("binary.class")));
  }
}
