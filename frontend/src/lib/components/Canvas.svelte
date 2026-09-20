<script lang="ts">
  import { onMount } from 'svelte';
  import { diagramStore } from '../state/diagram.svelte';
  import ComponentBox from './ComponentBox.svelte';
  import DependencyEdge from './DependencyEdge.svelte';
  import { ZoomIn, ZoomOut, Maximize2 } from 'lucide-svelte';

  let svgElement: SVGSVGElement | null = $state(null);
  let isPanning = $state(false);
  let startX = $state(0);
  let startY = $state(0);

  // Dragging individual boxes
  let draggedNodeId = $state<string | null>(null);
  let dragNodeStartX = $state(0);
  let dragNodeStartY = $state(0);
  let dragMouseStartX = $state(0);
  let dragMouseStartY = $state(0);

  // Position overrides for dragged components
  let componentOffsets = $state<Record<string, { x: number; y: number }>>({});

  let graph = $derived(diagramStore.graph);
  let components = $derived(graph?.components || []);
  let edges = $derived(graph?.edges || []);

  // Compute 2D Sugiyama-style rank coordinates
  const BOX_WIDTH = 260;
  const BOX_HEIGHT = 180;
  const GAP_X = 60;
  const GAP_Y = 120;

  let positionedComponents = $derived.by(() => {
    // Group components by level
    const levelMap = new Map<number, typeof components>();
    components.forEach(c => {
      const lvl = c.level !== null ? c.level : 3;
      if (!levelMap.has(lvl)) levelMap.set(lvl, []);
      levelMap.get(lvl)!.push(c);
    });

    const sortedLevels = Array.from(levelMap.keys()).sort((a, b) => a - b);
    const coords = new Map<string, { x: number; y: number; width: number; height: number; comp: any }>();

    sortedLevels.forEach((level, rowIdx) => {
      const row = levelMap.get(level)!;
      row.forEach((comp, colIdx) => {
        const baseOffset = componentOffsets[comp.id] || { x: 0, y: 0 };
        const x = 100 + colIdx * (BOX_WIDTH + GAP_X) + baseOffset.x;
        const y = 80 + rowIdx * (BOX_HEIGHT + GAP_Y) + baseOffset.y;
        coords.set(comp.id, { x, y, width: BOX_WIDTH, height: BOX_HEIGHT, comp });
      });
    });

    return coords;
  });

  function startNodeDrag(id: string, e: MouseEvent) {
    e.stopPropagation();
    draggedNodeId = id;
    const currentOffset = componentOffsets[id] || { x: 0, y: 0 };
    dragNodeStartX = currentOffset.x;
    dragNodeStartY = currentOffset.y;
    dragMouseStartX = e.clientX;
    dragMouseStartY = e.clientY;
  }

  function handleMouseDown(e: MouseEvent) {
    if (e.button === 0) {
      isPanning = true;
      startX = e.clientX - diagramStore.panX;
      startY = e.clientY - diagramStore.panY;
    }
  }

  function handleMouseMove(e: MouseEvent) {
    if (draggedNodeId) {
      const dx = (e.clientX - dragMouseStartX) / diagramStore.zoom;
      const dy = (e.clientY - dragMouseStartY) / diagramStore.zoom;
      componentOffsets[draggedNodeId] = {
        x: dragNodeStartX + dx,
        y: dragNodeStartY + dy
      };
      return;
    }
    if (isPanning) {
      diagramStore.panX = e.clientX - startX;
      diagramStore.panY = e.clientY - startY;
    }
  }

  function handleMouseUp() {
    isPanning = false;
    draggedNodeId = null;
  }

  function handleWheel(e: WheelEvent) {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
    diagramStore.zoom = Math.max(0.2, Math.min(3.0, diagramStore.zoom * zoomFactor));
  }
</script>

<!-- Canvas Container -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  class="relative flex-1 h-full overflow-hidden bg-slate-950 cursor-grab active:cursor-grabbing select-none"
  onmousedown={handleMouseDown}
  onmousemove={handleMouseMove}
  onmouseup={handleMouseUp}
  onwheel={handleWheel}
>
  <!-- Floating Proposal Banner -->
  {#if graph?.isProposal}
    <div class="absolute top-4 left-6 z-20 px-3 py-1.5 rounded-md bg-amber-500/20 border border-amber-500/40 text-amber-300 font-mono text-xs shadow-lg backdrop-blur">
      PROPOSAL — not instantiated in code
    </div>
  {/if}

  <!-- View Controls -->
  <div class="absolute bottom-6 left-6 z-20 flex items-center gap-1 bg-slate-900/90 border border-slate-800 rounded-lg p-1 shadow-xl backdrop-blur">
    <button
      onclick={() => diagramStore.zoom = Math.min(3.0, diagramStore.zoom * 1.1)}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white"
    >
      <ZoomIn size={16} />
    </button>
    <button
      onclick={() => diagramStore.zoom = Math.max(0.2, diagramStore.zoom * 0.9)}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white"
    >
      <ZoomOut size={16} />
    </button>
    <button
      onclick={() => diagramStore.resetZoom()}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white"
    >
      <Maximize2 size={16} />
    </button>
    <div class="px-2 font-mono text-[10px] text-slate-400">
      {Math.round(diagramStore.zoom * 100)}%
    </div>
  </div>

  <!-- SVG Graph Canvas -->
  <svg
    bind:this={svgElement}
    class="w-full h-full"
  >
    <defs>
      <!-- Normal arrow marker -->
      <marker id="arrow-normal" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
        <path d="M 0 1 L 10 5 L 0 9 z" fill="#64748b" />
      </marker>
      <!-- Violating arrow marker (Red) -->
      <marker id="arrow-violating" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
        <path d="M 0 1 L 10 5 L 0 9 z" fill="#ef4444" />
      </marker>
    </defs>

    <!-- Transformed Graph Group -->
    <g transform={`translate(${diagramStore.panX}, ${diagramStore.panY}) scale(${diagramStore.zoom})`}>
      <!-- Edges -->
      {#if diagramStore.declutterMode !== 'REMOVE_ARROWS'}
        {#each edges as edge}
          {@const findNodeForClass = (fullClassName: string) => {
            const simpleName = fullClassName.split('.').pop() || fullClassName;
            for (const item of positionedComponents.values()) {
              if (item.comp.id === fullClassName || item.comp.id === simpleName) return item;
              if (item.comp.classes && item.comp.classes.some((cl: any) => cl.id === fullClassName || cl.name === simpleName)) {
                return item;
              }
              const pkgSeg = fullClassName.split('.').slice(-2, -1)[0];
              if (item.comp.packages && item.comp.packages.includes(pkgSeg)) return item;
            }
            return null;
          }}
          {@const fromNode = findNodeForClass(edge.from)}
          {@const toNode = findNodeForClass(edge.to)}
          {#if fromNode && toNode && fromNode !== toNode}
            {@const goingDown = fromNode.y < toNode.y}
            <DependencyEdge
              {edge}
              x1={fromNode.x + fromNode.width / 2 + (edge.isViolating ? -15 : 15)}
              y1={goingDown ? fromNode.y + fromNode.height : fromNode.y}
              x2={toNode.x + toNode.width / 2 + (edge.isViolating ? -15 : 15)}
              y2={goingDown ? toNode.y : toNode.y + toNode.height}
            />
          {/if}
        {/each}
      {/if}

      <!-- Component Layer Boxes -->
      {#each Array.from(positionedComponents.values()) as item}
        <ComponentBox
          component={item.comp}
          x={item.x}
          y={item.y}
          width={item.width}
          height={item.height}
          onStartDrag={(e) => startNodeDrag(item.comp.id, e)}
        />
      {/each}
    </g>
  </svg>
</div>
