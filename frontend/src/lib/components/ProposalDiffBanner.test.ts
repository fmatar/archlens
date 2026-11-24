import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, unmount, flushSync } from 'svelte';
import ProposalDiffBanner from './ProposalDiffBanner.svelte';
import { diagramStore } from '../state/diagram.svelte';

describe('ProposalDiffBanner component', () => {
  let app: any;

  beforeEach(() => {
    document.body.innerHTML = '';
    diagramStore.baseGraph = null;
    diagramStore.policy = null;
    diagramStore.activeProposalId = null;
  });

  afterEach(() => {
    if (app) {
      unmount(app);
      app = null;
    }
    vi.restoreAllMocks();
  });

  it('renders resolved and remaining violation counts for active proposals and returns to live view', () => {
    const loadSpy = vi.spyOn(diagramStore, 'loadGraph').mockResolvedValue();
    diagramStore.policy = {
      title: 'Test',
      src: 'src',
      prefix: 'com.test',
      hierarchical: true,
      order: ['domain'],
      levels: [['domain']],
      foreign: [],
      proposals: [{ id: 'clean-core', name: 'Clean Architecture Standard', layers: [], omit: [] }],
      omit: []
    };
    diagramStore.activeProposalId = 'clean-core';
    diagramStore.baseGraph = {
      title: 'Test',
      isProposal: true,
      activeProposalId: 'clean-core',
      components: [],
      edges: [],
      unassigned: []
    };

    app = mount(ProposalDiffBanner, { target: document.body });
    flushSync();

    expect(document.body.textContent).toContain('WHAT-IF PROPOSAL: Clean Architecture Standard');
    expect(document.body.textContent).toContain('All Architectural Violations Resolved');

    diagramStore.baseGraph = {
      ...diagramStore.baseGraph,
      edges: [{ from: 'a', to: 'b', kind: 'DEPENDENCY', isViolating: true }]
    };
    flushSync();
    expect(document.body.textContent).toContain('1 Violations Remaining');

    const liveBtn = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Compare Real (Live)')
    );
    expect(liveBtn).toBeDefined();
    liveBtn!.click();
    expect(loadSpy).toHaveBeenCalledWith(null);
  });
});
