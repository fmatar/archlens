import { describe, it, expect } from 'vitest';
import {
  calculateComponentMartinMetrics,
  calculateAllMartinMetrics,
  simulateSandboxGraph,
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
});
