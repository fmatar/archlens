package io.slixes.archlens.domain.policy;

import io.slixes.archlens.domain.graph.ArchitectureGraph;
import io.slixes.archlens.domain.graph.ClassNode;
import io.slixes.archlens.domain.graph.ComponentNode;
import io.slixes.archlens.domain.graph.DependencyEdge;
import io.slixes.archlens.domain.graph.PackageCycle;
import java.util.*;

/**
 * Detects cyclic package dependencies in an {@link ArchitectureGraph} to enforce Robert C. Martin's
 * Acyclic Dependencies Principle (ADP).
 */
public final class PackageCycleDetector {

  private PackageCycleDetector() {}

  /**
   * Detects all elementary directed package dependency cycles in the provided architecture graph.
   *
   * @param graph the architecture graph to inspect
   * @return an immutable list of detected package cycles, or empty list if acyclic
   */
  public static List<PackageCycle> detectCycles(ArchitectureGraph graph) {
    if (graph == null || graph.components() == null || graph.edges() == null) {
      return List.of();
    }

    // 1. Map class IDs to their package names
    Map<String, String> classToPackage = new HashMap<>();
    for (ComponentNode comp : graph.components()) {
      if (comp.classes() != null) {
        for (ClassNode cls : comp.classes()) {
          if (cls.packageName() != null && !cls.packageName().isBlank()) {
            classToPackage.put(cls.id(), cls.packageName());
          }
        }
      }
    }

    // 2. Build directed package adjacency list
    Map<String, Set<String>> packageAdj = new TreeMap<>();
    for (DependencyEdge edge : graph.edges()) {
      String srcPkg = classToPackage.get(edge.from());
      String tgtPkg = classToPackage.get(edge.to());
      if (srcPkg != null && tgtPkg != null && !srcPkg.equals(tgtPkg)) {
        packageAdj.computeIfAbsent(srcPkg, k -> new TreeSet<>()).add(tgtPkg);
      }
    }

    if (packageAdj.isEmpty()) {
      return List.of();
    }

    // 3. Find elementary cycles using canonical DFS starting from each node
    List<PackageCycle> detectedCycles = new ArrayList<>();
    List<String> sortedPackages = new ArrayList<>(packageAdj.keySet());
    Collections.sort(sortedPackages);

    for (String startNode : sortedPackages) {
      List<String> path = new ArrayList<>();
      path.add(startNode);
      Set<String> visitedOnPath = new HashSet<>();
      visitedOnPath.add(startNode);

      dfsFindCycles(startNode, startNode, path, visitedOnPath, packageAdj, detectedCycles);
    }

    return Collections.unmodifiableList(detectedCycles);
  }

  private static void dfsFindCycles(
      String startNode,
      String currentNode,
      List<String> currentPath,
      Set<String> visitedOnPath,
      Map<String, Set<String>> packageAdj,
      List<PackageCycle> results) {

    Set<String> neighbors = packageAdj.get(currentNode);
    if (neighbors == null) {
      return;
    }

    for (String neighbor : neighbors) {
      if (neighbor.equals(startNode) && currentPath.size() > 1) {
        // Cycle completed back to startNode
        List<String> cyclePath = new ArrayList<>(currentPath);
        cyclePath.add(startNode);
        results.add(new PackageCycle(cyclePath));
      } else if (neighbor.compareTo(startNode) > 0 && !visitedOnPath.contains(neighbor)) {
        // Only explore paths where vertices are lexicographically >= startNode to prevent duplicate
        // cycles
        visitedOnPath.add(neighbor);
        currentPath.add(neighbor);

        dfsFindCycles(startNode, neighbor, currentPath, visitedOnPath, packageAdj, results);

        currentPath.remove(currentPath.size() - 1);
        visitedOnPath.remove(neighbor);
      }
    }
  }
}
