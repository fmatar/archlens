<script lang="ts">
  import type { ComponentNode } from '../types/diagram';
  import { diagramStore } from '../state/diagram.svelte';

  interface Props {
    component: ComponentNode;
    x: number;
    y: number;
    width: number;
    height: number;
    onStartDrag?: (e: MouseEvent) => void;
  }

  let { component, x, y, width, height, onStartDrag }: Props = $props();

  let rankBadge = $derived(component.level !== null ? `Level ${component.level}` : 'Unranked');
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<g transform={`translate(${x}, ${y})`} class="group cursor-pointer">
  <!-- Outer Box -->
  <rect
    width={width}
    height={height}
    rx="8"
    class="fill-slate-800/80 stroke-slate-600 group-hover:stroke-blue-400 transition-colors"
    stroke-width="1.5"
  />

  <!-- Component Title Banner (Draggable handle) -->
  <path
    d={`M 0 8 Q 0 0 8 0 L ${width - 8} 0 Q ${width} 0 ${width} 8 L ${width} 28 L 0 28 Z`}
    class="fill-slate-900/90 cursor-move hover:fill-slate-850"
    onmousedown={(e) => onStartDrag && onStartDrag(e)}
  />

  <text
    x="12"
    y="19"
    class="fill-slate-100 font-semibold text-xs tracking-wide cursor-move select-none"
    onmousedown={(e) => onStartDrag && onStartDrag(e)}
  >
    {component.label}
  </text>

  <!-- Clean Architecture Level Badge -->
  <rect
    x={width - 64}
    y="6"
    width="52"
    height="16"
    rx="4"
    class="fill-slate-700/90"
  />
  <text x={width - 38} y="18" text-anchor="middle" class="fill-slate-300 font-mono text-[10px]">
    {rankBadge}
  </text>

  <!-- Contained Classes / Modules -->
  {#if diagramStore.declutterMode !== 'CLASSES'}
    <g transform="translate(10, 36)">
      {#each component.classes.slice(0, 6) as cls, i}
        <!-- svelte-ignore a11y_click_events_have_key_events -->
        <g
          transform={`translate(0, ${i * 24})`}
          class="cursor-pointer"
          onclick={(e) => {
            e.stopPropagation();
            diagramStore.selectedClass = cls;
          }}
        >
          <rect
            width={width - 20}
            height="20"
            rx="4"
            class="fill-slate-900/50 hover:fill-blue-900/40 stroke-slate-750 stroke-[0.5]"
          />
          <text x="8" y="14" class="fill-slate-200 text-[11px] font-mono">
            {cls.name}
          </text>
          <!-- C / M badges -->
          <circle cx={width - 40} cy="10" r="4" class="fill-emerald-500" />
          <text x={width - 40} y="13" text-anchor="middle" class="fill-black text-[7px] font-bold">C</text>
          <circle cx={width - 28} cy="10" r="4" class="fill-emerald-500" />
          <text x={width - 28} y="13" text-anchor="middle" class="fill-black text-[7px] font-bold">M</text>
        </g>
      {/each}
      {#if component.classes.length > 6}
        <text x="10" y={6 * 24 + 14} class="fill-slate-500 text-[10px] italic">
          + {component.classes.length - 6} more classes...
        </text>
      {/if}
    </g>
  {/if}
</g>
