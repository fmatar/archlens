package com.design.umlviewer.scanner;

import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
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
}
