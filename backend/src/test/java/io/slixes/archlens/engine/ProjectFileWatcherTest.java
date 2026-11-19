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

    watcher.start(null, p -> {});
    watcher.start(tempDir.resolve("missing"), p -> {});
    assertFalse(watcher.isRunning());

    watcher.start(tempDir, p -> {});
    assertTrue(watcher.isRunning());

    watcher.stop();
    assertFalse(watcher.isRunning());

    watcher.start(tempDir, p -> {});
    assertTrue(watcher.isRunning());
    watcher.shutdown();
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

    Path trashDir = Files.createDirectories(tempDir.resolve(".archlens/trash/snap-1"));
    Files.writeString(trashDir.resolve("Trashed.java"), "class Trashed {}");

    boolean triggered = latch.await(300, TimeUnit.MILLISECONDS);
    var keysField = ProjectFileWatcher.class.getDeclaredField("watchKeys");
    keysField.setAccessible(true);
    @SuppressWarnings("unchecked")
    var watchedDirs = ((java.util.Map<?, Path>) keysField.get(watcher)).values();
    assertFalse(
        watchedDirs.stream().anyMatch(p -> p.toString().contains("trash")),
        ".archlens/trash must never be registered in WatchService");
    watcher.stop();

    assertFalse(
        triggered, "Watcher should ignore events in target/ and .archlens/trash/ directories");
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

  @Test
  @DisplayName(
      "start switches watched root when called with a new project directory and stays put on same root")
  void testSwitchesWatchedRoot(@TempDir Path tempDir) throws Exception {
    Path projectA = Files.createDirectories(tempDir.resolve("project-a"));
    Path projectB = Files.createDirectories(tempDir.resolve("project-b"));
    ProjectFileWatcher watcher = new ProjectFileWatcher(50);

    CountDownLatch latchB = new CountDownLatch(1);
    watcher.start(projectA, p -> {});
    Path firstRoot = watcher.getWatchedRoot();
    assertEquals(projectA.toRealPath(), firstRoot);

    watcher.start(projectA, p -> {});
    assertEquals(firstRoot, watcher.getWatchedRoot());

    watcher.start(projectB, p -> latchB.countDown());
    assertEquals(projectB.toRealPath(), watcher.getWatchedRoot());

    Files.writeString(projectB.resolve("SwitchedService.java"), "class SwitchedService {}");
    boolean triggeredB = latchB.await(5, TimeUnit.SECONDS);
    watcher.stop();
    assertNull(watcher.getWatchedRoot());

    assertTrue(triggeredB, "Expected watcher to switch from projectA to projectB");
  }

  @Test
  @DisplayName("stop cleans up WatchService and scheduler even if running was false")
  void testStopCleansUpPartiallyInitializedResources() throws Exception {
    ProjectFileWatcher watcher = new ProjectFileWatcher(50);
    var ws = java.nio.file.FileSystems.getDefault().newWatchService();
    var exec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

    var wsField = ProjectFileWatcher.class.getDeclaredField("watchService");
    wsField.setAccessible(true);
    wsField.set(watcher, ws);

    var execField = ProjectFileWatcher.class.getDeclaredField("debounceScheduler");
    execField.setAccessible(true);
    execField.set(watcher, exec);

    watcher.stop();
    assertTrue(
        exec.isShutdown(), "stop() must shut down debounceScheduler even when running==false");
    assertThrows(
        java.nio.file.ClosedWatchServiceException.class,
        ws::poll,
        "stop() must close watchService even when running==false");
  }
}
