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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class GraphCompiler {

  @Inject LanguageScannerRegistry scannerRegistry;

  private final ObjectMapper mapper = new ObjectMapper();

  public ArchitecturePolicy loadPolicy(String projectRoot) {
    File policyFile = null;
    if (projectRoot != null && !projectRoot.isBlank()) {
      policyFile = new File(projectRoot, ".uml-viewer/policy.json");
    } else if (new File(".uml-viewer/policy.json").exists()) {
      policyFile = new File(".uml-viewer/policy.json");
    } else if (new File("../.uml-viewer/policy.json").exists()) {
      policyFile = new File("../.uml-viewer/policy.json");
    }
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

    return new ArchitecturePolicy(
        "Default Project",
        "src/main/java",
        "com.design",
        true,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  public ArchitectureGraph compileGraph(String projectRoot, String proposalId) throws IOException {
    ArchitecturePolicy policy = loadPolicy(projectRoot);
    LanguageScanner scanner =
        scannerRegistry != null
            ? scannerRegistry.resolveScanner(projectRoot, policy)
            : new com.design.umlviewer.scanner.JavaAstScanner();

    LanguageScanner.ScanResult scan =
        scanner != null
            ? scanner.scanProject(
                projectRoot,
                policy.src() != null ? policy.src() : "",
                policy.prefix() != null ? policy.prefix() : "")
            : new LanguageScanner.ScanResult(List.of(), List.of());

    Proposal activeProposal = null;
    if (proposalId != null && policy.proposals() != null) {
      activeProposal =
          policy.proposals().stream()
              .filter(p -> p.id().equals(proposalId))
              .findFirst()
              .orElse(null);
    }

    DependencyRuleValidator validator =
        (activeProposal != null)
            ? DependencyRuleValidator.fromProposal(activeProposal)
            : DependencyRuleValidator.fromLevels(policy.levels());

    // Group into components
    Map<String, List<ClassNode>> pkgMap =
        scan.classes().stream().collect(Collectors.groupingBy(ClassNode::packageName));

    List<ComponentNode> components = new ArrayList<>();

    if (activeProposal != null) {
      // Build virtual components as defined by proposal
      for (Proposal.ProposalLayer layer : activeProposal.layers()) {
        List<ClassNode> layerClasses = new ArrayList<>();
        if (layer.packages() != null) {
          for (String pkg : layer.packages()) {
            pkgMap.forEach(
                (k, v) -> {
                  if (k.endsWith(pkg) || k.contains("." + pkg + ".") || k.equals(pkg)) {
                    layerClasses.addAll(v);
                  }
                });
          }
        }
        if (layer.classes() != null) {
          for (String cls : layer.classes()) {
            scan.classes().stream()
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
      for (Map.Entry<String, List<ClassNode>> entry : pkgMap.entrySet()) {
        String pkgName = entry.getKey();
        String shortId = pkgName.replace(policy.prefix() != null ? policy.prefix() + "." : "", "");
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
    }

    // Stamp levels on classes & evaluate edge violations
    List<DependencyEdge> evaluatedEdges =
        scan.edges().stream().map(validator::evaluate).distinct().toList();

    return new ArchitectureGraph(
        policy.title(), activeProposal != null, proposalId, components, evaluatedEdges, List.of());
  }
}
