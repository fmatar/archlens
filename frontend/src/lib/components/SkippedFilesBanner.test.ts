import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mount, unmount } from 'svelte';
import SkippedFilesBanner from './SkippedFilesBanner.svelte';

describe('SkippedFilesBanner component (#240)', () => {
  let app: any;

  beforeEach(() => {
    document.body.innerHTML = '';
  });

  afterEach(() => {
    if (app) {
      unmount(app);
      app = null;
    }
  });

  it('renders nothing when skipped count is 0', () => {
    app = mount(SkippedFilesBanner, {
      target: document.body,
      props: { count: 0 }
    });
    expect(document.querySelector('[data-testid="skipped-files-banner"]')).toBeNull();
  });

  it('renders singular and plural skipped file notices accurately', () => {
    app = mount(SkippedFilesBanner, {
      target: document.body,
      props: { count: 1 }
    });
    const banner = document.querySelector('[data-testid="skipped-files-banner"]');
    expect(banner).not.toBeNull();
    expect(banner?.textContent).toContain('Scan skipped 1 unreadable or unparseable source file.');
    unmount(app);

    app = mount(SkippedFilesBanner, {
      target: document.body,
      props: { count: 3 }
    });
    const pluralBanner = document.querySelector('[data-testid="skipped-files-banner"]');
    expect(pluralBanner?.textContent).toContain(
      'Scan skipped 3 unreadable or unparseable source files.'
    );
  });
});
