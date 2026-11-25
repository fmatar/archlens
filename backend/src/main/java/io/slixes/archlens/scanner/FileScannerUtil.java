package io.slixes.archlens.scanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Utility class providing hardened, fault-tolerant filesystem traversal.
 *
 * <p>Prunes hidden, metadata, and dependency directories before entry, and logs/counts unreadable
 * paths (e.g. broken symlinks, permission errors, or transient lock files).
 */
public final class FileScannerUtil {

  private static final Logger LOG = Logger.getLogger(FileScannerUtil.class);

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
    return findFiles(startDir, maxDepth, filePredicate, null);
  }

  /** Finds matching regular files up to {@code maxDepth}, logging and counting unreadable paths. */
  public static List<Path> findFiles(
      Path startDir, int maxDepth, Predicate<Path> filePredicate, AtomicInteger skippedCounter) {
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
              LOG.warnf("Skipping unreadable path %s: %s", file, exc.getMessage());
              if (skippedCounter != null
                  && (filePredicate == null
                      || filePredicate.test(file)
                      || Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))) {
                skippedCounter.incrementAndGet();
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException | SecurityException e) {
      LOG.warnf("Directory traversal failed under %s: %s", startDir, e.getMessage());
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
    return findFiles(startDir, Integer.MAX_VALUE, filePredicate, null);
  }

  /** Finds matching regular files with unbounded depth, logging and counting unreadable paths. */
  public static List<Path> findFiles(
      Path startDir, Predicate<Path> filePredicate, AtomicInteger skippedCounter) {
    return findFiles(startDir, Integer.MAX_VALUE, filePredicate, skippedCounter);
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
              LOG.warnf("Skipping unreadable directory entry %s: %s", file, exc.getMessage());
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException | SecurityException e) {
      LOG.warnf("Directory discovery failed under %s: %s", startDir, e.getMessage());
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
        } catch (IOException e) {
          LOG.warnf("Skipping unreadable Gradle settings %s: %s", settingsFile, e.getMessage());
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
      } catch (IOException e) {
        LOG.warnf("Skipping unreadable Cargo manifest %s: %s", cargoToml, e.getMessage());
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
      } catch (IOException e) {
        LOG.warnf("Skipping unreadable pnpm workspace %s: %s", pnpmWorkspace, e.getMessage());
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
      } catch (IOException e) {
        LOG.warnf("Skipping unreadable package.json %s: %s", packageJson, e.getMessage());
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
        } catch (IOException e) {
          LOG.warnf("Skipping unreadable workspace directory %s: %s", parentDir, e.getMessage());
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
    return indexBackendRestRoutes(rootDir, null);
  }

  /** Indexes backend REST/HTTP endpoints, logging and optionally counting unreadable files. */
  public static Map<String, String> indexBackendRestRoutes(
      Path rootDir, AtomicInteger skippedCounter) {
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
            },
            skippedCounter);

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
      } catch (IOException e) {
        LOG.warnf("Skipping unreadable backend route file %s: %s", file, e.getMessage());
        if (skippedCounter != null) {
          skippedCounter.incrementAndGet();
        }
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
        if (isInsideStringLiteral(stmt, m.start())) {
          continue;
        }
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

  /**
   * Strips {@code //} line comments, {@code /* ... *}{@code /} block comments (supporting nested
   * block comments in Rust), Go raw backtick string literals ({@code `...`}), and Rust string / raw
   * string literals ({@code "..."}, {@code r#"..."#}) while preserving every newline so line counts
   * remain intact.
   *
   * @param content raw UTF-8 source text
   * @param isRust true for Rust lexical rules (nested block comments, lifetimes, {@code r#"..."#});
   *     false for Go lexical rules (raw backticks {@code `...`}, keeping single-line {@code "..."}
   *     imports)
   * @return sanitized lines with comments and raw/non-import strings blanked to spaces
   */
  public static List<String> stripCommentsAndRawStrings(String content, boolean isRust) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }
    StringBuilder out = new StringBuilder(content.length());
    int len = content.length();
    int blockCommentDepth = 0;
    int i = 0;

    while (i < len) {
      char c = content.charAt(i);
      char next = i + 1 < len ? content.charAt(i + 1) : '\0';

      if (blockCommentDepth > 0) {
        if (isRust && c == '/' && next == '*') {
          blockCommentDepth++;
          out.append("  ");
          i += 2;
        } else if (c == '*' && next == '/') {
          blockCommentDepth--;
          out.append("  ");
          i += 2;
        } else {
          out.append(c == '\n' ? '\n' : ' ');
          i++;
        }
        continue;
      }

      if (c == '/' && next == '/') {
        while (i < len && content.charAt(i) != '\n') {
          out.append(' ');
          i++;
        }
        continue;
      }

      if (c == '/' && next == '*') {
        blockCommentDepth = 1;
        out.append("  ");
        i += 2;
        continue;
      }

      if (isRust) {
        int rawEnd = consumeRustRawString(content, i, out);
        if (rawEnd > i) {
          i = rawEnd;
          continue;
        }
        if (c == '\'') {
          if (i + 2 < len && content.charAt(i + 1) != '\\' && content.charAt(i + 2) == '\'') {
            out.append("   ");
            i += 3;
            continue;
          }
          if (i + 3 < len && content.charAt(i + 1) == '\\' && content.charAt(i + 3) == '\'') {
            out.append("    ");
            i += 4;
            continue;
          }
        }
        if (c == '"') {
          out.append('"');
          i++;
          while (i < len) {
            char sc = content.charAt(i);
            if (sc == '\\' && i + 1 < len) {
              out.append(' ');
              char esc = content.charAt(i + 1);
              out.append(esc == '\n' ? '\n' : ' ');
              i += 2;
            } else if (sc == '"') {
              out.append('"');
              i++;
              break;
            } else {
              out.append(sc == '\n' ? '\n' : ' ');
              i++;
            }
          }
          continue;
        }
      } else {
        if (c == '`') {
          out.append('`');
          i++;
          while (i < len) {
            char sc = content.charAt(i++);
            if (sc == '`') {
              out.append('`');
              break;
            }
            out.append(sc == '\n' ? '\n' : ' ');
          }
          continue;
        }
        if (c == '"' || c == '\'') {
          char quote = c;
          out.append(c);
          i++;
          while (i < len) {
            char sc = content.charAt(i);
            out.append(sc);
            if (sc == '\\' && i + 1 < len) {
              out.append(content.charAt(i + 1));
              i += 2;
            } else if (sc == quote || sc == '\n') {
              i++;
              break;
            } else {
              i++;
            }
          }
          continue;
        }
      }

      out.append(c);
      i++;
    }

    return List.of(out.toString().split("\\R", -1));
  }

  private static int consumeRustRawString(String content, int start, StringBuilder out) {
    int len = content.length();
    if (start > 0) {
      char prev = content.charAt(start - 1);
      if (Character.isLetterOrDigit(prev) || prev == '_') {
        return -1;
      }
    }
    char c = content.charAt(start);
    int rIdx = -1;
    if (c == 'r') {
      rIdx = start;
    } else if ((c == 'b' || c == 'c') && start + 1 < len && content.charAt(start + 1) == 'r') {
      rIdx = start + 1;
    }
    if (rIdx < 0) {
      return -1;
    }
    int p = rIdx + 1;
    int hashes = 0;
    while (p < len && content.charAt(p) == '#') {
      hashes++;
      p++;
    }
    if (p >= len || content.charAt(p) != '"') {
      return -1;
    }
    for (int k = start; k <= p; k++) {
      out.append(' ');
    }
    p++;
    while (p < len) {
      char sc = content.charAt(p);
      if (sc == '"' && hasClosingHashes(content, p + 1, hashes)) {
        for (int k = 0; k <= hashes; k++) {
          out.append(' ');
        }
        return p + 1 + hashes;
      }
      out.append(sc == '\n' ? '\n' : ' ');
      p++;
    }
    return len;
  }

  private static boolean hasClosingHashes(String content, int idx, int hashes) {
    if (idx + hashes > content.length()) {
      return false;
    }
    for (int k = 0; k < hashes; k++) {
      if (content.charAt(idx + k) != '#') {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} if {@code targetIndex} in a normalized single-line statement falls inside
   * a single-quoted, double-quoted, or backtick string literal.
   */
  public static boolean isInsideStringLiteral(String statement, int targetIndex) {
    if (statement == null || targetIndex <= 0) {
      return false;
    }
    int limit = Math.min(targetIndex, statement.length());
    boolean inSingle = false;
    boolean inDouble = false;
    boolean inTemplate = false;
    int i = 0;
    while (i < limit) {
      char c = statement.charAt(i);
      if ((inSingle || inDouble || inTemplate) && c == '\\' && i + 1 < statement.length()) {
        i += 2;
        continue;
      }
      if (!inDouble && !inTemplate && c == '\'') {
        inSingle = !inSingle;
      } else if (!inSingle && !inTemplate && c == '"') {
        inDouble = !inDouble;
      } else if (!inSingle && !inDouble && c == '`') {
        inTemplate = !inTemplate;
      }
      i++;
    }
    return inSingle || inDouble || inTemplate;
  }
}
