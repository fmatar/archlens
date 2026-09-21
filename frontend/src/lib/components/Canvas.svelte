<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import ComponentBox from './ComponentBox.svelte';
  import DependencyEdge from './DependencyEdge.svelte';
  import ProposalDiffBanner from './ProposalDiffBanner.svelte';
  import EdgeTooltip from './EdgeTooltip.svelte';
  import AgentDrawer from './AgentDrawer.svelte';
  import { ZoomIn, ZoomOut, Maximize2, Radio, Search, RotateCcw } from '@lucide/svelte';
  import type { ComponentNode, ClassNode, DependencyEdge as EdgeType } from '../types/diagram';

  let svgElement: SVGSVGElement | null = $state(null);

  // Canvas Panning State
  let isPanning = $state(false);
  let panStartX = 0;
  let panStartY = 0;
  let rafPanId: number | null = null;
  let targetPanX = 0;
  let targetPanY = 0;

  // Node Dragging State (Buttery smooth with RAF & global pointer tracking)
  let draggedNodeId = $state<string | null>(null);
  let dragNodeStartX = 0;
  let dragNodeStartY = 0;
  let dragPointerStartX = 0;
  let dragPointerStartY = 0;
  let dragHasMoved = false;
  let rafDragId: number | null = null;
  let targetDragX = 0;
  let targetDragY = 0;

  let graph = $derived(diagramStore.graph);
  let components = $derived<ComponentNode[]>(graph?.components || []);
  let edges = $derived(graph?.edges || []);

  // Compute 2D Sugiyama-style rank coordinates
  const BOX_WIDTH = 260;
  const BOX_HEIGHT = 180;
  const GAP_X = 60;
  const GAP_Y = 120;

  // Group levels
  let levelMap = $derived.by(() => {
    const map = new Map<number, ComponentNode[]>();
    components.forEach((c: ComponentNode) => {
      const lvl = c.level !== null ? c.level : 3;
      if (!map.has(lvl)) map.set(lvl, []);
      map.get(lvl)!.push(c);
    });
    return map;
  });

  let sortedLevels = $derived(Array.from(levelMap.keys()).sort((a, b) => a - b));

  // Directly derive layout coordinates from components and persistent offsets
  let positionedComponents = $derived.by(() => {
    const coords = new Map<string, { x: number; y: number; width: number; height: number; comp: ComponentNode }>();

    sortedLevels.forEach((level, rowIdx) => {
      const row = levelMap.get(level)!;
      row.forEach((comp: ComponentNode, colIdx: number) => {
        const baseOffset = diagramStore.componentOffsets[comp.id] || { x: 0, y: 0 };
        const x = 100 + colIdx * (BOX_WIDTH + GAP_X) + baseOffset.x;
        const y = 80 + rowIdx * (BOX_HEIGHT + GAP_Y) + baseOffset.y;
        coords.set(comp.id, { x, y, width: BOX_WIDTH, height: BOX_HEIGHT, comp });
      });
    });

    return coords;
  });

  // Radar Scanline Telemetry State
  let radarState = $state({ y: -120, opacity: 0 });

  // Agent Radar Telemetry Loop
  $effect(() => {
    const isRegenerating = diagramStore.isRegenerating;
    const ctx = gsap.context(() => {
      if (isRegenerating) {
        gsap.to(radarState, { opacity: 0.85, duration: 0.3 });
        gsap.fromTo(
          radarState,
          { y: -100 },
          {
            y: 1800,
            duration: 2.2,
            repeat: -1,
            ease: 'none'
          }
        );
      } else {
        gsap.to(radarState, {
          opacity: 0,
          duration: 0.6,
          onComplete: () => {
            radarState.y = -120;
          }
        });
      }
    });

    return () => ctx.revert();
  });

  function findNodeForClass(fullClassName: string) {
    const simpleName = fullClassName.split('.').pop() || fullClassName;
    for (const item of positionedComponents.values()) {
      if (item.comp.id === fullClassName || item.comp.id === simpleName) return item;
      if (item.comp.classes && item.comp.classes.some((cl: ClassNode) => cl.id === fullClassName || cl.name === simpleName)) {
        return item;
      }
      const pkgSeg = fullClassName.split('.').slice(-2, -1)[0];
      if (item.comp.packages && item.comp.packages.includes(pkgSeg)) return item;
    }
    return null;
  }

  // Focus and Dimming calculations
  let connectedNodeIds = $derived.by(() => {
    const focused = diagramStore.focusedNodeId;
    if (!focused) return null;
    const set = new Set<string>([focused]);
    edges.forEach((edge: EdgeType) => {
      const from = findNodeForClass(edge.from);
      const to = findNodeForClass(edge.to);
      if (from?.comp.id === focused && to) set.add(to.comp.id);
      if (to?.comp.id === focused && from) set.add(from.comp.id);
    });
    return set;
  });

  function getTierTitle(level: number): string {
    switch (level) {
      case 0: return 'Ring 0: Entities & Domain Core';
      case 1: return 'Ring 1: Use Cases & Application Services';
      case 2: return 'Ring 2: Interface Adapters & Controllers';
      case 3: return 'Ring 3: Frameworks & External Drivers';
      default: return `Ring ${level}: Architectural Tier`;
    }
  }

  // --- Smooth Node Dragging with Window Listeners & RAF ---
  function startNodeDrag(id: string, e: PointerEvent | MouseEvent) {
    e.stopPropagation();
    if (e.button !== 0) return;

    draggedNodeId = id;
    dragHasMoved = false;

    const currentOffset = diagramStore.componentOffsets[id] || { x: 0, y: 0 };
    dragNodeStartX = currentOffset.x;
    dragNodeStartY = currentOffset.y;
    dragPointerStartX = e.clientX;
    dragPointerStartY = e.clientY;
    targetDragX = dragNodeStartX;
    targetDragY = dragNodeStartY;

    document.body.style.cursor = 'grabbing';
    document.body.style.userSelect = 'none';

    window.addEventListener('pointermove', onNodeDragMove, { passive: false });
    window.addEventListener('pointerup', onNodeDragUp);
    window.addEventListener('pointercancel', onNodeDragUp);
  }

  function onNodeDragMove(e: PointerEvent) {
    if (!draggedNodeId) return;
    e.preventDefault();

    const dx = (e.clientX - dragPointerStartX) / diagramStore.zoom;
    const dy = (e.clientY - dragPointerStartY) / diagramStore.zoom;

    if (Math.hypot(dx, dy) > 3) {
      dragHasMoved = true;
    }

    targetDragX = dragNodeStartX + dx;
    targetDragY = dragNodeStartY + dy;

    if (!rafDragId) {
      rafDragId = requestAnimationFrame(() => {
        if (draggedNodeId) {
          diagramStore.setComponentOffset(draggedNodeId, {
            x: targetDragX,
            y: targetDragY
          });
        }
        rafDragId = null;
      });
    }
  }

  function onNodeDragUp() {
    window.removeEventListener('pointermove', onNodeDragMove);
    window.removeEventListener('pointerup', onNodeDragUp);
    window.removeEventListener('pointercancel', onNodeDragUp);

    document.body.style.cursor = '';
    document.body.style.userSelect = '';

    if (rafDragId) {
      cancelAnimationFrame(rafDragId);
      rafDragId = null;
    }

    if (draggedNodeId) {
      if (dragHasMoved) {
        diagramStore.setComponentOffset(draggedNodeId, {
          x: targetDragX,
          y: targetDragY
        });
      } else {
        diagramStore.setFocusedNode(
          diagramStore.focusedNodeId === draggedNodeId ? null : draggedNodeId
        );
      }
      draggedNodeId = null;
    }
  }

  // --- Smooth Canvas Panning with Window Listeners & RAF ---
  function handleMouseDown(e: MouseEvent) {
    if (e.button !== 0) return;
    const target = e.target as HTMLElement;
    if (target.tagName === 'svg' || target.tagName === 'DIV' || target.classList?.contains('tier-backdrop')) {
      diagramStore.setFocusedNode(null);
      diagramStore.activeEdgeTooltip = null;

      isPanning = true;
      panStartX = e.clientX - diagramStore.panX;
      panStartY = e.clientY - diagramStore.panY;
      targetPanX = diagramStore.panX;
      targetPanY = diagramStore.panY;

      document.body.style.cursor = 'grabbing';
      document.body.style.userSelect = 'none';

      window.addEventListener('pointermove', onCanvasPanMove, { passive: false });
      window.addEventListener('pointerup', onCanvasPanUp);
      window.addEventListener('pointercancel', onCanvasPanUp);
    }
  }

  function onCanvasPanMove(e: PointerEvent) {
    if (!isPanning) return;
    e.preventDefault();

    targetPanX = e.clientX - panStartX;
    targetPanY = e.clientY - panStartY;

    if (!rafPanId) {
      rafPanId = requestAnimationFrame(() => {
        if (isPanning) {
          diagramStore.panX = targetPanX;
          diagramStore.panY = targetPanY;
        }
        rafPanId = null;
      });
    }
  }

  function onCanvasPanUp() {
    window.removeEventListener('pointermove', onCanvasPanMove);
    window.removeEventListener('pointerup', onCanvasPanUp);
    window.removeEventListener('pointercancel', onCanvasPanUp);

    document.body.style.cursor = '';
    document.body.style.userSelect = '';

    if (rafPanId) {
      cancelAnimationFrame(rafPanId);
      rafPanId = null;
    }
    isPanning = false;
  }

  function handleWheel(e: WheelEvent) {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
    diagramStore.zoom = Math.max(0.2, Math.min(3.0, diagramStore.zoom * zoomFactor));
  }

  onDestroy(() => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('pointermove', onNodeDragMove);
      window.removeEventListener('pointerup', onNodeDragUp);
      window.removeEventListener('pointercancel', onNodeDragUp);
      window.removeEventListener('pointermove', onCanvasPanMove);
      window.removeEventListener('pointerup', onCanvasPanUp);
      window.removeEventListener('pointercancel', onCanvasPanUp);
    }
    if (rafDragId) cancelAnimationFrame(rafDragId);
    if (rafPanId) cancelAnimationFrame(rafPanId);
  });
</script>

<!-- Canvas Container -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  class="relative flex-1 h-full overflow-hidden bg-slate-950 cursor-grab active:cursor-grabbing select-none"
  onmousedown={handleMouseDown}
  onwheel={handleWheel}
>
  <!-- Floating Proposal Diff Banner -->
  <ProposalDiffBanner />

  <!-- Edge Explanation Tooltip -->
  <EdgeTooltip />

  <!-- Active Agent Radar Sweep Badge -->
  {#if diagramStore.isRegenerating}
    <div class="absolute top-4 right-6 z-20 px-3 py-1.5 rounded-md bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 font-mono text-xs shadow-xl backdrop-blur flex items-center gap-2">
      <Radio size={14} class="animate-spin text-emerald-400" />
      Autonomous Agent Analyzing AST & Clean Architecture Rules...
    </div>
  {/if}

  <!-- View Controls -->
  <div class="absolute bottom-12 left-6 z-20 flex items-center gap-1 bg-slate-900/90 border border-slate-800 rounded-lg p-1 shadow-xl backdrop-blur">
    <button
      onclick={() => diagramStore.isCommandPaletteOpen = true}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white transition-colors flex items-center gap-1.5 px-2"
      title="Search (Cmd+K)"
    >
      <Search size={14} />
      <span class="font-mono text-[10px] text-slate-400">Cmd+K</span>
    </button>
    <div class="h-3 w-px bg-slate-800"></div>
    <button
      onclick={() => diagramStore.zoom = Math.min(3.0, diagramStore.zoom * 1.1)}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white transition-colors"
      title="Zoom In (+)"
      aria-label="Zoom in"
    >
      <ZoomIn size={16} />
    </button>
    <button
      onclick={() => diagramStore.zoom = Math.max(0.2, diagramStore.zoom * 0.9)}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white transition-colors"
      title="Zoom Out (-)"
      aria-label="Zoom out"
    >
      <ZoomOut size={16} />
    </button>
    <button
      onclick={() => diagramStore.resetZoom()}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white transition-colors"
      title="Reset View (0)"
      aria-label="Reset zoom"
    >
      <Maximize2 size={16} />
    </button>
    <button
      onclick={() => diagramStore.resetLayout()}
      class="p-1.5 rounded hover:bg-slate-800 text-slate-300 hover:text-white transition-colors"
      title="Reset Node Positions to Clean Architecture Rings"
      aria-label="Reset layout"
    >
      <RotateCcw size={15} />
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

      <!-- Neon Violation Filter -->
      <filter id="violation-glow" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="3" result="blur" />
        <feComposite in="SourceGraphic" in2="blur" operator="over" />
      </filter>

      <!-- Elevated Node Drag Shadow Filter -->
      <filter id="node-drag-shadow" x="-30%" y="-30%" width="160%" height="160%">
        <feDropShadow dx="0" dy="16" stdDeviation="16" flood-color="#000000" flood-opacity="0.65" />
      </filter>

      <!-- Agent Radar Scanline Gradient -->
      <linearGradient id="radar-gradient" x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stop-color="#10b981" stop-opacity="0" />
        <stop offset="60%" stop-color="#10b981" stop-opacity="0.12" />
        <stop offset="100%" stop-color="#34d399" stop-opacity="0.5" />
      </linearGradient>
    </defs>

    <!-- Transformed Graph Group -->
    <g transform={`translate(${diagramStore.panX}, ${diagramStore.panY}) scale(${diagramStore.zoom})`}>
      <!-- Concentric Architecture Tier Lanes Backdrop -->
      {#each sortedLevels as level, rowIdx}
        {@const y = 80 + rowIdx * (BOX_HEIGHT + GAP_Y) - 35}
        {@const height = BOX_HEIGHT + 70}
        <g class="pointer-events-none select-none tier-backdrop">
          <rect
            x="-400"
            y={y}
            width="3200"
            height={height}
            rx="12"
            class="fill-slate-900/35 stroke-slate-800/40"
            stroke-width="1"
            stroke-dasharray="8 6"
          />
          <text
            x="-360"
            y={y + 24}
            class="fill-slate-500 font-mono text-[11px] font-semibold tracking-wider uppercase"
          >
            {getTierTitle(level)}
          </text>
        </g>
      {/each}

      <!-- Agent Radar Scanline Overlay -->
      {#if radarState.opacity > 0.01}
        <g opacity={radarState.opacity} class="pointer-events-none">
          <rect
            x="-500"
            y={radarState.y - 120}
            width="3500"
            height="120"
            fill="url(#radar-gradient)"
          />
          <line
            x1="-500"
            y1={radarState.y}
            x2="3000"
            y2={radarState.y}
            stroke="#34d399"
            stroke-width="2"
            stroke-dasharray="8 4"
            opacity="0.9"
          />
        </g>
      {/if}

      <!-- Animated Dependency Edges -->
      {#if diagramStore.declutterMode !== 'REMOVE_ARROWS'}
        {#each edges as edge, i (edge.from + '->' + edge.to + ':' + edge.kind + ':' + i)}
          {@const fromNode = findNodeForClass(edge.from)}
          {@const toNode = findNodeForClass(edge.to)}
          {#if fromNode && toNode && fromNode !== toNode}
            {@const goingDown = fromNode.y < toNode.y}
            {@const isHighlighted = diagramStore.focusedNodeId
              ? (fromNode.comp.id === diagramStore.focusedNodeId || toNode.comp.id === diagramStore.focusedNodeId)
              : false}
            {@const isDimmed = connectedNodeIds ? !isHighlighted : false}
            <DependencyEdge
              {edge}
              x1={fromNode.x + fromNode.width / 2 + (edge.isViolating ? -15 : 15)}
              y1={goingDown ? fromNode.y + fromNode.height : fromNode.y}
              x2={toNode.x + toNode.width / 2 + (edge.isViolating ? -15 : 15)}
              y2={goingDown ? toNode.y : toNode.y + toNode.height}
              fromLabel={fromNode.comp.label}
              toLabel={toNode.comp.label}
              fromLevel={fromNode.comp.level}
              toLevel={toNode.comp.level}
              {isDimmed}
              {isHighlighted}
            />
          {/if}
        {/each}
      {/if}

      <!-- Component Layer Boxes (Direct Reactive Positions & Focus Dimming) -->
      {#each Array.from(positionedComponents.values()) as item, i (item.comp.id + ':' + i)}
        {@const isFocused = diagramStore.focusedNodeId === item.comp.id}
        {@const isDimmed = connectedNodeIds ? !connectedNodeIds.has(item.comp.id) : false}
        {@const isDragging = draggedNodeId === item.comp.id}
        <ComponentBox
          component={item.comp}
          x={item.x}
          y={item.y}
          width={item.width}
          height={item.height}
          {isDimmed}
          {isFocused}
          {isDragging}
          onStartDrag={(e) => startNodeDrag(item.comp.id, e)}
        />
      {/each}
    </g>
  </svg>

  <!-- Agent Mailbox Telemetry Drawer -->
  <AgentDrawer />
</div>
