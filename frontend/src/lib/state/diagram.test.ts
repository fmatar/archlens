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
});
