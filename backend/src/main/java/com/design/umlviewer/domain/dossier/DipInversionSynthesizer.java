package com.design.umlviewer.domain.dossier;

import com.design.umlviewer.domain.graph.ArchitectureGraph;
import com.design.umlviewer.domain.graph.ClassNode;
import com.design.umlviewer.domain.graph.ComponentNode;
import com.design.umlviewer.domain.graph.MethodNode;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DipInversionSynthesizer {

  public DipInversionPlan synthesize(
      ArchitectureGraph graph, ArchitecturePolicy policy, String fromClass, String toClass) {
    if (fromClass == null) fromClass = "";
    if (toClass == null) toClass = "";

    ClassNode fromNode = findClassNode(graph, fromClass);
    ClassNode toNode = findClassNode(graph, toClass);

    Integer fromLevel = resolveLevel(fromNode, fromClass, policy);
    Integer toLevel = resolveLevel(toNode, toClass, policy);

    String portName = derivePortName(toClass);
    String portPackage = derivePortPackage(fromClass, fromNode);
    String portFilePath = derivePortFilePath(portPackage, portName, fromNode);

    List<String> targetMethods = extractMethodSignatures(toNode);
    String portInterfaceCode =
        generatePortInterfaceCode(
            portPackage, portName, fromClass, toClass, fromLevel, toLevel, targetMethods);

    String adapterRefactorPreview = generateAdapterPreview(toClass, portPackage, portName);
    String callerRefactorPreview = generateCallerPreview(fromClass, portPackage, portName);

    String surgicalPrompt =
        generateSurgicalPrompt(
            fromClass,
            toClass,
            fromLevel,
            toLevel,
            portName,
            portPackage,
            portFilePath,
            portInterfaceCode,
            adapterRefactorPreview,
            callerRefactorPreview);

    return new DipInversionPlan(
        fromClass,
        toClass,
        fromLevel,
        toLevel,
        portName,
        portPackage,
        portFilePath,
        portInterfaceCode,
        adapterRefactorPreview,
        callerRefactorPreview,
        surgicalPrompt,
        targetMethods);
  }

  private ClassNode findClassNode(ArchitectureGraph graph, String qualifiedName) {
    if (graph == null || qualifiedName.isBlank()) {
      return null;
    }
    if (graph.components() != null) {
      for (ComponentNode comp : graph.components()) {
        if (comp.classes() != null) {
          for (ClassNode cls : comp.classes()) {
            if (qualifiedName.equals(cls.id())
                || qualifiedName.equals(cls.name())
                || (cls.packageName() != null
                    && qualifiedName.equals(cls.packageName() + "." + cls.name()))) {
              return cls;
            }
          }
        }
      }
    }
    if (graph.unassigned() != null) {
      for (ClassNode cls : graph.unassigned()) {
        if (qualifiedName.equals(cls.id()) || qualifiedName.equals(cls.name())) {
          return cls;
        }
      }
    }
    return null;
  }

  private Integer resolveLevel(ClassNode node, String className, ArchitecturePolicy policy) {
    if (node != null && node.level() != null) {
      return node.level();
    }
    if (policy != null && policy.levels() != null) {
      List<List<String>> levels = policy.levels();
      for (int i = 0; i < levels.size(); i++) {
        for (String pkg : levels.get(i)) {
          if (className.contains("." + pkg + ".")
              || className.startsWith(pkg + ".")
              || className.contains("/" + pkg + "/")) {
            return i;
          }
        }
      }
    }
    return null;
  }

  private String derivePortName(String toClass) {
    String simple = simpleName(toClass);
    if (simple.endsWith("Impl")) {
      return simple.substring(0, simple.length() - 4) + "Port";
    }
    if (simple.endsWith("Port")) {
      return simple;
    }
    return simple + "Port";
  }

  private String derivePortPackage(String fromClass, ClassNode fromNode) {
    String callerPackage = "";
    if (fromNode != null && fromNode.packageName() != null && !fromNode.packageName().isBlank()) {
      callerPackage = fromNode.packageName();
    } else if (fromClass.contains(".")) {
      callerPackage = fromClass.substring(0, fromClass.lastIndexOf('.'));
    }

    if (callerPackage.isBlank()) {
      return "ports";
    }
    if (callerPackage.endsWith(".port") || callerPackage.endsWith(".ports")) {
      return callerPackage;
    }
    return callerPackage + ".ports";
  }

  private String derivePortFilePath(String portPackage, String portName, ClassNode fromNode) {
    String baseDir = "src/main/java";
    if (fromNode != null && fromNode.filePath() != null && fromNode.filePath().contains("src/")) {
      String path = fromNode.filePath().replace('\\', '/');
      int srcIdx = path.indexOf("src/");
      int javaIdx = path.indexOf("/java/", srcIdx);
      if (javaIdx != -1) {
        baseDir = path.substring(0, javaIdx + 6);
      } else {
        baseDir = path.substring(0, srcIdx + 4);
      }
    }
    if (baseDir.endsWith("/")) {
      baseDir = baseDir.substring(0, baseDir.length() - 1);
    }
    return baseDir + "/" + portPackage.replace('.', '/') + "/" + portName + ".java";
  }

  private List<String> extractMethodSignatures(ClassNode toNode) {
    List<String> signatures = new ArrayList<>();
    if (toNode != null && toNode.methods() != null) {
      for (MethodNode m : toNode.methods()) {
        if (!m.isPrivate()) {
          StringBuilder sb = new StringBuilder();
          String ret =
              (m.returnType() != null && !m.returnType().isBlank()) ? m.returnType() : "void";
          sb.append(ret).append(" ").append(m.name()).append("(");
          if (m.args() != null) {
            for (int i = 0; i < m.args().size(); i++) {
              if (i > 0) sb.append(", ");
              sb.append(m.args().get(i)).append(" arg").append(i);
            }
          }
          sb.append(");");
          signatures.add(sb.toString());
        }
      }
    }
    return signatures;
  }

  private String generatePortInterfaceCode(
      String portPackage,
      String portName,
      String fromClass,
      String toClass,
      Integer fromLevel,
      Integer toLevel,
      List<String> targetMethods) {
    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(portPackage).append(";\n\n");
    sb.append("/**\n");
    sb.append(" * Clean Architecture Interface Port.\n");
    sb.append(" * Synthesized by Archlens to invert outward dependency from:\n");
    sb.append(" *   ")
        .append(fromClass)
        .append(" (Level ")
        .append(fromLevel != null ? fromLevel : "?")
        .append(")\n");
    sb.append(" * to outer concrete implementation:\n");
    sb.append(" *   ")
        .append(toClass)
        .append(" (Level ")
        .append(toLevel != null ? toLevel : "?")
        .append(")\n");
    sb.append(" */\n");
    sb.append("public interface ").append(portName).append(" {\n");

    if (targetMethods.isEmpty()) {
      sb.append("    // TODO: Declare contract methods required by ")
          .append(simpleName(fromClass))
          .append("\n");
    } else {
      for (String methodSig : targetMethods) {
        sb.append("    ").append(methodSig).append("\n");
      }
    }
    sb.append("}\n");
    return sb.toString();
  }

  private String generateAdapterPreview(String toClass, String portPackage, String portName) {
    String adapterPkg = toClass.contains(".") ? toClass.substring(0, toClass.lastIndexOf('.')) : "";
    String toSimple = simpleName(toClass);

    StringBuilder sb = new StringBuilder();
    if (!adapterPkg.isBlank()) {
      sb.append("package ").append(adapterPkg).append(";\n\n");
    }
    sb.append("import ").append(portPackage).append(".").append(portName).append(";\n\n");
    sb.append("public class ")
        .append(toSimple)
        .append(" implements ")
        .append(portName)
        .append(" {\n");
    sb.append("    // Concrete implementation of ").append(portName).append(" operations\n");
    sb.append("}\n");
    return sb.toString();
  }

  private String generateCallerPreview(String fromClass, String portPackage, String portName) {
    String callerPkg =
        fromClass.contains(".") ? fromClass.substring(0, fromClass.lastIndexOf('.')) : "";
    String fromSimple = simpleName(fromClass);
    String fieldName = Character.toLowerCase(portName.charAt(0)) + portName.substring(1);

    StringBuilder sb = new StringBuilder();
    if (!callerPkg.isBlank()) {
      sb.append("package ").append(callerPkg).append(";\n\n");
    }
    sb.append("import ").append(portPackage).append(".").append(portName).append(";\n\n");
    sb.append("public class ").append(fromSimple).append(" {\n");
    sb.append("    // Dependency Inverted: depends on abstraction, not concretion\n");
    sb.append("    private final ").append(portName).append(" ").append(fieldName).append(";\n\n");
    sb.append("    public ")
        .append(fromSimple)
        .append("(")
        .append(portName)
        .append(" ")
        .append(fieldName)
        .append(") {\n");
    sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
    sb.append("    }\n");
    sb.append("}\n");
    return sb.toString();
  }

  private String generateSurgicalPrompt(
      String fromClass,
      String toClass,
      Integer fromLevel,
      Integer toLevel,
      String portName,
      String portPackage,
      String portFilePath,
      String portInterfaceCode,
      String adapterRefactorPreview,
      String callerRefactorPreview) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Clean Architecture DIP Refactoring Directive\n\n");
    sb.append("## Objective\n");
    sb.append(
        "Resolve an outward Clean Architecture violation using the **Dependency Inversion Principle (DIP)**.\n\n");
    sb.append("- **Violating Source**: `")
        .append(fromClass)
        .append("` (Ring Level ")
        .append(fromLevel != null ? fromLevel : "?")
        .append(")\n");
    sb.append("- **Violating Target**: `")
        .append(toClass)
        .append("` (Ring Level ")
        .append(toLevel != null ? toLevel : "?")
        .append(")\n");
    sb.append("- **Synthesized Port**: `")
        .append(portPackage)
        .append(".")
        .append(portName)
        .append("`\n");
    sb.append(
        "- **Dependency Rule**: Source code dependencies must point ONLY inward toward higher-level policies.\n\n");

    sb.append("## Step 1: Create Interface Port\n");
    sb.append("Create file `").append(portFilePath).append("`:\n");
    sb.append("```java\n").append(portInterfaceCode).append("```\n\n");

    sb.append("## Step 2: Implement Interface in Adapter\n");
    sb.append("Update concrete adapter `")
        .append(toClass)
        .append("` to implement `")
        .append(portName)
        .append("`:\n");
    sb.append("```java\n").append(adapterRefactorPreview).append("```\n\n");

    sb.append("## Step 3: Refactor Caller to Depend Exclusively on Port\n");
    sb.append("Update caller `")
        .append(fromClass)
        .append("` to replace concrete reference with `")
        .append(portName)
        .append("`:\n");
    sb.append("```java\n").append(callerRefactorPreview).append("```\n\n");

    sb.append("## Step 4: Verification\n");
    sb.append(
        "Run project test suite (`mvn test`) to verify zero regressions and compile success.\n");
    return sb.toString();
  }

  private String simpleName(String qualifiedName) {
    int idx = qualifiedName.lastIndexOf('.');
    return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
  }
}
