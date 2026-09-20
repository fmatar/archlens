package com.design.umlviewer.domain.policy;

import java.util.List;

public record Proposal(String id, String name, List<ProposalLayer> layers, List<String> omit) {
  public record ProposalLayer(
      String id, String label, List<String> packages, List<String> classes) {
    public ProposalLayer(String id, String label, List<String> packages) {
      this(id, label, packages, List.of());
    }
  }
}
