import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, unmount } from 'svelte';
import OpenProjectModal from './OpenProjectModal.svelte';
import { diagramStore } from '../state/diagram.svelte';

describe('OpenProjectModal component', () => {
  let app: any;

  beforeEach(() => {
    document.body.innerHTML = '';
    diagramStore.isOpenProjectModalOpen = false;
    diagramStore.projectRoot = '.';
  });

  afterEach(() => {
    if (app) {
      unmount(app);
      app = null;
    }
    vi.restoreAllMocks();
  });

  it('should auto-populate inputPath with active container workspace and adapt browse button', async () => {
    // Mock global fetch for container environment
    globalThis.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/fs/directories')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            currentPath: '/workspace',
            parentPath: '/',
            breadcrumbs: [{ name: 'workspace', path: '/workspace' }],
            quickNav: [{ name: 'Mounted Workspace', path: '/workspace', icon: 'folder-git' }],
            directories: [
              { name: 'docs', path: '/workspace/docs', isProject: false, hasChildren: true },
              { name: 'src', path: '/workspace/src', isProject: true, projectType: 'Maven', hasChildren: true }
            ],
            isContainer: true,
            nativePickerSupported: false
          })
        });
      }
      return Promise.resolve({
        ok: true,
        json: async () => ({})
      });
    });

    diagramStore.isOpenProjectModalOpen = true;
    app = mount(OpenProjectModal, { target: document.body });

    // Allow effects and microtasks to complete
    await new Promise((resolve) => setTimeout(resolve, 80));

    // Verify input is auto-populated with container directory
    const input = document.querySelector('#project-path-input') as HTMLInputElement;
    expect(input).not.toBeNull();
    expect(input.value).toBe('/workspace');

    // Verify container adaptive browse button
    const browseButton = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Browse Folders')
    );
    expect(browseButton).toBeDefined();

    // Verify container guidance card
    const banner = document.body.textContent;
    expect(banner).toContain('Docker Container Filesystem');
    expect(banner).toContain('Mounted: /workspace');
  });

  it('should support native picker button in host macOS environment', async () => {
    globalThis.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/fs/directories')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            currentPath: '/Users/developer/project',
            parentPath: '/Users/developer',
            breadcrumbs: [{ name: 'project', path: '/Users/developer/project' }],
            quickNav: [{ name: 'Home', path: '/Users/developer', icon: 'home' }],
            directories: [],
            isContainer: false,
            nativePickerSupported: true
          })
        });
      }
      return Promise.resolve({
        ok: true,
        json: async () => ({})
      });
    });

    diagramStore.isOpenProjectModalOpen = true;
    app = mount(OpenProjectModal, { target: document.body });

    await new Promise((resolve) => setTimeout(resolve, 80));

    const input = document.querySelector('#project-path-input') as HTMLInputElement;
    expect(input).not.toBeNull();
    expect(input.value).toBe('/Users/developer/project');

    const nativeBrowseButton = Array.from(document.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Browse...')
    );
    expect(nativeBrowseButton).toBeDefined();
  });
});
