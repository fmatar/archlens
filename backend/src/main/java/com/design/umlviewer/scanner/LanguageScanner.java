package com.design.umlviewer.scanner;

import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.CrapScore;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.model.FieldNode;
import com.design.umlviewer.domain.model.MethodNode;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Service Provider Interface (SPI) for language-specific AST and dependency scanners. */
public interface LanguageScanner {

  record ScanResult(List<ClassNode> classes, List<DependencyEdge> edges) {
    public ScanResult {
      classes = classes != null ? List.copyOf(classes) : List.of();
      edges = edges != null ? List.copyOf(edges) : List.of();
    }
  }

  /** Unique identifier of the language handled by this scanner (e.g., "java", "python", "rust"). */
  String languageId();

  /**
   * Returns true if this scanner can process the project based on directory structure or policy.
   */
  boolean supports(String projectRoot, ArchitecturePolicy policy);

  /** Scans source files and generates AST nodes and dependency edges. */
  ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException;

  /** Resolves project scan directory considering explicit or fallback relative paths. */
  static File resolveScanDirectory(
      String projectRoot, String srcRelativePath, String fallbackChild) {
    File rootDir = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    if (srcRelativePath != null
        && !srcRelativePath.isBlank()
        && !srcRelativePath.equals(".")
        && !srcRelativePath.equals("/")) {
      File targetDir = new File(rootDir, srcRelativePath);
      if (targetDir.exists()) {
        return targetDir;
      }
    }
    if (fallbackChild != null && !fallbackChild.isBlank()) {
      File fallback = new File(rootDir, fallbackChild);
      if (fallback.exists()) {
        return fallback;
      }
    }
    return rootDir;
  }

  /** Creates a baseline ClassNode with default metric scores. */
  static ClassNode createDefaultClassNode(
      String fullId,
      String simpleName,
      String packageName,
      String filePath,
      List<FieldNode> fields,
      List<MethodNode> methods) {
    return new ClassNode(
        fullId,
        simpleName,
        packageName,
        filePath,
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
        methods);
  }

  /** Adds discovered type declarations or a fallback file module node. */
  static void addModuleOrTypeClasses(
      List<ClassNode> classes,
      List<String> typeNames,
      String fallbackSimpleName,
      String currentModule,
      String packageName,
      String relPath,
      List<FieldNode> fields,
      List<MethodNode> methods) {
    if (typeNames.isEmpty()) {
      classes.add(
          createDefaultClassNode(
              currentModule, fallbackSimpleName, packageName, relPath, fields, methods));
    } else {
      for (String typeName : typeNames) {
        classes.add(
            createDefaultClassNode(
                currentModule + "." + typeName, typeName, currentModule, relPath, fields, methods));
      }
    }
  }
}
