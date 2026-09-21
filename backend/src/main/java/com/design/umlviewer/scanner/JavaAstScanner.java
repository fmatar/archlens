package com.design.umlviewer.scanner;

import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.CrapScore;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.model.FieldNode;
import com.design.umlviewer.domain.model.MethodNode;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.metrics.CrapScoreCalculator;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@ApplicationScoped
public class JavaAstScanner implements LanguageScanner {

  @Inject CrapScoreCalculator crapCalculator;

  private final JavaParser javaParser =
      new JavaParser(
          new com.github.javaparser.ParserConfiguration()
              .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_21));

  @Override
  public String languageId() {
    return "java";
  }

  @Override
  public boolean supports(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null && "java".equalsIgnoreCase(policy.lang())) {
      return true;
    }
    if (projectRoot == null || projectRoot.isBlank()) {
      return true;
    }
    File root = new File(projectRoot);
    return new File(root, "pom.xml").exists()
        || new File(root, "build.gradle").exists()
        || new File(root, "build.gradle.kts").exists()
        || new File(root, "src/main/java").exists()
        || new File(root, "backend/pom.xml").exists()
        || !resolveSourceDirs(projectRoot, null).isEmpty();
  }

  public List<File> resolveSourceDirs(String projectRoot, String srcRelativePath) {
    List<File> dirs = new ArrayList<>();
    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");

    // 1. Check explicit/configured path if provided and not default
    if (srcRelativePath != null
        && !srcRelativePath.isBlank()
        && !srcRelativePath.equals("src/main/java")
        && !srcRelativePath.equals(".")) {
      File explicit = new File(root, srcRelativePath);
      if (explicit.exists() && explicit.isDirectory()) {
        dirs.add(explicit);
        return dirs;
      }
      File direct = new File(srcRelativePath);
      if (direct.exists() && direct.isDirectory()) {
        dirs.add(direct);
        return dirs;
      }
    }

    // 2. Standard root src/main/java
    File rootSrc = new File(root, "src/main/java");
    if (rootSrc.exists() && rootSrc.isDirectory()) {
      dirs.add(rootSrc);
      return dirs;
    }

    // 3. Multi-module discovery: look for nested **/src/main/java
    if (root.exists() && root.isDirectory()) {
      try (Stream<Path> stream = Files.walk(root.toPath(), 4)) {
        List<File> subModuleDirs =
            stream
                .filter(p -> p.endsWith("src/main/java"))
                .map(Path::toFile)
                .filter(File::isDirectory)
                .filter(
                    f -> {
                      String p = f.getAbsolutePath();
                      return !p.contains("/target/")
                          && !p.contains("/build/")
                          && !p.contains("/node_modules/")
                          && !p.contains("/.git/");
                    })
                .toList();
        for (File d : subModuleDirs) {
          if (!dirs.contains(d)) {
            dirs.add(d);
          }
        }
      } catch (IOException ignored) {
      }
    }

    // 4. Fallback to src/ if no src/main/java found
    if (dirs.isEmpty()) {
      File genericSrc = new File(root, "src");
      if (genericSrc.exists() && genericSrc.isDirectory()) {
        dirs.add(genericSrc);
      }
    }

    return dirs;
  }

  @Override
  public ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException {

    List<File> sourceDirs = resolveSourceDirs(projectRoot, srcRelativePath);
    if (sourceDirs.isEmpty()) {
      return new ScanResult(List.of(), List.of());
    }

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> rawEdges = new ArrayList<>();
    Map<String, String> simpleToFullyQualified = new HashMap<>();

    List<Path> javaFiles = new ArrayList<>();
    for (File sDir : sourceDirs) {
      try (Stream<Path> paths = Files.walk(sDir.toPath())) {
        javaFiles.addAll(paths.filter(p -> p.toString().endsWith(".java")).toList());
      } catch (IOException ignored) {
      }
    }

    if (javaFiles.isEmpty()) {
      return new ScanResult(List.of(), List.of());
    }

    // First pass: collect all classes and map simple name -> fully qualified name
    for (Path path : javaFiles) {
      ParseResult<CompilationUnit> parseResult = javaParser.parse(path);
      if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
        continue;
      }
      CompilationUnit cu = parseResult.getResult().get();
      String packageName = cu.getPackageDeclaration().map(p -> p.getName().asString()).orElse("");

      cu.findAll(ClassOrInterfaceDeclaration.class)
          .forEach(
              decl -> {
                String simpleName = decl.getNameAsString();
                String fullId = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
                simpleToFullyQualified.put(simpleName, fullId);
              });

      cu.findAll(RecordDeclaration.class)
          .forEach(
              decl -> {
                String simpleName = decl.getNameAsString();
                String fullId = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
                simpleToFullyQualified.put(simpleName, fullId);
              });
    }

    // Second pass: extract classes, records, and dependencies
    for (Path path : javaFiles) {
      ParseResult<CompilationUnit> parseResult = javaParser.parse(path);
      if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
        continue;
      }
      CompilationUnit cu = parseResult.getResult().get();
      String packageName = cu.getPackageDeclaration().map(p -> p.getName().asString()).orElse("");

      // Collect imports
      List<String> imports =
          cu.getImports().stream().map(ImportDeclaration::getNameAsString).toList();

      // Process classes, interfaces
      cu.findAll(ClassOrInterfaceDeclaration.class)
          .forEach(
              decl -> {
                String simpleName = decl.getNameAsString();
                String fullId = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

                ClassNode.Stereotype stereotype =
                    decl.isInterface()
                        ? ClassNode.Stereotype.INTERFACE
                        : (decl.isAbstract()
                            ? ClassNode.Stereotype.ABSTRACT
                            : ClassNode.Stereotype.CLASS);

                List<FieldNode> fields = extractFields(decl);
                List<MethodNode> methods = extractMethods(decl);

                // Compute mock coverage & CRAP for demo metrics
                List<Double> methodCraps = methods.stream().map(MethodNode::crap).toList();
                CrapScore crapScore =
                    crapCalculator != null
                        ? crapCalculator.aggregate(methodCraps)
                        : CrapScore.zero();

                classes.add(
                    new ClassNode(
                        fullId,
                        simpleName,
                        packageName,
                        path.toAbsolutePath().toString(),
                        stereotype,
                        false,
                        null,
                        crapScore,
                        0.85, // base coverage
                        methods.stream().mapToInt(MethodNode::cc).sum(),
                        methods.stream().mapToInt(MethodNode::killed).sum(),
                        methods.stream().mapToInt(MethodNode::survived).sum(),
                        methods.stream().mapToInt(MethodNode::uncovered).sum(),
                        fields,
                        methods));

                // Check inheritance / implements
                for (ClassOrInterfaceType ext : decl.getExtendedTypes()) {
                  rawEdges.add(
                      new DependencyEdge(
                          fullId,
                          ext.getNameAsString(),
                          DependencyEdge.Kind.INHERITANCE,
                          null,
                          false));
                }
                for (ClassOrInterfaceType impl : decl.getImplementedTypes()) {
                  rawEdges.add(
                      new DependencyEdge(
                          fullId,
                          impl.getNameAsString(),
                          DependencyEdge.Kind.IMPLEMENTS,
                          null,
                          false));
                }

                // Treat internal imports as dependencies
                for (String imp : imports) {
                  if (imp.startsWith(basePrefix) && !imp.equals(fullId)) {
                    rawEdges.add(
                        new DependencyEdge(
                            fullId, imp, DependencyEdge.Kind.DEPENDENCY, null, false));
                  }
                }

                // Also check referenced simple types from within the same package or project
                decl.findAll(ClassOrInterfaceType.class)
                    .forEach(
                        t -> {
                          String typeName = t.getNameAsString();
                          if (simpleToFullyQualified.containsKey(typeName)) {
                            String targetFullId = simpleToFullyQualified.get(typeName);
                            if (!targetFullId.equals(fullId)) {
                              rawEdges.add(
                                  new DependencyEdge(
                                      fullId,
                                      targetFullId,
                                      DependencyEdge.Kind.DEPENDENCY,
                                      null,
                                      false));
                            }
                          }
                        });
              });

      // Records
      cu.findAll(RecordDeclaration.class)
          .forEach(
              decl -> {
                String simpleName = decl.getNameAsString();
                String fullId = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

                classes.add(
                    new ClassNode(
                        fullId,
                        simpleName,
                        packageName,
                        path.toAbsolutePath().toString(),
                        ClassNode.Stereotype.RECORD,
                        false,
                        null,
                        CrapScore.zero(),
                        1.0,
                        1,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of()));

                // Check referenced simple types in record components/methods
                decl.findAll(ClassOrInterfaceType.class)
                    .forEach(
                        t -> {
                          String typeName = t.getNameAsString();
                          if (simpleToFullyQualified.containsKey(typeName)) {
                            String targetFullId = simpleToFullyQualified.get(typeName);
                            if (!targetFullId.equals(fullId)) {
                              rawEdges.add(
                                  new DependencyEdge(
                                      fullId,
                                      targetFullId,
                                      DependencyEdge.Kind.DEPENDENCY,
                                      null,
                                      false));
                            }
                          }
                        });
              });
    }

    // Normalize edge targets using fully-qualified names
    List<DependencyEdge> normalizedEdges = new ArrayList<>();
    Set<String> seenEdges = new HashSet<>();

    for (DependencyEdge edge : rawEdges) {
      String toId = simpleToFullyQualified.getOrDefault(edge.to(), edge.to());
      String key = edge.from() + "->" + toId + ":" + edge.kind();
      if (!seenEdges.contains(key) && !edge.from().equals(toId)) {
        seenEdges.add(key);
        normalizedEdges.add(
            new DependencyEdge(edge.from(), toId, edge.kind(), edge.label(), false));
      }
    }

    return new ScanResult(classes, normalizedEdges);
  }

  private List<FieldNode> extractFields(ClassOrInterfaceDeclaration decl) {
    List<FieldNode> fields = new ArrayList<>();
    for (FieldDeclaration f : decl.getFields()) {
      f.getVariables()
          .forEach(
              v -> {
                fields.add(new FieldNode(v.getNameAsString(), v.getTypeAsString(), f.isPrivate()));
              });
    }
    return fields;
  }

  private List<MethodNode> extractMethods(ClassOrInterfaceDeclaration decl) {
    List<MethodNode> methods = new ArrayList<>();
    for (MethodDeclaration m : decl.getMethods()) {
      int cc = 1; // baseline Cyclomatic Complexity
      cc += m.findAll(com.github.javaparser.ast.stmt.IfStmt.class).size();
      cc += m.findAll(com.github.javaparser.ast.stmt.ForStmt.class).size();
      cc += m.findAll(com.github.javaparser.ast.stmt.WhileStmt.class).size();
      cc += m.findAll(com.github.javaparser.ast.expr.ConditionalExpr.class).size();

      double coverage = 0.8;
      double crap = crapCalculator != null ? crapCalculator.calculateMethodCrap(cc, coverage) : cc;
      int line = m.getRange().map(r -> r.begin.line).orElse(1);

      List<String> params =
          m.getParameters().stream()
              .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
              .toList();

      methods.add(
          new MethodNode(
              m.getNameAsString(),
              params,
              m.getTypeAsString(),
              m.isPrivate(),
              cc,
              coverage,
              Math.round(crap * 10.0) / 10.0,
              3,
              0,
              0, // killed, survived, uncovered mock
              line));
    }
    return methods;
  }
}
