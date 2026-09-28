package com.design.umlviewer.engine;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Background file system watcher (Level 1 Engine) that recursively monitors project source code and
 * architecture policies using Java NIO {@link WatchService}, debouncing rapid modification bursts
 * before emitting change notifications.
 */
@ApplicationScoped
public class ProjectFileWatcher {

  private static final Set<String> EXCLUDED_DIRS =
      Set.of(
          ".git",
          "target",
          "node_modules",
          "dist",
          "build",
          "bin",
          ".idea",
          ".vscode",
          "cache",
          "snapshots");

  private static final Set<String> WATCHED_EXTENSIONS =
      Set.of(".java", ".ts", ".tsx", ".js", ".jsx", ".py", ".go", ".rs", ".clj", ".cljs", ".json");

  private final long debounceMs;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();

  private WatchService watchService;
  private Thread watcherThread;
  private ScheduledExecutorService debounceScheduler;
  private ScheduledFuture<?> pendingDebounceTask;

  public ProjectFileWatcher() {
    this(250);
  }

  public ProjectFileWatcher(long debounceMs) {
    this.debounceMs = debounceMs;
  }

  public boolean isRunning() {
    return running.get();
  }

  public synchronized void start(Path rootPath, Consumer<Path> onChange) {
    if (running.get() || rootPath == null || !Files.exists(rootPath)) {
      return;
    }

    try {
      this.watchService = FileSystems.getDefault().newWatchService();
      this.debounceScheduler =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                Thread t = new Thread(r, "archlens-watcher-debounce");
                t.setDaemon(true);
                return t;
              });

      registerRecursive(rootPath);
      running.set(true);

      this.watcherThread = new Thread(() -> processEvents(onChange), "archlens-file-watcher");
      this.watcherThread.setDaemon(true);
      this.watcherThread.start();
    } catch (IOException e) {
      stop();
    }
  }

  public synchronized void stop() {
    if (!running.getAndSet(false)) {
      return;
    }

    if (debounceScheduler != null) {
      debounceScheduler.shutdownNow();
    }

    if (watchService != null) {
      try {
        watchService.close();
      } catch (IOException ignored) {
      }
    }

    if (watcherThread != null) {
      watcherThread.interrupt();
    }

    watchKeys.clear();
  }

  private void registerRecursive(Path root) throws IOException {
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
            if (EXCLUDED_DIRS.contains(dirName)
                || (dirName.startsWith(".")
                    && !dirName.equals(".archlens")
                    && !dirName.equals(".uml-viewer"))) {
              return FileVisitResult.SKIP_SUBTREE;
            }

            WatchKey key =
                dir.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(key, dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private void processEvents(Consumer<Path> onChange) {
    while (running.get()) {
      WatchKey key;
      try {
        key = watchService.poll(100, TimeUnit.MILLISECONDS);
      } catch (InterruptedException | ClosedWatchServiceException e) {
        break;
      }

      if (key == null) {
        continue;
      }

      Path dir = watchKeys.get(key);
      if (dir == null) {
        key.cancel();
        continue;
      }

      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == StandardWatchEventKinds.OVERFLOW) {
          continue;
        }

        @SuppressWarnings("unchecked")
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path filename = ev.context();
        Path child = dir.resolve(filename);

        // If a new directory was created, register it
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
          try {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
              registerRecursive(child);
            }
          } catch (IOException ignored) {
          }
        }

        if (isWatchedFile(child)) {
          triggerDebouncedChange(child, onChange);
        }
      }

      boolean valid = key.reset();
      if (!valid) {
        watchKeys.remove(key);
        if (watchKeys.isEmpty()) {
          break;
        }
      }
    }
  }

  private synchronized void triggerDebouncedChange(Path path, Consumer<Path> onChange) {
    if (!running.get() || debounceScheduler == null || debounceScheduler.isShutdown()) {
      return;
    }

    if (pendingDebounceTask != null && !pendingDebounceTask.isDone()) {
      pendingDebounceTask.cancel(false);
    }

    pendingDebounceTask =
        debounceScheduler.schedule(
            () -> {
              try {
                onChange.accept(path);
              } catch (Exception ignored) {
              }
            },
            debounceMs,
            TimeUnit.MILLISECONDS);
  }

  public static boolean isWatchedFile(Path path) {
    if (path == null) {
      return false;
    }
    String name = path.getFileName() != null ? path.getFileName().toString() : "";
    if (name.equals("policy.json")) {
      return true;
    }
    for (String ext : WATCHED_EXTENSIONS) {
      if (name.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }
}
