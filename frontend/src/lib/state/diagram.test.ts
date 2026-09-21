import { describe, it, expect, beforeEach } from 'vitest';
import { diagramStore } from './diagram.svelte';

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
});
