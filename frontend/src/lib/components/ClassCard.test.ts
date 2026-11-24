import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, unmount, flushSync } from 'svelte';
import ClassCard from './ClassCard.svelte';
import { diagramStore } from '../state/diagram.svelte';
import type { ClassNode } from '../types/diagram';

describe('ClassCard component', () => {
  let app: any;

  const sampleClass: ClassNode = {
    id: 'com.example.domain.OrderService',
    name: 'OrderService',
    packageName: 'com.example.domain',
    stereotype: 'CLASS',
    isForeign: false,
    level: 0,
    filePath: 'src/main/java/com/example/domain/OrderService.java',
    crap: { mu: 2.4, max: 5.0, sigma: 1.1 },
    coverage: 0.92,
    cc: 3,
    killed: 14,
    survived: 1,
    uncovered: 1,
    fields: [
      { name: 'repo', type: 'OrderRepository', isPrivate: true },
      { name: 'VERSION', type: 'String', isPrivate: false }
    ],
    methods: [
      {
        name: 'placeOrder',
        args: ['OrderCmd'],
        returnType: 'OrderId',
        isPrivate: false,
        cc: 3,
        coverage: 0.9,
        crap: 3.1,
        killed: 5,
        survived: 0,
        uncovered: 0,
        line: 42
      }
    ]
  };

  beforeEach(() => {
    document.body.innerHTML = '';
    diagramStore.selectedClass = null;
    diagramStore.sourceFileModal = null;
    diagramStore.isSandboxActive = false;
    diagramStore.stagedClassMoves.clear();
    diagramStore.baseGraph = {
      title: 'Demo',
      isProposal: false,
      components: [
        {
          id: 'domain',
          label: 'domain',
          level: 0,
          crap: { mu: 2.0, max: 5.0, sigma: 1.0 },
          mutationScore: 0.9,
          childPackageIds: ['com.example.domain'],
          packages: ['com.example.domain'],
          classes: [sampleClass]
        },
        {
          id: 'usecase',
          label: 'usecase',
          level: 1,
          crap: { mu: 1.0, max: 1.0, sigma: 0.0 },
          mutationScore: 1.0,
          childPackageIds: ['com.example.usecase'],
          packages: ['com.example.usecase'],
          classes: []
        }
      ],
      edges: [],
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

  it('renders fields, methods, sandbox reassignment controls, and opens source on click', () => {
    const openSourceSpy = vi.spyOn(diagramStore, 'openSource').mockResolvedValue();
    diagramStore.isSandboxActive = true;
    diagramStore.selectedClass = sampleClass;

    app = mount(ClassCard, { target: document.body });
    flushSync();

    expect(document.body.textContent).toContain('OrderService');
    expect(document.body.textContent).toContain('«CLASS»');
    expect(document.body.textContent).toContain('OrderRepository');
    expect(document.body.textContent).toContain('placeOrder(OrderCmd)');
    expect(document.body.textContent).toContain('Sandbox Reassign:');

    const methodBtn = document.querySelector('.method-item') as HTMLButtonElement;
    methodBtn.click();
    expect(openSourceSpy).toHaveBeenCalledWith(sampleClass.filePath, 42);

    const viewSourceBtn = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('View Source Code')
    );
    viewSourceBtn!.click();
    expect(openSourceSpy).toHaveBeenCalledWith(sampleClass.filePath, 1);

    const select = document.querySelector('select') as HTMLSelectElement;
    select.value = 'usecase';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    expect(diagramStore.stagedClassMoves.get(sampleClass.id)).toBe('usecase');

    select.value = 'domain';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    expect(diagramStore.stagedClassMoves.has(sampleClass.id)).toBe(false);
  });
});
