<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import type { DependencyEdge } from '../types/diagram';
  import { diagramStore } from '../state/diagram.svelte';

  interface Props {
    edge: DependencyEdge;
    x1: number;
    y1: number;
    x2: number;
    y2: number;
    fromLabel?: string;
    toLabel?: string;
    fromLevel?: number | null;
    toLevel?: number | null;
    isDimmed?: boolean;
    isHighlighted?: boolean;
    isBundled?: boolean;
    bundleCount?: number;
    violationCount?: number;
  }

  let {
    edge,
    x1,
    y1,
    x2,
    y2,
    fromLabel = '',
    toLabel = '',
    fromLevel = null,
    toLevel = null,
    isDimmed = false,
    isHighlighted = false,
    isBundled = false,
    bundleCount = 1,
    violationCount = 0
  }: Props = $props();

  let flowPathEl: SVGPathElement | null = $state(null);
  let glowPathEl: SVGPathElement | null = $state(null);
  let badgeEl: SVGGElement | null = $state(null);

  // Compute smooth cubic Bezier control points
  let midY = $derived((y1 + y2) / 2);
  let midX = $derived((x1 + x2) / 2);
  let pathD = $derived(`M ${x1} ${y1} C ${x1} ${midY}, ${x2} ${midY}, ${x2} ${y2}`);
  let markerId = $derived(edge.isViolating ? 'arrow-violating' : 'arrow-normal');

  // Scale stroke width dynamically for bundled corridors
  let strokeThickness = $derived.by(() => {
    if (!isBundled) {
      return edge.isViolating ? 2.5 : (isHighlighted ? 2.5 : 1.5);
    }
    const count = bundleCount || 1;
    const base = edge.isViolating ? 3.0 : (isHighlighted ? 3.0 : 2.0);
    return Math.min(7.5, base + Math.log2(count) * 1.1);
  });

  $effect(() => {
    // Svelte 5 + GSAP lifecycle management
    const ctx = gsap.context(() => {
      if (edge.isViolating) {
        // Violating: Pulsing red alarm glow and alarm dash
        if (glowPathEl) {
          gsap.to(glowPathEl, {
            attr: { 'stroke-width': 7, 'stroke-opacity': 0.75 },
            duration: 0.7,
            repeat: -1,
            yoyo: true,
            ease: 'sine.inOut'
          });
        }
        if (flowPathEl) {
          gsap.to(flowPathEl, {
            strokeDashoffset: 24,
            duration: 0.6,
            repeat: -1,
            ease: 'none'
          });
        }
        if (badgeEl) {
          gsap.to(badgeEl, {
            scale: 1.15,
            transformOrigin: 'center center',
            duration: 0.7,
            repeat: -1,
            yoyo: true,
            ease: 'sine.inOut'
          });
        }
      } else {
        // Valid Clean Architecture: Smooth directional flow pulse in arrow direction
        if (flowPathEl) {
          gsap.to(flowPathEl, {
            strokeDashoffset: -28,
            duration: 1.4,
            repeat: -1,
            ease: 'none'
          });
        }
      }
    });

    return () => ctx.revert();
  });

  function handleMouseEnter(e: MouseEvent) {
    diagramStore.activeEdgeTooltip = {
      edge,
      fromLabel: fromLabel || edge.from,
      toLabel: toLabel || edge.to,
      fromLevel,
      toLevel,
      x: e.clientX,
      y: e.clientY,
      isBundled,
      bundleCount,
      violationCount
    };
  }

  function handleMouseLeave() {
    // slight delay or clear
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<g
  class="group cursor-pointer transition-opacity duration-300"
  opacity={isDimmed ? 0.12 : 1.0}
  onmouseenter={handleMouseEnter}
>
  <!-- Red Violation Ambient Glow (if violating) -->
  {#if edge.isViolating}
    <path
      bind:this={glowPathEl}
      d={pathD}
      fill="none"
      stroke="#ef4444"
      stroke-width={isHighlighted ? strokeThickness + 3 : strokeThickness + 2}
      stroke-opacity="0.3"
      filter="url(#violation-glow)"
    />
  {/if}

  <!-- Base Solid Line -->
  <path
    d={pathD}
    fill="none"
    stroke={edge.isViolating ? '#dc2626' : (isHighlighted ? '#60a5fa' : '#475569')}
    stroke-width={strokeThickness}
    marker-end={`url(#${markerId})`}
    class="group-hover:stroke-blue-400 transition-colors"
  />

  <!-- Directional Motion Flow Overlay -->
  <path
    bind:this={flowPathEl}
    d={pathD}
    fill="none"
    stroke={edge.isViolating ? '#fca5a5' : '#38bdf8'}
    stroke-width={Math.max(1.2, strokeThickness - 0.75)}
    stroke-dasharray={edge.isViolating ? '5 7' : '6 8'}
    stroke-opacity={edge.isViolating ? 0.9 : 0.75}
    class="pointer-events-none"
  />

  <!-- Bundled Pill Badge vs Single Edge Badges -->
  {#if isBundled && bundleCount && bundleCount > 1}
    <g
      transform={`translate(${midX}, ${midY})`}
      class="pointer-events-none select-none"
    >
      <rect
        x={edge.isViolating ? -26 : -18}
        y="-10"
        width={edge.isViolating ? 52 : 36}
        height="20"
        rx="10"
        class={edge.isViolating
          ? 'fill-rose-950/95 stroke-rose-500 stroke-[1.5] shadow-lg shadow-rose-950/80'
          : 'fill-slate-900/95 stroke-slate-600 stroke-[1] shadow-md'}
      />
      <text
        y="4"
        text-anchor="middle"
        class={edge.isViolating
          ? 'fill-rose-200 font-bold text-[10px] font-mono'
          : 'fill-slate-300 font-medium text-[10px] font-mono'}
      >
        {edge.isViolating ? `! ${violationCount}/${bundleCount}` : `${bundleCount}`}
      </text>
    </g>
  {:else if edge.isViolating}
    <g
      bind:this={badgeEl}
      transform={`translate(${midX}, ${midY})`}
      class="pointer-events-none"
    >
      <circle r="9" class="fill-rose-950 stroke-rose-500 stroke-1.5 shadow-lg" />
      <text
        y="3.5"
        text-anchor="middle"
        class="fill-rose-300 font-bold text-[9px] font-mono select-none"
      >
        !
      </text>
    </g>
  {:else if edge.label}
    <text
      x={midX}
      y={midY - 6}
      text-anchor="middle"
      class="fill-slate-400 group-hover:fill-blue-300 text-[10px] font-mono select-none transition-colors"
    >
      {edge.label}
    </text>
  {/if}
</g>
