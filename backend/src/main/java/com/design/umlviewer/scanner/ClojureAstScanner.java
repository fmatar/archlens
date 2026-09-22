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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ClojureAstScanner implements LanguageScanner {

  private static final Pattern DEFN_PATTERN =
      Pattern.compile(
          "^\\s*\\((defn|defn-)\\s+([a-zA-Z0-9*+!?'<>=/-]+)(?:\\s+\"[^\"]*\")?\\s*(?:\\[([^\\]]*)\\])?");
  private static final Pattern DEF_PATTERN =
      Pattern.compile("^\\s*\\(def\\s+([a-zA-Z0-9*+!?'<>=/-]+)");

  @Override
  public String languageId() {
    return "clojure";
  }

  @Override
  public boolean supports(String projectRoot, ArchitecturePolicy policy) {
    if (policy != null
        && policy.lang() != null
        && "clojure".equalsIgnoreCase(policy.lang().replace(":", ""))) {
      return true;
    }
    if (projectRoot == null || projectRoot.isBlank()) {
      return false;
    }
    File root = new File(projectRoot);
    return new File(root, "examples/uml-viewer.edn").exists()
        || new File(root, "examples/uml-viewer.policy.edn").exists()
        || new File(root, "deps.edn").exists()
        || new File(root, "src/uml_viewer").exists();
  }

  @Override
  public ScanResult scanProject(String projectRoot, String srcRelativePath, String basePrefix)
      throws IOException {
    File root = new File(projectRoot != null ? projectRoot : ".");
    File ednFile = new File(root, "examples/uml-viewer.edn");

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();

    if (ednFile.exists()) {
      String content = Files.readString(ednFile.toPath());
      parseClassesAndEdgesFromEdn(root, content, classes, edges);
    } else {
      // Fallback: scan src directory for .clj files
      File srcDir =
          new File(
              root,
              srcRelativePath != null && !srcRelativePath.isBlank() ? srcRelativePath : "src");
      if (srcDir.exists()) {
        scanClojureDirectory(srcDir, classes);
      }
    }

    return new ScanResult(classes, edges);
  }

  private void parseClassesAndEdgesFromEdn(
      File root, String content, List<ClassNode> classes, List<DependencyEdge> edges) {

    // Parse edges: {:from :<from>, :to :<to>, :kind :<kind>}
    Pattern edgePattern =
        Pattern.compile(
            "\\{:from :([a-zA-Z0-9_.-]+),\\s*:to :([a-zA-Z0-9_.-]+),\\s*:kind :([a-zA-Z0-9_.-]+)\\}");
    Matcher edgeMatcher = edgePattern.matcher(content);
    while (edgeMatcher.find()) {
      String from = edgeMatcher.group(1);
      String to = edgeMatcher.group(2);
      String kindStr = edgeMatcher.group(3);

      DependencyEdge.Kind kind = DependencyEdge.Kind.DEPENDENCY;
      if ("implements".equalsIgnoreCase(kindStr)) {
        kind = DependencyEdge.Kind.IMPLEMENTS;
      } else if ("association".equalsIgnoreCase(kindStr)) {
        kind = DependencyEdge.Kind.ASSOCIATION;
      }

      edges.add(new DependencyEdge(from, to, kind, null, false));
    }

    // Parse classes: {:id :<id>, ...}
    Pattern classBlockPattern = Pattern.compile("\\{:id :([a-zA-Z0-9_.-]+)([^}]+)\\}");
    Matcher classMatcher = classBlockPattern.matcher(content);
    while (classMatcher.find()) {
      String id = classMatcher.group(1);
      String body = classMatcher.group(2);

      boolean isForeign = body.contains(":foreign true");
      String name = extractStringField(body, ":name");
      if (name == null) name = id;

      String ns = extractStringField(body, ":ns");
      String pkg = "domain";
      if (id.contains(".")) {
        pkg = id.substring(0, id.indexOf('.'));
      } else if (ns != null && ns.contains(".")) {
        String cleanNs = ns.replace("uml-viewer.", "");
        if (cleanNs.contains(".")) {
          pkg = cleanNs.substring(0, cleanNs.indexOf('.'));
        } else {
          pkg = cleanNs;
        }
      } else {
        pkg = id;
      }

      Integer level = null;
      Pattern levelPattern = Pattern.compile(":level\\s+([0-9]+)");
      Matcher lm = levelPattern.matcher(body);
      if (lm.find()) {
        level = Integer.parseInt(lm.group(1));
      }

      ClassNode.Stereotype stereotype = ClassNode.Stereotype.CLASS;
      if (body.contains(":stereotype :interface")) {
        stereotype = ClassNode.Stereotype.INTERFACE;
      }

      List<MethodNode> methods = new ArrayList<>();
      List<FieldNode> fields = new ArrayList<>();
      String resolvedFilePath = "";

      if (!isForeign && ns != null) {
        // Resolve source file in src/
        String relNs = ns.replace("uml-viewer.", "");
        String filePath = "src/uml_viewer/" + relNs.replace('.', '/').replace('-', '_') + ".clj";
        File cljFile = new File(root, filePath);
        if (!cljFile.exists()) {
          cljFile = new File(root, "src/" + relNs.replace('.', '/').replace('-', '_') + ".clj");
        }

        if (cljFile.exists()) {
          resolvedFilePath = cljFile.getAbsolutePath();
          extractClojureMembers(cljFile, methods, fields);
        }
      }

      classes.add(
          new ClassNode(
              id,
              name,
              pkg,
              resolvedFilePath,
              stereotype,
              isForeign,
              level,
              new CrapScore(1.2, 3.0, 0.5),
              0.88,
              Math.max(1, methods.size()),
              methods.size() * 3,
              0,
              0,
              fields,
              methods));
    }
  }

  private void extractClojureMembers(
      File cljFile, List<MethodNode> methods, List<FieldNode> fields) {
    try {
      List<String> lines = Files.readAllLines(cljFile.toPath());
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        Matcher defnMatcher = DEFN_PATTERN.matcher(line);
        if (defnMatcher.find()) {
          boolean isPrivate = "defn-".equals(defnMatcher.group(1));
          String fnName = defnMatcher.group(2);
          String rawArgs = defnMatcher.group(3);
          List<String> argsList = new ArrayList<>();
          if (rawArgs != null && !rawArgs.isBlank()) {
            for (String arg : rawArgs.trim().split("\\s+")) {
              if (!arg.isBlank()) argsList.add(arg);
            }
          }
          methods.add(
              new MethodNode(
                  fnName,
                  argsList,
                  "Object",
                  isPrivate,
                  Math.max(1, argsList.size()),
                  0.9,
                  1.5,
                  argsList.size() * 2,
                  0,
                  0,
                  i + 1));
          continue;
        }

        Matcher defMatcher = DEF_PATTERN.matcher(line);
        if (defMatcher.find()) {
          String varName = defMatcher.group(1);
          fields.add(new FieldNode(varName, "Object", false));
        }
      }
    } catch (IOException ignored) {
      // Gracefully continue on unreadable files
    }
  }

  private void scanClojureDirectory(File dir, List<ClassNode> classes) {
    try (var stream = Files.walk(dir.toPath())) {
      stream
          .filter(p -> p.toString().endsWith(".clj"))
          .forEach(
              path -> {
                File file = path.toFile();
                String name = file.getName().replace(".clj", "");
                List<MethodNode> methods = new ArrayList<>();
                List<FieldNode> fields = new ArrayList<>();
                extractClojureMembers(file, methods, fields);

                classes.add(
                    new ClassNode(
                        name,
                        name,
                        "clojure",
                        file.getAbsolutePath(),
                        ClassNode.Stereotype.CLASS,
                        false,
                        0,
                        CrapScore.zero(),
                        0.85,
                        methods.size(),
                        0,
                        0,
                        0,
                        fields,
                        methods));
              });
    } catch (IOException ignored) {
    }
  }

  private String extractStringField(String block, String fieldKey) {
    Pattern p = Pattern.compile(fieldKey + "\\s+\"([^\"]+)\"");
    Matcher m = p.matcher(block);
    return m.find() ? m.group(1) : null;
  }
}
