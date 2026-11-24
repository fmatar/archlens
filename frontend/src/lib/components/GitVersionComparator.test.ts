import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, unmount, flushSync } from 'svelte';
import GitVersionComparator from './GitVersionComparator.svelte';
import { diagramStore } from '../state/diagram.svelte';

describe('GitVersionComparator component', () => {
  let app: any;

  beforeEach(() => {
    document.body.innerHTML = '';
    diagramStore.comparisonTargetId = null;
    diagramStore.snapshotGraph = null;
    diagramStore.availableSnapshots = [
      { id: 'v1.1.0', tag: 'v1.1.0', label: 'v1.1.0', date: '2026-11-10' }
    ];
    diagramStore.baseGraph = {
      title: 'Live',
      isProposal: false,
      components: [
        {
          id: 'domain',
          label: 'domain',
          level: 0,
          crap: { mu: 1, max: 1, sigma: 0 },
          mutationScore: 1,
          childPackageIds: ['domain'],
          classes: []
        },
        {
          id: 'usecase',
          label: 'usecase',
          level: 1,
          crap: { mu: 1, max: 1, sigma: 0 },
          mutationScore: 1,
          childPackageIds: ['usecase'],
          classes: []
        }
      ],
      edges: [{ from: 'a', to: 'b', kind: 'DEPENDENCY', isViolating: true }],
      unassigned: []
    };
  });

  afterEach(() => {
    if (app) {
      unmount(app);
      app = null;
    }
    vi.restoreAllMocks();
  });

  it('opens dropdown, selects a snapshot, renders diff metrics, and exits comparison', () => {
    const setTargetSpy = vi.spyOn(diagramStore, 'setComparisonTarget').mockImplementation(async (id) => {
      diagramStore.comparisonTargetId = id;
      diagramStore.snapshotGraph = id
        ? {
            title: 'Snap',
            isProposal: false,
            components: [
              {
                id: 'domain',
                label: 'domain',
                level: 0,
                crap: { mu: 1, max: 1, sigma: 0 },
                mutationScore: 1,
                childPackageIds: ['domain'],
                classes: []
              }
            ],
            edges: [],
            unassigned: []
          }
        : null;
    });

    app = mount(GitVersionComparator, { target: document.body });
    flushSync();

    const trigger = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Compare Release')
    );
    expect(trigger).toBeDefined();
    trigger!.click();
    flushSync();

    expect(document.body.textContent).toContain('Git Release Comparator');
    const snapOption = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('v1.1.0')
    );
    snapOption!.click();
    flushSync();

    expect(setTargetSpy).toHaveBeenCalledWith('v1.1.0');
    expect(document.body.textContent).toContain('+1 breach');
    expect(document.body.textContent).toContain('+1 comp');

    const exitBtn = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Exit')
    );
    exitBtn!.click();
    flushSync();
    expect(setTargetSpy).toHaveBeenCalledWith(null);
  });
});
