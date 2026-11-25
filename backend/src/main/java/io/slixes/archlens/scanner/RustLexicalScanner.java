package io.slixes.archlens.scanner;

import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.FieldNode;
import io.slixes.archlens.domain.graph.MethodNode;
import io.slixes.archlens.domain.policy.ArchitecturePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RustLexicalScanner implements LanguageScanner {

  private static final Logger LOG = Logger.getLogger(RustLexicalScanner.class);

  private static final Pattern USE_START_PATTERN =
      Pattern.compile("^\\s*(?:pub(?:\\s*\\([^)]*\\))?\\s+)?use\\s+(.+)$");
  private static final Pattern STRUCT_PATTERN =
      Pattern.compile(
          "^\\s*(?:pub(?:\\s*\\([^)]*\\))?\\s+)?(?:struct|enum|trait)\\s+([a-zA-Z0-9_]+)");
  private static final Pattern IMPL_TRAIT_PATTERN =
      Pattern.compile("^\\s*impl(?:<[^>]+>)?\\s+([a-zA-Z0-9_:]+)\\s+for\\s+([a-zA-Z0-9_:]+)");
  private static final Pattern FN_PATTERN =
      Pattern.compile(
          "^\\s*(?:pub(?:\\s*\\([^)]*\\))?\\s+)?(?:async\\s+)?fn\\s+([a-zA-Z0-9_]+)\\s*\\(");

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
    AtomicInteger skippedFiles = new AtomicInteger();
    File rootDir = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    File scanDir = LanguageScanner.resolveScanDirectory(projectRoot, srcRelativePath, "src");
    List<Path> rsFiles = new ArrayList<>();
    if (scanDir.exists()) {
      rsFiles.addAll(
          FileScannerUtil.findFiles(
              scanDir.toPath(), p -> p.toString().endsWith(".rs"), skippedFiles));
    }
    if (rsFiles.isEmpty() && rootDir.exists()) {
      for (Path memberDir : FileScannerUtil.discoverWorkspaceModules(rootDir.toPath())) {
        Path memberSrc = memberDir.resolve("src");
        Path targetScan = Files.isDirectory(memberSrc) ? memberSrc : memberDir;
        rsFiles.addAll(
            FileScannerUtil.findFiles(targetScan, p -> p.toString().endsWith(".rs"), skippedFiles));
      }
    }
    if (rsFiles.isEmpty()) {
      return new ScanResult(List.of(), List.of(), skippedFiles.get());
    }

    List<ClassNode> classes = new ArrayList<>();
    List<DependencyEdge> edges = new ArrayList<>();
    Map<String, String> internalModules = new HashMap<>();

    Path rootPath = rootDir.toPath();
    for (Path rsFile : rsFiles) {
      String rel = rootPath.relativize(rsFile).toString();
      String modId = toRustModuleId(rel);
      internalModules.put(modId, rel);
    }

    for (Path rsFile : rsFiles) {
      List<String> lines;
      try {
        String rawContent = Files.readString(rsFile, StandardCharsets.UTF_8);
        lines = FileScannerUtil.stripCommentsAndRawStrings(rawContent, true);
      } catch (IOException e) {
        LOG.warnf("Skipping unreadable Rust file %s: %s", rsFile, e.getMessage());
        skippedFiles.incrementAndGet();
        continue;
      }
      String rel = rootPath.relativize(rsFile).toString();
      String currentModule = toRustModuleId(rel);
      String packageName = getPackageName(currentModule);
      List<FieldNode> fields = new ArrayList<>();
      List<MethodNode> methods = new ArrayList<>();
      Set<String> importedPaths = new HashSet<>();
      List<String> typeNames = new ArrayList<>();

      StringBuilder pendingUse = new StringBuilder();
      boolean inUseStatement = false;

      for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }

        if (inUseStatement) {
          pendingUse.append(' ').append(trimmed);
          int semi = pendingUse.indexOf(";");
          if (semi >= 0) {
            expandRustUseTree("", pendingUse.substring(0, semi).trim(), importedPaths);
            pendingUse.setLength(0);
            inUseStatement = false;
          }
          continue;
        }

        Matcher useMatcher = USE_START_PATTERN.matcher(line);
        if (useMatcher.find()) {
          String rest = useMatcher.group(1).trim();
          int semi = rest.indexOf(';');
          if (semi >= 0) {
            expandRustUseTree("", rest.substring(0, semi).trim(), importedPaths);
          } else {
            pendingUse.setLength(0);
            pendingUse.append(rest);
            inUseStatement = true;
          }
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

      LanguageScanner.addModuleOrTypeClasses(
          classes,
          typeNames,
          new File(rel).getName().replace(".rs", ""),
          currentModule,
          packageName,
          rel,
          fields,
          methods);

      for (String imported : importedPaths) {
        String normalized = imported.replace("crate::", "").replace("::", ".");
        edges.add(
            new DependencyEdge(
                currentModule, normalized, DependencyEdge.Kind.DEPENDENCY, null, false));
      }
    }

    return new ScanResult(classes, edges, skippedFiles.get());
  }

  private static final int MAX_USE_TREE_DEPTH = 32;

  private record UseTreeFrame(String prefix, String tree, int depth) {}

  static void expandRustUseTree(String rootPrefix, String rootTree, Set<String> out) {
    if (rootTree == null || rootTree.isBlank()) {
      return;
    }
    Deque<UseTreeFrame> stack = new ArrayDeque<>();
    stack.push(new UseTreeFrame(rootPrefix, rootTree, 0));

    while (!stack.isEmpty()) {
      UseTreeFrame frame = stack.pop();
      if (frame.depth() > MAX_USE_TREE_DEPTH || frame.tree().isBlank()) {
        continue;
      }
      String tree = frame.tree();
      String prefix = frame.prefix();

      int braceDepth = 0;
      int start = 0;
      List<String> parts = new ArrayList<>();
      for (int i = 0; i < tree.length(); i++) {
        char c = tree.charAt(i);
        if (c == '{') {
          braceDepth++;
        } else if (c == '}') {
          braceDepth = Math.max(0, braceDepth - 1);
        } else if (c == ',' && braceDepth == 0) {
          parts.add(tree.substring(start, i));
          start = i + 1;
        }
      }
      parts.add(tree.substring(start));

      for (int idx = parts.size() - 1; idx >= 0; idx--) {
        String part = parts.get(idx).trim();
        if (part.isEmpty()) {
          continue;
        }
        int openBrace = part.indexOf('{');
        int closeBrace = part.lastIndexOf('}');
        if (openBrace >= 0 && closeBrace > openBrace) {
          String subPrefix = part.substring(0, openBrace).trim();
          if (subPrefix.endsWith("::")) {
            subPrefix = subPrefix.substring(0, subPrefix.length() - 2).trim();
          }
          String combined = joinRustPrefix(prefix, subPrefix);
          stack.push(
              new UseTreeFrame(
                  combined, part.substring(openBrace + 1, closeBrace), frame.depth() + 1));
          continue;
        }
        int asIdx = part.indexOf(" as ");
        if (asIdx > 0) {
          part = part.substring(0, asIdx).trim();
        }
        if (part.startsWith("::")) {
          part = part.substring(2).trim();
        }
        if ("self".equals(part) || "*".equals(part)) {
          if (!prefix.isEmpty()) {
            out.add(prefix);
          }
        } else if (!part.isEmpty()) {
          out.add(joinRustPrefix(prefix, part));
        }
      }
    }
  }

  private static String joinRustPrefix(String prefix, String segment) {
    if (prefix == null || prefix.isEmpty()) {
      return segment;
    }
    if (segment == null || segment.isEmpty()) {
      return prefix;
    }
    return prefix + "::" + segment;
  }

  private String toRustModuleId(String relPath) {
    String mod = relPath.replace('\\', '/');
    if (mod.startsWith("src/")) {
      mod = mod.substring(4);
    } else {
      mod = mod.replace("/src/", "/");
    }
    if (mod.endsWith(".rs")) {
      mod = mod.substring(0, mod.length() - 3);
    }
    if (mod.endsWith("/mod") || mod.endsWith("/lib") || mod.endsWith("/main")) {
      int lastSlash = mod.lastIndexOf('/');
      if (lastSlash > 0) {
        mod = mod.substring(0, lastSlash);
      }
    }
    return mod.replace('/', '.');
  }

  private String getPackageName(String moduleId) {
    int idx = moduleId.lastIndexOf('.');
    return idx > 0 ? moduleId.substring(0, idx) : moduleId;
  }
}
