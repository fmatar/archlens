import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mount, unmount } from 'svelte';
import ArchlensLogo from './ArchlensLogo.svelte';

describe('ArchlensLogo component (Concept C — Hexagonal Dependency Vault)', () => {
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

  it('renders the 4 concentric Clean Architecture layers of the Hexagonal Dependency Vault', () => {
    app = mount(ArchlensLogo, {
      target: document.body,
      props: { size: 32, title: 'Archlens Brand Mark' }
    });

    const svg = document.querySelector('svg[data-testid="archlens-logo"]') as SVGSVGElement;
    expect(svg).not.toBeNull();
    expect(svg.getAttribute('width')).toBe('32');
    expect(svg.getAttribute('height')).toBe('32');

    const titleEl = svg.querySelector('title');
    expect(titleEl?.textContent).toBe('Archlens Brand Mark');

    // Squircle container + 4 architectural paths (Outer Vault, Middle Vault, Inward Chevron, Domain Core Hexagon)
    const rect = svg.querySelector('rect');
    expect(rect).not.toBeNull();
    expect(rect?.getAttribute('fill')).toBe('#0B0F19');

    const paths = svg.querySelectorAll('path');
    expect(paths.length).toBe(4);
    expect(paths[0].getAttribute('fill')).toBe('#38BDF8');
    expect(paths[1].getAttribute('fill')).toBe('#6366F1');
    expect(paths[2].getAttribute('fill')).toBe('#A855F7');
    expect(paths[3].getAttribute('fill')).toContain('url(#archlens-core-gold');
  });

  it('supports hiding the squircle badge and toggling live pulse state', () => {
    app = mount(ArchlensLogo, {
      target: document.body,
      props: { size: 20, showSquircle: false, pulse: true }
    });

    const svg = document.querySelector('svg[data-testid="archlens-logo"]') as SVGSVGElement;
    expect(svg).not.toBeNull();
    expect(svg.querySelector('rect')).toBeNull();
    expect(svg.classList.contains('animate-pulse')).toBe(true);
  });
});
