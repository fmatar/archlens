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
public class RustAstScanner implements LanguageScanner {

  private static final Pattern USE_PATTERN =
      Pattern.compile("^\\s*use\\s+([a-zA-Z0-9_:]+)(?:\\s*as\\s+([a-zA-Z0-9_]+))?;");
  private static final Pattern MOD_PATTERN =
      Pattern.compile("^\\s*(?:pub\\s+)?mod\\s+([a-zA-Z0-9_]+);");
  private static final Pattern STRUCT_PATTERN =
      Pattern.compile("^\\s*(?:pub\\s+)?(?:struct|enum|trait)\\s+([a-zA-Z0-9_]+)");
  private static final Pattern IMPL_TRAIT_PATTERN =
      Pattern.compile("^\\s*impl(?:<[^>]+>)?\\s+([a-zA-Z0-9_:]+)\\s+for\\s+([a-zA-Z0-9_:]+)");
  private static final Pattern FN_PATTERN =
      Pattern.compile("^\\s*(?:pub\\s+)?(?:async\\s+)?fn\\s+([a-zA-Z0-9_]+)\\s*\\(");

  @Override
  public String languageId() {
    return "rust";
  }

  @Override
  public boolean supports(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null && "rust".equalsIgnoreCase(policy.lang())) {
      return true;
    }
    if (projectRoot == null || projectRoot.isBlank()) {
      return false;
    }
    File root = new File(projectRoot);
    return new File(root, "Cargo.toml").exists();
  }

  @Override
  public ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException {
    File rootDir = new File(projectRoot != null ? projectRoot : ".");
    File scanDir = rootDir;
    if (srcRelativePath != null
        && !srcRelativePath.isBlank()
        && !srcRelativePath.equals(".")
        && !srcRelativePath.equals("/")) {
      File targetDir = new File(rootDir, srcRelativePath);
      if (targetDir.exists()) {
        scanDir = targetDir;
      }
    } else {
      File defaultSrc = new File(rootDir, "src");
      if (defaultSrc.exists()) {
        scanDir = defaultSrc;
      }
    }

    if (!scanDir.exists()) {
      return new ScanResult(List.of(), List.of());
    }

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();
    Map<String, String> internalModules = new HashMap<>();

    List<Path> rsFiles;
    try (Stream<Path> stream = Files.walk(scanDir.toPath())) {
      rsFiles =
          stream
              .filter(p -> p.toString().endsWith(".rs"))
              .filter(p -> !p.toString().contains("/target/") && !p.toString().contains("/."))
              .toList();
    }

    Path rootPath = rootDir.toPath();
    for (Path rsFile : rsFiles) {
      String rel = rootPath.relativize(rsFile).toString();
      String modId = toRustModuleId(rel);
      internalModules.put(modId, rel);
    }

    for (Path rsFile : rsFiles) {
      String rel = rootPath.relativize(rsFile).toString();
      String currentModule = toRustModuleId(rel);
      String packageName = getPackageName(currentModule);

      List<String> lines = Files.readAllLines(rsFile, StandardCharsets.UTF_8);
      List<FieldNode> fields = new ArrayList<>();
      List<MethodNode> methods = new ArrayList<>();
      Set<String> importedPaths = new HashSet<>();
      List<String> typeNames = new ArrayList<>();

      for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
          continue;
        }

        Matcher useMatcher = USE_PATTERN.matcher(line);
        if (useMatcher.find()) {
          String usePath = useMatcher.group(1).trim();
          importedPaths.add(usePath);
          continue;
        }

        Matcher structMatcher = STRUCT_PATTERN.matcher(line);
        if (structMatcher.find()) {
          typeNames.add(structMatcher.group(1));
          continue;
        }

        Matcher implMatcher = IMPL_TRAIT_PATTERN.matcher(line);
        if (implMatcher.find()) {
          String traitName = implMatcher.group(1).trim();
          String structName = implMatcher.group(2).trim();
          edges.add(
              new DependencyEdge(
                  currentModule + "." + structName,
                  traitName,
                  DependencyEdge.Kind.IMPLEMENTS,
                  "implements",
                  false));
          continue;
        }

        Matcher fnMatcher = FN_PATTERN.matcher(line);
        if (fnMatcher.find()) {
          String fnName = fnMatcher.group(1);
          methods.add(
              new MethodNode(
                  fnName, List.of(), "()", !line.contains("pub fn"), 1, 1.0, 1.0, 0, 0, 0, 1));
        }
      }

      if (typeNames.isEmpty()) {
        String simpleName = new File(rel).getName().replace(".rs", "");
        classes.add(
            new ClassNode(
                currentModule,
                simpleName,
                packageName,
                rel,
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
        for (String typeName : typeNames) {
          classes.add(
              new ClassNode(
                  currentModule + "." + typeName,
                  typeName,
                  currentModule,
                  rel,
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

      for (String imported : importedPaths) {
        String normalized = imported.replace("crate::", "").replace("::", ".");
        edges.add(
            new DependencyEdge(
                currentModule, normalized, DependencyEdge.Kind.DEPENDENCY, null, false));
      }
    }

    return new ScanResult(classes, edges);
  }

  private String toRustModuleId(String relPath) {
    String mod = relPath.replace('\\', '/');
    if (mod.startsWith("src/")) {
      mod = mod.substring(4);
    }
    if (mod.endsWith(".rs")) {
      mod = mod.substring(0, mod.length() - 3);
    }
    if (mod.endsWith("/mod") || mod.equals("lib") || mod.equals("main")) {
      if (mod.endsWith("/mod")) {
        mod = mod.substring(0, mod.length() - 4);
      }
    }
    return mod.replace('/', '.');
  }

  private String getPackageName(String moduleId) {
    int idx = moduleId.lastIndexOf('.');
    return idx > 0 ? moduleId.substring(0, idx) : moduleId;
  }
}
