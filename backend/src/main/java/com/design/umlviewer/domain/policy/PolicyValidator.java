package com.design.umlviewer.domain.policy;

import com.design.umlviewer.domain.graph.DependencyEdge;

/** Domain policy abstraction for Clean Architecture rule enforcement and layer rank resolution. */
public interface PolicyValidator {

  /**
   * Resolves the concentric tier level for a component or class identifier.
   *
   * @param id Package, component, or class identifier
   * @return Numeric concentric rank (0=Domain, 1=Application, 2=Adapters, 3=Infrastructure), or
   *     null if unranked
   */
  Integer resolveRank(String id);

  /**
   * Evaluates a dependency edge against Clean Architecture rules (inward dependency rule).
   *
   * @param edge Evaluated edge
   * @return Edge tagged with violation status and explanatory message
   */
  DependencyEdge evaluate(DependencyEdge edge);
}
