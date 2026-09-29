import type {
  ArchitectureGraph,
  ComponentNode,
  ClassNode,
  DependencyEdge,
  MartinMetrics,
  StagedClassMove,
  SandboxSimulationResult,
  ScatterPlotPoint
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
    let targetComp = compMap.get(targetCompId);
    if (!targetComp) {
      const label = targetCompId.includes('.')
        ? targetCompId.substring(targetCompId.lastIndexOf('.') + 1)
        : targetCompId;
      targetComp = {
        id: targetCompId,
        label,
        level: 0,
        crap: { mu: 0, max: 0, sigma: 0 },
        mutationScore: 100,
        childPackageIds: [],
        packages: [targetCompId],
        classes: []
      };
      simulatedComponents.push(targetComp);
      compMap.set(targetCompId, targetComp);
    }

    // Find class in source component
    for (const sourceComp of simulatedComponents) {
      if (sourceComp === targetComp) continue;
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

  // Recalculate ScreamingMetric for simulated graph
  const technicalMarkers = new Set([
    'controller',
    'controllers',
    'service',
    'services',
    'dao',
    'daos',
    'repository',
    'repositories',
    'dto',
    'dtos',
    'model',
    'models',
    'entity',
    'entities',
    'util',
    'utils',
    'helper',
    'helpers',
    'common',
    'infra',
    'infrastructure',
    'adapter',
    'adapters',
    'resource',
    'resources',
    'api',
    'endpoint',
    'endpoints',
    'view',
    'views',
    'handler',
    'handlers'
  ]);
  const activeComps = simulatedComponents.filter((c) => (c.classes || []).length > 0);
  const domainPkgs: string[] = [];
  const techPkgs: string[] = [];
  activeComps.forEach((c) => {
    const id = c.id.toLowerCase();
    const leaf = id.includes('.') ? id.substring(id.lastIndexOf('.') + 1) : id;
    if (technicalMarkers.has(leaf) || technicalMarkers.has(id)) {
      techPkgs.push(c.id);
    } else {
      domainPkgs.push(c.id);
    }
  });
  const totalPkgs = domainPkgs.length + techPkgs.length;
  const simScore = totalPkgs > 0 ? Math.round((domainPkgs.length / totalPkgs) * 100) / 100 : 1.0;
  let simClassification = 'PACKAGE_BY_LAYER';
  if (simScore >= 0.75) simClassification = 'PACKAGE_BY_FEATURE';
  else if (simScore >= 0.40) simClassification = 'HYBRID';

  const simulatedGraph: ArchitectureGraph = {
    ...baseGraph,
    components: simulatedComponents,
    edges: simulatedEdges,
    screamingMetric: {
      score: simScore,
      domainPackageCount: domainPkgs.length,
      technicalPackageCount: techPkgs.length,
      totalPackageCount: totalPkgs,
      classification: simClassification,
      domainPackages: domainPkgs,
      technicalPackages: techPkgs,
      frameworkGravityWarnings: []
    }
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

/**
 * Computes SVG (x, y) pixel coordinates on a 2D Cartesian plane for given Instability and Abstractness.
 * Instability: 0.0 (left) to 1.0 (right).
 * Abstractness: 0.0 (bottom) to 1.0 (top).
 */
export function computeScatterCoordinates(
  instability: number,
  abstractness: number,
  plotBounds = { minX: 60, maxX: 500, minY: 40, maxY: 480 }
): { x: number; y: number } {
  const clampedI = Math.max(0, Math.min(1, Number.isFinite(instability) ? instability : 0));
  const clampedA = Math.max(0, Math.min(1, Number.isFinite(abstractness) ? abstractness : 0));
  const width = plotBounds.maxX - plotBounds.minX;
  const height = plotBounds.maxY - plotBounds.minY;
  const x = plotBounds.minX + clampedI * width;
  const y = plotBounds.maxY - clampedA * height;
  return { x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 };
}

/**
 * Computes dynamic visual node radius scaled logarithmically by class count.
 */
export function computeNodeRadius(classCount: number): number {
  const count = Math.max(1, classCount || 1);
  return Math.round(6 + Math.min(14, Math.sqrt(count) * 2.8));
}

/**
 * Maps a list of components and their Martin metrics into drawable ScatterPlotPoints.
 */
export function buildScatterPlotPoints(
  components: ComponentNode[],
  metrics: Record<string, MartinMetrics>,
  plotBounds = { minX: 60, maxX: 500, minY: 40, maxY: 480 }
): ScatterPlotPoint[] {
  if (!components || !metrics) return [];

  return components.map((comp) => {
    const m = metrics[comp.id] || {
      ca: 0,
      ce: 0,
      instability: 0,
      abstractness: 0,
      distance: 0,
      zone: 'MAIN_SEQUENCE',
      totalClasses: comp.classes?.length || 0,
      abstractClasses: 0
    };

    const coords = computeScatterCoordinates(m.instability, m.abstractness, plotBounds);
    const radius = computeNodeRadius(m.totalClasses);

    return {
      componentId: comp.id,
      label: comp.label,
      level: comp.level,
      instability: m.instability,
      abstractness: m.abstractness,
      distance: m.distance,
      zone: m.zone,
      classCount: m.totalClasses,
      abstractCount: m.abstractClasses,
      ca: m.ca,
      ce: m.ce,
      x: coords.x,
      y: coords.y,
      radius
    };
  });
}
