import { describe, it, expect } from 'vitest';
import {
  calculateComponentMartinMetrics,
  calculateAllMartinMetrics,
  simulateSandboxGraph,
  computeScatterCoordinates,
  computeNodeRadius,
  buildScatterPlotPoints,
  type ComponentMetricsSummary
} from './martinMetrics';
import type { ArchitectureGraph, ComponentNode, ClassNode, DependencyEdge, ArchitecturePolicy } from '../types/diagram';

describe('Robert C. Martin Architectural Metrics Engine (TDD)', () => {
  const createMockClass = (id: string, name: string, stereotype: 'CLASS' | 'INTERFACE' | 'ABSTRACT' = 'CLASS'): ClassNode => ({
    id,
    name,
    packageName: 'com.example',
    filePath: `src/${name}.java`,
    stereotype,
    isForeign: false,
    level: 1,
    crap: { mu: 1, max: 1, sigma: 0 },
    coverage: 100,
    cc: 1,
    killed: 0,
    survived: 0,
    uncovered: 0,
    fields: [],
    methods: []
  });

  it('should calculate metrics for a perfectly balanced component on the Main Sequence', () => {
    // 2 interfaces, 2 concrete classes -> A = 2/4 = 0.5
    const compClasses = [
      createMockClass('c1', 'ServicePort', 'INTERFACE'),
      createMockClass('c2', 'AbstractRepo', 'ABSTRACT'),
      createMockClass('c3', 'ServiceImpl', 'CLASS'),
      createMockClass('c4', 'OrderEntity', 'CLASS')
    ];

    const comp: ComponentNode = {
      id: 'comp-app',
      label: 'application',
      level: 1,
      crap: { mu: 1, max: 1, sigma: 0 },
      mutationScore: 100,
      childPackageIds: [],
      classes: compClasses
    };

    // 2 incoming edges from outside (Ca = 2)
    // 2 outgoing edges to outside (Ce = 2)
    // 1 internal edge (must NOT count toward Ca or Ce)
    const edges: DependencyEdge[] = [
      { from: 'ext-caller-1', to: 'c1', kind: 'DEPENDENCY', isViolating: false },
      { from: 'ext-caller-2', to: 'c2', kind: 'DEPENDENCY', isViolating: false },
      { from: 'c3', to: 'ext-target-1', kind: 'DEPENDENCY', isViolating: false },
      { from: 'c4', to: 'ext-target-2', kind: 'DEPENDENCY', isViolating: false },
      { from: 'c3', to: 'c1', kind: 'DEPENDENCY', isViolating: false } // internal
    ];

    const metrics = calculateComponentMartinMetrics(comp, edges);

    expect(metrics.totalClasses).toBe(4);
    expect(metrics.abstractClasses).toBe(2);
    expect(metrics.abstractness).toBeCloseTo(0.5, 4);
    expect(metrics.ca).toBe(2);
    expect(metrics.ce).toBe(2);
    expect(metrics.instability).toBeCloseTo(0.5, 4);
    expect(metrics.distance).toBeCloseTo(0.0, 4);
    expect(metrics.zone).toBe('MAIN_SEQUENCE');
  });

  it('should classify purely concrete, unreferenced component in Zone of Pain', () => {
    // 4 concrete classes, 0 interfaces -> A = 0
    // Ca = 5, Ce = 0 -> I = 0 / 5 = 0 (Maximally stable, zero abstractions -> rigid)
    const compClasses = [
      createMockClass('c1', 'Concrete1', 'CLASS'),
      createMockClass('c2', 'Concrete2', 'CLASS')
    ];

    const comp: ComponentNode = {
      id: 'comp-domain',
      label: 'domain',
      level: 0,
      crap: { mu: 1, max: 1, sigma: 0 },
      mutationScore: 100,
      childPackageIds: [],
      classes: compClasses
    };

    const edges: DependencyEdge[] = [
      { from: 'ext-1', to: 'c1', kind: 'DEPENDENCY', isViolating: false },
      { from: 'ext-2', to: 'c2', kind: 'DEPENDENCY', isViolating: false }
    ];

    const metrics = calculateComponentMartinMetrics(comp, edges);

    expect(metrics.abstractness).toBe(0.0);
    expect(metrics.ca).toBe(2);
    expect(metrics.ce).toBe(0);
    expect(metrics.instability).toBe(0.0);
    expect(metrics.distance).toBeCloseTo(1.0, 4);
    expect(metrics.zone).toBe('ZONE_OF_PAIN');
  });

  it('should classify purely abstract component that depends on others with no dependents in Zone of Uselessness', () => {
    // 2 interfaces -> A = 1.0
    // Ca = 0, Ce = 3 -> I = 3/3 = 1.0
    // D = |1.0 + 1.0 - 1| = 1.0
    const compClasses = [
      createMockClass('i1', 'UnusedPort1', 'INTERFACE'),
      createMockClass('i2', 'UnusedPort2', 'INTERFACE')
    ];

    const comp: ComponentNode = {
      id: 'comp-unused',
      label: 'unused',
      level: 2,
      crap: { mu: 1, max: 1, sigma: 0 },
      mutationScore: 100,
      childPackageIds: [],
      classes: compClasses
    };

    const edges: DependencyEdge[] = [
      { from: 'i1', to: 'ext-target', kind: 'DEPENDENCY', isViolating: false }
    ];

    const metrics = calculateComponentMartinMetrics(comp, edges);

    expect(metrics.abstractness).toBe(1.0);
    expect(metrics.instability).toBe(1.0);
    expect(metrics.distance).toBeCloseTo(1.0, 4);
    expect(metrics.zone).toBe('ZONE_OF_USELESSNESS');
  });

  it('should simulate sandbox graph, reassign classes, and resolve outward violations', () => {
    // Base graph:
    // Domain (level 0): OrderService (c1)
    // Adapters (level 2): PostgresRepo (c2)
    // Edge c1 -> c2: level 0 -> level 2 = VIOLATION!
    const c1 = createMockClass('c1', 'OrderService', 'CLASS');
    const c2 = createMockClass('c2', 'PostgresRepo', 'CLASS');
    c1.level = 0;
    c2.level = 2;

    const domainComp: ComponentNode = {
      id: 'comp-domain',
      label: 'domain',
      level: 0,
      crap: { mu: 1, max: 1, sigma: 0 },
      mutationScore: 100,
      childPackageIds: [],
      classes: [c1]
    };

    const adaptersComp: ComponentNode = {
      id: 'comp-adapters',
      label: 'adapters',
      level: 2,
      crap: { mu: 1, max: 1, sigma: 0 },
      mutationScore: 100,
      childPackageIds: [],
      classes: [c2]
    };

    const baseGraph: ArchitectureGraph = {
      title: 'Test',
      isProposal: false,
      components: [domainComp, adaptersComp],
      edges: [
        { from: 'c1', to: 'c2', kind: 'DEPENDENCY', isViolating: true }
      ],
      unassigned: []
    };

    // Staging: Move OrderService (c1) from Domain (0) to Adapters (2)
    // Now both c1 and c2 are in Adapters (level 2).
    // Edge c1 -> c2 is level 2 -> level 2, which is CONFORMING (isViolating = false)!
    const stagedMoves = new Map<string, string>();
    stagedMoves.set('c1', 'comp-adapters');

    const simulation = simulateSandboxGraph(baseGraph, stagedMoves);

    expect(simulation.simulatedGraph.components.find(c => c.id === 'comp-domain')?.classes).toHaveLength(0);
    const simulatedAdapters = simulation.simulatedGraph.components.find(c => c.id === 'comp-adapters');
    expect(simulatedAdapters?.classes).toHaveLength(2);

    // Edge must now be conforming
    expect(simulation.simulatedGraph.edges[0].isViolating).toBe(false);
    expect(simulation.baselineViolations).toBe(1);
    expect(simulation.simulatedViolations).toBe(0);
    expect(simulation.violationDelta).toBe(-1); // 1 violation resolved!
  });

  it('should handle unassigned classes and empty staged moves cleanly', () => {
    const unassignedCls = createMockClass('unassigned-1', 'LegacyScript', 'CLASS');
    unassignedCls.level = 3;

    const baseGraph: ArchitectureGraph = {
      title: 'Test with Unassigned',
      isProposal: false,
      components: [
        {
          id: 'comp-app',
          label: 'app',
          level: 1,
          crap: { mu: 1, max: 1, sigma: 0 },
          mutationScore: 100,
          childPackageIds: [],
          classes: [createMockClass('c1', 'AppService', 'CLASS')]
        }
      ],
      edges: [
        { from: 'c1', to: 'unassigned-1', kind: 'DEPENDENCY', isViolating: true }
      ],
      unassigned: [unassignedCls]
    };

    // Empty staged moves
    const emptySim = simulateSandboxGraph(baseGraph, new Map());
    expect(emptySim.stagedMoves).toHaveLength(0);
    expect(emptySim.simulatedViolations).toBe(1);

    // Moves with an unassigned class and non-existent target component
    const stagedMoves = new Map<string, string>();
    stagedMoves.set('non-existent-class', 'comp-app');
    stagedMoves.set('c1', 'non-existent-comp');

    const sim = simulateSandboxGraph(baseGraph, stagedMoves);
    expect(sim.simulatedGraph).toBeDefined();

    // calculateAllMartinMetrics with null/empty
    const emptyMetrics = calculateAllMartinMetrics(null as any);
    expect(emptyMetrics).toEqual({});

    // simulateSandboxGraph with null graph or null components
    const nullGraphSim = simulateSandboxGraph(null as any, new Map());
    expect(nullGraphSim.baselineViolations).toBe(0);

    // simulateSandboxGraph with edge between unknown or null-level nodes
    const edgeWithNullLevelsGraph: ArchitectureGraph = {
      title: 'Null Levels',
      isProposal: false,
      components: [
        {
          id: 'c-null',
          label: 'c-null',
          level: null,
          crap: { mu: 1, max: 1, sigma: 0 },
          mutationScore: 100,
          childPackageIds: [],
          classes: [createMockClass('cls-null', 'NullLevelClass', 'CLASS')]
        }
      ],
      edges: [
        { from: 'cls-null', to: 'cls-unknown', kind: 'DEPENDENCY', isViolating: true }
      ],
      unassigned: []
    };

    const nullLevelsMoves = new Map<string, string>();
    nullLevelsMoves.set('cls-null', 'c-null');
    const simNullLevels = simulateSandboxGraph(edgeWithNullLevelsGraph, nullLevelsMoves);
    expect(simNullLevels.simulatedGraph.edges[0].isViolating).toBe(true);
  });

  describe('Main Sequence Scatter Plot Transformations', () => {
    it('should map (I=0, A=1) to top-left and (I=1, A=0) to bottom-right', () => {
      // Default bounds: minX: 60, maxX: 500, minY: 40, maxY: 480
      const topLeft = computeScatterCoordinates(0, 1);
      expect(topLeft).toEqual({ x: 60, y: 40 });

      const bottomRight = computeScatterCoordinates(1, 0);
      expect(bottomRight).toEqual({ x: 500, y: 480 });

      const center = computeScatterCoordinates(0.5, 0.5);
      expect(center).toEqual({ x: 280, y: 260 });
    });

    it('should clamp out-of-bounds coordinates safely', () => {
      const clampedNegative = computeScatterCoordinates(-0.5, -0.2);
      expect(clampedNegative).toEqual({ x: 60, y: 480 });

      const clampedExcess = computeScatterCoordinates(1.5, 1.8);
      expect(clampedExcess).toEqual({ x: 500, y: 40 });

      const nanSafe = computeScatterCoordinates(NaN, NaN);
      expect(nanSafe).toEqual({ x: 60, y: 480 });
    });

    it('should scale node radius based on class count', () => {
      const r1 = computeNodeRadius(1);
      const r16 = computeNodeRadius(16);
      const r100 = computeNodeRadius(100);

      expect(r1).toBeGreaterThanOrEqual(6);
      expect(r16).toBeGreaterThan(r1);
      expect(r100).toBeLessThanOrEqual(20);
    });

    it('should build complete scatter plot points for architecture graph components', () => {
      const comp1: ComponentNode = {
        id: 'comp-1',
        label: 'domain',
        level: 0,
        crap: { mu: 1, max: 1, sigma: 0 },
        mutationScore: 100,
        childPackageIds: [],
        classes: [createMockClass('c1', 'Entity', 'CLASS'), createMockClass('c2', 'Port', 'INTERFACE')]
      };

      const comp2: ComponentNode = {
        id: 'comp-2',
        label: 'adapters',
        level: 2,
        crap: { mu: 1, max: 1, sigma: 0 },
        mutationScore: 90,
        childPackageIds: [],
        classes: [createMockClass('c3', 'Adapter', 'CLASS')]
      };

      const metrics = {
        'comp-1': {
          ca: 2,
          ce: 0,
          instability: 0.0,
          abstractness: 0.5,
          distance: 0.5,
          zone: 'ZONE_OF_PAIN' as const,
          totalClasses: 2,
          abstractClasses: 1
        },
        'comp-2': {
          ca: 0,
          ce: 2,
          instability: 1.0,
          abstractness: 0.0,
          distance: 0.0,
          zone: 'MAIN_SEQUENCE' as const,
          totalClasses: 1,
          abstractClasses: 0
        }
      };

      const points = buildScatterPlotPoints([comp1, comp2], metrics);
      expect(points).toHaveLength(2);

      expect(points[0].componentId).toBe('comp-1');
      expect(points[0].label).toBe('domain');
      expect(points[0].level).toBe(0);
      expect(points[0].x).toBe(60);
      expect(points[0].y).toBe(260); // 480 - 0.5 * 440 = 260
      expect(points[0].zone).toBe('ZONE_OF_PAIN');

      expect(points[1].componentId).toBe('comp-2');
      expect(points[1].x).toBe(500); // 60 + 1 * 440 = 500
      expect(points[1].y).toBe(480); // 480 - 0 = 480
      expect(points[1].zone).toBe('MAIN_SEQUENCE');

      // Edge cases: null/empty inputs
      expect(buildScatterPlotPoints(null as any, metrics)).toEqual([]);
      expect(buildScatterPlotPoints([comp1], null as any)).toEqual([]);
      expect(buildScatterPlotPoints([comp1], {})[0].instability).toBe(0);
    });
  });
});
