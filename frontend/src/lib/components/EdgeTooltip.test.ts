import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, unmount, flushSync } from 'svelte';
import EdgeTooltip from './EdgeTooltip.svelte';
import { diagramStore } from '../state/diagram.svelte';

describe('EdgeTooltip component', () => {
  let app: any;

  beforeEach(() => {
    document.body.innerHTML = '';
    diagramStore.clearEdgeTooltip();
    diagramStore.comparisonTargetId = null;
    diagramStore.snapshotGraph = null;
  });

  afterEach(() => {
    if (app) {
      unmount(app);
      app = null;
    }
    vi.restoreAllMocks();
  });

  it('renders violating and conforming dependency tooltips and triggers DIP inversion', () => {
    const dipSpy = vi.spyOn(diagramStore, 'openDipInversion').mockResolvedValue();

    diagramStore.activeEdgeTooltip = {
      edge: { from: 'com.app.OrderService', to: 'com.app.StripeClient', kind: 'DEPENDENCY', isViolating: true },
      fromLabel: 'domain',
      toLabel: 'adapters',
      fromLevel: 0,
      toLevel: 2,
      x: 250,
      y: 180,
      isBundled: true,
      bundleCount: 3,
      violationCount: 2
    };

    app = mount(EdgeTooltip, { target: document.body });
    flushSync();

    expect(document.body.textContent).toContain('VIOLATING CORRIDOR (2/3 BREACHES)');
    expect(document.body.textContent).toContain('Ring 0: Domain Core');
    expect(document.body.textContent).toContain('Ring 2: Adapters');

    const invertBtn = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Invert Dependency (DIP)')
    );
    invertBtn!.click();
    expect(dipSpy).toHaveBeenCalledWith('com.app.OrderService', 'com.app.StripeClient');

    diagramStore.activeEdgeTooltip = {
      edge: { from: 'com.app.Controller', to: 'com.app.UseCase', kind: 'DEPENDENCY', isViolating: false },
      fromLabel: 'rest',
      toLabel: 'usecase',
      fromLevel: 3,
      toLevel: 1,
      x: 200,
      y: 120
    };
    flushSync();
    expect(document.body.textContent).toContain('CONFORMING DEPENDENCY');
    expect(document.body.textContent).toContain('Ring 3: Frameworks');
    expect(document.body.textContent).toContain('Ring 1: Application');
  });
});
