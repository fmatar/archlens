import { describe, it, expect, beforeEach, vi } from 'vitest';
import { diagramStore } from './diagram.svelte';
import type { ArchitectureGraph, ArchitecturePolicy, ComponentNode, ClassNode, DependencyEdge } from '../types/diagram';

describe('diagramStore Architectural Sandbox ("What-If") Tests', () => {
  const createMockClass = (id: string, name: string, level: number): ClassNode => ({
    id,
    name,
    packageName: 'com.example',
    filePath: `src/${name}.java`,
    stereotype: 'CLASS',
    isForeign: false,
    level,
    crap: { mu: 1, max: 1, sigma: 0 },
    coverage: 100,
    cc: 1,
    killed: 0,
    survived: 0,
    uncovered: 0,
    fields: [],
    methods: []
  });

  let testGraph: ArchitectureGraph;
  let testPolicy: ArchitecturePolicy;

  beforeEach(() => {
    diagramStore.exitSandbox();
    diagramStore.resetZoom(true);

    const c1 = createMockClass('c1', 'OrderService', 0); // Domain level 0
    const c2 = createMockClass('c2', 'PostgresRepo', 2); // Adapters level 2

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

    const violatingEdge: DependencyEdge = {
      from: 'c1',
      to: 'c2',
      kind: 'DEPENDENCY',
      isViolating: true
    };

    testGraph = {
      title: 'Sandbox Test Graph',
      isProposal: false,
      components: [domainComp, adaptersComp],
      edges: [violatingEdge],
      unassigned: []
    };

    testPolicy = {
      title: 'Policy',
      src: 'src',
      prefix: 'com.example',
      hierarchical: true,
      order: ['domain', 'adapters'],
      levels: [['domain'], ['adapters']],
      foreign: [],
      proposals: [],
      omit: []
    };

    diagramStore.graph = testGraph;
    diagramStore.policy = testPolicy;
  });

  it('should toggle sandbox mode and reflect live vs simulated graph', () => {
    expect(diagramStore.isSandboxActive).toBe(false);
    expect(diagramStore.graph).toEqual(testGraph);

    diagramStore.enterSandbox();
    expect(diagramStore.isSandboxActive).toBe(true);

    // Initial sandbox with no moves matches base graph
    expect(diagramStore.graph?.components[0].classes).toHaveLength(1);

    diagramStore.toggleSandbox();
    expect(diagramStore.isSandboxActive).toBe(false);

    diagramStore.toggleSandbox();
    expect(diagramStore.isSandboxActive).toBe(true);
  });

  it('should stage class move, resolve violation, and update simulated graph dynamically', () => {
    diagramStore.enterSandbox();

    // Stage moving c1 (OrderService) to comp-adapters
    diagramStore.stageClassMove('c1', 'comp-adapters');

    expect(diagramStore.stagedClassMoves.size).toBe(1);
    expect(diagramStore.stagedClassMoves.get('c1')).toBe('comp-adapters');

    const sim = diagramStore.activeSandboxSimulation;
    expect(sim).not.toBeNull();
    expect(sim?.stagedMoves).toHaveLength(1);
    expect(sim?.stagedMoves[0].className).toBe('OrderService');
    expect(sim?.stagedMoves[0].targetComponentName).toBe('adapters');

    // Baseline had 1 violation; simulated should have 0
    expect(sim?.baselineViolations).toBe(1);
    expect(sim?.simulatedViolations).toBe(0);
    expect(sim?.violationDelta).toBe(-1);

    // Verify getter returns simulated graph where c1 is now in adapters
    const currentGraph = diagramStore.graph;
    const domainComp = currentGraph?.components.find((c) => c.id === 'comp-domain');
    const adaptersComp = currentGraph?.components.find((c) => c.id === 'comp-adapters');

    expect(domainComp?.classes).toHaveLength(0);
    expect(adaptersComp?.classes).toHaveLength(2);
    expect(currentGraph?.edges[0].isViolating).toBe(false);

    // Unstage move
    diagramStore.unstageClassMove('c1');
    expect(diagramStore.stagedClassMoves.size).toBe(0);
    expect(diagramStore.graph?.components.find((c) => c.id === 'comp-domain')?.classes).toHaveLength(1);
  });

  it('should reset sandbox and clear all staged moves', () => {
    diagramStore.enterSandbox();
    diagramStore.stageClassMove('c1', 'comp-adapters');
    expect(diagramStore.stagedClassMoves.size).toBe(1);

    diagramStore.resetSandbox();
    expect(diagramStore.stagedClassMoves.size).toBe(0);
  });

  it('should save sandbox simulation as a living proposal and activate it', () => {
    diagramStore.enterSandbox();
    diagramStore.stageClassMove('c1', 'comp-adapters');

    const proposalId = diagramStore.saveSandboxAsProposal();

    expect(proposalId).toContain('sandbox-');
    expect(diagramStore.isSandboxActive).toBe(false);
    expect(diagramStore.activeProposalId).toBe(proposalId);
    expect(diagramStore.policy?.proposals.some((p) => p.id === proposalId)).toBe(true);
  });

  it('should dispatch sandbox proposal to AI agent mailbox and handle timeout & errors', async () => {
    vi.useFakeTimers();
    const originalFetch = globalThis.fetch;
    const postMock = vi.fn().mockResolvedValue({ ok: true } as any);
    globalThis.fetch = postMock;

    diagramStore.enterSandbox();
    diagramStore.stageClassMove('c1', 'comp-adapters');

    const dispatchPromise = diagramStore.dispatchSandboxToAgent();
    await dispatchPromise;

    expect(postMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/mailbox/to-agent'),
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('APPLY_PROPOSAL')
      })
    );
    expect(diagramStore.sandboxDispatchNotice).toBe('Refactoring task dispatched to AI agent mailbox!');

    // Advance timers by 1500ms
    vi.advanceTimersByTime(1500);
    expect(diagramStore.isDispatchingSandboxProposal).toBe(false);
    expect(diagramStore.sandboxDispatchNotice).toBeNull();

    // Test network error handling
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('IPC offline'));
    await diagramStore.dispatchSandboxToAgent();
    expect(diagramStore.sandboxDispatchNotice).toContain('Error dispatching task: IPC offline');
    expect(diagramStore.isDispatchingSandboxProposal).toBe(false);

    vi.useRealTimers();
    globalThis.fetch = originalFetch;
  });
});
