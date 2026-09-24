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

@ApplicationScoped
public class GoAstScanner implements LanguageScanner {

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_]+)");
  private static final Pattern SINGLE_IMPORT_PATTERN =
      Pattern.compile("^\\s*import\\s+(?:[a-zA-Z0-9_]+\\s+)?\"([^\"]+)\"");
  private static final Pattern TYPE_STRUCT_PATTERN =
      Pattern.compile("^\\s*type\\s+([a-zA-Z0-9_]+)\\s+struct\\s*\\{");
  private static final Pattern TYPE_INTERFACE_PATTERN =
      Pattern.compile("^\\s*type\\s+([a-zA-Z0-9_]+)\\s+interface\\s*\\{");
  private static final Pattern METHOD_RECEIVER_PATTERN =
      Pattern.compile("^\\s*func\\s*\\([^)]*\\*?([a-zA-Z0-9_]+)\\)\\s*([a-zA-Z0-9_]+)\\s*\\(");
  private static final Pattern FUNC_PATTERN =
      Pattern.compile("^\\s*func\\s+([a-zA-Z0-9_]+)\\s*\\(");

  @Override
  public String languageId() {
    return "go";
  }

  @Override
  public boolean supports(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null && "go".equalsIgnoreCase(policy.lang())) {
      return true;
    }
    if (projectRoot == null || projectRoot.isBlank()) {
      return false;
    }
    File root = new File(projectRoot);
    return new File(root, "go.mod").exists();
  }

  @Override
  public ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException {
    File rootDir = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    File scanDir = LanguageScanner.resolveScanDirectory(projectRoot, srcRelativePath, null);
    if (!scanDir.exists()) {
      return new ScanResult(List.of(), List.of());
    }

    String modulePrefix = readGoModuleName(rootDir);

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();
    Map<String, String> internalPackages = new HashMap<>();

    List<Path> goFiles =
        FileScannerUtil.findFiles(
            scanDir.toPath(),
            p -> p.toString().endsWith(".go") && !p.toString().endsWith("_test.go"));

    Path rootPath = rootDir.toPath();
    for (Path goFile : goFiles) {
      String rel = rootPath.relativize(goFile).toString();
      String pkgId = toGoPackageId(rel);
      internalPackages.put(pkgId, rel);
    }

    for (Path goFile : goFiles) {
      String rel = rootPath.relativize(goFile).toString();
      String currentPackage = toGoPackageId(rel);

      List<String> lines = Files.readAllLines(goFile, StandardCharsets.UTF_8);
      List<FieldNode> fields = new ArrayList<>();
      List<MethodNode> methods = new ArrayList<>();
      Set<String> importedPaths = new HashSet<>();
      List<String> typeNames = new ArrayList<>();
      boolean inImportBlock = false;

      for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
          continue;
        }

        if (trimmed.equals("import (")) {
          inImportBlock = true;
          continue;
        }
        if (inImportBlock) {
          if (trimmed.equals(")")) {
            inImportBlock = false;
            continue;
          }
          if (trimmed.contains("\"")) {
            String imp = trimmed.replaceAll("^.*\"([^\"]+)\".*$", "$1");
            importedPaths.add(imp);
          }
          continue;
        }

        Matcher singleImp = SINGLE_IMPORT_PATTERN.matcher(line);
        if (singleImp.find()) {
          importedPaths.add(singleImp.group(1));
          continue;
        }

        Matcher structMatcher = TYPE_STRUCT_PATTERN.matcher(line);
        if (structMatcher.find()) {
          typeNames.add(structMatcher.group(1));
          continue;
        }

        Matcher interfaceMatcher = TYPE_INTERFACE_PATTERN.matcher(line);
        if (interfaceMatcher.find()) {
          typeNames.add(interfaceMatcher.group(1));
          continue;
        }

        Matcher methodMatcher = METHOD_RECEIVER_PATTERN.matcher(line);
        if (methodMatcher.find()) {
          String receiver = methodMatcher.group(1);
          String methodName = methodMatcher.group(2);
          boolean isPrivate = Character.isLowerCase(methodName.charAt(0));
          methods.add(
              new MethodNode(
                  methodName, List.of(receiver), "void", isPrivate, 1, 1.0, 1.0, 0, 0, 0, 1));
          continue;
        }

        Matcher fnMatcher = FUNC_PATTERN.matcher(line);
        if (fnMatcher.find()) {
          String fnName = fnMatcher.group(1);
          boolean isPrivate = Character.isLowerCase(fnName.charAt(0));
          methods.add(
              new MethodNode(fnName, List.of(), "void", isPrivate, 1, 1.0, 1.0, 0, 0, 0, 1));
        }
      }

      if (typeNames.isEmpty()) {
        String simpleName = new File(rel).getName().replace(".go", "");
        classes.add(
            new ClassNode(
                currentPackage,
                simpleName,
                currentPackage,
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
                  currentPackage + "." + typeName,
                  typeName,
                  currentPackage,
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
        String targetPackage = imported;
        if (!modulePrefix.isEmpty() && targetPackage.startsWith(modulePrefix)) {
          targetPackage = targetPackage.substring(modulePrefix.length());
          if (targetPackage.startsWith("/")) {
            targetPackage = targetPackage.substring(1);
          }
        }
        targetPackage = targetPackage.replace('/', '.');
        edges.add(
            new DependencyEdge(
                currentPackage, targetPackage, DependencyEdge.Kind.DEPENDENCY, null, false));
      }
    }

    return new ScanResult(classes, edges);
  }

  private String readGoModuleName(File rootDir) {
    File goMod = new File(rootDir, "go.mod");
    if (!goMod.exists()) {
      return "";
    }
    try {
      List<String> lines = Files.readAllLines(goMod.toPath(), StandardCharsets.UTF_8);
      for (String line : lines) {
        String t = line.trim();
        if (t.startsWith("module ")) {
          return t.substring(7).trim();
        }
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private String toGoPackageId(String relPath) {
    String p = relPath.replace('\\', '/');
    int lastSlash = p.lastIndexOf('/');
    if (lastSlash >= 0) {
      return p.substring(0, lastSlash).replace('/', '.');
    }
    return "main";
  }
}
