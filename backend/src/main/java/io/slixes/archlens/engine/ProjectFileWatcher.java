package io.slixes.archlens.engine;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jboss.logging.Logger;

/**
 * Background file system watcher (Level 1 Engine) that recursively monitors project source code and
 * architecture policies using Java NIO {@link WatchService}, debouncing rapid modification bursts
 * before emitting change notifications.
 */
@ApplicationScoped
public class ProjectFileWatcher {

  private static final Logger LOG = Logger.getLogger(ProjectFileWatcher.class);

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
          "snapshots",
          "trash");

  private static final Set<String> WATCHED_EXTENSIONS =
      Set.of(".java", ".ts", ".tsx", ".js", ".jsx", ".py", ".go", ".rs", ".svelte", ".json");

  private final long debounceMs;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();

  private volatile WatchService watchService;
  private volatile Path watchedRoot;
  private volatile Consumer<Path> activeCallback;
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

  public Path getWatchedRoot() {
    return watchedRoot;
  }

  public synchronized void switchRootIfRunning(Path newRoot) {
    Consumer<Path> callback = this.activeCallback;
    if (running.get() && callback != null) {
      start(newRoot, callback);
    }
  }

  public synchronized void start(Path rootPath, Consumer<Path> onChange) {
    if (rootPath == null || !Files.exists(rootPath)) {
      return;
    }

    Path normalizedRoot = normalizeRoot(rootPath);
    if (running.get() && normalizedRoot.equals(watchedRoot)) {
      this.activeCallback = onChange;
      return;
    }
    if (running.get()) {
      stop();
    }

    try {
      WatchService activeService = FileSystems.getDefault().newWatchService();
      this.watchService = activeService;
      this.debounceScheduler =
          Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "archlens-watcher-debounce");
            t.setDaemon(true);
            return t;
          });

      registerRecursive(normalizedRoot, activeService);
      this.watchedRoot = normalizedRoot;
      this.activeCallback = onChange;
      running.set(true);

      this.watcherThread =
          new Thread(() -> processEvents(activeService, onChange), "archlens-file-watcher");
      this.watcherThread.setDaemon(true);
      this.watcherThread.start();
    } catch (IOException e) {
      stop();
    }
  }

  private static Path normalizeRoot(Path rootPath) {
    try {
      return rootPath.toRealPath();
    } catch (IOException e) {
      return rootPath.toAbsolutePath().normalize();
    }
  }

  @PreDestroy
  public void shutdown() {
    stop();
  }

  public synchronized void stop() {
    this.watchedRoot = null;
    this.activeCallback = null;
    boolean wasRunning = running.getAndSet(false);

    if (debounceScheduler != null) {
      debounceScheduler.shutdownNow();
      debounceScheduler = null;
    }

    if (watchService != null) {
      try {
        watchService.close();
      } catch (IOException e) {
        LOG.debugf(e, "Error closing WatchService");
      }
      watchService = null;
    }

    if (wasRunning && watcherThread != null && watcherThread != Thread.currentThread()) {
      watcherThread.interrupt();
      try {
        watcherThread.join(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    watcherThread = null;
    watchKeys.clear();
  }

  private void registerRecursive(Path root, WatchService activeService) throws IOException {
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            Path fileName = dir.getFileName();
            if (fileName == null) {
              return FileVisitResult.CONTINUE;
            }
            String dirName = fileName.toString();
            if (EXCLUDED_DIRS.contains(dirName)
                || (dirName.startsWith(".") && !dirName.equals(".archlens"))) {
              return FileVisitResult.SKIP_SUBTREE;
            }

            WatchKey key =
                dir.register(
                    activeService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(key, dir);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            LOG.debugf("Watcher skipping unreadable path %s: %s", file, exc.getMessage());
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private void processEvents(WatchService activeService, Consumer<Path> onChange) {
    while (running.get() && this.watchService == activeService) {
      WatchKey key;
      try {
        key = activeService.poll(100, TimeUnit.MILLISECONDS);
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
              registerRecursive(child, activeService);
            }
          } catch (IOException e) {
            LOG.debugf("Watcher skipping transient directory %s: %s", child, e.getMessage());
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
              } catch (Exception e) {
                LOG.debugf(e, "Error processing change for path: %s", path);
              }
            },
            debounceMs,
            TimeUnit.MILLISECONDS);
  }

  public static boolean isWatchedFile(Path path) {
    if (path == null) {
      return false;
    }
    Path fileName = path.getFileName();
    if (fileName == null) {
      return false;
    }
    String name = fileName.toString();
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
