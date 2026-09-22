package com.design.umlviewer.scanner;

import com.design.umlviewer.domain.model.ClassNode;
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
public class TypeScriptAstScanner implements LanguageScanner {

  private static final Pattern IMPORT_FROM_PATTERN =
      Pattern.compile(
          "^\\s*import\\s+(?:\\{?[^}]*\\}?|\\*\\s+as\\s+[a-zA-Z0-9_]+)?\\s*from\\s+['\"]([^'\"]+)['\"];?");
  private static final Pattern EXPORT_FROM_PATTERN =
      Pattern.compile("^\\s*export\\s+(?:\\{?[^}]*\\}?|\\*)\\s*from\\s+['\"]([^'\"]+)['\"];?");
  private static final Pattern REQUIRE_PATTERN =
      Pattern.compile("(?:const|let|var)\\s+.*=\\s*require\\(['\"]([^'\"]+)['\"]\\)");
  private static final Pattern CLASS_PATTERN =
      Pattern.compile(
          "^\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?class\\s+([a-zA-Z0-9_]+)(?:\\s+extends\\s+([a-zA-Z0-9_]+))?(?:\\s+implements\\s+([a-zA-Z0-9_, ]+))?");
  private static final Pattern INTERFACE_PATTERN =
      Pattern.compile(
          "^\\s*(?:export\\s+)?interface\\s+([a-zA-Z0-9_]+)(?:\\s+extends\\s+([a-zA-Z0-9_, ]+))?");
  private static final Pattern FUNCTION_PATTERN =
      Pattern.compile("^\\s*(?:export\\s+)?(?:async\\s+)?function\\s+([a-zA-Z0-9_]+)\\s*\\(");

  @Override
  public String languageId() {
    return "typescript";
  }

  @Override
  public boolean supports(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null
        && ("typescript".equalsIgnoreCase(policy.lang())
            || "javascript".equalsIgnoreCase(policy.lang()))) {
      return true;
    }
    if (projectRoot == null || projectRoot.isBlank()) {
      return false;
    }
    File root = new File(projectRoot);
    return new File(root, "tsconfig.json").exists()
        || new File(root, "package.json").exists()
        || new File(root, "jsconfig.json").exists();
  }

  @Override
  public ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException {
    File rootDir = new File(projectRoot != null ? projectRoot : ".");
    File scanDir = LanguageScanner.resolveScanDirectory(projectRoot, srcRelativePath, "src");
    if (!scanDir.exists()) {
      return new ScanResult(List.of(), List.of());
    }

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();
    Map<String, String> internalModules = new HashMap<>();

    List<Path> tsFiles;
    try (Stream<Path> stream = Files.walk(scanDir.toPath())) {
      tsFiles =
          stream
              .filter(
                  p -> {
                    String s = p.toString();
                    return (s.endsWith(".ts")
                            || s.endsWith(".tsx")
                            || s.endsWith(".js")
                            || s.endsWith(".jsx"))
                        && !s.endsWith(".d.ts")
                        && !s.contains("/node_modules/")
                        && !s.contains("/dist/")
                        && !s.contains("/build/")
                        && !s.contains("/.");
                  })
              .toList();
    }

    Path rootPath = rootDir.toPath();
    for (Path tsFile : tsFiles) {
      String rel = rootPath.relativize(tsFile).toString();
      String modId = toTsModuleId(rel);
      internalModules.put(modId, rel);
    }

    for (Path tsFile : tsFiles) {
      String rel = rootPath.relativize(tsFile).toString();
      String currentModule = toTsModuleId(rel);
      String packageName = getPackageName(currentModule);

      List<String> lines = Files.readAllLines(tsFile, StandardCharsets.UTF_8);
      List<FieldNode> fields = new ArrayList<>();
      List<MethodNode> methods = new ArrayList<>();
      Set<String> importedPaths = new HashSet<>();
      List<String> typeNames = new ArrayList<>();

      for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
          continue;
        }

        Matcher importMatcher = IMPORT_FROM_PATTERN.matcher(line);
        if (importMatcher.find()) {
          importedPaths.add(importMatcher.group(1).trim());
          continue;
        }

        Matcher exportMatcher = EXPORT_FROM_PATTERN.matcher(line);
        if (exportMatcher.find()) {
          importedPaths.add(exportMatcher.group(1).trim());
          continue;
        }

        Matcher reqMatcher = REQUIRE_PATTERN.matcher(line);
        if (reqMatcher.find()) {
          importedPaths.add(reqMatcher.group(1).trim());
          continue;
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(line);
        if (classMatcher.find()) {
          String clsName = classMatcher.group(1);
          typeNames.add(clsName);
          String extendsCls = classMatcher.group(2);
          if (extendsCls != null && !extendsCls.isBlank()) {
            edges.add(
                new DependencyEdge(
                    currentModule + "." + clsName,
                    extendsCls.trim(),
                    DependencyEdge.Kind.INHERITANCE,
                    "extends",
                    false));
          }
          String impls = classMatcher.group(3);
          if (impls != null && !impls.isBlank()) {
            for (String impl : impls.split(",")) {
              if (!impl.isBlank()) {
                edges.add(
                    new DependencyEdge(
                        currentModule + "." + clsName,
                        impl.trim(),
                        DependencyEdge.Kind.IMPLEMENTS,
                        "implements",
                        false));
              }
            }
          }
          continue;
        }

        Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(line);
        if (interfaceMatcher.find()) {
          typeNames.add(interfaceMatcher.group(1));
          continue;
        }

        Matcher fnMatcher = FUNCTION_PATTERN.matcher(line);
        if (fnMatcher.find()) {
          String fnName = fnMatcher.group(1);
          methods.add(new MethodNode(fnName, List.of(), "void", false, 1, 1.0, 1.0, 0, 0, 0, 1));
        }
      }

      LanguageScanner.addModuleOrTypeClasses(
          classes,
          typeNames,
          new File(rel).getName().replaceAll("\\.[a-z]+$", ""),
          currentModule,
          packageName,
          rel,
          fields,
          methods);

      for (String imported : importedPaths) {
        String resolvedModule = resolveTsImport(currentModule, imported);
        edges.add(
            new DependencyEdge(
                currentModule, resolvedModule, DependencyEdge.Kind.DEPENDENCY, null, false));
      }
    }

    return new ScanResult(classes, edges);
  }

  private String toTsModuleId(String relPath) {
    String mod = relPath.replace('\\', '/');
    if (mod.startsWith("src/")) {
      mod = mod.substring(4);
    }
    mod = mod.replaceAll("\\.(ts|tsx|js|jsx)$", "");
    if (mod.endsWith("/index")) {
      mod = mod.substring(0, mod.length() - 6);
    }
    return mod.replace('/', '.');
  }

  private String getPackageName(String moduleId) {
    int idx = moduleId.lastIndexOf('.');
    return idx > 0 ? moduleId.substring(0, idx) : moduleId;
  }

  private String resolveTsImport(String currentModule, String importPath) {
    if (importPath.startsWith(".")) {
      // Relative import resolution
      String currentPkg = getPackageName(currentModule);
      String combined = currentPkg.isEmpty() ? importPath : currentPkg + "/" + importPath;
      return normalizePath(combined).replace('/', '.');
    }
    if (importPath.startsWith("@/") || importPath.startsWith("~/")) {
      return importPath.substring(2).replace('/', '.');
    }
    return importPath;
  }

  private String normalizePath(String path) {
    String[] parts = path.split("/+");
    List<String> result = new ArrayList<>();
    for (String p : parts) {
      if (p.equals(".") || p.isEmpty()) continue;
      if (p.equals("..")) {
        if (!result.isEmpty()) result.remove(result.size() - 1);
      } else {
        result.add(p);
      }
    }
    return String.join("/", result);
  }
}
