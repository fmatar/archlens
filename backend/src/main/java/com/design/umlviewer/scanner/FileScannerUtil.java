package com.design.umlviewer.scanner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility class providing hardened, fault-tolerant filesystem traversal.
 *
 * <p>Prunes hidden, metadata, and dependency directories before entry, and gracefully continues
 * past unreadable special files (e.g. Unix domain sockets like .git/fsmonitor--daemon.ipc, broken
 * symlinks, or transient lock files).
 */
public final class FileScannerUtil {

  private static final Set<String> DEFAULT_IGNORED_DIRS =
      Set.of(
          "target",
          "build",
          "node_modules",
          "dist",
          "out",
          "bin",
          "obj",
          "vendor",
          "venv",
          ".venv",
          "__pycache__",
          ".git",
          ".svn",
          ".hg",
          ".archlens",
          ".uml-viewer",
          ".idea",
          ".vscode",
          ".cache");

  private FileScannerUtil() {}

  /**
   * Determines whether the given directory should be ignored during AST/source discovery.
   *
   * @param dir the path of the directory to inspect
   * @return true if the directory is hidden or a known build/dependency artifact folder
   */
  public static boolean isIgnoredDirectory(Path dir) {
    if (dir == null) {
      return true;
    }
    Path fileName = dir.getFileName();
    if (fileName == null) {
      return false;
    }
    String name = fileName.toString();
    if (name.startsWith(".")) {
      return true;
    }
    return DEFAULT_IGNORED_DIRS.contains(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Finds matching regular files up to {@code maxDepth}, pruning ignored directory subtrees.
   *
   * @param startDir the root directory to search from
   * @param maxDepth maximum directory depth to traverse
   * @param filePredicate predicate matching desired files
   * @return list of matching paths
   */
  public static List<Path> findFiles(Path startDir, int maxDepth, Predicate<Path> filePredicate) {
    if (startDir == null || !Files.exists(startDir) || !Files.isDirectory(startDir)) {
      return List.of();
    }
    List<Path> matchedFiles = new ArrayList<>();
    try {
      Files.walkFileTree(
          startDir,
          Collections.emptySet(),
          Math.max(1, maxDepth),
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (!dir.equals(startDir) && isIgnoredDirectory(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (attrs.isRegularFile() && (filePredicate == null || filePredicate.test(file))) {
                matchedFiles.add(file);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              // Gracefully skip unreadable files, sockets, or broken symlinks
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException | SecurityException e) {
      return matchedFiles;
    }
    return matchedFiles;
  }

  /**
   * Finds matching regular files with unbounded depth, pruning ignored directory subtrees.
   *
   * @param startDir the root directory to search from
   * @param filePredicate predicate matching desired files
   * @return list of matching paths
   */
  public static List<Path> findFiles(Path startDir, Predicate<Path> filePredicate) {
    return findFiles(startDir, Integer.MAX_VALUE, filePredicate);
  }

  /**
   * Finds matching directories up to {@code maxDepth}, pruning ignored directory subtrees.
   *
   * @param startDir the root directory to search from
   * @param maxDepth maximum directory depth to traverse
   * @param dirPredicate predicate matching desired directories
   * @return list of matching directory paths
   */
  public static List<Path> findDirectories(
      Path startDir, int maxDepth, Predicate<Path> dirPredicate) {
    if (startDir == null || !Files.exists(startDir) || !Files.isDirectory(startDir)) {
      return List.of();
    }
    List<Path> matchedDirs = new ArrayList<>();
    try {
      Files.walkFileTree(
          startDir,
          Collections.emptySet(),
          Math.max(1, maxDepth),
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (!dir.equals(startDir)) {
                if (isIgnoredDirectory(dir)) {
                  return FileVisitResult.SKIP_SUBTREE;
                }
                if (dirPredicate != null && dirPredicate.test(dir)) {
                  matchedDirs.add(dir);
                  return FileVisitResult.SKIP_SUBTREE;
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException | SecurityException e) {
      return matchedDirs;
    }
    return matchedDirs;
  }
}
