<script lang="ts">
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import type { ScatterPlotPoint } from '../types/diagram';
  import {
    X,
    Activity,
    ShieldCheck,
    AlertTriangle,
    Sparkles,
    Search,
    Layers,
    Filter,
    Compass,
    ExternalLink,
    Crosshair,
    HelpCircle
  } from '@lucide/svelte';

  let isOpen = $derived(diagramStore.isMainSequenceOpen);
  let allPoints = $derived(diagramStore.activeScatterPlotPoints);
  let isSandbox = $derived(diagramStore.isSandboxActive);

  let backdropEl: HTMLDivElement | null = $state(null);
  let dialogEl: HTMLDivElement | null = $state(null);
  let isClosing = $state(false);

  // Filters & Selection
  let tierFilter = $state<number | 'ALL'>('ALL');
  let searchQuery = $state<string>('');
  let selectedPoint = $state<ScatterPlotPoint | null>(null);
  let hoveredPoint = $state<ScatterPlotPoint | null>(null);

  let activePoint = $derived(hoveredPoint || selectedPoint);

  let filteredPoints = $derived(
    allPoints.filter((pt) => {
      if (tierFilter !== 'ALL' && pt.level !== tierFilter) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        return pt.label.toLowerCase().includes(q) || pt.componentId.toLowerCase().includes(q);
      }
      return true;
    })
  );

  let tierCounts = $derived({
    ALL: allPoints.length,
    0: allPoints.filter((p) => p.level === 0).length,
    1: allPoints.filter((p) => p.level === 1).length,
    2: allPoints.filter((p) => p.level === 2).length,
    3: allPoints.filter((p) => p.level === 3).length
  });

  // Summary Metrics
  let totalCount = $derived(allPoints.length);
  let balancedCount = $derived(allPoints.filter((p) => p.zone === 'MAIN_SEQUENCE').length);
  let painCount = $derived(allPoints.filter((p) => p.zone === 'ZONE_OF_PAIN').length);
  let uselessCount = $derived(allPoints.filter((p) => p.zone === 'ZONE_OF_USELESSNESS').length);
  let meanDistance = $derived(
    totalCount > 0 ? (allPoints.reduce((acc, p) => acc + p.distance, 0) / totalCount).toFixed(3) : '0.000'
  );

  $effect(() => {
    if (isOpen && backdropEl && dialogEl && !isClosing) {
      const ctx = gsap.context(() => {
        gsap.fromTo(backdropEl, { opacity: 0 }, { opacity: 1, duration: 0.2, ease: 'power2.out' });
        gsap.fromTo(
          dialogEl,
          { scale: 0.94, y: 16, opacity: 0 },
          { scale: 1, y: 0, opacity: 1, duration: 0.3, ease: 'back.out(1.3)' }
        );
      });
      return () => ctx.revert();
    }
  });

  function handleClose() {
    if (isClosing || !dialogEl || !backdropEl) {
      diagramStore.closeMainSequence();
      return;
    }
    isClosing = true;
    gsap.to(dialogEl, {
      scale: 0.95,
      y: 12,
      opacity: 0,
      duration: 0.2,
      ease: 'power2.in'
    });
    gsap.to(backdropEl, {
      opacity: 0,
      duration: 0.2,
      ease: 'power2.in',
      onComplete: () => {
        diagramStore.closeMainSequence();
        isClosing = false;
        selectedPoint = null;
        hoveredPoint = null;
      }
    });
  }

  function getTierColor(level: number | null): string {
    switch (level) {
      case 0:
        return '#10b981'; // Emerald
      case 1:
        return '#3b82f6'; // Blue
      case 2:
        return '#f59e0b'; // Amber
      case 3:
        return '#8b5cf6'; // Violet
      default:
        return '#94a3b8'; // Slate
    }
  }

  function getTierName(level: number | null): string {
    switch (level) {
      case 0:
        return 'Domain Core (L0)';
      case 1:
        return 'Application (L1)';
      case 2:
        return 'Adapters (L2)';
      case 3:
        return 'Frameworks (L3)';
      default:
        return 'Unassigned';
    }
  }

  function focusComponentOnCanvas(componentId: string) {
    diagramStore.setFocusedNode(componentId);
    handleClose();
  }
</script>

{#if isOpen}
  <!-- Backdrop -->
  <div
    bind:this={backdropEl}
    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md"
    onclick={(e) => {
      if (e.target === backdropEl) handleClose();
    }}
    onkeydown={(e) => {
      if (e.key === 'Escape') handleClose();
    }}
    tabindex="-1"
    role="presentation"
  >
    <!-- Dialog Window -->
    <div
      bind:this={dialogEl}
      class="relative w-full max-w-5xl max-h-[92vh] flex flex-col bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl shadow-slate-950 overflow-hidden font-sans"
    >
      <!-- Header -->
      <div class="px-6 py-4 bg-slate-900 border-b border-slate-800 flex items-center justify-between select-none">
        <div class="flex items-center gap-3">
          <div class="p-2 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-400">
            <Activity size={20} />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h2 class="text-base font-semibold text-white tracking-tight">
                Robert C. Martin Main Sequence & Stability Quadrant
              </h2>
              {#if isSandbox}
                <span class="px-2 py-0.5 rounded-full bg-amber-500/15 border border-amber-500/30 text-amber-300 text-[10px] font-mono font-medium">
                  🧪 Sandbox Simulation
                </span>
              {/if}
            </div>
            <p class="text-xs text-slate-400">
              Evaluates component Abstractness ($A$) vs. Instability ($I$) and deviation from the optimal Main Sequence ($A + I = 1$).
            </p>
          </div>
        </div>

        <button
          onclick={handleClose}
          class="p-2 rounded-xl bg-slate-800/80 hover:bg-slate-700 text-slate-400 hover:text-white transition-colors cursor-pointer"
          title="Close (Esc)"
          aria-label="Close Modal"
        >
          <X size={18} />
        </button>
      </div>

      <!-- Overview Stats Strip -->
      <div class="px-6 py-2.5 bg-slate-950/50 border-b border-slate-800/80 flex flex-wrap items-center justify-between gap-4 text-xs">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-1.5 text-slate-300">
            <span class="text-slate-500">Components:</span>
            <span class="font-mono font-semibold text-white">{totalCount}</span>
          </div>
          <div class="flex items-center gap-1.5 text-slate-300">
            <span class="text-slate-500">Mean Distance ($D$):</span>
            <span class="font-mono font-semibold text-blue-300">{meanDistance}</span>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <span class="px-2.5 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 font-mono font-medium flex items-center gap-1">
            <ShieldCheck size={12} />
            {balancedCount} Balanced
          </span>
          <span class="px-2.5 py-0.5 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-400 font-mono font-medium flex items-center gap-1">
            <AlertTriangle size={12} />
            {painCount} Zone of Pain
          </span>
          <span class="px-2.5 py-0.5 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-400 font-mono font-medium flex items-center gap-1">
            <Sparkles size={12} />
            {uselessCount} Zone of Uselessness
          </span>
        </div>
      </div>

      <!-- Controls & Filter Toolbar -->
      <div class="px-6 py-2.5 bg-slate-900 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3 text-xs">
        <!-- Tier Filter Buttons -->
        <div class="flex items-center gap-1">
          <span class="text-slate-500 mr-1 flex items-center gap-1">
            <Filter size={12} /> Tier:
          </span>
          <button
            onclick={() => tierFilter = 'ALL'}
            class={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
              tierFilter === 'ALL'
                ? 'bg-blue-600 text-white shadow-sm shadow-blue-900/50'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-750'
            }`}
          >
            All <span class="ml-1 text-[10px] opacity-75 font-mono">({tierCounts.ALL})</span>
          </button>
          <button
            onclick={() => tierFilter = 0}
            class={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer flex items-center gap-1.5 ${
              tierFilter === 0
                ? 'bg-emerald-600 text-white shadow-sm shadow-emerald-900/50'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-750'
            }`}
          >
            <span class="w-2 h-2 rounded-full bg-emerald-400"></span>
            Domain (L0) <span class="ml-1 text-[10px] opacity-75 font-mono">({tierCounts[0]})</span>
          </button>
          <button
            onclick={() => tierFilter = 1}
            class={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer flex items-center gap-1.5 ${
              tierFilter === 1
                ? 'bg-blue-600 text-white shadow-sm shadow-blue-900/50'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-750'
            }`}
          >
            <span class="w-2 h-2 rounded-full bg-blue-400"></span>
            App (L1) <span class="ml-1 text-[10px] opacity-75 font-mono">({tierCounts[1]})</span>
          </button>
          <button
            onclick={() => tierFilter = 2}
            class={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer flex items-center gap-1.5 ${
              tierFilter === 2
                ? 'bg-amber-600 text-white shadow-sm shadow-amber-900/50'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-750'
            }`}
          >
            <span class="w-2 h-2 rounded-full bg-amber-400"></span>
            Adapters (L2) <span class="ml-1 text-[10px] opacity-75 font-mono">({tierCounts[2]})</span>
          </button>
          <button
            onclick={() => tierFilter = 3}
            class={`px-2.5 py-1 rounded-lg font-medium transition-colors cursor-pointer flex items-center gap-1.5 ${
              tierFilter === 3
                ? 'bg-purple-600 text-white shadow-sm shadow-purple-900/50'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-750'
            }`}
          >
            <span class="w-2 h-2 rounded-full bg-purple-400"></span>
            Frameworks (L3) <span class="ml-1 text-[10px] opacity-75 font-mono">({tierCounts[3]})</span>
          </button>
        </div>

        <!-- Search Bar -->
        <div class="relative w-48">
          <Search size={13} class="absolute left-2.5 top-2.5 text-slate-500" />
          <input
            type="text"
            bind:value={searchQuery}
            placeholder="Search component..."
            class="w-full pl-8 pr-3 py-1 bg-slate-800/80 border border-slate-700 rounded-lg text-slate-200 text-xs focus:outline-none focus:border-blue-500 transition-colors"
          />
        </div>
      </div>

      <!-- Main Body: SVG Chart & Detail Panel -->
      <div class="flex-1 overflow-y-auto p-6 grid grid-cols-1 lg:grid-cols-12 gap-6 bg-slate-950/40">
        <!-- Left: SVG Cartesian Chart (7 cols) -->
        <div class="lg:col-span-7 flex flex-col items-center justify-center bg-slate-900/60 border border-slate-800 rounded-xl p-4 relative select-none">
          <svg
            viewBox="0 0 560 540"
            class="w-full max-w-[520px] aspect-square overflow-visible"
          >
            <defs>
              <!-- Zone of Pain Gradient -->
              <linearGradient id="painGrad" x1="0%" y1="100%" x2="50%" y2="50%">
                <stop offset="0%" stop-color="#f43f5e" stop-opacity="0.22" />
                <stop offset="100%" stop-color="#f43f5e" stop-opacity="0.02" />
              </linearGradient>

              <!-- Zone of Uselessness Gradient -->
              <linearGradient id="uselessGrad" x1="100%" y1="0%" x2="50%" y2="50%">
                <stop offset="0%" stop-color="#f59e0b" stop-opacity="0.22" />
                <stop offset="100%" stop-color="#f59e0b" stop-opacity="0.02" />
              </linearGradient>

              <!-- Balanced Corridor Gradient -->
              <linearGradient id="corridorGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#10b981" stop-opacity="0.08" />
                <stop offset="50%" stop-color="#10b981" stop-opacity="0.14" />
                <stop offset="100%" stop-color="#10b981" stop-opacity="0.08" />
              </linearGradient>
            </defs>

            <!-- Plot Background -->
            <rect x="60" y="40" width="440" height="440" fill="#0b0f17" rx="6" stroke="#1e293b" stroke-width="1" />

            <!-- Grid Lines (Every 0.2) -->
            {#each [0.2, 0.4, 0.6, 0.8] as tick}
              <!-- Vertical Grid -->
              <line
                x1={60 + tick * 440}
                y1="40"
                x2={60 + tick * 440}
                y2="480"
                stroke="#1e293b"
                stroke-dasharray="3,3"
                stroke-width="1"
              />
              <!-- Horizontal Grid -->
              <line
                x1="60"
                y1={480 - tick * 440}
                x2="500"
                y2={480 - tick * 440}
                stroke="#1e293b"
                stroke-dasharray="3,3"
                stroke-width="1"
              />
            {/each}

            <!-- 1. Balanced Corridor Ribbon: |A + I - 1| <= 0.25 -->
            <polygon
              points="60,150 60,40 170,40 500,370 500,480 390,480"
              fill="url(#corridorGrad)"
            />

            <!-- 2. Zone of Pain (Bottom-Left Polygon) -->
            <polygon
              points="60,480 214,480 60,326"
              fill="url(#painGrad)"
              stroke="#f43f5e"
              stroke-width="1"
              stroke-dasharray="2,2"
              opacity="0.8"
            />
            <text
              x="85"
              y="455"
              fill="#fda4af"
              font-size="11"
              font-family="monospace"
              font-weight="600"
              opacity="0.9"
            >
              Zone of Pain
            </text>
            <text
              x="85"
              y="470"
              fill="#94a3b8"
              font-size="9"
              font-family="sans-serif"
              opacity="0.75"
            >
              (Rigid • Highly Concrete)
            </text>

            <!-- 3. Zone of Uselessness (Top-Right Polygon) -->
            <polygon
              points="500,40 346,40 500,194"
              fill="url(#uselessGrad)"
              stroke="#f59e0b"
              stroke-width="1"
              stroke-dasharray="2,2"
              opacity="0.8"
            />
            <text
              x="360"
              y="65"
              fill="#fcd34d"
              font-size="11"
              font-family="monospace"
              font-weight="600"
              opacity="0.9"
            >
              Zone of Uselessness
            </text>
            <text
              x="360"
              y="80"
              fill="#94a3b8"
              font-size="9"
              font-family="sans-serif"
              opacity="0.75"
            >
              (Unused • Pure Abstraction)
            </text>

            <!-- 4. The Ideal Main Sequence Line: A + I = 1 -->
            <line
              x1="60"
              y1="40"
              x2="500"
              y2="480"
              stroke="#10b981"
              stroke-width="2"
              stroke-dasharray="6,4"
              opacity="0.85"
            />
            <!-- Main Sequence Diagonal Text Label -->
            <text
              x="260"
              y="245"
              fill="#34d399"
              font-size="10"
              font-family="monospace"
              font-weight="600"
              transform="rotate(45, 260, 245)"
              text-anchor="middle"
              opacity="0.9"
            >
              Main Sequence (A + I = 1)
            </text>

            <!-- 5. Axes Tick Marks & Numeric Labels -->
            <!-- X-Axis Labels (Instability) -->
            {#each [0.0, 0.2, 0.4, 0.6, 0.8, 1.0] as val}
              <text
                x={60 + val * 440}
                y="498"
                fill="#64748b"
                font-size="10"
                font-family="monospace"
                text-anchor="middle"
              >
                {val.toFixed(1)}
              </text>
            {/each}

            <!-- Y-Axis Labels (Abstractness) -->
            {#each [0.0, 0.2, 0.4, 0.6, 0.8, 1.0] as val}
              <text
                x="50"
                y={484 - val * 440}
                fill="#64748b"
                font-size="10"
                font-family="monospace"
                text-anchor="end"
              >
                {val.toFixed(1)}
              </text>
            {/each}

            <!-- Axis Titles -->
            <!-- X-Axis Title -->
            <text
              x="280"
              y="525"
              fill="#94a3b8"
              font-size="11"
              font-family="sans-serif"
              font-weight="600"
              text-anchor="middle"
            >
              Instability (I = Ce / (Ca + Ce))
            </text>
            <text x="60" y="525" fill="#475569" font-size="9" text-anchor="start">
              ← 0.0 Stable (Foundational)
            </text>
            <text x="500" y="525" fill="#475569" font-size="9" text-anchor="end">
              1.0 Instable (Leaf) →
            </text>

            <!-- Y-Axis Title -->
            <text
              x="20"
              y="260"
              fill="#94a3b8"
              font-size="11"
              font-family="sans-serif"
              font-weight="600"
              text-anchor="middle"
              transform="rotate(-90, 20, 260)"
            >
              Abstractness (A = Na / Nc)
            </text>
            <text
              x="42"
              y="480"
              fill="#475569"
              font-size="9"
              text-anchor="middle"
              transform="rotate(-90, 42, 480)"
            >
              ← 0.0 Concrete
            </text>
            <text
              x="42"
              y="60"
              fill="#475569"
              font-size="9"
              text-anchor="middle"
              transform="rotate(-90, 42, 60)"
            >
              1.0 Abstract →
            </text>

            <!-- 6. Component Scatter Dots -->
            {#each filteredPoints as pt (pt.componentId)}
              {@const isSelected = selectedPoint?.componentId === pt.componentId}
              {@const isHovered = hoveredPoint?.componentId === pt.componentId}
              {@const tierColor = getTierColor(pt.level)}

              <g
                class="cursor-pointer transition-transform duration-200"
                tabindex="0"
                role="button"
                aria-label={`Component ${pt.label}: Instability ${pt.instability.toFixed(2)}, Abstractness ${pt.abstractness.toFixed(2)}`}
                onclick={() => (selectedPoint = pt)}
                ondblclick={() => focusComponentOnCanvas(pt.componentId)}
                onmouseenter={() => (hoveredPoint = pt)}
                onmouseleave={() => {
                  if (hoveredPoint?.componentId === pt.componentId) hoveredPoint = null;
                }}
                onkeydown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    selectedPoint = pt;
                  }
                }}
              >
                <!-- Halo on hover/selected -->
                {#if isHovered || isSelected}
                  <circle
                    cx={pt.x}
                    cy={pt.y}
                    r={pt.radius + 8}
                    fill={tierColor}
                    opacity="0.25"
                    class="animate-pulse"
                  />
                  <circle
                    cx={pt.x}
                    cy={pt.y}
                    r={pt.radius + 4}
                    fill="none"
                    stroke={tierColor}
                    stroke-width="1.5"
                    stroke-dasharray="3,2"
                  />
                {/if}

                <!-- Dot Body -->
                <circle
                  cx={pt.x}
                  cy={pt.y}
                  r={pt.radius}
                  fill={tierColor}
                  stroke="#0f172a"
                  stroke-width="2"
                  class="filter drop-shadow-md"
                />

                <!-- Dot Label (Small Tag) -->
                <text
                  x={pt.x}
                  y={pt.y - pt.radius - 4}
                  fill={isHovered || isSelected ? '#ffffff' : '#cbd5e1'}
                  font-size="9"
                  font-family="monospace"
                  font-weight={isHovered || isSelected ? '700' : '500'}
                  text-anchor="middle"
                  class="pointer-events-none select-none transition-colors"
                >
                  {pt.label.length > 14 ? pt.label.slice(0, 12) + '…' : pt.label}
                </text>
              </g>
            {/each}

            <!-- 7. Interactive Floating SVG Tooltip -->
            {#if hoveredPoint}
              {@const ttX = hoveredPoint.x > 340 ? hoveredPoint.x - 175 : hoveredPoint.x + 18}
              {@const ttY = hoveredPoint.y > 380 ? hoveredPoint.y - 85 : hoveredPoint.y - 12}
              {@const ttColor = getTierColor(hoveredPoint.level)}
              <g
                transform={`translate(${ttX}, ${ttY})`}
                class="pointer-events-none select-none transition-all duration-150"
              >
                <rect
                  x="0"
                  y="0"
                  width="160"
                  height="72"
                  rx="8"
                  fill="#0b0f17"
                  stroke={ttColor}
                  stroke-width="1.5"
                  opacity="0.95"
                  class="filter drop-shadow-xl"
                />
                <text x="10" y="18" fill="#ffffff" font-size="11" font-weight="700" font-family="sans-serif">
                  {hoveredPoint.label.length > 18 ? hoveredPoint.label.slice(0, 16) + '…' : hoveredPoint.label}
                </text>
                <text x="10" y="32" fill={ttColor} font-size="9" font-family="monospace">
                  {getTierName(hoveredPoint.level)}
                </text>
                <text x="10" y="48" fill="#94a3b8" font-size="9" font-family="monospace">
                  I: {hoveredPoint.instability.toFixed(2)} | A: {hoveredPoint.abstractness.toFixed(2)}
                </text>
                <text x="10" y="62" fill="#cbd5e1" font-size="9" font-family="monospace">
                  Distance: {hoveredPoint.distance.toFixed(3)}
                </text>
              </g>
            {/if}
          </svg>
        </div>

        <!-- Right: Telemetry & Detail Card (5 cols) -->
        <div class="lg:col-span-5 flex flex-col justify-between bg-slate-900/80 border border-slate-800 rounded-xl p-5 select-none">
          {#if activePoint}
            <!-- Component Telemetry Card -->
            <div class="space-y-4">
              <div class="flex items-start justify-between">
                <div>
                  <div class="flex items-center gap-2">
                    <span
                      class="w-3 h-3 rounded-full"
                      style={`background-color: ${getTierColor(activePoint.level)}`}
                    ></span>
                    <h3 class="text-base font-bold text-white tracking-tight break-all">
                      {activePoint.label}
                    </h3>
                  </div>
                  <span class="text-xs text-slate-400 font-mono">
                    {getTierName(activePoint.level)}
                  </span>
                </div>

                <!-- Zone Badge -->
                {#if activePoint.zone === 'MAIN_SEQUENCE'}
                  <span class="px-2 py-0.5 rounded-full bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 text-[11px] font-mono font-medium">
                    ✓ Balanced
                  </span>
                {:else if activePoint.zone === 'ZONE_OF_PAIN'}
                  <span class="px-2 py-0.5 rounded-full bg-rose-500/15 border border-rose-500/30 text-rose-400 text-[11px] font-mono font-medium">
                    ⚠️ Zone of Pain
                  </span>
                {:else}
                  <span class="px-2 py-0.5 rounded-full bg-amber-500/15 border border-amber-500/30 text-amber-400 text-[11px] font-mono font-medium">
                    ⚡ Zone of Uselessness
                  </span>
                {/if}
              </div>

              <!-- Metrics Matrix Grid -->
              <div class="grid grid-cols-2 gap-2 text-xs">
                <div class="p-2.5 rounded-lg bg-slate-800/60 border border-slate-750">
                  <div class="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                    Instability ($I$)
                  </div>
                  <div class="text-lg font-mono font-bold text-blue-400">
                    {activePoint.instability.toFixed(3)}
                  </div>
                  <div class="text-[10px] text-slate-500">
                    $C_e$: {activePoint.ce} &bull; $C_a$: {activePoint.ca}
                  </div>
                </div>

                <div class="p-2.5 rounded-lg bg-slate-800/60 border border-slate-750">
                  <div class="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                    Abstractness ($A$)
                  </div>
                  <div class="text-lg font-mono font-bold text-purple-400">
                    {activePoint.abstractness.toFixed(3)}
                  </div>
                  <div class="text-[10px] text-slate-500">
                    {activePoint.abstractCount} / {activePoint.classCount} classes
                  </div>
                </div>

                <div class="p-2.5 rounded-lg bg-slate-800/60 border border-slate-750">
                  <div class="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                    Distance from Main ($D$)
                  </div>
                  <div class="text-lg font-mono font-bold text-emerald-400">
                    {activePoint.distance.toFixed(3)}
                  </div>
                  <div class="text-[10px] text-slate-500">
                    Formula: $|A + I - 1|$
                  </div>
                </div>

                <div class="p-2.5 rounded-lg bg-slate-800/60 border border-slate-750">
                  <div class="text-[10px] text-slate-400 uppercase tracking-wider font-semibold">
                    Total Classes ($N_c$)
                  </div>
                  <div class="text-lg font-mono font-bold text-white">
                    {activePoint.classCount}
                  </div>
                  <div class="text-[10px] text-slate-500">
                    Interfaces: {activePoint.abstractCount}
                  </div>
                </div>
              </div>

              <!-- Zone Diagnostic Recommendation -->
              <div class={`p-3 rounded-lg border text-xs leading-relaxed ${
                activePoint.zone === 'ZONE_OF_PAIN'
                  ? 'bg-rose-950/20 border-rose-900/40 text-rose-200'
                  : activePoint.zone === 'ZONE_OF_USELESSNESS'
                  ? 'bg-amber-950/20 border-amber-900/40 text-amber-200'
                  : 'bg-emerald-950/20 border-emerald-900/40 text-emerald-200'
              }`}>
                {#if activePoint.zone === 'ZONE_OF_PAIN'}
                  <strong>Architectural Warning:</strong> This component is highly concrete and heavily depended upon, making it rigid and fragile to change.
                  <div class="mt-1 text-[11px] text-rose-300">
                    💡 <em>Prescription:</em> Apply the <strong>Dependency Inversion Principle (DIP)</strong> to extract interface ports in this tier.
                  </div>
                {:else if activePoint.zone === 'ZONE_OF_USELESSNESS'}
                  <strong>Architectural Advisory:</strong> This component contains abstract interfaces that have zero dependents, indicating speculative over-engineering or dead abstractions.
                {:else}
                  <strong>Architecturally Balanced:</strong> This component tracks closely along Uncle Bob's Main Sequence ($A + I = 1$), striking an optimal balance between extensibility and stability.
                {/if}
              </div>

              <!-- Quick Focus Action -->
              <button
                onclick={() => focusComponentOnCanvas(activePoint.componentId)}
                class="w-full py-2 px-3 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-xs flex items-center justify-center gap-1.5 transition-colors cursor-pointer shadow-md shadow-blue-950"
              >
                <Crosshair size={14} />
                Focus Component on Canvas (F)
              </button>
            </div>
          {:else}
            <!-- Empty State / Architectural Guide -->
            <div class="space-y-4">
              <div class="flex items-center gap-2 text-white font-semibold text-sm">
                <Compass size={18} class="text-blue-400" />
                Understanding the Main Sequence
              </div>

              <div class="text-xs text-slate-300 space-y-2.5 leading-relaxed">
                <p>
                  In <em>Clean Architecture</em>, Robert C. Martin established that components should ideally adhere to the <strong>Main Sequence line</strong> ($A + I = 1$):
                </p>
                <ul class="list-disc pl-4 space-y-1 text-slate-400">
                  <li><strong>Stable packages</strong> ($I \to 0$) should be <strong>abstract</strong> ($A \to 1$) so they can be easily extended.</li>
                  <li><strong>Instable packages</strong> ($I \to 1$) should be <strong>concrete</strong> ($A \to 0$) since they are easy to modify.</li>
                </ul>

                <div class="p-3 rounded-lg bg-slate-800/60 border border-slate-750 space-y-1.5 font-mono text-[11px]">
                  <div class="text-blue-300">Instability: I = Ce / (Ca + Ce)</div>
                  <div class="text-purple-300">Abstractness: A = Na / Nc</div>
                  <div class="text-emerald-300">Distance: D = |A + I - 1|</div>
                </div>

                <p class="text-[11px] text-slate-400">
                  Hover over or click any component dot on the scatter plot to inspect its coupling telemetry, exact $(I, A)$ coordinates, and targeted DIP recommendations.
                </p>
              </div>
            </div>
          {/if}

          <!-- Footer Shortcut Notice -->
          <div class="pt-4 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-500">
            <span>Press <kbd class="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-[9px] font-mono text-slate-400">Esc</kbd> to close</span>
            <span>Press <kbd class="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-[9px] font-mono text-slate-400">M</kbd> to toggle</span>
          </div>
        </div>
      </div>
    </div>
  </div>
{/if}
