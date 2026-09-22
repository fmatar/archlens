package com.design.umlviewer.scanner;

import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.CrapScore;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.model.FieldNode;
import com.design.umlviewer.domain.model.MethodNode;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
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
import java.util.stream.Stream;

@ApplicationScoped
public class PythonAstScanner implements LanguageScanner {

  private static final Pattern FROM_IMPORT_PATTERN =
      Pattern.compile("^\\s*from\\s+([a-zA-Z0-9_.]+)\\s+import\\s+(.+)$");
  private static final Pattern DIRECT_IMPORT_PATTERN =
      Pattern.compile("^\\s*import\\s+([a-zA-Z0-9_., ]+)$");
  private static final Pattern CLASS_DEF_PATTERN =
      Pattern.compile("^\\s*class\\s+([a-zA-Z0-9_]+)(?:\\(([^)]*)\\))?:");
  private static final Pattern DEF_PATTERN =
      Pattern.compile("^(\\s*)def\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)");

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
    if (omitPatterns != null && !omitPatterns.isEmpty()) {
      for (String pattern : omitPatterns) {
        if (pattern == null || pattern.isBlank()) continue;
        String clean = pattern.trim().replace('\\', '/');
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        if (clean.isBlank()) continue;

        if (rel.equals(clean)
            || rel.startsWith(clean + "/")
            || rel.contains("/" + clean + "/")
            || rel.endsWith("/" + clean)) {
          return true;
        }
      }
    }
    return false;
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
    Set<String> omitPatterns = LanguageScanner.extractOmitPatterns(policy);
    List<Path> pyFiles = discoverPythonFiles(scanDir.toPath(), rootPath, omitPatterns);

    Map<String, String> internalModules = new HashMap<>(); // modulePath -> fullModuleId
    for (Path pyFile : pyFiles) {
      String rel = rootPath.relativize(pyFile).toString();
      String modId = toModuleId(rel);
      internalModules.put(modId, rel);
    }

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();

    // Pass 2: Parse source contents
    for (Path pyFile : pyFiles) {
      String rel = rootPath.relativize(pyFile).toString();
      String currentModule = toModuleId(rel);
      String packageName = getPackageName(currentModule);

      List<String> lines = Files.readAllLines(pyFile, StandardCharsets.UTF_8);
      List<FieldNode> fields = new ArrayList<>();
      List<MethodNode> methods = new ArrayList<>();
      Set<String> importedModules = new HashSet<>();
      List<String> classNames = new ArrayList<>();

      for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
          continue;
        }

        Matcher fromMatcher = FROM_IMPORT_PATTERN.matcher(line);
        if (fromMatcher.find()) {
          String fromMod = fromMatcher.group(1).trim();
          String resolvedFromMod = resolvePythonImport(currentModule, packageName, fromMod);
          String importedItems = fromMatcher.group(2).trim();
          if (resolvedFromMod != null && !isStdlib(resolvedFromMod)) {
            importedModules.add(resolvedFromMod);
            for (String item : importedItems.split(",")) {
              String cleanItem = item.trim().split("\\s+")[0].replaceAll("[()#]", "").trim();
              if (!cleanItem.isBlank() && Character.isJavaIdentifierStart(cleanItem.charAt(0))) {
                importedModules.add(resolvedFromMod + "." + cleanItem);
              }
            }
          }
          continue;
        }

        Matcher directMatcher = DIRECT_IMPORT_PATTERN.matcher(line);
        if (directMatcher.find()) {
          String mods = directMatcher.group(1).trim();
          for (String m : mods.split(",")) {
            String cleanMod = m.trim().split("\\s+")[0].replaceAll("[()#]", "").trim();
            if (!cleanMod.isBlank()
                && Character.isJavaIdentifierStart(cleanMod.charAt(0))
                && !isStdlib(cleanMod)) {
              importedModules.add(cleanMod);
            }
          }
          continue;
        }

        Matcher classMatcher = CLASS_DEF_PATTERN.matcher(line);
        if (classMatcher.find()) {
          String clsName = classMatcher.group(1);
          classNames.add(clsName);
          String baseClasses = classMatcher.group(2);
          if (baseClasses != null && !baseClasses.isBlank()) {
            for (String base : baseClasses.split(",")) {
              String cleanBase = base.trim();
              if (!cleanBase.isBlank()) {
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
      for (String imported : importedModules) {
        String matchedTarget = resolveInternalTarget(imported, internalModules);
        boolean isForeign = matchedTarget == null;
        String targetId = isForeign ? imported : matchedTarget;

        if (targetId.equals(currentModule) || targetId.isBlank() || isStdlib(targetId)) {
          continue;
        }

        edges.add(
            new DependencyEdge(
                currentModule, targetId, DependencyEdge.Kind.DEPENDENCY, null, false));
      }
    }

    return new ScanResult(classes, edges);
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
    int idx = moduleId.lastIndexOf('.');
    return idx > 0 ? moduleId.substring(0, idx) : moduleId;
  }

  private String resolveInternalTarget(String imported, Map<String, String> internalModules) {
    if (internalModules.containsKey(imported)) {
      return imported;
    }
    String cur = imported;
    while (cur.contains(".")) {
      cur = cur.substring(0, cur.lastIndexOf('.'));
      if (internalModules.containsKey(cur)) {
        return cur;
      }
    }
    return null;
  }

  private List<Path> discoverPythonFiles(Path scanPath, Path rootPath, Set<String> omitPatterns)
      throws IOException {
    try (Stream<Path> stream = Files.walk(scanPath)) {
      return stream
          .filter(p -> p.toString().endsWith(".py"))
          .filter(p -> !isIgnored(p, rootPath, omitPatterns))
          .toList();
    }
  }
}
