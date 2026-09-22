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
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('Network offline'));

    await diagramStore.openSource('org.llmwiki.Dummy', 10);

    expect(diagramStore.sourceFileModal).not.toBeNull();
    expect(diagramStore.sourceFileModal?.content).toContain('Error connecting to server');

    globalThis.fetch = originalFetch;
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
});
