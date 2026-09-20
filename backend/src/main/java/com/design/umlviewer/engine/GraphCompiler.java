package com.design.umlviewer.engine;

import com.design.umlviewer.domain.model.ArchitectureGraph;
import com.design.umlviewer.domain.model.ClassNode;
import com.design.umlviewer.domain.model.ComponentNode;
import com.design.umlviewer.domain.model.CrapScore;
import com.design.umlviewer.domain.model.DependencyEdge;
import com.design.umlviewer.domain.policy.ArchitecturePolicy;
import com.design.umlviewer.domain.policy.DependencyRuleValidator;
import com.design.umlviewer.domain.policy.Proposal;
import com.design.umlviewer.scanner.JavaAstScanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class GraphCompiler {

    @Inject
    JavaAstScanner scanner;

    private final ObjectMapper mapper = new ObjectMapper();

    public ArchitecturePolicy loadPolicy(String projectRoot) {
        File policyFile = new File(projectRoot, ".uml-viewer/policy.json");
        if (!policyFile.exists()) {
            policyFile = new File(".uml-viewer/policy.json");
        }
        if (!policyFile.exists()) {
            policyFile = new File("../.uml-viewer/policy.json");
        }
        if (policyFile.exists()) {
            try {
                return mapper.readValue(policyFile, ArchitecturePolicy.class);
            } catch (Exception e) {
                System.err.println("Error reading policy from " + policyFile.getAbsolutePath() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        return new ArchitecturePolicy(
                "Default Project", "src/main/java", "com.design", true,
                List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    public ArchitectureGraph compileGraph(String projectRoot, String proposalId) throws IOException {
        ArchitecturePolicy policy = loadPolicy(projectRoot);
        JavaAstScanner.ScanResult scan = scanner.scanProject(
                projectRoot,
                policy.src() != null ? policy.src() : "src/main/java",
                policy.prefix() != null ? policy.prefix() : ""
        );

        Proposal activeProposal = null;
        if (proposalId != null && policy.proposals() != null) {
            activeProposal = policy.proposals().stream()
                    .filter(p -> p.id().equals(proposalId))
                    .findFirst()
                    .orElse(null);
        }

        DependencyRuleValidator validator = (activeProposal != null)
                ? DependencyRuleValidator.fromProposal(activeProposal)
                : DependencyRuleValidator.fromLevels(policy.levels());

        // Group into components
        Map<String, List<ClassNode>> pkgMap = scan.classes().stream()
                .collect(Collectors.groupingBy(ClassNode::packageName));

        List<ComponentNode> components = new ArrayList<>();

        if (activeProposal != null) {
            // Build virtual components as defined by proposal
            for (Proposal.ProposalLayer layer : activeProposal.layers()) {
                List<ClassNode> layerClasses = new ArrayList<>();
                if (layer.packages() != null) {
                    for (String pkg : layer.packages()) {
                        pkgMap.forEach((k, v) -> {
                            if (k.endsWith(pkg) || k.contains("." + pkg + ".") || k.equals(pkg)) {
                                layerClasses.addAll(v);
                            }
                        });
                    }
                }
                if (layer.classes() != null) {
                    for (String cls : layer.classes()) {
                        scan.classes().stream()
                                .filter(c -> c.name().equals(cls) || c.id().endsWith("." + cls) || c.id().equals(cls))
                                .forEach(c -> {
                                    if (!layerClasses.contains(c)) {
                                        layerClasses.add(c);
                                    }
                                });
                    }
                }
                Integer level = validator.resolveRank(layer.id());
                components.add(new ComponentNode(
                        layer.id(),
                        layer.label(),
                        level,
                        CrapScore.zero(),
                        0.9,
                        layer.packages() != null ? layer.packages() : List.of(),
                        layerClasses
                ));
            }
        } else {
            // Group according to packages
            for (Map.Entry<String, List<ClassNode>> entry : pkgMap.entrySet()) {
                String pkgName = entry.getKey();
                String shortId = pkgName.replace(policy.prefix() != null ? policy.prefix() + "." : "", "");
                Integer level = validator.resolveRank(shortId);

                components.add(new ComponentNode(
                        shortId,
                        shortId,
                        level,
                        CrapScore.zero(),
                        0.88,
                        List.of(pkgName),
                        entry.getValue()
                ));
            }
        }

        // Stamp levels on classes & evaluate edge violations
        List<DependencyEdge> evaluatedEdges = scan.edges().stream()
                .map(validator::evaluate)
                .toList();

        return new ArchitectureGraph(
                policy.title(),
                activeProposal != null,
                proposalId,
                components,
                evaluatedEdges,
                List.of()
        );
    }
}
