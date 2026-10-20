package io.slixes.archlens.scanner;

import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.CrapScore;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.FieldNode;
import io.slixes.archlens.domain.graph.MethodNode;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class PythonAstScanner implements LanguageScanner {

  private static final Pattern FROM_IMPORT_PATTERN =
      Pattern.compile("^\\s*from\\s+([a-zA-Z0-9_.]+)(?:\\s+|(?<=\\.))import\\s+(.+)$");
  private static final Pattern DIRECT_IMPORT_PATTERN =
      Pattern.compile("^\\s*import\\s+([a-zA-Z0-9_., ]+)$");
  private static final Pattern DYNAMIC_IMPORT_PATTERN =
      Pattern.compile(
          "(?:importlib\\.import_module|__import__)\\s*\\(\\s*['\"]([a-zA-Z0-9_.]+)['\"]");
  private static final Pattern CLASS_DEF_PATTERN =
      Pattern.compile("^\\s*class\\s+([a-zA-Z0-9_]+)(?:\\(([^)]*)\\))?\\s*:");
  private static final Pattern DEF_PATTERN =
      Pattern.compile("^(\\s*)(?:async\\s+)?def\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)");

  @Override
  public String languageId() {
    return "python";
  }

  @Override
  public boolean supports(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null && "python".equalsIgnoreCase(policy.lang())) {
      return true;
    }
    if (projectRoot == null || projectRoot.isBlank()) {
      return false;
    }
    File root = new File(projectRoot);
    return new File(root, "pyproject.toml").exists()
        || new File(root, "setup.py").exists()
        || new File(root, "requirements.txt").exists()
        || new File(root, "Pipfile").exists()
        || new File(root, "Taskfile.yml").exists() && hasPythonFiles(root);
  }

  private static final Set<String> DEFAULT_EXCLUDED_DIRS =
      Set.of(
          "node_modules",
          "tests",
          "test",
          "venv",
          ".venv",
          "__pycache__",
          "dist",
          "build",
          "target",
          "site-packages");

  private static final Set<String> PYTHON_STDLIB =
      Set.of(
          "abc",
          "argparse",
          "asyncio",
          "base64",
          "collections",
          "contextlib",
          "copy",
          "csv",
          "dataclasses",
          "datetime",
          "decimal",
          "enum",
          "functools",
          "glob",
          "gzip",
          "hashlib",
          "io",
          "itertools",
          "json",
          "logging",
          "math",
          "os",
          "pathlib",
          "pickle",
          "platform",
          "pprint",
          "random",
          "re",
          "shutil",
          "signal",
          "socket",
          "sqlite3",
          "string",
          "subprocess",
          "sys",
          "tempfile",
          "threading",
          "time",
          "traceback",
          "types",
          "typing",
          "unittest",
          "urllib",
          "uuid",
          "warnings",
          "weakref",
          "xml",
          "zipfile");

  private boolean hasPythonFiles(File dir) {
    if (!dir.exists() || !dir.isDirectory()) {
      return false;
    }
    File[] files = dir.listFiles();
    if (files == null) {
      return false;
    }
    for (File f : files) {
      if (f.isFile() && f.getName().endsWith(".py") && !f.getName().startsWith("test_")) {
        return true;
      }
      if (f.isDirectory()
          && !f.getName().startsWith(".")
          && !DEFAULT_EXCLUDED_DIRS.contains(f.getName())) {
        File[] sub = f.listFiles((d, name) -> name.endsWith(".py") && !name.startsWith("test_"));
        if (sub != null && sub.length > 0) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean isIgnored(Path path, Path rootPath, Set<String> omitPatterns) {
    Path fn = path.getFileName();
    if (fn == null) {
      return false;
    }
    String fileName = fn.toString();
    String rel = rootPath.relativize(path).toString().replace('\\', '/');

    // Exclude hidden files, test files, and setup scripts
    if (fileName.startsWith(".") || fileName.startsWith("test_") || fileName.endsWith("_test.py")) {
      return true;
    }

    // Check directory path segments against defaults and hidden folders
    String[] segments = rel.split("/");
    for (int i = 0; i < segments.length - 1; i++) {
      String seg = segments[i];
      if (seg.startsWith(".") || DEFAULT_EXCLUDED_DIRS.contains(seg) || seg.endsWith(".egg-info")) {
        return true;
      }
    }

    // Check against architecture policy omit patterns
    return LanguageScanner.matchesOmitPattern(rel, omitPatterns);
  }

  @Override
  public ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException {
    return scanProject(projectRoot, srcRelativePath, basePrefix, null);
  }

  @Override
  public ScanResult scanProject(
      String projectRoot, String srcRelativePath, String basePrefix, ArchitecturePolicy policy)
      throws IOException {
    File scanDir = LanguageScanner.resolveScanDirectory(projectRoot, srcRelativePath, null);
    if (!scanDir.exists()) {
      return new ScanResult(List.of(), List.of());
    }

    Path rootPath =
        new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".").toPath();
    Path scanPath = scanDir.toPath();
    Path effectiveRoot =
        (!scanPath.equals(rootPath) && !Files.exists(scanPath.resolve("__init__.py")))
            ? scanPath
            : rootPath;
    boolean stripSrcPrefix = !Files.exists(rootPath.resolve("src").resolve("__init__.py"));

    Set<String> omitPatterns = LanguageScanner.extractOmitPatterns(policy);
    List<Path> pyFiles = discoverPythonFiles(scanPath, rootPath, omitPatterns);

    Map<String, String> internalModules = new HashMap<>(); // moduleOrExportKey -> canonicalModuleId
    Map<Path, String> fileToModuleId = new HashMap<>();
    Map<Path, List<String>> fileLogicalLines = new HashMap<>();

    for (Path pyFile : pyFiles) {
      String rawRel = rootPath.relativize(pyFile).toString();
      String canonicalRel = toCanonicalRelPath(pyFile, effectiveRoot, rootPath, stripSrcPrefix);
      String modId = toModuleId(canonicalRel);
      String rawModId = toModuleId(rawRel);
      fileToModuleId.put(pyFile, modId);
      internalModules.put(modId, modId);
      internalModules.putIfAbsent(rawModId, modId);
      fileLogicalLines.put(
          pyFile, extractLogicalStatements(Files.readAllLines(pyFile, StandardCharsets.UTF_8)));
    }

    // Pass 1.5: Index __init__.py symbol & submodule re-exports (up to 4 hops for chained barrels)
    for (int hop = 0; hop < 4; hop++) {
      boolean added = false;
      for (Path pyFile : pyFiles) {
        Path fn = pyFile.getFileName();
        if (fn == null || !fn.toString().equals("__init__.py")) {
          continue;
        }
        String currentModule = fileToModuleId.get(pyFile);
        String packageName = getPackageName(currentModule, true);
        for (String line : fileLogicalLines.getOrDefault(pyFile, List.of())) {
          Matcher fromMatcher = FROM_IMPORT_PATTERN.matcher(line);
          if (!fromMatcher.find()) {
            continue;
          }
          String fromMod = fromMatcher.group(1).trim();
          String resolvedFromMod = resolvePythonImport(currentModule, packageName, fromMod);
          if (resolvedFromMod == null || isStdlib(resolvedFromMod)) {
            continue;
          }
          for (ImportItem item : parseImportItems(fromMatcher.group(2))) {
            if ("*".equals(item.originalName())) {
              continue;
            }
            String originCandidate = resolvedFromMod + "." + item.originalName();
            String resolvedTarget = resolveInternalTarget(originCandidate, internalModules);
            if (resolvedTarget != null) {
              String exportKey = currentModule + "." + item.exportedName();
              if (!internalModules.containsKey(exportKey)) {
                internalModules.put(exportKey, resolvedTarget);
                added = true;
              }
            }
          }
        }
      }
      if (!added) {
        break;
      }
    }

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();

    // Pass 2: Parse source contents and resolve dependency edges
    for (Path pyFile : pyFiles) {
      String rel = toCanonicalRelPath(pyFile, effectiveRoot, rootPath, stripSrcPrefix);
      String currentModule = fileToModuleId.get(pyFile);
      Path fn = pyFile.getFileName();
      boolean isInitFile = fn != null && fn.toString().equals("__init__.py");
      String packageName = getPackageName(currentModule, isInitFile);

      List<String> logicalLines = fileLogicalLines.getOrDefault(pyFile, List.of());
      List<FieldNode> fields = new ArrayList<>();
      List<MethodNode> methods = new ArrayList<>();
      Set<String> importedModules = new HashSet<>();
      List<String> classNames = new ArrayList<>();

      for (String line : logicalLines) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }

        Matcher fromMatcher = FROM_IMPORT_PATTERN.matcher(line);
        if (fromMatcher.find()) {
          String fromMod = fromMatcher.group(1).trim();
          String resolvedFromMod = resolvePythonImport(currentModule, packageName, fromMod);
          if (resolvedFromMod != null && !isStdlib(resolvedFromMod)) {
            boolean anyItemResolvedInternally = false;
            List<ImportItem> items = parseImportItems(fromMatcher.group(2));
            for (ImportItem item : items) {
              if ("*".equals(item.originalName())) {
                importedModules.add(resolvedFromMod);
                anyItemResolvedInternally = true;
                continue;
              }
              String candidate = resolvedFromMod + "." + item.originalName();
              if (resolveInternalTarget(candidate, internalModules) != null) {
                anyItemResolvedInternally = true;
              }
              importedModules.add(candidate);
            }
            if (!anyItemResolvedInternally) {
              importedModules.add(resolvedFromMod);
            }
          }
          continue;
        }

        Matcher directMatcher = DIRECT_IMPORT_PATTERN.matcher(line);
        if (directMatcher.find()) {
          for (ImportItem item : parseImportItems(directMatcher.group(1))) {
            String cleanMod = item.originalName();
            if (!cleanMod.isBlank()
                && Character.isJavaIdentifierStart(cleanMod.charAt(0))
                && !isStdlib(cleanMod)) {
              importedModules.add(cleanMod);
            }
          }
          continue;
        }

        Matcher dynMatcher = DYNAMIC_IMPORT_PATTERN.matcher(line);
        while (dynMatcher.find()) {
          String dynMod = dynMatcher.group(1).trim();
          String resolvedDyn = resolvePythonImport(currentModule, packageName, dynMod);
          if (resolvedDyn != null && !resolvedDyn.isBlank() && !isStdlib(resolvedDyn)) {
            importedModules.add(resolvedDyn);
          }
        }

        Matcher classMatcher = CLASS_DEF_PATTERN.matcher(line);
        if (classMatcher.find()) {
          String clsName = classMatcher.group(1);
          classNames.add(clsName);
          String baseClasses = classMatcher.group(2);
          if (baseClasses != null && !baseClasses.isBlank()) {
            for (String base : baseClasses.split(",")) {
              String cleanBase = base.trim();
              if (!cleanBase.isBlank() && !cleanBase.contains("=")) {
                edges.add(
                    new DependencyEdge(
                        currentModule + "." + clsName,
                        cleanBase,
                        DependencyEdge.Kind.INHERITANCE,
                        "inherits",
                        false));
              }
            }
          }
          continue;
        }

        Matcher defMatcher = DEF_PATTERN.matcher(line);
        if (defMatcher.find()) {
          String methodName = defMatcher.group(2);
          boolean isPrivate = methodName.startsWith("_") && !methodName.startsWith("__init__");
          int cc = 1; // baseline cyclomatic complexity
          methods.add(
              new MethodNode(methodName, List.of(), "Any", isPrivate, cc, 1.0, 1.0, 0, 0, 0, 1));
        }
      }

      // Add ClassNodes for discovered classes or fallback file module node
      if (classNames.isEmpty()) {
        String simpleName = new File(rel).getName().replace(".py", "");
        classes.add(
            new ClassNode(
                currentModule,
                simpleName,
                packageName,
                pyFile.toAbsolutePath().toString(),
                ClassNode.Stereotype.CLASS,
                false,
                null,
                new CrapScore(1.0, 1.0, 0.0),
                1.0,
                1,
                0,
                0,
                0,
                fields,
                methods));
      } else {
        for (String cls : classNames) {
          classes.add(
              new ClassNode(
                  currentModule + "." + cls,
                  cls,
                  packageName,
                  pyFile.toAbsolutePath().toString(),
                  ClassNode.Stereotype.CLASS,
                  false,
                  null,
                  new CrapScore(1.0, 1.0, 0.0),
                  1.0,
                  1,
                  0,
                  0,
                  0,
                  fields,
                  methods));
        }
      }

      // Resolve edges to imported modules
      Set<String> seenTargets = new HashSet<>();
      for (String imported : importedModules) {
        String matchedTarget = resolveInternalTarget(imported, internalModules);
        boolean isForeign = matchedTarget == null;
        String targetId = isForeign ? imported : matchedTarget;

        if (targetId.equals(currentModule)
            || targetId.isBlank()
            || isStdlib(targetId)
            || !seenTargets.add(targetId)) {
          continue;
        }

        edges.add(
            new DependencyEdge(
                currentModule, targetId, DependencyEdge.Kind.DEPENDENCY, null, false));
      }
    }

    return new ScanResult(classes, edges);
  }

  private record ImportItem(String originalName, String exportedName) {}

  static List<ImportItem> parseImportItems(String rawClause) {
    if (rawClause == null || rawClause.isBlank()) {
      return List.of();
    }
    String cleaned = rawClause.replace('(', ' ').replace(')', ' ').trim();
    List<ImportItem> result = new ArrayList<>();
    for (String part : cleaned.split(",")) {
      String token = part.trim();
      if (token.isEmpty()) {
        continue;
      }
      if ("*".equals(token)) {
        result.add(new ImportItem("*", "*"));
        continue;
      }
      String[] words = token.split("\\s+");
      String orig = words[0].trim();
      if (orig.isEmpty() || !Character.isJavaIdentifierStart(orig.charAt(0))) {
        continue;
      }
      String alias =
          (words.length >= 3 && "as".equals(words[1]) && !words[2].isBlank())
              ? words[2].trim()
              : orig;
      result.add(new ImportItem(orig, alias));
    }
    return result;
  }

  /**
   * Normalizes raw physical Python source lines into logical statements: - Strips triple-quoted
   * docstrings/multiline strings ("""...""" and '''...''') - Strips inline # comments outside
   * string literals - Joins multi-line statements across parentheses/brackets/braces and trailing
   * backslashes (\) - Splits top-level semicolon-separated statements
   */
  static List<String> extractLogicalStatements(List<String> rawLines) {
    if (rawLines == null || rawLines.isEmpty()) {
      return List.of();
    }
    List<String> statements = new ArrayList<>();
    StringBuilder currentStmt = new StringBuilder();
    boolean inTripleDouble = false;
    boolean inTripleSingle = false;
    int parenDepth = 0;

    for (String line : rawLines) {
      StringBuilder lineBuf = new StringBuilder();
      int len = line.length();
      int i = 0;

      while (i < len) {
        if (inTripleDouble) {
          int close = line.indexOf("\"\"\"", i);
          if (close != -1) {
            inTripleDouble = false;
            i = close + 3;
          } else {
            break;
          }
          continue;
        }
        if (inTripleSingle) {
          int close = line.indexOf("'''", i);
          if (close != -1) {
            inTripleSingle = false;
            i = close + 3;
          } else {
            break;
          }
          continue;
        }

        if (line.startsWith("\"\"\"", i)) {
          inTripleDouble = true;
          i += 3;
          continue;
        }
        if (line.startsWith("'''", i)) {
          inTripleSingle = true;
          i += 3;
          continue;
        }

        char c = line.charAt(i);
        if (c == '#') {
          break; // Inline comment to end of physical line
        }
        if (c == '"' || c == '\'') {
          char quote = c;
          lineBuf.append(c);
          i++;
          while (i < len) {
            char sc = line.charAt(i);
            lineBuf.append(sc);
            if (sc == '\\' && i + 1 < len) {
              i++;
              lineBuf.append(line.charAt(i));
            } else if (sc == quote) {
              i++;
              break;
            }
            i++;
          }
          continue;
        }

        if (c == '(' || c == '[' || c == '{') {
          parenDepth++;
        } else if (c == ')' || c == ']' || c == '}') {
          parenDepth = Math.max(0, parenDepth - 1);
        } else if (c == ';' && parenDepth == 0) {
          currentStmt.append(lineBuf);
          String emitted = currentStmt.toString();
          if (!emitted.isBlank()) {
            statements.add(emitted);
          }
          currentStmt.setLength(0);
          lineBuf.setLength(0);
          i++;
          continue;
        }

        lineBuf.append(c);
        i++;
      }

      if (inTripleDouble || inTripleSingle) {
        continue;
      }

      String cleanedLine = lineBuf.toString();
      String trimmedRight = cleanedLine.stripTrailing();
      if (trimmedRight.endsWith("\\")) {
        currentStmt.append(trimmedRight, 0, trimmedRight.length() - 1).append(' ');
        continue;
      }

      if (parenDepth > 0) {
        if (!cleanedLine.isBlank()) {
          if (currentStmt.length() > 0) {
            currentStmt.append(' ').append(cleanedLine.trim());
          } else {
            currentStmt.append(cleanedLine);
          }
        }
        continue;
      }

      if (currentStmt.length() > 0) {
        if (!cleanedLine.isBlank()) {
          currentStmt.append(' ').append(cleanedLine.trim());
        }
        String emitted = currentStmt.toString();
        if (!emitted.isBlank()) {
          statements.add(emitted);
        }
        currentStmt.setLength(0);
      } else if (!cleanedLine.isBlank()) {
        statements.add(cleanedLine);
      }
    }

    if (currentStmt.length() > 0 && !currentStmt.toString().isBlank()) {
      statements.add(currentStmt.toString());
    }

    return statements;
  }

  private String toCanonicalRelPath(
      Path pyFile, Path effectiveRoot, Path rootPath, boolean stripSrcPrefix) {
    String rel =
        (pyFile.startsWith(effectiveRoot)
                ? effectiveRoot.relativize(pyFile)
                : rootPath.relativize(pyFile))
            .toString()
            .replace('\\', '/');
    if (stripSrcPrefix && rel.startsWith("src/")) {
      rel = rel.substring(4);
    }
    return rel;
  }

  static String resolvePythonImport(String currentModule, String packageName, String imported) {
    if (imported == null || imported.isBlank()) {
      return null;
    }
    if (imported.startsWith(".")) {
      int dotCount = 0;
      while (dotCount < imported.length() && imported.charAt(dotCount) == '.') {
        dotCount++;
      }
      String rest = imported.substring(dotCount);
      String[] pkgParts =
          packageName == null || packageName.isBlank() ? new String[0] : packageName.split("\\.");
      int targetPartsLen = pkgParts.length - (dotCount - 1);
      if (targetPartsLen < 0) {
        targetPartsLen = 0;
      }
      String base = String.join(".", java.util.Arrays.copyOfRange(pkgParts, 0, targetPartsLen));
      if (base.isEmpty()) {
        return rest;
      }
      return rest.isEmpty() ? base : base + "." + rest;
    }
    return imported;
  }

  static boolean isStdlib(String mod) {
    if (mod == null || mod.isBlank()) {
      return false;
    }
    String rootPkg = mod.split("\\.")[0];
    return PYTHON_STDLIB.contains(rootPkg);
  }

  private String toModuleId(String relPath) {
    String mod = relPath.replace('\\', '/');
    if (mod.endsWith(".py")) {
      mod = mod.substring(0, mod.length() - 3);
    }
    if (mod.endsWith("/__init__")) {
      mod = mod.substring(0, mod.length() - 9);
    }
    return mod.replace('/', '.');
  }

  private String getPackageName(String moduleId) {
    return getPackageName(moduleId, false);
  }

  private String getPackageName(String moduleId, boolean isInitFile) {
    if (isInitFile) {
      return moduleId;
    }
    int idx = moduleId.lastIndexOf('.');
    return idx > 0 ? moduleId.substring(0, idx) : moduleId;
  }

  private String resolveInternalTarget(String imported, Map<String, String> internalModules) {
    if (internalModules.containsKey(imported)) {
      return internalModules.get(imported);
    }
    String cur = imported;
    while (cur.contains(".")) {
      cur = cur.substring(0, cur.lastIndexOf('.'));
      if (internalModules.containsKey(cur)) {
        return internalModules.get(cur);
      }
    }
    return null;
  }

  private List<Path> discoverPythonFiles(Path scanPath, Path rootPath, Set<String> omitPatterns) {
    return FileScannerUtil.findFiles(
        scanPath, p -> p.toString().endsWith(".py") && !isIgnored(p, rootPath, omitPatterns));
  }
}
