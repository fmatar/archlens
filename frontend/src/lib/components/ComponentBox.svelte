<script lang="ts">
  import type { ComponentNode, ClassNode } from '../types/diagram';
  import { diagramStore } from '../state/diagram.svelte';

  interface Props {
    component: ComponentNode;
    x: number;
    y: number;
    width: number;
    height: number;
    isDimmed?: boolean;
    isFocused?: boolean;
    isDragging?: boolean;
    onStartDrag?: (e: PointerEvent | MouseEvent) => void;
  }

  let {
    component,
    x,
    y,
    width,
    height,
    isDimmed = false,
    isFocused = false,
    isDragging = false,
    onStartDrag
  }: Props = $props();

  let rankBadge = $derived(component.level !== null ? `Ring ${component.level}` : 'Unranked');
  let hasHalo = $derived(diagramStore.targetHaloNodeId === component.id);

  function getCrapColor(crapMu: number): string {
    if (crapMu <= 4) return '#10b981'; // Emerald
    if (crapMu <= 15) return '#f59e0b'; // Amber
    return '#ef4444'; // Rose
  }

  function getCoverageColor(cov: number): string {
    if (cov >= 0.8) return '#10b981';
    if (cov >= 0.5) return '#f59e0b';
    return '#ef4444';
  }
</script>

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<g
  transform={`translate(${x}, ${y})`}
  class="group select-none"
  opacity={isDimmed ? 0.18 : 1.0}
  filter={isDragging ? 'url(#node-drag-shadow)' : undefined}
>
  <!-- Spotlight Target Halo (Triggered by Command Palette) -->
  {#if hasHalo}
    <rect
      x="-8"
      y="-8"
      width={width + 16}
      height={height + 16}
      rx="14"
      fill="none"
      stroke="#38bdf8"
      stroke-width="3"
      stroke-dasharray="6 4"
      class="animate-pulse"
      filter="url(#violation-glow)"
    />
  {/if}

  <!-- Active Focus Glow -->
  {#if isFocused}
    <rect
      x="-3"
      y="-3"
      width={width + 6}
      height={height + 6}
      rx="10"
      fill="none"
      stroke="#3b82f6"
      stroke-width="2"
      opacity="0.8"
    />
  {/if}

  <!-- Outer Box Card Surface (Entire card is draggable) -->
  <rect
    width={width}
    height={height}
    rx="8"
    class={`fill-slate-800/90 transition-colors ${
      isDragging
        ? 'stroke-sky-400 stroke-2 fill-slate-800 cursor-grabbing'
        : isFocused
          ? 'stroke-blue-400 stroke-2 fill-slate-800 cursor-grab'
          : 'stroke-slate-600 group-hover:stroke-blue-400 stroke-[1.5] cursor-grab'
    }`}
    onpointerdown={(e) => {
      if (e.button === 0) onStartDrag?.(e);
    }}
  />

  <!-- Component Title Banner -->
  <path
    d={`M 0 8 Q 0 0 8 0 L ${width - 8} 0 Q ${width} 0 ${width} 8 L ${width} 28 L 0 28 Z`}
    class={`transition-colors ${isDragging ? 'fill-slate-900 cursor-grabbing' : 'fill-slate-900/90 group-hover:fill-slate-850 cursor-grab'}`}
    onpointerdown={(e) => {
      if (e.button === 0) onStartDrag?.(e);
    }}
  />

  <text
    x="12"
    y="19"
    class={`fill-slate-100 font-semibold text-xs tracking-wide select-none ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}`}
    onpointerdown={(e) => {
      if (e.button === 0) onStartDrag?.(e);
    }}
  >
    {component.label}
  </text>

  <!-- Clean Architecture Ring Badge -->
  <rect
    x={width - 64}
    y="6"
    width="52"
    height="16"
    rx="4"
    class="fill-slate-700/90 cursor-grab pointer-events-none"
  />
  <text x={width - 38} y="18" text-anchor="middle" class="fill-slate-300 font-mono text-[10px] font-medium pointer-events-none select-none">
    {rankBadge}
  </text>

  <!-- Contained Classes / Modules with Health Heatmap Badges -->
  {#if diagramStore.declutterMode !== 'CLASSES'}
    <g transform="translate(10, 36)">
      {#each component.classes.slice(0, 6) as cls, i}
        {@const crapCol = getCrapColor(cls.crap.mu)}
        {@const covCol = getCoverageColor(cls.coverage)}
        <!-- svelte-ignore a11y_click_events_have_key_events -->
        <g
          transform={`translate(0, ${i * 24})`}
          class="cursor-pointer group/row"
          onpointerdown={(e) => {
            // Stop drag so user can click to inspect class
            e.stopPropagation();
          }}
          onclick={(e) => {
            e.stopPropagation();
            diagramStore.selectedClass = cls;
          }}
        >
          <rect
            width={width - 20}
            height="20"
            rx="4"
            class="fill-slate-900/60 hover:fill-blue-900/40 stroke-slate-750 stroke-[0.5] transition-colors"
          />
          <text x="8" y="14" class="fill-slate-200 group-hover/row:fill-blue-300 text-[11px] font-mono transition-colors">
            {cls.name}
          </text>

          <!-- Dynamic CRAP Badge Indicator -->
          <rect
            x={width - 64}
            y="4"
            width="20"
            height="12"
            rx="2"
            fill={crapCol}
            opacity="0.25"
          />
          <text
            x={width - 54}
            y="13"
            text-anchor="middle"
            fill={crapCol}
            class="text-[8px] font-mono font-bold select-none"
          >
            {Math.round(cls.crap.mu)}
          </text>

          <!-- Coverage Dot -->
          <circle cx={width - 32} cy="10" r="3.5" fill={covCol} />

          <!-- Mutation Indicator Dot -->
          <circle cx={width - 22} cy="10" r="3.5" fill="#10b981" />
        </g>
      {/each}
      {#if component.classes.length > 6}
        <text x="10" y={6 * 24 + 14} class="fill-slate-500 text-[10px] italic pointer-events-none select-none">
          + {component.classes.length - 6} more classes...
        </text>
      {/if}
    </g>
  {/if}
</g>
