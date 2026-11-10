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

      diagramStore.openExportModal('DIP_REFACTOR');
      expect(diagramStore.isExportModalOpen).toBe(true);
      expect(diagramStore.exportInitialDiagramType).toBe('DIP_REFACTOR');
      diagramStore.closeExportModal();
      expect(diagramStore.isExportModalOpen).toBe(false);
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

  it('should dispatch DIP with intent note, verify refactoring, sync ADR, and rollback snapshot', async () => {
    const originalFetch = globalThis.fetch;
    const calls: { url: string; body: any }[] = [];

    globalThis.fetch = async (url: any, init?: any) => {
      const u = url.toString();
      const parsedBody = init?.body ? JSON.parse(init.body) : null;
      calls.push({ url: u, body: parsedBody });

      if (u.includes('/api/mailbox/to-agent')) {
        return {
          ok: true,
          json: async () => ({
            id: 1,
            op: 'INVERT_DEPENDENCY',
            payload: { snapshotId: 'snap-test-999' }
          })
        } as any;
      }
      if (u.includes('/api/mailbox/verify')) {
        return {
          ok: true,
          json: async () => ({
            passed: true,
            initialViolations: 2,
            remainingViolations: 0,
            violationDelta: 2,
            summary: 'Refactoring verified: violations reduced by 2',
            canRollback: true,
            snapshotId: 'snap-test-999'
          })
        } as any;
      }
      if (u.includes('/api/mailbox/sync-adr')) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            filename: 'ADR-0004-invert-order-repo.md',
            index: 4
          })
        } as any;
      }
      if (u.includes('/api/mailbox/rollback')) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            snapshotId: 'snap-test-999'
          })
        } as any;
      }
      if (u.includes('/api/graph')) {
        return {
          ok: true,
          json: async () => DEMO_GRAPH_REAL
        } as any;
      }
      if (u.includes('/api/policy')) {
        return {
          ok: true,
          json: async () => ({ title: 'Demo' })
        } as any;
      }
      if (u.includes('/api/fitness')) {
        return {
          ok: true,
          json: async () => ({ fitnessScore: 95, grade: 'A', overallPassed: true, rules: [] })
        } as any;
      }
      return originalFetch(url, init);
    };

    try {
      diagramStore.graph = DEMO_GRAPH_REAL;
      diagramStore.humanIntentNote = 'Keep Domain Core free of JDBC drivers';

      const mockPlan = diagramStore.createFallbackDipPlan(
        'com.example.domain.OrderService',
        'com.example.adapters.JdbcOrderRepo'
      );
      await diagramStore.dispatchDipToAgent(mockPlan);

      expect(diagramStore.lastRefactorSnapshotId).toBe('snap-test-999');
      expect(diagramStore.isTelemetryDrawerOpen).toBe(true);
      const dispatchCall = calls.find((c) => c.url.includes('/api/mailbox/to-agent'));
      expect(dispatchCall?.body.payload.architecturalIntent).toBe(
        'Keep Domain Core free of JDBC drivers'
      );

      // Verify Refactoring
      const verifyRes = await diagramStore.verifyLastRefactoring();
      expect(verifyRes?.passed).toBe(true);
      expect(verifyRes?.violationDelta).toBe(2);
      expect(diagramStore.lastVerificationResult?.passed).toBe(true);

      // Sync Living ADR
      const adrFile = await diagramStore.syncVerifiedRefactorAdr();
      expect(adrFile).toBe('ADR-0004-invert-order-repo.md');
      expect(diagramStore.lastSyncedAdrFilename).toBe('ADR-0004-invert-order-repo.md');

      // Rollback Snapshot
      const rolledBack = await diagramStore.rollbackLastRefactoring();
      expect(rolledBack).toBe(true);
      expect(diagramStore.lastRefactorSnapshotId).toBeNull();
      expect(diagramStore.lastVerificationResult).toBeNull();
    } finally {
      globalThis.fetch = originalFetch;
      diagramStore.humanIntentNote = '';
    }
  });

  it('automatically activates WebGL/Canvas renderer at 500+ classes and supports manual renderEngineMode overrides', () => {
    diagramStore.setRenderEngineMode('auto');
    diagramStore.graph = DEMO_GRAPH_REAL;
    expect(diagramStore.totalClassCount).toBeLessThan(500);
    expect(diagramStore.isWebGlRendererActive).toBe(false);

    // Cycle to explicit WebGL mode
    diagramStore.toggleRenderEngineMode();
    expect(diagramStore.renderEngineMode).toBe('webgl');
    expect(diagramStore.isWebGlRendererActive).toBe(true);

    // Cycle to explicit SVG mode
    diagramStore.toggleRenderEngineMode();
    expect(diagramStore.renderEngineMode).toBe('svg');
    expect(diagramStore.isWebGlRendererActive).toBe(false);

    // Cycle back to Auto mode and load a 505-class synthetic graph
    diagramStore.toggleRenderEngineMode();
    expect(diagramStore.renderEngineMode).toBe('auto');
    const manyClasses = Array.from({ length: 505 }, (_, i) => ({
      id: `com.acme.domain.Entity${i}`,
      name: `Entity${i}`,
      packageName: 'com.acme.domain',
      filePath: `Entity${i}.java`,
      stereotype: 'CLASS' as const,
      isForeign: false,
      level: 0,
      crap: { mu: 1, max: 1, sigma: 0 },
      coverage: 1,
      cc: 1,
      killed: 1,
      survived: 0,
      uncovered: 0,
      fields: [],
      methods: []
    }));
    diagramStore.graph = {
      ...DEMO_GRAPH_REAL,
      components: [
        {
          id: 'domain',
          label: 'domain',
          level: 0,
          crap: { mu: 1, max: 1, sigma: 0 },
          mutationScore: 1,
          childPackageIds: [],
          classes: manyClasses
        }
      ]
    };
    expect(diagramStore.totalClassCount).toBe(505);
    expect(diagramStore.isWebGlRendererActive).toBe(true);
  });

  it('loads curated interactive reference architecture showcase presets (PetClinic, Quarkus, RealWorld)', async () => {
    expect(diagramStore.showcasePresets.length).toBeGreaterThanOrEqual(3);

    await diagramStore.setProjectRoot('preset:spring-petclinic', false);
    expect(diagramStore.activeShowcasePresetId).toBe('spring-petclinic');
    expect(diagramStore.graph?.title).toBe('Spring PetClinic Clean Architecture');
    expect(diagramStore.violatingEdgesCount).toBe(1);

    await diagramStore.setProjectRoot('preset:quarkus-quickstart', false);
    expect(diagramStore.activeShowcasePresetId).toBe('quarkus-quickstart');
    expect(diagramStore.violatingEdgesCount).toBe(0);

    await diagramStore.setProjectRoot('preset:realworld-conduit', false);
    expect(diagramStore.activeShowcasePresetId).toBe('realworld-conduit');
    expect(diagramStore.hasCycles).toBe(true);
    expect(
      diagramStore.graph?.edges.some((e) => e.label === 'HTTP /api/articles')
    ).toBe(true);
  });
});
