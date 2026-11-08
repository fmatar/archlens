package io.slixes.archlens.scanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("['\"]([^'\"]+)['\"]");
  private static final Pattern JAVA_PACKAGE_PATTERN =
      Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;", Pattern.MULTILINE);
  private static final Pattern JAVA_CLASS_PATTERN =
      Pattern.compile("\\b(?:class|interface|record)\\s+([a-zA-Z0-9_]+)\\b");
  private static final Pattern JAVA_ROUTE_PATTERN =
      Pattern.compile(
          "@(?:Path|RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"(/[^\"]*)\"");
  private static final Pattern PYTHON_ROUTE_PATTERN =
      Pattern.compile(
          "@(?:app|router|bp|api)\\.(?:get|post|put|delete|patch|route)\\s*\\(\\s*['\"](/[^'\"]*)['\"]");
  private static final Pattern FRONTEND_API_CALL_PATTERN =
      Pattern.compile(
          "(?:\\bfetch|\\baxios(?:\\.(?:get|post|put|delete|patch|request))?)\\s*(?:<[^>]*>)?\\s*\\(\\s*['\"`](/api/[^'\"`$?\\s)]*)");

  /**
   * Discovers multi-module workspace subprojects across Gradle ({@code settings.gradle(.kts)}),
   * Cargo workspaces ({@code Cargo.toml [workspace]}), pnpm workspaces ({@code
   * pnpm-workspace.yaml}), and npm/Yarn/Nx/Turborepo workspaces ({@code package.json workspaces}).
   *
   * @param rootDir the workspace root directory
   * @return deduplicated list of existing workspace module directories
   */
  public static List<Path> discoverWorkspaceModules(Path rootDir) {
    if (rootDir == null || !Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
      return List.of();
    }
    Set<Path> modules = new LinkedHashSet<>();

    // 1. Gradle settings.gradle / settings.gradle.kts
    for (String gradleSettings : List.of("settings.gradle", "settings.gradle.kts")) {
      Path settingsFile = rootDir.resolve(gradleSettings);
      if (Files.exists(settingsFile)) {
        try {
          List<String> lines = Files.readAllLines(settingsFile, StandardCharsets.UTF_8);
          for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || !trimmed.contains("include")) {
              continue;
            }
            Matcher m = QUOTED_STRING_PATTERN.matcher(trimmed);
            while (m.find()) {
              String rawMod = m.group(1).trim();
              if (rawMod.startsWith(":")) {
                rawMod = rawMod.substring(1);
              }
              String relPath = rawMod.replace(':', '/');
              if (!relPath.isEmpty()) {
                expandWorkspaceGlob(rootDir, relPath, modules);
              }
            }
          }
        } catch (IOException ignored) {
        }
      }
    }

    // 2. Cargo.toml [workspace] members
    Path cargoToml = rootDir.resolve("Cargo.toml");
    if (Files.exists(cargoToml)) {
      try {
        String content = Files.readString(cargoToml, StandardCharsets.UTF_8);
        if (content.contains("[workspace]")) {
          Matcher membersBlock =
              Pattern.compile("members\\s*=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL).matcher(content);
          if (membersBlock.find()) {
            Matcher m = QUOTED_STRING_PATTERN.matcher(membersBlock.group(1));
            while (m.find()) {
              expandWorkspaceGlob(rootDir, m.group(1).trim(), modules);
            }
          }
        }
      } catch (IOException ignored) {
      }
    }

    // 3. pnpm-workspace.yaml
    Path pnpmWorkspace = rootDir.resolve("pnpm-workspace.yaml");
    if (Files.exists(pnpmWorkspace)) {
      try {
        List<String> lines = Files.readAllLines(pnpmWorkspace, StandardCharsets.UTF_8);
        for (String line : lines) {
          String trimmed = line.trim();
          if (trimmed.startsWith("-")) {
            String item = trimmed.substring(1).trim().replaceAll("^['\"]|['\"]$", "");
            if (!item.isEmpty() && !item.startsWith("!")) {
              expandWorkspaceGlob(rootDir, item, modules);
            }
          }
        }
      } catch (IOException ignored) {
      }
    }

    // 4. package.json workspaces
    Path packageJson = rootDir.resolve("package.json");
    if (Files.exists(packageJson)) {
      try {
        String content = Files.readString(packageJson, StandardCharsets.UTF_8);
        Matcher wsMatcher =
            Pattern.compile(
                    "\"workspaces\"\\s*:\\s*(?:\\{[^{}]*\"packages\"\\s*:\\s*)?\\[([^\\]]*)\\]",
                    Pattern.DOTALL)
                .matcher(content);
        if (wsMatcher.find()) {
          Matcher m = QUOTED_STRING_PATTERN.matcher(wsMatcher.group(1));
          while (m.find()) {
            String pattern = m.group(1).trim();
            if (!pattern.startsWith("!")) {
              expandWorkspaceGlob(rootDir, pattern, modules);
            }
          }
        }
      } catch (IOException ignored) {
      }
    }

    return new ArrayList<>(modules);
  }

  private static void expandWorkspaceGlob(Path rootDir, String pattern, Set<Path> outModules) {
    String clean = pattern.replace('\\', '/').replaceAll("^\\./+|/+$", "");
    if (clean.isEmpty()) {
      return;
    }
    if (clean.endsWith("/*") || clean.endsWith("/**")) {
      String parentRel = clean.replaceAll("/\\*+$", "");
      Path parentDir = parentRel.isEmpty() ? rootDir : rootDir.resolve(parentRel).normalize();
      if (parentDir.startsWith(rootDir) && Files.isDirectory(parentDir)) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir)) {
          for (Path child : stream) {
            if (Files.isDirectory(child) && !isIgnoredDirectory(child)) {
              outModules.add(child.normalize());
            }
          }
        } catch (IOException ignored) {
        }
      }
      return;
    }
    Path candidate = rootDir.resolve(clean).normalize();
    if (candidate.startsWith(rootDir) && Files.isDirectory(candidate)) {
      outModules.add(candidate);
    }
  }

  /**
   * Indexes backend REST/HTTP endpoints declared in Java ({@code @Path}, {@code @RequestMapping})
   * or Python ({@code @app.get}, {@code @router.post}) across the repository root so frontend HTTP
   * calls ({@code fetch('/api/...')}, {@code axios.get('/api/...')}) can be linked across language
   * boundaries.
   *
   * @param rootDir the repository root directory
   * @return map of normalized API route prefix (e.g. {@code /api/graph}) to backend class/module ID
   */
  public static Map<String, String> indexBackendRestRoutes(Path rootDir) {
    if (rootDir == null || !Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
      return Map.of();
    }
    Map<String, String> routeToBackendId = new LinkedHashMap<>();
    List<Path> backendFiles =
        findFiles(
            rootDir,
            12,
            p -> {
              String s = p.toString();
              Path fn = p.getFileName();
              String fnStr = fn != null ? fn.toString() : "";
              return (s.endsWith(".java") && !s.endsWith("Test.java"))
                  || (s.endsWith(".py") && !s.endsWith("_test.py") && !fnStr.startsWith("test_"));
            });

    for (Path file : backendFiles) {
      try {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (file.toString().endsWith(".java")) {
          Matcher routeMatcher = JAVA_ROUTE_PATTERN.matcher(content);
          List<String> routes = new ArrayList<>();
          while (routeMatcher.find()) {
            String r = normalizeApiRoute(routeMatcher.group(1));
            if (r.startsWith("/api")) {
              routes.add(r);
            }
          }
          if (!routes.isEmpty()) {
            Matcher pkgMatcher = JAVA_PACKAGE_PATTERN.matcher(content);
            String pkg = pkgMatcher.find() ? pkgMatcher.group(1).trim() : "";
            Matcher clsMatcher = JAVA_CLASS_PATTERN.matcher(content);
            Path fn = file.getFileName();
            String fallbackCls = fn != null ? fn.toString().replace(".java", "") : "Endpoint";
            String clsName = clsMatcher.find() ? clsMatcher.group(1).trim() : fallbackCls;
            String fullId = pkg.isEmpty() ? clsName : pkg + "." + clsName;
            routeToBackendId.putIfAbsent(routes.get(0), fullId);
          }
        } else if (file.toString().endsWith(".py")) {
          Matcher pyRouteMatcher = PYTHON_ROUTE_PATTERN.matcher(content);
          while (pyRouteMatcher.find()) {
            String r = normalizeApiRoute(pyRouteMatcher.group(1));
            if (r.startsWith("/api")) {
              String rel = rootDir.relativize(file).toString().replace('\\', '/');
              if (rel.startsWith("src/")) {
                rel = rel.substring(4);
              }
              String modId = rel.replace(".py", "").replace('/', '.');
              Matcher pyCls =
                  Pattern.compile("^class\\s+([a-zA-Z0-9_]+)", Pattern.MULTILINE).matcher(content);
              String targetId = pyCls.find() ? modId + "." + pyCls.group(1) : modId;
              routeToBackendId.putIfAbsent(r, targetId);
            }
          }
        }
      } catch (IOException ignored) {
      }
    }
    return routeToBackendId;
  }

  /**
   * Extracts normalized {@code /api/...} route paths invoked via {@code fetch(...)} or {@code
   * axios(...)} from TypeScript/JavaScript/Svelte source statements.
   */
  public static Set<String> extractFrontendApiCalls(List<String> statements) {
    if (statements == null || statements.isEmpty()) {
      return Set.of();
    }
    Set<String> routes = new LinkedHashSet<>();
    for (String stmt : statements) {
      Matcher m = FRONTEND_API_CALL_PATTERN.matcher(stmt);
      while (m.find()) {
        String normalized = normalizeApiRoute(m.group(1));
        if (normalized.startsWith("/api")) {
          routes.add(normalized);
        }
      }
    }
    return routes;
  }

  /**
   * Resolves a frontend {@code /api/...} route call against indexed backend REST routes using exact
   * or longest-prefix matching.
   */
  public static String resolveBackendEndpointForRoute(
      String apiRoute, Map<String, String> backendRoutes) {
    if (apiRoute == null
        || apiRoute.isBlank()
        || backendRoutes == null
        || backendRoutes.isEmpty()) {
      return null;
    }
    String normalized = normalizeApiRoute(apiRoute);
    if (backendRoutes.containsKey(normalized)) {
      return backendRoutes.get(normalized);
    }
    String bestMatchKey = null;
    for (String key : backendRoutes.keySet()) {
      if ((normalized.startsWith(key + "/") || key.startsWith(normalized + "/"))
          && (bestMatchKey == null || key.length() > bestMatchKey.length())) {
        bestMatchKey = key;
      }
    }
    return bestMatchKey != null ? backendRoutes.get(bestMatchKey) : null;
  }

  private static String normalizeApiRoute(String rawRoute) {
    if (rawRoute == null) {
      return "";
    }
    String clean = rawRoute.trim();
    int queryIdx = clean.indexOf('?');
    if (queryIdx >= 0) {
      clean = clean.substring(0, queryIdx);
    }
    int interpIdx = clean.indexOf("${");
    if (interpIdx >= 0) {
      clean = clean.substring(0, interpIdx);
    }
    while (clean.endsWith("/") && clean.length() > 1) {
      clean = clean.substring(0, clean.length() - 1);
    }
    return clean;
  }
}
