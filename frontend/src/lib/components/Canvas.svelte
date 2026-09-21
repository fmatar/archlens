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

  // Container Dimensions for Viewport Frustum Culling
  let containerWidth = $state(1920);
  let containerHeight = $state(1080);

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

  // Viewport Frustum Culling (Spatial Virtualization for 120 FPS on massive repos)
  let viewportBounds = $derived.by(() => {
    const margin = 250; // Pre-render buffer margin for seamless panning
    const z = diagramStore.zoom || 1;
    const minX = -diagramStore.panX / z - margin;
    const minY = -diagramStore.panY / z - margin;
    const maxX = (-diagramStore.panX + containerWidth) / z + margin;
    const maxY = (-diagramStore.panY + containerHeight) / z + margin;
    return { minX, minY, maxX, maxY };
  });

  function isBoxInViewport(x: number, y: number, width: number, height: number): boolean {
    const vb = viewportBounds;
    return !(x + width < vb.minX || x > vb.maxX || y + height < vb.minY || y > vb.maxY);
  }

  interface RenderableEdge {
    key: string;
    edge: EdgeType;
    fromNode: { x: number; y: number; width: number; height: number; comp: ComponentNode };
    toNode: { x: number; y: number; width: number; height: number; comp: ComponentNode };
    isBundled: boolean;
    bundleCount: number;
    violationCount: number;
  }

  let visibleComponents = $derived.by(() => {
    const list: Array<{ x: number; y: number; width: number; height: number; comp: ComponentNode }> = [];
    const isNeighborhoodIsolation = diagramStore.hasDeclutterFilter('ISOLATE_NEIGHBORHOOD');
    const focused = diagramStore.focusedNodeId;

    for (const item of positionedComponents.values()) {
      if (isNeighborhoodIsolation && focused && connectedNodeIds && !connectedNodeIds.has(item.comp.id)) {
        continue;
      }

      if (
        draggedNodeId === item.comp.id ||
        diagramStore.focusedNodeId === item.comp.id ||
        diagramStore.targetHaloNodeId === item.comp.id ||
        isBoxInViewport(item.x, item.y, item.width, item.height)
      ) {
        list.push(item);
      }
    }
    return list;
  });

  let visibleEdges = $derived.by(() => {
    if (diagramStore.declutterMode === 'REMOVE_ARROWS' || diagramStore.hasDeclutterFilter('HIDE_ALL_EDGES')) return [];
    const isXRayMode = diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES');

    const list: EdgeType[] = [];
    for (const edge of edges) {
      if (isXRayMode && !edge.isViolating) continue;

      const fromNode = findNodeForClass(edge.from);
      const toNode = findNodeForClass(edge.to);
      if (!fromNode || !toNode || fromNode === toNode) continue;

      const isFromVisible = isBoxInViewport(fromNode.x, fromNode.y, fromNode.width, fromNode.height);
      const isToVisible = isBoxInViewport(toNode.x, toNode.y, toNode.width, toNode.height);
      const isFocusedEdge = diagramStore.focusedNodeId
        ? fromNode.comp.id === diagramStore.focusedNodeId || toNode.comp.id === diagramStore.focusedNodeId
        : false;

      if (isFromVisible || isToVisible || isFocusedEdge) {
        list.push(edge);
      }
    }
    return list;
  });

  let renderedGraphEdges = $derived.by(() => {
    if (!diagramStore.isEdgeBundlingEnabled) {
      return visibleEdges.map((edge, idx) => {
        const fromNode = findNodeForClass(edge.from)!;
        const toNode = findNodeForClass(edge.to)!;
        return {
          key: `${edge.from}->${edge.to}:${edge.kind}:${idx}`,
          edge,
          fromNode,
          toNode,
          isBundled: false,
          bundleCount: 1,
          violationCount: edge.isViolating ? 1 : 0
        };
      });
    }

    const focusedId = diagramStore.focusedNodeId;
    const bundles = new Map<string, {
      fromNode: { x: number; y: number; width: number; height: number; comp: ComponentNode };
      toNode: { x: number; y: number; width: number; height: number; comp: ComponentNode };
      edges: EdgeType[];
      violationCount: number;
    }>();
    const unbundled: RenderableEdge[] = [];

    for (const edge of visibleEdges) {
      const fromNode = findNodeForClass(edge.from);
      const toNode = findNodeForClass(edge.to);
      if (!fromNode || !toNode) continue;

      const touchesFocused = focusedId && (fromNode.comp.id === focusedId || toNode.comp.id === focusedId);
      if (touchesFocused) {
        unbundled.push({
          key: `unbundled:${edge.from}->${edge.to}:${edge.kind}`,
          edge,
          fromNode,
          toNode,
          isBundled: false,
          bundleCount: 1,
          violationCount: edge.isViolating ? 1 : 0
        });
      } else {
        const bundleKey = `${fromNode.comp.id}->${toNode.comp.id}`;
        if (!bundles.has(bundleKey)) {
          bundles.set(bundleKey, {
            fromNode,
            toNode,
            edges: [],
            violationCount: 0
          });
        }
        const b = bundles.get(bundleKey)!;
        b.edges.push(edge);
        if (edge.isViolating) b.violationCount++;
      }
    }

    const result: RenderableEdge[] = [...unbundled];
    for (const [key, b] of bundles.entries()) {
      const representativeEdge: EdgeType = {
        from: b.fromNode.comp.id,
        to: b.toNode.comp.id,
        kind: 'DEPENDENCY',
        isViolating: b.violationCount > 0,
        label: b.violationCount > 0 ? `! ${b.violationCount}/${b.edges.length}` : `${b.edges.length}`
      };
      result.push({
        key: `bundle:${key}`,
        edge: representativeEdge,
        fromNode: b.fromNode,
        toNode: b.toNode,
        isBundled: true,
        bundleCount: b.edges.length,
        violationCount: b.violationCount
      });
    }

    return result;
  });

  // O(1) Class-to-Component Spatial Index for Massive Repositories
  let classToNodeIdMap = $derived.by(() => {
    const map = new Map<string, string>();
    for (const comp of components) {
      map.set(comp.id, comp.id);
      map.set(comp.label, comp.id);
      const simpleComp = comp.label.split('.').pop();
      if (simpleComp) map.set(simpleComp, comp.id);

      if (comp.packages) {
        for (const pkg of comp.packages) {
          map.set(pkg, comp.id);
          const pkgSeg = pkg.split('.').pop();
          if (pkgSeg) map.set(pkgSeg, comp.id);
        }
      }

      if (comp.classes) {
        for (const cls of comp.classes) {
          map.set(cls.id, comp.id);
          map.set(cls.name, comp.id);
          if (cls.packageName) {
            map.set(cls.packageName, comp.id);
            const pSeg = cls.packageName.split('.').pop();
            if (pSeg) map.set(pSeg, comp.id);
          }
        }
      }
    }
    return map;
  });

  function findNodeForClass(fullClassName: string) {
    if (!fullClassName) return null;
    const directCompId = classToNodeIdMap.get(fullClassName);
    if (directCompId) return positionedComponents.get(directCompId) || null;

    const simpleName = fullClassName.split('.').pop() || fullClassName;
    const simpleCompId = classToNodeIdMap.get(simpleName);
    if (simpleCompId) return positionedComponents.get(simpleCompId) || null;

    const parts = fullClassName.split('.');
    if (parts.length > 2) {
      const pkgSeg = parts[parts.length - 2];
      const pkgCompId = classToNodeIdMap.get(pkgSeg);
      if (pkgCompId) return positionedComponents.get(pkgCompId) || null;
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
  bind:clientWidth={containerWidth}
  bind:clientHeight={containerHeight}
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
    <div class="h-3 w-px bg-slate-800"></div>
    <div class="px-2 font-mono text-[10px] text-slate-400" title="Frustum Culled Rendered Nodes">
      {visibleComponents.length}/{components.length} nodes
    </div>
    <div class="h-3 w-px bg-slate-800"></div>

    <!-- Multi-Select HUD Triage Chips -->
    <button
      onclick={() => diagramStore.toggleEdgeBundling()}
      class={`px-2 py-1 rounded text-[10px] font-mono transition-all flex items-center gap-1 ${
        diagramStore.isEdgeBundlingEnabled
          ? 'bg-blue-500/20 text-blue-300 border border-blue-500/40 hover:bg-blue-500/30'
          : 'bg-slate-800/80 text-slate-400 hover:text-white border border-transparent'
      }`}
      title="Toggle Hierarchical Edge Bundling (B)"
    >
      <span>{diagramStore.isEdgeBundlingEnabled ? 'Bundled' : 'Detailed'}</span>
      <span class="text-[9px] text-slate-400">B</span>
    </button>

    <button
      onclick={() => diagramStore.toggleDeclutterFilter('HIDE_CONFORMING_EDGES')}
      class={`px-2 py-1 rounded text-[10px] font-mono transition-all flex items-center gap-1 ${
        diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')
          ? 'bg-rose-500/25 text-rose-300 border border-rose-500/50 hover:bg-rose-500/35 shadow-[0_0_12px_rgba(244,63,94,0.35)]'
          : 'bg-slate-800/80 text-slate-400 hover:text-white border border-transparent'
      }`}
      title="Violation X-Ray Mode: Only Show Violations (V)"
    >
      <span class={diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES') ? 'font-bold text-rose-200' : ''}>X-Ray</span>
      <span class="text-[9px] text-slate-400">V</span>
    </button>

    <button
      onclick={() => diagramStore.toggleDeclutterFilter('ISOLATE_NEIGHBORHOOD')}
      class={`px-2 py-1 rounded text-[10px] font-mono transition-all flex items-center gap-1 ${
        diagramStore.hasDeclutterFilter('ISOLATE_NEIGHBORHOOD')
          ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 hover:bg-emerald-500/30'
          : 'bg-slate-800/80 text-slate-400 hover:text-white border border-transparent'
      }`}
      title="1-Hop Neighborhood Focus (F)"
    >
      <span>1-Hop</span>
      <span class="text-[9px] text-slate-400">F</span>
    </button>
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
      <!-- Concentric Architecture Tier Lanes Backdrop (Culled to viewport & declutter filter) -->
      {#if !diagramStore.hasDeclutterFilter('HIDE_TIER_LANES')}
        {#each sortedLevels as level, rowIdx}
          {@const y = 80 + rowIdx * (BOX_HEIGHT + GAP_Y) - 35}
          {@const height = BOX_HEIGHT + 70}
          {#if !(y + height < viewportBounds.minY || y > viewportBounds.maxY)}
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
          {/if}
        {/each}
      {/if}

      <!-- Agent Radar Scanline Overlay -->
      {#if diagramStore.isRegenerating}
        <g class="pointer-events-none animate-radar-scan">
          <rect
            x="-500"
            y="-120"
            width="3500"
            height="120"
            fill="url(#radar-gradient)"
          />
          <line
            x1="-500"
            y1="0"
            x2="3000"
            y2="0"
            stroke="#34d399"
            stroke-width="2"
            stroke-dasharray="8 4"
            opacity="0.9"
          />
        </g>
      {/if}

      <!-- Animated Dependency Edges (Frustum Culled & Hierarchically Bundled) -->
      {#if diagramStore.declutterMode !== 'REMOVE_ARROWS' && !diagramStore.hasDeclutterFilter('HIDE_ALL_EDGES')}
        {#each renderedGraphEdges as item (item.key)}
          {@const goingDown = item.fromNode.y < item.toNode.y}
          {@const isHighlighted = diagramStore.focusedNodeId
            ? (item.fromNode.comp.id === diagramStore.focusedNodeId || item.toNode.comp.id === diagramStore.focusedNodeId)
            : false}
          {@const isDimmed = connectedNodeIds ? !isHighlighted : false}
          <DependencyEdge
            edge={item.edge}
            x1={item.fromNode.x + item.fromNode.width / 2 + (item.edge.isViolating ? -15 : 15)}
            y1={goingDown ? item.fromNode.y + item.fromNode.height : item.fromNode.y}
            x2={item.toNode.x + item.toNode.width / 2 + (item.edge.isViolating ? -15 : 15)}
            y2={goingDown ? item.toNode.y : item.toNode.y + item.toNode.height}
            fromLabel={item.fromNode.comp.label}
            toLabel={item.toNode.comp.label}
            fromLevel={item.fromNode.comp.level}
            toLevel={item.toNode.comp.level}
            {isDimmed}
            {isHighlighted}
            isBundled={item.isBundled}
            bundleCount={item.bundleCount}
            violationCount={item.violationCount}
          />
        {/each}
      {/if}

      <!-- Component Layer Boxes (Frustum Culled & Focus Highlighted) -->
      {#each visibleComponents as item, i (item.comp.id + ':' + i)}
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

  <!-- Canvas Radar Scrim Overlay (Active while isLoading) -->
  {#if diagramStore.isLoading}
    <div
      data-testid="canvas-loading-scrim"
      class="absolute inset-0 z-30 bg-slate-950/40 backdrop-blur-[2px] flex items-center justify-center pointer-events-none select-none transition-opacity duration-200"
    >
      <div
        class="flex items-center gap-3 px-4 py-2 rounded-full bg-slate-900/95 border border-blue-500/40 shadow-2xl text-xs font-mono text-blue-300"
      >
        <div class="relative w-4 h-4 flex items-center justify-center">
          <span class="absolute w-4 h-4 rounded-full bg-blue-500/30 animate-ping"></span>
          <span class="w-3.5 h-3.5 rounded-full border-2 border-blue-400 border-t-transparent animate-spin"></span>
        </div>
        <span>Compiling AST & Concentric Topology...</span>
      </div>
    </div>
  {/if}

  <!-- Autonomous Agent Sonar Wave (Active while isRegenerating) -->
  {#if diagramStore.isRegenerating}
    <div class="absolute inset-0 z-20 pointer-events-none overflow-hidden select-none">
      <div
        class="w-full h-32 bg-gradient-to-b from-transparent via-cyan-400/20 to-transparent animate-sonar-sweep border-b border-cyan-400/50"
      ></div>
    </div>
  {/if}

  <!-- Agent Mailbox Telemetry Drawer -->
  <AgentDrawer />
</div>

<style>
  @keyframes radar-scan {
    0% { transform: translateY(-100px); opacity: 0; }
    15% { opacity: 0.85; }
    85% { opacity: 0.85; }
    100% { transform: translateY(1800px); opacity: 0; }
  }
  .animate-radar-scan {
    animation: radar-scan 2.2s linear infinite;
  }
  @keyframes sonar-sweep {
    0% { transform: translateY(-100%); opacity: 0; }
    25% { opacity: 1; }
    75% { opacity: 1; }
    100% { transform: translateY(100vh); opacity: 0; }
  }
  .animate-sonar-sweep {
    animation: sonar-sweep 1.8s cubic-bezier(0.4, 0, 0.2, 1) infinite;
  }
</style>
