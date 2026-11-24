import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, unmount, flushSync } from 'svelte';
import SourceModal from './SourceModal.svelte';
import { diagramStore } from '../state/diagram.svelte';

describe('SourceModal component', () => {
  let app: any;

  beforeEach(() => {
    document.body.innerHTML = '';
    diagramStore.sourceFileModal = null;
    diagramStore.isSourceLoading = false;
    diagramStore.projectRoot = '/workspace/app';
  });

  afterEach(() => {
    if (app) {
      unmount(app);
      app = null;
    }
    vi.restoreAllMocks();
  });

  it('renders relative file path, highlights target line, copies path, and handles loading state', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    diagramStore.sourceFileModal = {
      filePath: '/workspace/app/src/Order.java',
      content: 'package app;\npublic class Order {\n  int id;\n}',
      line: 2
    };

    app = mount(SourceModal, { target: document.body });
    flushSync();

    expect(document.body.textContent).toContain('src/Order.java');
    expect(document.body.textContent).toContain(': line 2');
    const targetRow = document.querySelector('tr.target-code-row');
    expect(targetRow).not.toBeNull();
    expect(targetRow?.textContent).toContain('public class Order');

    const copyBtn = document.querySelector('button[title="Copy path to clipboard"]') as HTMLButtonElement;
    await copyBtn.click();
    expect(writeText).toHaveBeenCalledWith('/workspace/app/src/Order.java');

    diagramStore.isSourceLoading = true;
    flushSync();
    expect(document.querySelector('.animate-pulse')).not.toBeNull();
  });
});
