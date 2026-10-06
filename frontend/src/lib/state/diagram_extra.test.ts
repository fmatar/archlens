import { describe, it, expect, beforeEach, vi } from 'vitest';
import { diagramStore } from './diagram.svelte';

describe('diagramStore extended coverage tests', () => {
  beforeEach(() => {
    diagramStore.resetZoom(true);
    diagramStore.selectedClass = null;
    diagramStore.focusedNodeId = null;
    diagramStore.componentOffsets = {};
    diagramStore.clearDeclutterFilters();
  });

  it('should update component offsets correctly', () => {
    diagramStore.setComponentOffset('node-1', { x: 50, y: 100 });
    expect(diagramStore.componentOffsets['node-1']).toEqual({ x: 50, y: 100 });

    diagramStore.setComponentOffset('node-2', { x: -20, y: 30 });
    expect(diagramStore.componentOffsets['node-1']).toEqual({ x: 50, y: 100 });
    expect(diagramStore.componentOffsets['node-2']).toEqual({ x: -20, y: 30 });
  });

  it('should reset layout and clear offsets', () => {
    diagramStore.setComponentOffset('node-1', { x: 100, y: 200 });
    diagramStore.zoom = 1.8;
    diagramStore.panX = 300;

    diagramStore.resetLayout(true);

    expect(diagramStore.componentOffsets).toEqual({});
    expect(diagramStore.zoom).toBe(1.0);
    expect(diagramStore.panX).toBe(0);
  });

  it('should toggle and clear declutter filters correctly', () => {
    expect(diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')).toBe(false);

    diagramStore.toggleDeclutterFilter('HIDE_CONFORMING_EDGES');
    expect(diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')).toBe(true);

    diagramStore.toggleDeclutterFilter('HIDE_CONFORMING_EDGES');
    expect(diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')).toBe(false);

    diagramStore.toggleDeclutterFilter('HIDE_CLASSES');
    diagramStore.toggleDeclutterFilter('HIDE_TIER_LANES');
    expect(diagramStore.declutterFilters.size).toBe(2);

    diagramStore.clearDeclutterFilters();
    expect(diagramStore.declutterFilters.size).toBe(0);
  });

  it('should handle source code fetch error fallback gracefully', async () => {
    // Mock fetch to reject
    const originalFetch = globalThis.fetch;
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('Network offline'));

    await diagramStore.openSource('org.llmwiki.Dummy', 10);

    expect(diagramStore.sourceFileModal).not.toBeNull();
    expect(diagramStore.sourceFileModal?.content).toContain('Error connecting to server');

    globalThis.fetch = originalFetch;
    consoleSpy.mockRestore();
  });

  it('should pan to component safely when graph is initialized or empty', () => {
    // If graph is null, should return without error
    diagramStore.graph = null;
    expect(() => diagramStore.panToComponent('comp-1')).not.toThrow();

    // If graph has components
    diagramStore.graph = {
      title: 'Test',
      isProposal: false,
      components: [
        {
          id: 'comp-1',
          label: 'Domain',
          level: 0,
          crap: { mu: 1, max: 2, sigma: 0.1 },
          mutationScore: 100,
          childPackageIds: [],
          classes: []
        }
      ],
      edges: [],
      unassigned: []
    };

    expect(() => diagramStore.panToComponent('comp-1')).not.toThrow();
    expect(diagramStore.focusedNodeId).toBe('comp-1');
  });

  it('should initialize and close DIP inversion modal properly', async () => {
    const originalFetch = globalThis.fetch;
    const mockPlan = {
      fromClass: 'com.example.OrderService',
      toClass: 'com.example.PostgresRepo',
      fromLevel: 1,
      toLevel: 2,
      portName: 'PostgresRepoPort',
      portPackage: 'com.example.ports',
      portFilePath: 'src/main/java/com/example/ports/PostgresRepoPort.java',
      portInterfaceCode: 'public interface PostgresRepoPort {}',
      adapterRefactorPreview: 'public class PostgresRepo implements PostgresRepoPort {}',
      callerRefactorPreview: 'public class OrderService {}',
      surgicalPrompt: '# DIP Directive'
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockPlan
    } as any);

    await diagramStore.openDipInversion('com.example.OrderService', 'com.example.PostgresRepo');

    expect(diagramStore.isDipModalOpen).toBe(true);
    expect(diagramStore.activeDipPlan).toEqual(mockPlan);

    diagramStore.closeDipModal();
    expect(diagramStore.isDipModalOpen).toBe(false);
    expect(diagramStore.activeDipPlan).toBeNull();

    globalThis.fetch = originalFetch;
  });

  it('should generate fallback DIP plan when server request fails', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('Offline'));

    await diagramStore.openDipInversion('com.example.app.OrderService', 'com.example.db.PostgresOrderRepoImpl');

    expect(diagramStore.isDipModalOpen).toBe(true);
    expect(diagramStore.activeDipPlan).not.toBeNull();
    expect(diagramStore.activeDipPlan?.portName).toBe('PostgresOrderRepoPort');
    expect(diagramStore.activeDipPlan?.portPackage).toBe('com.example.app.ports');
    expect(diagramStore.activeDipPlan?.portInterfaceCode).toContain('PostgresOrderRepoPort');

    diagramStore.closeDipModal();
    globalThis.fetch = originalFetch;
  });

  it('should fallback when server returns non-ok HTTP status and pass projectRoot/proposalId', async () => {
    const originalFetch = globalThis.fetch;
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 500
    } as any);
    globalThis.fetch = fetchMock;

    diagramStore.projectRoot = '/home/user/project';
    diagramStore.activeProposalId = 'prop-99';

    await diagramStore.openDipInversion('com.example.Order', 'com.example.Repo');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/projectRoot=%2Fhome%2Fuser%2Fproject.*proposalId=prop-99|proposalId=prop-99.*projectRoot=%2Fhome%2Fuser%2Fproject/)
    );
    expect(diagramStore.isDipModalOpen).toBe(true);
    expect(diagramStore.activeDipPlan?.portName).toBe('RepoPort');

    diagramStore.projectRoot = '';
    diagramStore.activeProposalId = null;
    diagramStore.closeDipModal();
    globalThis.fetch = originalFetch;
  });

  it('should dispatch DIP inversion task with projectRoot and complete countdown timer', async () => {
    vi.useFakeTimers();
    const originalFetch = globalThis.fetch;
    const postMock = vi.fn().mockResolvedValue({ ok: true } as any);
    globalThis.fetch = postMock;

    diagramStore.projectRoot = '/home/user/project';
    const testPlan = diagramStore.createFallbackDipPlan('com.example.Service', 'com.example.Repo');
    
    diagramStore.isDipModalOpen = true;
    const dispatchPromise = diagramStore.dispatchDipToAgent(testPlan);
    await dispatchPromise;

    expect(postMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/mailbox/to-agent?projectRoot=%2Fhome%2Fuser%2Fproject'),
      expect.anything()
    );
    expect(diagramStore.dipDispatchNotice).toBe('Task dispatched to AI agent mailbox!');
    expect(diagramStore.isDipModalOpen).toBe(true);

    // Advance fake timers by 1200ms
    vi.advanceTimersByTime(1200);

    expect(diagramStore.isDispatchingDip).toBe(false);
    expect(diagramStore.isDipModalOpen).toBe(false);
    expect(diagramStore.dipDispatchNotice).toBeNull();

    diagramStore.projectRoot = '';
    vi.useRealTimers();
    globalThis.fetch = originalFetch;
  });

  it('should handle DIP dispatch network rejection cleanly', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('Agent offline'));

    const testPlan = diagramStore.createFallbackDipPlan('com.example.Service', 'com.example.Repo');
    await diagramStore.dispatchDipToAgent(testPlan);

    expect(diagramStore.isDispatchingDip).toBe(false);
    expect(diagramStore.dipDispatchNotice).toContain('Error dispatching task: Agent offline');

    globalThis.fetch = originalFetch;
  });
});

