package io.slixes.archlens.domain.policy;

import io.slixes.archlens.domain.graph.DependencyEdge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyRuleValidator implements PolicyValidator {

  private final Map<String, Integer> ranks;
  private final Map<String, Integer> classRanks;

  public DependencyRuleValidator(Map<String, Integer> ranks) {
    this(ranks, Map.of());
  }

  public DependencyRuleValidator(Map<String, Integer> ranks, Map<String, Integer> classRanks) {
    this.ranks = ranks;
    this.classRanks = classRanks != null ? classRanks : Map.of();
  }

  public static DependencyRuleValidator fromLevels(List<List<String>> levels) {
    Map<String, Integer> rankMap = new HashMap<>();
    if (levels != null) {
      for (int rank = 0; rank < levels.size(); rank++) {
        List<String> group = levels.get(rank);
        if (group != null) {
          for (String seg : group) {
            rankMap.put(seg, rank);
          }
        }
      }
    }
    return new DependencyRuleValidator(rankMap);
  }

  public static DependencyRuleValidator fromProposal(Proposal proposal) {
    Map<String, Integer> rankMap = new HashMap<>();
    Map<String, Integer> explicitClassRanks = new HashMap<>();
    if (proposal != null && proposal.layers() != null) {
      for (int rank = 0; rank < proposal.layers().size(); rank++) {
        Proposal.ProposalLayer layer = proposal.layers().get(rank);
        if (layer.id() != null) {
          rankMap.put(layer.id(), rank);
        }
        if (layer.packages() != null) {
          for (String pkg : layer.packages()) {
            rankMap.put(pkg, rank);
          }
        }
        if (layer.classes() != null) {
          for (String cls : layer.classes()) {
            rankMap.put(cls, rank);
            explicitClassRanks.put(cls, rank);
          }
        }
      }
    }
    return new DependencyRuleValidator(rankMap, explicitClassRanks);
  }

  public Integer resolveRank(String id) {
    if (id == null || id.isBlank()) return null;
    if (classRanks.containsKey(id)) {
      return classRanks.get(id);
    }
    String[] parts = id.split("\\.");
    if (parts.length > 0 && classRanks.containsKey(parts[parts.length - 1])) {
      return classRanks.get(parts[parts.length - 1]);
    }
    if (ranks.containsKey(id)) {
      return ranks.get(id);
    }
    // Check longest prefix to root (package containment)
    for (int i = parts.length; i > 0; i--) {
      String sub = String.join(".", java.util.Arrays.copyOfRange(parts, 0, i));
      if (ranks.containsKey(sub)) {
        return ranks.get(sub);
      }
    }
    // Check individual package segments from root to leaf
    for (String seg : parts) {
      if (ranks.containsKey(seg)) {
        return ranks.get(seg);
      }
    }
    // Check simple name (last token) as fallback
    if (parts.length > 0 && ranks.containsKey(parts[parts.length - 1])) {
      return ranks.get(parts[parts.length - 1]);
    }
    return null;
  }

  public DependencyEdge evaluate(DependencyEdge edge) {
    if (edge.kind() != DependencyEdge.Kind.DEPENDENCY) {
      return edge.withViolating(false);
    }
    Integer fromRank = resolveRank(edge.from());
    Integer toRank = resolveRank(edge.to());

    // Violating if both ends are ranked and rank(from) < rank(to)
    // (inner layer 0 depends on outer layer 1+)
    boolean isViolating = fromRank != null && toRank != null && fromRank < toRank;
    return edge.withViolating(isViolating);
  }

  public Map<String, Integer> getRanks() {
    return ranks;
  }
}
