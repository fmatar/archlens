import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, unmount, flushSync } from 'svelte';
import { diagramStore } from './diagram.svelte';
import MainSequenceModal from '../components/MainSequenceModal.svelte';
import PolicyEditorModal from '../components/PolicyEditorModal.svelte';
import App from '../../App.svelte';
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

  describe('UI Keyboard Shortcuts (M, P, and Escape dismissal)', () => {
    let mountedApp: any = null;

    beforeEach(() => {
      document.body.innerHTML = '';
      diagramStore.closeMainSequence();
      diagramStore.closePolicyEditor();
      if (typeof globalThis.ResizeObserver === 'undefined') {
        globalThis.ResizeObserver = class {
          observe() {}
          unobserve() {}
          disconnect() {}
        } as any;
      }
    });

    afterEach(() => {
      if (mountedApp) {
        unmount(mountedApp);
        mountedApp = null;
      }
      vi.restoreAllMocks();
    });

    it('should close MainSequenceModal when Escape is pressed on window after opening with M', () => {
      mountedApp = mount(MainSequenceModal, { target: document.body });
      flushSync();

      diagramStore.openMainSequence();
      flushSync();
      expect(diagramStore.isMainSequenceOpen).toBe(true);
      expect(document.body.textContent).toContain('Robert C. Martin Main Sequence');

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      flushSync();

      expect(diagramStore.isMainSequenceOpen).toBe(false);
    });

    it('should open MainSequenceModal when M shortcut is pressed in App and close it on Escape', () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockGraph
      } as any);

      mountedApp = mount(App, { target: document.body });
      flushSync();

      expect(diagramStore.isMainSequenceOpen).toBe(false);

      // Press 'm' to open Main Sequence panel
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'm', bubbles: true }));
      flushSync();
      expect(diagramStore.isMainSequenceOpen).toBe(true);
      expect(document.body.textContent).toContain('Robert C. Martin Main Sequence');

      // Press 'Escape' to close Main Sequence panel
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      flushSync();
      expect(diagramStore.isMainSequenceOpen).toBe(false);
    });

    it('should open PolicyEditorModal when P shortcut is pressed and close it on Escape or P', () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockGraph
      } as any);

      mountedApp = mount(App, { target: document.body });
      flushSync();

      expect(diagramStore.isPolicyEditorOpen).toBe(false);

      // Press 'p' to open Policy Designer
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'p', bubbles: true }));
      flushSync();
      expect(diagramStore.isPolicyEditorOpen).toBe(true);
      expect(document.body.textContent).toContain('Concentric Policy Designer');

      // Press 'Escape' to close Policy Designer
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      flushSync();
      expect(diagramStore.isPolicyEditorOpen).toBe(false);

      // Press 'P' (uppercase) to open and 'P' again to toggle closed
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'P', bubbles: true }));
      flushSync();
      expect(diagramStore.isPolicyEditorOpen).toBe(true);

      // Exercise PolicyEditorModal controls while open
      const presetBtn = Array.from(document.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Preset 4-Tier')
      );
      presetBtn?.click();
      flushSync();

      const addTierBtn = Array.from(document.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Add Outer Tier')
      );
      addTierBtn?.click();
      flushSync();

      const resetBtn = document.querySelector('button[aria-label="Reset policy"]') as HTMLButtonElement | null;
      resetBtn?.click();
      flushSync();

      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'P', bubbles: true }));
      flushSync();
      expect(diagramStore.isPolicyEditorOpen).toBe(false);
    });

    it('should exercise MainSequenceModal filters, point selection, and workstation shortcuts (/, l, s, d, v, b, c, f, t, g, +, -, 0)', () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockGraph,
        text: async () => '# Dossier'
      } as any);

      mountedApp = mount(App, { target: document.body });
      flushSync();

      // Open MainSequenceModal with 'm' and exercise tier filters & dot interactions
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'm', bubbles: true }));
      flushSync();
      expect(diagramStore.isMainSequenceOpen).toBe(true);

      const tierButtons = Array.from(document.querySelectorAll('button'));
      for (const label of ['Domain (L0)', 'App (L1)', 'Adapters (L2)', 'Frameworks (L3)', 'All']) {
        tierButtons.find((b) => b.textContent?.includes(label))?.click();
        flushSync();
      }

      const dot = document.querySelector('svg g[role="button"]') as SVGGElement | null;
      dot?.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }));
      dot?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      flushSync();
      dot?.dispatchEvent(new MouseEvent('mouseleave', { bubbles: true }));
      flushSync();

      // Close MainSequenceModal with Escape
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      flushSync();
      expect(diagramStore.isMainSequenceOpen).toBe(false);

      // Command palette ('/') + navigation + Escape
      window.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }));
      flushSync();
      expect(diagramStore.isCommandPaletteOpen).toBe(true);
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      flushSync();
      expect(diagramStore.isCommandPaletteOpen).toBe(false);

      // LLM Prompt modal ('l') + Escape
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'l', bubbles: true }));
      flushSync();
      expect(diagramStore.isLlmPromptModalOpen).toBe(true);
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      flushSync();

      // Sandbox ('s') + details drawer + exit
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 's', bubbles: true }));
      flushSync();
      expect(diagramStore.isSandboxActive).toBe(true);
      const metricsBtn = Array.from(document.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Metrics & Staged')
      );
      metricsBtn?.click();
      flushSync();
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 's', bubbles: true }));
      flushSync();
      expect(diagramStore.isSandboxActive).toBe(false);

      // Declutter and zoom shortcuts
      for (const k of ['d', 'v', 'b', 'c', 'f', 't', 'g', '+', '-', '0', 'Escape']) {
        window.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));
      }
      flushSync();
    });
  });
});
