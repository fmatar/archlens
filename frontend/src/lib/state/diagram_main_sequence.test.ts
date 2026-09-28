import { describe, it, expect, beforeEach } from 'vitest';
import { diagramStore } from './diagram.svelte';
import type { ArchitectureGraph, ComponentNode, ClassNode } from '../types/diagram';

describe('Main Sequence Scatter Plot & State Integration', () => {
  const createMockClass = (id: string, name: string, stereotype: 'CLASS' | 'INTERFACE' = 'CLASS'): ClassNode => ({
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

  const mockGraph: ArchitectureGraph = {
    title: 'Test Architecture',
    isProposal: false,
    components: [
      {
        id: 'comp-domain',
        label: 'domain',
        level: 0,
        crap: { mu: 1, max: 1, sigma: 0 },
        mutationScore: 100,
        childPackageIds: [],
        classes: [createMockClass('c1', 'Order', 'CLASS'), createMockClass('c2', 'OrderPort', 'INTERFACE')]
      },
      {
        id: 'comp-adapters',
        label: 'adapters',
        level: 2,
        crap: { mu: 1, max: 1, sigma: 0 },
        mutationScore: 90,
        childPackageIds: [],
        classes: [createMockClass('c3', 'PostgresRepo', 'CLASS')]
      }
    ],
    edges: [
      { from: 'c3', to: 'c2', kind: 'DEPENDENCY', isViolating: false }
    ],
    unassigned: []
  };

  beforeEach(() => {
    diagramStore.graph = mockGraph;
    diagramStore.closeMainSequence();
    diagramStore.exitSandbox();
  });

  it('should initialize with modal closed and toggle correctly', () => {
    expect(diagramStore.isMainSequenceOpen).toBe(false);

    diagramStore.openMainSequence();
    expect(diagramStore.isMainSequenceOpen).toBe(true);

    diagramStore.closeMainSequence();
    expect(diagramStore.isMainSequenceOpen).toBe(false);

    diagramStore.toggleMainSequence();
    expect(diagramStore.isMainSequenceOpen).toBe(true);

    diagramStore.toggleMainSequence();
    expect(diagramStore.isMainSequenceOpen).toBe(false);
  });

  it('should compute activeComponentMetrics for baseGraph components', () => {
    const metrics = diagramStore.activeComponentMetrics;
    expect(metrics['comp-domain']).toBeDefined();
    expect(metrics['comp-adapters']).toBeDefined();

    // comp-domain has 1 incoming edge (Ca = 1, Ce = 0 -> I = 0)
    expect(metrics['comp-domain'].ca).toBe(1);
    expect(metrics['comp-domain'].ce).toBe(0);
    expect(metrics['comp-domain'].instability).toBe(0);

    // comp-adapters has 1 outgoing edge (Ca = 0, Ce = 1 -> I = 1)
    expect(metrics['comp-adapters'].ca).toBe(0);
    expect(metrics['comp-adapters'].ce).toBe(1);
    expect(metrics['comp-adapters'].instability).toBe(1);
  });

  it('should generate activeScatterPlotPoints with accurate coordinates and levels', () => {
    const points = diagramStore.activeScatterPlotPoints;
    expect(points).toHaveLength(2);

    const domainPt = points.find((p) => p.componentId === 'comp-domain')!;
    expect(domainPt).toBeDefined();
    expect(domainPt.level).toBe(0);
    expect(domainPt.x).toBe(60); // I = 0 -> x = 60
    expect(domainPt.y).toBe(260); // A = 0.5 -> y = 260
    expect(domainPt.radius).toBeGreaterThanOrEqual(6);

    const adapterPt = points.find((p) => p.componentId === 'comp-adapters')!;
    expect(adapterPt).toBeDefined();
    expect(adapterPt.level).toBe(2);
    expect(adapterPt.x).toBe(500); // I = 1 -> x = 500
    expect(adapterPt.y).toBe(480); // A = 0 -> y = 480
  });

  it('should dynamically update scatter plot points when Architectural Sandbox is active', () => {
    diagramStore.enterSandbox();
    expect(diagramStore.isSandboxActive).toBe(true);

    // Reassign c3 from adapters to domain
    diagramStore.stageClassMove('c3', 'comp-domain');

    const simulatedPoints = diagramStore.activeScatterPlotPoints;
    expect(simulatedPoints).toHaveLength(2);

    const domainPt = simulatedPoints.find((p) => p.componentId === 'comp-domain')!;
    expect(domainPt.classCount).toBe(3); // was 2, now 3 classes
  });

  it('should support tier filtering and accurate distance calculation', () => {
    const points = diagramStore.activeScatterPlotPoints;
    const domainOnly = points.filter((p) => p.level === 0);
    const adaptersOnly = points.filter((p) => p.level === 2);

    expect(domainOnly).toHaveLength(1);
    expect(adaptersOnly).toHaveLength(1);

    // Domain: I = 0, A = 0.5 -> D = |0.5 + 0 - 1| = 0.5
    expect(domainOnly[0].distance).toBeCloseTo(0.5, 2);

    // Adapters: I = 1, A = 0 -> D = |0 + 1 - 1| = 0
    expect(adaptersOnly[0].distance).toBeCloseTo(0.0, 2);
    expect(adaptersOnly[0].zone).toBe('MAIN_SEQUENCE');
  });
});
