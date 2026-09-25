import { describe, it, expect, beforeEach, vi } from 'vitest';
import { diagramStore } from './diagram.svelte';
import { DEMO_GRAPH_REAL } from '../data/demoData';

describe('diagramStore state management', () => {
  beforeEach(() => {
    diagramStore.resetZoom();
    diagramStore.selectedClass = null;
    diagramStore.activeProposalId = null;
    diagramStore.declutterMode = 'ARROWS';
  });

  it('should initialize with default states', () => {
    expect(diagramStore.zoom).toBe(1.0);
    expect(diagramStore.panX).toBe(0);
    expect(diagramStore.panY).toBe(0);
    expect(diagramStore.selectedClass).toBeNull();
    expect(diagramStore.activeProposalId).toBeNull();
  });

  it('should cycle declutter modes', () => {
    expect(diagramStore.declutterMode).toBe('ARROWS');
    diagramStore.cycleDeclutter();
    expect(diagramStore.declutterMode).toBe('REMOVE_ARROWS');
    diagramStore.cycleDeclutter();
    expect(diagramStore.declutterMode).toBe('ELEMENTS');
    diagramStore.cycleDeclutter();
    expect(diagramStore.declutterMode).toBe('CLASSES');
    diagramStore.cycleDeclutter();
    expect(diagramStore.declutterMode).toBe('NONE');
    diagramStore.cycleDeclutter();
    expect(diagramStore.declutterMode).toBe('ARROWS');
  });

  it('should reset zoom and camera coordinates', () => {
    diagramStore.zoom = 2.5;
    diagramStore.panX = 150;
    diagramStore.panY = -80;

    diagramStore.resetZoom();

    expect(diagramStore.zoom).toBe(1.0);
    expect(diagramStore.panX).toBe(0);
    expect(diagramStore.panY).toBe(0);
  });

  it('should manage agent regeneration state and notices', () => {
    expect(diagramStore.isRegenerating).toBe(false);
    expect(diagramStore.regenNotice).toBeNull();

    diagramStore.isRegenerating = true;
    diagramStore.regenNotice = 'Agent analyzing AST...';

    expect(diagramStore.isRegenerating).toBe(true);
    expect(diagramStore.regenNotice).toContain('Agent');
  });

  it('should toggle active proposals', () => {
    expect(diagramStore.activeProposalId).toBeNull();
    diagramStore.activeProposalId = 'clean-core';
    expect(diagramStore.activeProposalId).toBe('clean-core');
  });

  it('should manage focused node state', () => {
    expect(diagramStore.focusedNodeId).toBeNull();
    diagramStore.setFocusedNode('domain');
    expect(diagramStore.focusedNodeId).toBe('domain');
    diagramStore.setFocusedNode(null);
    expect(diagramStore.focusedNodeId).toBeNull();
  });

  it('should record agent telemetry events', () => {
    const initialCount = diagramStore.telemetryEvents.length;
    diagramStore.addTelemetryEvent('TASK', 'Test refactor directive', 'Target: AST scan');
    expect(diagramStore.telemetryEvents.length).toBe(initialCount + 1);
    expect(diagramStore.telemetryEvents[0].message).toBe('Test refactor directive');
    expect(diagramStore.telemetryEvents[0].type).toBe('TASK');
  });

  it('should toggle command palette and telemetry drawer', () => {
    expect(diagramStore.isCommandPaletteOpen).toBe(false);
    diagramStore.isCommandPaletteOpen = true;
    expect(diagramStore.isCommandPaletteOpen).toBe(true);

    expect(diagramStore.isTelemetryDrawerOpen).toBe(false);
    diagramStore.isTelemetryDrawerOpen = true;
    expect(diagramStore.isTelemetryDrawerOpen).toBe(true);
  });

  it('should toggle edge bundling corridors', () => {
    expect(diagramStore.isEdgeBundlingEnabled).toBe(true);
    diagramStore.toggleEdgeBundling();
    expect(diagramStore.isEdgeBundlingEnabled).toBe(false);
    diagramStore.toggleEdgeBundling();
    expect(diagramStore.isEdgeBundlingEnabled).toBe(true);
  });

  it('should toggle and clear multi-select declutter filters', () => {
    expect(diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')).toBe(false);
    expect(diagramStore.hasDeclutterFilter('HIDE_CLASSES')).toBe(false);

    // Toggle X-Ray
    diagramStore.toggleDeclutterFilter('HIDE_CONFORMING_EDGES');
    expect(diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')).toBe(true);

    // Toggle Compact Cards simultaneously
    diagramStore.toggleDeclutterFilter('HIDE_CLASSES');
    expect(diagramStore.hasDeclutterFilter('HIDE_CLASSES')).toBe(true);
    expect(diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')).toBe(true);

    // Toggle off one filter
    diagramStore.toggleDeclutterFilter('HIDE_CONFORMING_EDGES');
    expect(diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')).toBe(false);
    expect(diagramStore.hasDeclutterFilter('HIDE_CLASSES')).toBe(true);

    // Clear all filters
    diagramStore.clearDeclutterFilters();
    expect(diagramStore.hasDeclutterFilter('HIDE_CLASSES')).toBe(false);
  });

  it('should manage recent projects and open project modal', () => {
    expect(diagramStore.isOpenProjectModalOpen).toBe(false);
    diagramStore.isOpenProjectModalOpen = true;
    expect(diagramStore.isOpenProjectModalOpen).toBe(true);

    diagramStore.saveRecentProject('/Users/dev/cool-project', 'cool-project');
    expect(diagramStore.recentProjects.length).toBeGreaterThan(0);
    expect(diagramStore.recentProjects[0].path).toBe('/Users/dev/cool-project');
    expect(diagramStore.recentProjects[0].name).toBe('cool-project');
  });

  it('should manage LLM prompt modal state and fetch dossier', async () => {
    expect(diagramStore.isLlmPromptModalOpen).toBe(false);
    expect(diagramStore.llmPromptDossier).toBe('');

    // Mock fetch
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url: any) => {
      if (url.toString().includes('/api/diagram/llm-dossier')) {
        return {
          ok: true,
          text: async () => '# Clean Architecture Optimization Dossier — Mock'
        } as any;
      }
      return originalFetch(url);
    };

    try {
      await diagramStore.openLlmPromptModal();
      expect(diagramStore.isLlmPromptModalOpen).toBe(true);
      expect(diagramStore.llmPromptDossier).toContain('# Clean Architecture Optimization Dossier');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('should handle LLM dossier fetch failure gracefully', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url: any) => {
      if (url.toString().includes('/api/diagram/llm-dossier')) {
        return {
          ok: false,
          status: 500,
          statusText: 'Internal Server Error'
        } as any;
      }
      return originalFetch(url);
    };

    try {
      await diagramStore.fetchLlmDossier();
      expect(diagramStore.llmPromptDossier).toContain('Error loading LLM Prompt Dossier');
      expect(diagramStore.llmPromptDossier).toContain('500');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('should handle edge tooltip lifecycle with hover intent, cancel, and clear', () => {
    vi.useFakeTimers();
    try {
      expect(diagramStore.activeEdgeTooltip).toBeNull();

      const mockInfo: any = {
        edge: { from: 'a', to: 'b', kind: 'DEPENDS_ON', isViolating: false },
        fromLabel: 'CompA',
        toLabel: 'CompB',
        fromLevel: 1,
        toLevel: 0,
        x: 100,
        y: 200
      };

      diagramStore.showEdgeTooltip(mockInfo);
      expect(diagramStore.activeEdgeTooltip).toEqual(mockInfo);

      // Schedule dismiss with 180ms delay
      diagramStore.scheduleDismissEdgeTooltip(180);
      expect(diagramStore.activeEdgeTooltip).toEqual(mockInfo);

      // Cancel dismiss while still within grace period
      diagramStore.cancelDismissEdgeTooltip();
      vi.advanceTimersByTime(250);
      expect(diagramStore.activeEdgeTooltip).toEqual(mockInfo);

      // Schedule dismiss and let timer expire
      diagramStore.scheduleDismissEdgeTooltip(180);
      vi.advanceTimersByTime(180);
      expect(diagramStore.activeEdgeTooltip).toBeNull();

      // Immediate clear
      diagramStore.showEdgeTooltip(mockInfo);
      expect(diagramStore.activeEdgeTooltip).toEqual(mockInfo);
      diagramStore.clearEdgeTooltip();
      expect(diagramStore.activeEdgeTooltip).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('should manage snapshots, version comparison, and calculate diff metrics', async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url: any) => {
      const u = url.toString();
      if (u.includes('/api/snapshots/v0.0.1-Alpha-05')) {
        return {
          ok: true,
          json: async () => ({
            title: 'Snapshot',
            isProposal: false,
            components: [{ id: 'core', label: 'Core', level: 0, classes: [] }],
            edges: [
              { from: 'core', to: 'ext', kind: 'USES', isViolating: true }
            ]
          })
        } as any;
      }
      if (u.includes('/api/snapshots')) {
        return {
          ok: true,
          json: async () => ({
            snapshots: [
              { id: 'v0.0.1-Alpha-05', label: 'v0.0.1-Alpha-05', tag: 'v0.0.1-Alpha-05' }
            ]
          })
        } as any;
      }
      return originalFetch(url);
    };

    try {
      await diagramStore.loadSnapshots();
      expect(diagramStore.availableSnapshots.length).toBe(1);
      expect(diagramStore.availableSnapshots[0].id).toBe('v0.0.1-Alpha-05');

      // Set base graph and comparison target
      diagramStore.graph = DEMO_GRAPH_REAL;
      await diagramStore.setComparisonTarget('v0.0.1-Alpha-05');
      expect(diagramStore.isComparing).toBe(true);
      expect(diagramStore.comparisonTargetId).toBe('v0.0.1-Alpha-05');
      expect(diagramStore.snapshotGraph).not.toBeNull();

      // Check diff metrics calculation
      expect(diagramStore.diffMetrics).toEqual({
        addedNodes: 3,
        removedNodes: 1,
        newViolations: 0,
        fixedViolations: 1,
        totalBefore: 1,
        totalAfter: 0
      });

      // Exit comparison
      await diagramStore.setComparisonTarget(null);
      expect(diagramStore.isComparing).toBe(false);
      expect(diagramStore.comparisonTargetId).toBeNull();
      expect(diagramStore.snapshotGraph).toBeNull();
      expect(diagramStore.diffMetrics).toBeNull();
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});
