package com.design.umlviewer.engine;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.model.CrapScore;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.domain.policy.DependencyRuleValidator;
import com.design.umlviewer.domain.policy.Proposal;
import com.design.umlviewer.scanner.LanguageScanner;
import com.design.umlviewer.scanner.LanguageScannerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class GraphCompiler {

  @Inject LanguageScannerRegistry scannerRegistry;

  private final ObjectMapper mapper = new ObjectMapper();

  private static final String PRIMARY_CONFIG_DIR = ".archlens";
  private static final String LEGACY_CONFIG_DIR = ".uml-viewer";
  private static final String POLICY_FILENAME = "policy.json";

  private File resolvePolicyFile(String projectRoot) {
    if (projectRoot != null && !projectRoot.isBlank()) {
      File primary = new File(projectRoot, PRIMARY_CONFIG_DIR + "/" + POLICY_FILENAME);
      if (primary.exists()) {
        return primary;
      }
      File legacy = new File(projectRoot, LEGACY_CONFIG_DIR + "/" + POLICY_FILENAME);
      if (legacy.exists()) {
        return legacy;
      }
      return primary;
    }
    File primaryLocal = new File(PRIMARY_CONFIG_DIR, POLICY_FILENAME);
    if (primaryLocal.exists()) {
      return primaryLocal;
    }
    File legacyLocal = new File(LEGACY_CONFIG_DIR, POLICY_FILENAME);
    if (legacyLocal.exists()) {
      return legacyLocal;
    }
    File primaryParent = new File("../" + PRIMARY_CONFIG_DIR, POLICY_FILENAME);
    if (primaryParent.exists()) {
      return primaryParent;
    }
    File legacyParent = new File("../" + LEGACY_CONFIG_DIR, POLICY_FILENAME);
    if (legacyParent.exists()) {
      return legacyParent;
    }
    return primaryLocal;
  }

  public ArchitecturePolicy loadPolicy(String projectRoot) {
    File policyFile = resolvePolicyFile(projectRoot);
    if (policyFile != null && policyFile.exists()) {
      try {
        return mapper.readValue(policyFile, ArchitecturePolicy.class);
      } catch (Exception e) {
        System.err.println(
            "Error reading policy from " + policyFile.getAbsolutePath() + ": " + e.getMessage());
        e.printStackTrace();
      }
    }

    File root = new File(projectRoot != null && !projectRoot.isBlank() ? projectRoot : ".");
    File policyEdn = new File(root, "examples/uml-viewer.policy.edn");
    if (policyEdn.exists()) {
      return new ArchitecturePolicy(
          "Uncle Bob UML Viewer",
          "src",
          "uml-viewer",
          true,
          List.of(
              "domain",
              "source",
              "graph",
              "clojure-language",
              "engine",
              "application",
              "adapters",
              "main"),
          List.of(
              List.of("domain", "source", "graph", "clojure-language"),
              List.of("engine"),
              List.of("application"),
              List.of("adapters"),
              List.of("main")),
          List.of("quil", "javax.swing"),
          List.of(
              new Proposal(
                  "clean-core",
                  "Clean Architecture Standard",
                  List.of(
                      new Proposal.ProposalLayer(
                          "domain",
                          "Level 0: Domain & Interfaces",
                          List.of("domain", "source", "graph", "clojure-language"),
                          List.of()),
                      new Proposal.ProposalLayer(
                          "engine", "Level 1: Layout Engine", List.of("engine"), List.of()),
                      new Proposal.ProposalLayer(
                          "application",
                          "Level 2: Application Use Cases",
                          List.of("application"),
                          List.of()),
                      new Proposal.ProposalLayer(
                          "adapters",
                          "Level 3: Quil & UI Adapters",
                          List.of("adapters"),
                          List.of()),
                      new Proposal.ProposalLayer(
                          "main", "Level 4: Main Entrypoint", List.of("main"), List.of())),
                  List.of())),
          List.of());
    }

    String projectTitle = root.getName().equals(".") ? "Current Workspace" : root.getName();

    return new ArchitecturePolicy(
        projectTitle,
        "src/main/java",
        null,
        true,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  public static String computeCommonPrefix(List<ClassNode> classes) {
    if (classes == null || classes.isEmpty()) {
      return "";
    }
    List<String> pkgs =
        classes.stream()
            .map(ClassNode::packageName)
            .filter(p -> p != null && !p.isBlank())
            .distinct()
            .toList();
    if (pkgs.isEmpty()) {
      return "";
    }
    if (pkgs.size() == 1) {
      String only = pkgs.get(0);
      int lastDot = only.lastIndexOf('.');
      return lastDot > 0 ? only.substring(0, lastDot) : only;
    }

    String[] parts = pkgs.get(0).split("\\.");
    int commonSegments = parts.length;
    for (int i = 1; i < pkgs.size(); i++) {
      String[] cur = pkgs.get(i).split("\\.");
      int match = 0;
      while (match < commonSegments && match < cur.length && parts[match].equals(cur[match])) {
        match++;
      }
      commonSegments = match;
      if (commonSegments == 0) {
        break;
      }
    }
    if (commonSegments == 0) {
      return "";
    }
    return String.join(".", java.util.Arrays.copyOf(parts, commonSegments));
  }

  public ArchitectureGraph compileGraph(String projectRoot, String proposalId) throws IOException {
    ArchitecturePolicy policy = loadPolicy(projectRoot);
    LanguageScanner scanner =
        scannerRegistry != null
            ? scannerRegistry.resolveScanner(projectRoot, policy)
            : new com.design.umlviewer.scanner.JavaAstScanner();

    Proposal activeProposal = null;
    if (proposalId != null && policy.proposals() != null) {
      activeProposal =
          policy.proposals().stream()
              .filter(p -> p.id().equals(proposalId))
              .findFirst()
              .orElse(null);
    }

    LanguageScanner.ScanResult scan =
        scanner != null
            ? scanner.scanProject(
                projectRoot,
                policy.src() != null ? policy.src() : "",
                policy.prefix() != null ? policy.prefix() : "",
                policy)
            : new LanguageScanner.ScanResult(List.of(), List.of());

    Set<String> omitPatterns = new HashSet<>();
    if (policy.omit() != null) {
      omitPatterns.addAll(policy.omit());
    }
    if (activeProposal != null && activeProposal.omit() != null) {
      omitPatterns.addAll(activeProposal.omit());
    }

    List<ClassNode> validClasses =
        scan.classes().stream().filter(c -> !matchesOmit(c, omitPatterns)).toList();

    DependencyRuleValidator validator =
        (activeProposal != null)
            ? DependencyRuleValidator.fromProposal(activeProposal)
            : DependencyRuleValidator.fromLevels(policy.levels());

    // Group into components
    Map<String, List<ClassNode>> pkgMap =
        validClasses.stream().collect(Collectors.groupingBy(ClassNode::packageName));

    List<ComponentNode> components = new ArrayList<>();

    if (activeProposal != null) {
      // Build virtual components as defined by proposal
      for (Proposal.ProposalLayer layer : activeProposal.layers()) {
        List<ClassNode> layerClasses = new ArrayList<>();
        if (layer.packages() != null) {
          for (String pkg : layer.packages()) {
            pkgMap.forEach(
                (k, v) -> {
                  if (k.equals(pkg)
                      || k.startsWith(pkg + ".")
                      || k.endsWith("." + pkg)
                      || k.contains("." + pkg + ".")) {
                    layerClasses.addAll(v);
                  }
                });
          }
        }
        if (layer.classes() != null) {
          for (String cls : layer.classes()) {
            validClasses.stream()
                .filter(
                    c -> c.name().equals(cls) || c.id().endsWith("." + cls) || c.id().equals(cls))
                .forEach(
                    c -> {
                      if (!layerClasses.contains(c)) {
                        layerClasses.add(c);
                      }
                    });
          }
        }
        Integer level = validator.resolveRank(layer.id());
        components.add(
            new ComponentNode(
                layer.id(),
                layer.label(),
                level,
                CrapScore.zero(),
                0.9,
                layer.packages() != null ? layer.packages() : List.of(),
                layerClasses));
      }
    } else {
      // Group according to packages
      String effectivePrefix = policy.prefix();
      if ((effectivePrefix == null
              || effectivePrefix.isBlank()
              || "com.design".equals(effectivePrefix))
          && !validClasses.isEmpty()) {
        effectivePrefix = computeCommonPrefix(validClasses);
      }

      for (Map.Entry<String, List<ClassNode>> entry : pkgMap.entrySet()) {
        String pkgName = entry.getKey();
        String shortId = pkgName;
        if (effectivePrefix != null && !effectivePrefix.isBlank()) {
          if (pkgName.startsWith(effectivePrefix + ".")) {
            shortId = pkgName.substring(effectivePrefix.length() + 1);
          } else if (pkgName.equals(effectivePrefix)) {
            shortId = pkgName;
          }
        }
        if (shortId.isBlank()) {
          shortId = pkgName;
        }
        Integer level = validator.resolveRank(shortId);

        components.add(
            new ComponentNode(
                shortId,
                shortId,
                level,
                CrapScore.zero(),
                0.88,
                List.of(pkgName),
                entry.getValue()));
      }

      if (policy.order() != null && !policy.order().isEmpty()) {
        components.sort(
            Comparator.comparingInt(
                    (ComponentNode c) -> c.level() != null ? c.level() : Integer.MAX_VALUE)
                .thenComparingInt(
                    c -> {
                      for (int i = 0; i < policy.order().size(); i++) {
                        String ord = policy.order().get(i);
                        if (c.id().equals(ord)
                            || c.id().startsWith(ord + ".")
                            || c.id().contains("." + ord)) {
                          return i;
                        }
                      }
                      return Integer.MAX_VALUE;
                    })
                .thenComparing(ComponentNode::id));
      } else {
        components.sort(
            Comparator.comparingInt(
                    (ComponentNode c) -> c.level() != null ? c.level() : Integer.MAX_VALUE)
                .thenComparing(ComponentNode::id));
      }
    }

    Set<String> validIdentifiers = new HashSet<>();
    for (ClassNode c : validClasses) {
      validIdentifiers.add(c.id());
      validIdentifiers.add(c.name());
      if (c.packageName() != null && !c.packageName().isBlank()) {
        validIdentifiers.add(c.packageName());
      }
    }
    for (ComponentNode comp : components) {
      validIdentifiers.add(comp.id());
      if (comp.childPackageIds() != null) {
        validIdentifiers.addAll(comp.childPackageIds());
      }
    }

    // Filter edges against omit patterns, prune orphan edges, and evaluate violations
    List<DependencyEdge> evaluatedEdges =
        scan.edges().stream()
            .filter(e -> !matchesOmit(e.from(), omitPatterns) && !matchesOmit(e.to(), omitPatterns))
            .filter(
                e ->
                    isKnownNode(e.from(), validIdentifiers, policy.foreign())
                        && isKnownNode(e.to(), validIdentifiers, policy.foreign()))
            .map(validator::evaluate)
            .distinct()
            .toList();

    return new ArchitectureGraph(
        policy.title(), activeProposal != null, proposalId, components, evaluatedEdges, List.of());
  }

  private boolean isKnownNode(
      String identifier, Set<String> validIdentifiers, List<String> foreignList) {
    if (identifier == null || identifier.isBlank()) {
      return false;
    }
    if (validIdentifiers.contains(identifier)) {
      return true;
    }
    String simpleName =
        identifier.contains(".")
            ? identifier.substring(identifier.lastIndexOf('.') + 1)
            : identifier;
    if (validIdentifiers.contains(simpleName)) {
      return true;
    }
    for (String id : validIdentifiers) {
      if (identifier.startsWith(id + ".") || id.startsWith(identifier + ".")) {
        return true;
      }
    }
    if (foreignList != null) {
      for (String foreign : foreignList) {
        if (identifier.equals(foreign)
            || identifier.startsWith(foreign + ".")
            || foreign.startsWith(identifier + ".")) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean matchesOmit(ClassNode c, Set<String> omitPatterns) {
    if (omitPatterns == null || omitPatterns.isEmpty()) {
      return false;
    }
    return matchesOmit(c.filePath(), omitPatterns)
        || matchesOmit(c.packageName(), omitPatterns)
        || matchesOmit(c.id(), omitPatterns);
  }

  private boolean matchesOmit(String value, Set<String> omitPatterns) {
    if (value == null || value.isBlank() || omitPatterns == null || omitPatterns.isEmpty()) {
      return false;
    }
    String normalized = value.replace('\\', '/');
    for (String pattern : omitPatterns) {
      if (pattern == null || pattern.isBlank()) continue;
      String clean = pattern.trim().replace('\\', '/');
      if (clean.startsWith("/")) clean = clean.substring(1);
      if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
      if (clean.isBlank()) continue;

      if (normalized.equals(clean)
          || normalized.startsWith(clean + "/")
          || normalized.endsWith("/" + clean)
          || normalized.contains("/" + clean + "/")) {
        return true;
      }
      String dotPattern = clean.replace('/', '.');
      if (normalized.equals(dotPattern)
          || normalized.startsWith(dotPattern + ".")
          || normalized.endsWith("." + dotPattern)
          || normalized.contains("." + dotPattern + ".")) {
        return true;
      }
    }
    return false;
  }
}
