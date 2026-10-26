package io.slixes.archlens.domain.dossier;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.graph.MethodNode;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    String lang = resolveLanguage(policy, fromNode, toNode);

    String effectiveTargetName =
        (toNode != null
                && toNode.name() != null
                && !toNode.name().isBlank()
                && simpleName(toClass).contains("_"))
            ? toNode.name()
            : toClass;
    String portName = derivePortName(effectiveTargetName);
    String portPackage = derivePortPackage(fromClass, fromNode);
    String portFilePath = derivePortFilePath(lang, portPackage, portName, fromNode, policy);

    List<String> targetMethods = extractMethodSignatures(lang, toNode);
    String portInterfaceCode =
        generatePortInterfaceCode(
            lang, portPackage, portName, fromClass, toClass, fromLevel, toLevel, targetMethods);

    String adapterRefactorPreview =
        generateAdapterPreview(lang, toClass, toNode, portPackage, portName);
    String callerRefactorPreview =
        generateCallerPreview(lang, fromClass, fromNode, portPackage, portName);

    String surgicalPrompt =
        generateSurgicalPrompt(
            lang,
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

  private String resolveLanguage(ArchitecturePolicy policy, ClassNode fromNode, ClassNode toNode) {
    if (policy != null && policy.lang() != null && !policy.lang().isBlank()) {
      String l = policy.lang().trim().toLowerCase(Locale.ROOT);
      if (l.equals("python") || l.equals("py")) return "python";
      if (l.equals("typescript") || l.equals("ts") || l.equals("javascript") || l.equals("js")) {
        return "typescript";
      }
      if (l.equals("go") || l.equals("golang")) return "go";
      if (l.equals("rust") || l.equals("rs")) return "rust";
      if (l.equals("java") || l.equals("kotlin")) return "java";
    }
    for (ClassNode node : new ClassNode[] {fromNode, toNode}) {
      if (node != null && node.filePath() != null) {
        String p = node.filePath().toLowerCase(Locale.ROOT);
        if (p.endsWith(".py")) return "python";
        if (p.endsWith(".ts")
            || p.endsWith(".tsx")
            || p.endsWith(".js")
            || p.endsWith(".jsx")
            || p.endsWith(".svelte")) {
          return "typescript";
        }
        if (p.endsWith(".go")) return "go";
        if (p.endsWith(".rs")) return "rust";
      }
    }
    return "java";
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
                    && qualifiedName.equals(cls.packageName() + "." + cls.name()))
                || (cls.id() != null && cls.id().startsWith(qualifiedName + "."))) {
              return cls;
            }
          }
          if (qualifiedName.equals(comp.id()) && !comp.classes().isEmpty()) {
            return comp.classes().get(0);
          }
        }
      }
    }
    if (graph.unassigned() != null) {
      for (ClassNode cls : graph.unassigned()) {
        if (qualifiedName.equals(cls.id())
            || qualifiedName.equals(cls.name())
            || (cls.id() != null && cls.id().startsWith(qualifiedName + "."))) {
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
    if (simple.contains("_")) {
      simple = toPascalCase(simple);
    }
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

  private String derivePortFilePath(
      String lang,
      String portPackage,
      String portName,
      ClassNode fromNode,
      ArchitecturePolicy policy) {
    if ("python".equals(lang)) {
      String baseDir = resolveNonJavaBaseDir(fromNode, policy, "src");
      return baseDir + "/" + portPackage.replace('.', '/') + "/" + toSnakeCase(portName) + ".py";
    }
    if ("typescript".equals(lang)) {
      String baseDir = resolveNonJavaBaseDir(fromNode, policy, "src");
      return baseDir + "/" + portPackage.replace('.', '/') + "/" + portName + ".ts";
    }
    if ("go".equals(lang)) {
      String baseDir = resolveNonJavaBaseDir(fromNode, policy, "internal");
      return baseDir + "/" + portPackage.replace('.', '/') + "/" + toSnakeCase(portName) + ".go";
    }
    if ("rust".equals(lang)) {
      String baseDir = resolveNonJavaBaseDir(fromNode, policy, "src");
      return baseDir + "/" + portPackage.replace('.', '/') + "/" + toSnakeCase(portName) + ".rs";
    }

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

  private String resolveNonJavaBaseDir(
      ClassNode fromNode, ArchitecturePolicy policy, String defaultDir) {
    if (fromNode != null && fromNode.filePath() != null && fromNode.filePath().contains("src/")) {
      String path = fromNode.filePath().replace('\\', '/');
      int srcIdx = path.indexOf("src/");
      String base = path.substring(0, srcIdx + 3);
      return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
    if (policy != null && policy.src() != null && !policy.src().isBlank()) {
      String s = policy.src().replace('\\', '/');
      return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
    return defaultDir;
  }

  private List<String> extractMethodSignatures(String lang, ClassNode toNode) {
    List<String> signatures = new ArrayList<>();
    if (toNode != null && toNode.methods() != null) {
      for (MethodNode m : toNode.methods()) {
        if (m.isPrivate()) {
          continue;
        }
        if ("python".equals(lang) && m.name().startsWith("__")) {
          continue;
        }
        if ("typescript".equals(lang) && "constructor".equals(m.name())) {
          continue;
        }

        if ("python".equals(lang)) {
          StringBuilder sb = new StringBuilder();
          String ret =
              (m.returnType() != null
                      && !m.returnType().isBlank()
                      && !"void".equalsIgnoreCase(m.returnType()))
                  ? m.returnType()
                  : "None";
          sb.append("def ").append(m.name()).append("(self");
          if (m.args() != null) {
            for (int i = 0; i < m.args().size(); i++) {
              String arg = m.args().get(i).trim();
              if (arg.isEmpty()) continue;
              sb.append(", ");
              if (arg.contains(":") || arg.contains("=")) {
                sb.append(arg);
              } else {
                sb.append("arg").append(i).append(": ").append(arg);
              }
            }
          }
          sb.append(") -> ").append(ret).append(": ...");
          signatures.add(sb.toString());
        } else if ("typescript".equals(lang)) {
          StringBuilder sb = new StringBuilder();
          String ret =
              (m.returnType() != null && !m.returnType().isBlank()) ? m.returnType() : "void";
          sb.append(m.name()).append("(");
          if (m.args() != null) {
            for (int i = 0; i < m.args().size(); i++) {
              if (i > 0) sb.append(", ");
              String arg = m.args().get(i).trim();
              if (arg.contains(":")) {
                sb.append(arg);
              } else {
                sb.append("arg").append(i).append(": ").append(arg);
              }
            }
          }
          sb.append("): ").append(ret).append(";");
          signatures.add(sb.toString());
        } else if ("go".equals(lang)) {
          StringBuilder sb = new StringBuilder();
          String ret =
              (m.returnType() != null
                      && !m.returnType().isBlank()
                      && !"void".equalsIgnoreCase(m.returnType()))
                  ? " " + m.returnType()
                  : "";
          sb.append(toPascalCase(m.name())).append("(");
          if (m.args() != null) {
            for (int i = 0; i < m.args().size(); i++) {
              if (i > 0) sb.append(", ");
              String arg = m.args().get(i).trim();
              if (arg.contains(" ")) {
                sb.append(arg);
              } else {
                sb.append("arg").append(i).append(" ").append(arg);
              }
            }
          }
          sb.append(")").append(ret);
          signatures.add(sb.toString());
        } else if ("rust".equals(lang)) {
          StringBuilder sb = new StringBuilder();
          String ret =
              (m.returnType() != null
                      && !m.returnType().isBlank()
                      && !"void".equalsIgnoreCase(m.returnType()))
                  ? " -> " + m.returnType()
                  : "";
          sb.append("fn ").append(toSnakeCase(m.name())).append("(&self");
          if (m.args() != null) {
            for (int i = 0; i < m.args().size(); i++) {
              String arg = m.args().get(i).trim();
              if (arg.isEmpty()) continue;
              sb.append(", ");
              if (arg.contains(":")) {
                sb.append(arg);
              } else {
                sb.append("arg").append(i).append(": ").append(arg);
              }
            }
          }
          sb.append(")").append(ret).append(";");
          signatures.add(sb.toString());
        } else {
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
      String lang,
      String portPackage,
      String portName,
      String fromClass,
      String toClass,
      Integer fromLevel,
      Integer toLevel,
      List<String> targetMethods) {
    if ("python".equals(lang)) {
      StringBuilder sb = new StringBuilder();
      sb.append("from typing import Protocol\n\n\n");
      sb.append("class ").append(portName).append("(Protocol):\n");
      sb.append("    \"\"\"Clean Architecture Interface Port.\n");
      sb.append("    Synthesized by Archlens to invert outward dependency from:\n");
      sb.append("      ")
          .append(fromClass)
          .append(" (Level ")
          .append(fromLevel != null ? fromLevel : "?")
          .append(")\n");
      sb.append("    to outer concrete implementation:\n");
      sb.append("      ")
          .append(toClass)
          .append(" (Level ")
          .append(toLevel != null ? toLevel : "?")
          .append(")\n");
      sb.append("    \"\"\"\n");
      if (targetMethods.isEmpty()) {
        sb.append("    # TODO: Declare contract methods required by ")
            .append(simpleName(fromClass))
            .append("\n");
        sb.append("    ...\n");
      } else {
        for (String methodSig : targetMethods) {
          sb.append("    ").append(methodSig).append("\n");
        }
      }
      return sb.toString();
    }

    if ("typescript".equals(lang)) {
      StringBuilder sb = new StringBuilder();
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
      sb.append("export interface ").append(portName).append(" {\n");
      if (targetMethods.isEmpty()) {
        sb.append("  // TODO: Declare contract methods required by ")
            .append(simpleName(fromClass))
            .append("\n");
      } else {
        for (String methodSig : targetMethods) {
          sb.append("  ").append(methodSig).append("\n");
        }
      }
      sb.append("}\n");
      return sb.toString();
    }

    if ("go".equals(lang)) {
      String pkgSimple = simpleName(portPackage);
      StringBuilder sb = new StringBuilder();
      sb.append("package ").append(pkgSimple.isBlank() ? "ports" : pkgSimple).append("\n\n");
      sb.append("// ").append(portName).append(" is a Clean Architecture Interface Port.\n");
      sb.append("type ").append(portName).append(" interface {\n");
      if (targetMethods.isEmpty()) {
        sb.append("\t// TODO: Declare contract methods required by ")
            .append(simpleName(fromClass))
            .append("\n");
      } else {
        for (String methodSig : targetMethods) {
          sb.append("\t").append(methodSig).append("\n");
        }
      }
      sb.append("}\n");
      return sb.toString();
    }

    if ("rust".equals(lang)) {
      StringBuilder sb = new StringBuilder();
      sb.append("/// Clean Architecture Interface Port.\n");
      sb.append("pub trait ").append(portName).append(" {\n");
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

  private String generateAdapterPreview(
      String lang, String toClass, ClassNode toNode, String portPackage, String portName) {
    String toSimple =
        (toNode != null && toNode.name() != null && !toNode.name().isBlank())
            ? toNode.name()
            : simpleName(toClass);
    if ("python".equals(lang)) {
      String snakePort = toSnakeCase(portName);
      String clsName = toSimple.contains("_") ? toPascalCase(toSimple) : toSimple;
      return "from "
          + portPackage
          + "."
          + snakePort
          + " import "
          + portName
          + "\n\n\nclass "
          + clsName
          + "("
          + portName
          + "):\n    # Concrete implementation of "
          + portName
          + " operations\n    pass\n";
    }
    if ("typescript".equals(lang)) {
      String importPath = portPackage.replace('.', '/') + "/" + portName;
      return "import type { "
          + portName
          + " } from '"
          + importPath
          + "';\n\nexport class "
          + toSimple
          + " implements "
          + portName
          + " {\n  // Concrete implementation of "
          + portName
          + " operations\n}\n";
    }
    if ("go".equals(lang)) {
      return "// Ensure "
          + toSimple
          + " satisfies "
          + portName
          + "\nvar _ "
          + portName
          + " = (*"
          + toSimple
          + ")(nil)\n";
    }
    if ("rust".equals(lang)) {
      return "impl "
          + portName
          + " for "
          + toSimple
          + " {\n    // Concrete implementation of "
          + portName
          + " operations\n}\n";
    }

    String adapterPkg = toClass.contains(".") ? toClass.substring(0, toClass.lastIndexOf('.')) : "";
    String javaSimple = simpleName(toClass);

    StringBuilder sb = new StringBuilder();
    if (!adapterPkg.isBlank()) {
      sb.append("package ").append(adapterPkg).append(";\n\n");
    }
    sb.append("import ").append(portPackage).append(".").append(portName).append(";\n\n");
    sb.append("public class ")
        .append(javaSimple)
        .append(" implements ")
        .append(portName)
        .append(" {\n");
    sb.append("    // Concrete implementation of ").append(portName).append(" operations\n");
    sb.append("}\n");
    return sb.toString();
  }

  private String generateCallerPreview(
      String lang, String fromClass, ClassNode fromNode, String portPackage, String portName) {
    String fromSimple =
        (fromNode != null && fromNode.name() != null && !fromNode.name().isBlank())
            ? fromNode.name()
            : simpleName(fromClass);
    String fieldName =
        portName.isEmpty()
            ? "port"
            : Character.toLowerCase(portName.charAt(0)) + portName.substring(1);

    if ("python".equals(lang)) {
      String snakePort = toSnakeCase(portName);
      String clsName = fromSimple.contains("_") ? toPascalCase(fromSimple) : fromSimple;
      return "from "
          + portPackage
          + "."
          + snakePort
          + " import "
          + portName
          + "\n\n\nclass "
          + clsName
          + ":\n    # Dependency Inverted: depends on Protocol abstraction, not concretion\n    def __init__(self, "
          + snakePort
          + ": "
          + portName
          + ") -> None:\n        self._"
          + snakePort
          + " = "
          + snakePort
          + "\n";
    }
    if ("typescript".equals(lang)) {
      String importPath = portPackage.replace('.', '/') + "/" + portName;
      return "import type { "
          + portName
          + " } from '"
          + importPath
          + "';\n\nexport class "
          + fromSimple
          + " {\n  // Dependency Inverted: depends on abstraction, not concretion\n  constructor(private readonly "
          + fieldName
          + ": "
          + portName
          + ") {}\n}\n";
    }
    if ("go".equals(lang)) {
      return "type "
          + fromSimple
          + " struct {\n\t// Dependency Inverted: depends on abstraction, not concretion\n\t"
          + fieldName
          + " "
          + portName
          + "\n}\n";
    }
    if ("rust".equals(lang)) {
      return "pub struct "
          + fromSimple
          + "<P: "
          + portName
          + "> {\n    // Dependency Inverted: depends on abstraction, not concretion\n    "
          + toSnakeCase(portName)
          + ": P,\n}\n";
    }

    String callerPkg =
        fromClass.contains(".") ? fromClass.substring(0, fromClass.lastIndexOf('.')) : "";
    String javaSimple = simpleName(fromClass);

    StringBuilder sb = new StringBuilder();
    if (!callerPkg.isBlank()) {
      sb.append("package ").append(callerPkg).append(";\n\n");
    }
    sb.append("import ").append(portPackage).append(".").append(portName).append(";\n\n");
    sb.append("public class ").append(javaSimple).append(" {\n");
    sb.append("    // Dependency Inverted: depends on abstraction, not concretion\n");
    sb.append("    private final ").append(portName).append(" ").append(fieldName).append(";\n\n");
    sb.append("    public ")
        .append(javaSimple)
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
      String lang,
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
    String codeFence = lang;
    String testCommand =
        switch (lang) {
          case "python" -> "pytest";
          case "typescript" -> "npm test";
          case "go" -> "go test ./...";
          case "rust" -> "cargo test";
          default -> "mvn test";
        };

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
    sb.append("```").append(codeFence).append("\n").append(portInterfaceCode).append("```\n\n");

    sb.append("## Step 2: Implement Interface in Adapter\n");
    sb.append("Update concrete adapter `")
        .append(toClass)
        .append("` to implement `")
        .append(portName)
        .append("`:\n");
    sb.append("```")
        .append(codeFence)
        .append("\n")
        .append(adapterRefactorPreview)
        .append("```\n\n");

    sb.append("## Step 3: Refactor Caller to Depend Exclusively on Port\n");
    sb.append("Update caller `")
        .append(fromClass)
        .append("` to replace concrete reference with `")
        .append(portName)
        .append("`:\n");
    sb.append("```").append(codeFence).append("\n").append(callerRefactorPreview).append("```\n\n");

    sb.append("## Step 4: Verification\n");
    sb.append("Run project test suite (`")
        .append(testCommand)
        .append("`) to verify zero regressions and compile success.\n");
    return sb.toString();
  }

  private String toSnakeCase(String input) {
    if (input == null || input.isBlank()) {
      return "port";
    }
    return input
        .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .toLowerCase(Locale.ROOT);
  }

  private String toPascalCase(String input) {
    if (input == null || input.isBlank()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (String part : input.split("[_\\-]+")) {
      if (!part.isEmpty()) {
        sb.append(Character.toUpperCase(part.charAt(0)));
        if (part.length() > 1) {
          sb.append(part.substring(1));
        }
      }
    }
    return sb.toString();
  }

  private String simpleName(String qualifiedName) {
    int idx = qualifiedName.lastIndexOf('.');
    return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
  }
}
