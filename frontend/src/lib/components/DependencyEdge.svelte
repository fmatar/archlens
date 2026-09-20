<script lang="ts">
  import type { DependencyEdge } from '../types/diagram';

  interface Props {
    edge: DependencyEdge;
    x1: number;
    y1: number;
    x2: number;
    y2: number;
  }

  let { edge, x1, y1, x2, y2 }: Props = $props();

  // Compute smooth cubic Bezier control points
  let midY = $derived((y1 + y2) / 2);
  let pathD = $derived(`M ${x1} ${y1} C ${x1} ${midY}, ${x2} ${midY}, ${x2} ${y2}`);
  let strokeColor = $derived(edge.isViolating ? '#ef4444' : '#64748b');
  let strokeWidth = $derived(edge.isViolating ? 2.5 : 1.5);
  let markerId = $derived(edge.isViolating ? 'arrow-violating' : 'arrow-normal');
</script>

<g class="transition-all duration-300">
  <path
    d={pathD}
    fill="none"
    stroke={strokeColor}
    stroke-width={strokeWidth}
    stroke-dasharray={edge.kind === 'DEPENDENCY' ? '4 2' : 'none'}
    marker-end={`url(#${markerId})`}
    class="hover:stroke-blue-400 hover:stroke-[3px] cursor-pointer"
  />
  {#if edge.label}
    <text
      x={(x1 + x2) / 2}
      y={midY - 4}
      text-anchor="middle"
      class="fill-slate-400 text-[10px]"
    >
      {edge.label}
    </text>
  {/if}
</g>
