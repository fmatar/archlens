import type {
  ArchitectureGraph,
  ComponentNode,
  ClassNode,
  DependencyEdge,
  MartinMetrics,
  StagedClassMove,
  SandboxSimulationResult
} from '../types/diagram';

export type ComponentMetricsSummary = MartinMetrics;

/**
 * Calculates Robert C. Martin metrics for a single component:
 * - Afferent Coupling (Ca): incoming dependencies from external classes
 * - Efferent Coupling (Ce): outgoing dependencies to external classes
 * - Instability (I): Ce / (Ca + Ce)
 * - Abstractness (A): Na / Nc
 * - Distance from Main Sequence (D): |A + I - 1|
 * - Zone: MAIN_SEQUENCE, ZONE_OF_PAIN, or ZONE_OF_USELESSNESS
 */
export function calculateComponentMartinMetrics(
  comp: ComponentNode,
  edges: DependencyEdge[]
): MartinMetrics {
  const compClassKeys = new Set<string>();
  const classes = comp.classes || [];

  for (const cls of classes) {
    compClassKeys.add(cls.id);
    compClassKeys.add(cls.name);
    if (cls.packageName) {
      compClassKeys.add(`${cls.packageName}.${cls.name}`);
    }
  }

  const totalClasses = classes.length;
  const abstractClasses = classes.filter(
    (c) => c.stereotype === 'INTERFACE' || c.stereotype === 'ABSTRACT'
  ).length;

  const abstractness = totalClasses > 0 ? abstractClasses / totalClasses : 0;

  let ca = 0; // Incoming dependencies from outside this component
  let ce = 0; // Outgoing dependencies from this component to outside

  for (const edge of edges) {
    const isFromInside = compClassKeys.has(edge.from);
    const isToInside = compClassKeys.has(edge.to);

    if (!isFromInside && isToInside) {
      ca++;
    } else if (isFromInside && !isToInside) {
      ce++;
    }
  }

  const instability = ca + ce > 0 ? ce / (ca + ce) : 0;
  const rawDistance = Math.abs(abstractness + instability - 1);
  const distance = Math.min(1.0, Math.max(0.0, rawDistance));

  let zone: 'MAIN_SEQUENCE' | 'ZONE_OF_PAIN' | 'ZONE_OF_USELESSNESS' = 'MAIN_SEQUENCE';
  if (distance > 0.25) {
    if (abstractness + instability < 1.0) {
      zone = 'ZONE_OF_PAIN';
    } else {
      zone = 'ZONE_OF_USELESSNESS';
    }
  }

  return {
    ca,
    ce,
    instability,
    abstractness,
    distance,
    zone,
    totalClasses,
    abstractClasses
  };
}

/**
 * Calculates Robert C. Martin metrics for all components in the architecture graph.
 */
export function calculateAllMartinMetrics(
  graph: ArchitectureGraph
): Record<string, MartinMetrics> {
  const result: Record<string, MartinMetrics> = {};
  if (!graph || !graph.components) return result;

  for (const comp of graph.components) {
    result[comp.id] = calculateComponentMartinMetrics(comp, graph.edges || []);
  }

  return result;
}

/**
 * Simulates the architecture graph with staged class reassignments.
 * Recalculates all concentric tier dependency violations and Martin metrics in real time.
 */
export function simulateSandboxGraph(
  baseGraph: ArchitectureGraph,
  stagedMoves: Map<string, string>
): SandboxSimulationResult {
  const baselineViolations = (baseGraph?.edges || []).filter((e) => e.isViolating).length;

  if (!baseGraph || !baseGraph.components || stagedMoves.size === 0) {
    return {
      simulatedGraph: baseGraph,
      baselineViolations,
      simulatedViolations: baselineViolations,
      violationDelta: 0,
      stagedMoves: [],
      componentMetrics: calculateAllMartinMetrics(baseGraph)
    };
  }

  // Deep clone components and their classes
  const simulatedComponents: ComponentNode[] = baseGraph.components.map((c) => ({
    ...c,
    classes: (c.classes || []).map((cls) => ({ ...cls }))
  }));

  const compMap = new Map<string, ComponentNode>();
  simulatedComponents.forEach((c) => compMap.set(c.id, c));

  const stagedMoveRecords: StagedClassMove[] = [];

  // Apply class reassignments
  stagedMoves.forEach((targetCompId, classId) => {
    const targetComp = compMap.get(targetCompId);
    if (!targetComp) return;

    // Find class in source component
    for (const sourceComp of simulatedComponents) {
      const clsIdx = sourceComp.classes.findIndex(
        (cls) => cls.id === classId || cls.name === classId || `${cls.packageName}.${cls.name}` === classId
      );

      if (clsIdx !== -1) {
        const [movedClass] = sourceComp.classes.splice(clsIdx, 1);
        movedClass.level = targetComp.level;
        targetComp.classes.push(movedClass);

        stagedMoveRecords.push({
          classId: movedClass.id,
          className: movedClass.name,
          sourceComponentId: sourceComp.id,
          sourceComponentName: sourceComp.label,
          targetComponentId: targetComp.id,
          targetComponentName: targetComp.label,
          timestamp: Date.now()
        });
        break;
      }
    }
  });

  // Build class-to-level mapping for re-evaluating edge violations
  const classToLevelMap = new Map<string, number | null>();
  simulatedComponents.forEach((comp) => {
    (comp.classes || []).forEach((cls) => {
      classToLevelMap.set(cls.id, comp.level);
      classToLevelMap.set(cls.name, comp.level);
      if (cls.packageName) {
        classToLevelMap.set(`${cls.packageName}.${cls.name}`, comp.level);
      }
    });
  });

  // Also include unassigned classes
  (baseGraph.unassigned || []).forEach((cls) => {
    classToLevelMap.set(cls.id, cls.level);
    classToLevelMap.set(cls.name, cls.level);
    if (cls.packageName) {
      classToLevelMap.set(`${cls.packageName}.${cls.name}`, cls.level);
    }
  });

  // Re-evaluate edges for violations
  const simulatedEdges: DependencyEdge[] = (baseGraph.edges || []).map((edge) => {
    const fromLevel = classToLevelMap.get(edge.from);
    const toLevel = classToLevelMap.get(edge.to);

    let isViolating = edge.isViolating;
    if (fromLevel !== undefined && toLevel !== undefined && fromLevel !== null && toLevel !== null) {
      // Inward rule: fromLevel >= toLevel is conforming, fromLevel < toLevel is outward violation
      isViolating = fromLevel < toLevel;
    }

    return {
      ...edge,
      isViolating
    };
  });

  const simulatedViolations = simulatedEdges.filter((e) => e.isViolating).length;
  const violationDelta = simulatedViolations - baselineViolations;

  const simulatedGraph: ArchitectureGraph = {
    ...baseGraph,
    components: simulatedComponents,
    edges: simulatedEdges
  };

  const componentMetrics = calculateAllMartinMetrics(simulatedGraph);

  return {
    simulatedGraph,
    baselineViolations,
    simulatedViolations,
    violationDelta,
    stagedMoves: stagedMoveRecords,
    componentMetrics
  };
}
