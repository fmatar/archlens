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

    return () => {
      ctx.revert();
      if (hoverTimeoutId) clearTimeout(hoverTimeoutId);
    };
  });

  let hoverTimeoutId: ReturnType<typeof setTimeout> | null = null;

  let diffStatus = $derived.by<'none' | 'new-violation' | 'resolved' | 'added'>(() => {
    if (!diagramStore.isComparing || !diagramStore.snapshotGraph) return 'none';
    const wasInSnapshot = diagramStore.snapshotGraph.edges.find(
      (e) => e.from === edge.from && e.to === edge.to
    );
    if (!wasInSnapshot) return 'added';
    if (edge.isViolating && !wasInSnapshot.isViolating) return 'new-violation';
    if (!edge.isViolating && wasInSnapshot.isViolating) return 'resolved';
    return 'none';
  });

  function handleMouseEnter(e: MouseEvent) {
    if (hoverTimeoutId) clearTimeout(hoverTimeoutId);
    diagramStore.cancelDismissEdgeTooltip();

    const clientX = e.clientX;
    const clientY = e.clientY;

    hoverTimeoutId = setTimeout(() => {
      diagramStore.showEdgeTooltip({
        edge,
        fromLabel: fromLabel || edge.from,
        toLabel: toLabel || edge.to,
        fromLevel,
        toLevel,
        x: clientX,
        y: clientY,
        isBundled,
        bundleCount,
        violationCount
      });
      hoverTimeoutId = null;
    }, 140);
  }

  function handleMouseLeave() {
    if (hoverTimeoutId) {
      clearTimeout(hoverTimeoutId);
      hoverTimeoutId = null;
    }
    diagramStore.scheduleDismissEdgeTooltip(180);
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<g
  class="group cursor-pointer transition-opacity duration-300"
  opacity={isDimmed ? 0.35 : 1.0}
  onmouseenter={handleMouseEnter}
  onmouseleave={handleMouseLeave}
>
  <!-- Invisible Wide Hit Target (Prevents missing thin lines and stabilizes hover) -->
  <path
    d={pathD}
    fill="none"
    stroke="transparent"
    stroke-width="12"
    class="cursor-pointer"
  />

  <!-- Red Violation Ambient Glow (if violating or new violation) -->
  {#if edge.isViolating || diffStatus === 'new-violation'}
    <path
      bind:this={glowPathEl}
      d={pathD}
      fill="none"
      stroke="#ef4444"
      stroke-width={isHighlighted ? strokeThickness + 3 : strokeThickness + 2}
      stroke-opacity="0.3"
      filter="url(#violation-glow)"
    />
  {:else if diffStatus === 'resolved'}
    <path
      d={pathD}
      fill="none"
      stroke="#10b981"
      stroke-width={isHighlighted ? strokeThickness + 3 : strokeThickness + 2}
      stroke-opacity="0.4"
      filter="url(#violation-glow)"
    />
  {/if}

  <!-- Base Solid Line -->
  <path
    d={pathD}
    fill="none"
    stroke={diffStatus === 'new-violation' ? '#ef4444' : diffStatus === 'resolved' ? '#10b981' : (edge.isViolating ? '#dc2626' : (isHighlighted ? '#60a5fa' : '#475569'))}
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
  {#if isBundled && bundleCount && bundleCount >= 1}
    <g
      transform={`translate(${midX}, ${midY})`}
      class="pointer-events-none select-none"
    >
      <g class={edge.isViolating ? 'edge-badge-pulsing' : ''}>
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
    </g>
  {:else if edge.isViolating}
    <g
      transform={`translate(${midX}, ${midY})`}
      class="pointer-events-none"
    >
      <g class="edge-badge-pulsing">
        <circle r="9" class="fill-rose-950 stroke-rose-500 stroke-1.5 shadow-lg" />
        <text
          y="3.5"
          text-anchor="middle"
          class="fill-rose-300 font-bold text-[9px] font-mono select-none"
        >
          !
        </text>
      </g>
    </g>
  {:else if diffStatus === 'resolved'}
    <g
      transform={`translate(${midX}, ${midY})`}
      class="pointer-events-none select-none"
    >
      <circle r="9" class="fill-emerald-950 stroke-emerald-400 stroke-1.5 shadow-lg" />
      <text
        y="3.5"
        text-anchor="middle"
        class="fill-emerald-300 font-bold text-[9px] font-mono select-none"
      >
        ✓
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

<style>
  @keyframes edge-pulse {
    0%, 100% {
      transform: scale(1);
      filter: drop-shadow(0 0 2px rgba(244, 63, 94, 0.4));
    }
    50% {
      transform: scale(1.1);
      filter: drop-shadow(0 0 6px rgba(244, 63, 94, 0.85));
    }
  }

  .edge-badge-pulsing {
    transform-box: fill-box;
    transform-origin: center;
    animation: edge-pulse 1.2s ease-in-out infinite;
  }
</style>
