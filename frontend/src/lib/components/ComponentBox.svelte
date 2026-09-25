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
  let isCompactLOD = $derived(
    (diagramStore.zoom < 0.55 || diagramStore.hasDeclutterFilter('HIDE_CLASSES')) && !isFocused
  );
  let isAddedInComparison = $derived(
    diagramStore.isComparing &&
    diagramStore.snapshotGraph &&
    !diagramStore.snapshotGraph.components.some((c) => c.id === component.id)
  );

  let classPage = $state(0);
  let totalClasses = $derived(component.classes?.length || 0);
  let pageSize = $derived(totalClasses > 6 ? 5 : 6);
  let totalPages = $derived(Math.max(1, Math.ceil(totalClasses / pageSize)));

  let displayedClasses = $derived.by(() => {
    if (!component.classes) return [];
    if (totalClasses <= 6) return component.classes.slice(0, 6);
    const start = classPage * pageSize;
    return component.classes.slice(start, start + pageSize);
  });

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
  data-component-id={component.id}
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

  <defs>
    <clipPath id={`card-clip-${component.id}`}>
      <rect width={width} height={height} rx="8" />
    </clipPath>
  </defs>

  <!-- Clipped Card Surface & Header Interior -->
  <g clip-path={`url(#card-clip-${component.id})`}>
    <!-- Base Card Fill -->
    <rect
      width={width}
      height={height}
      class={`fill-slate-800/90 transition-colors ${
        isDragging ? 'fill-slate-800' : isFocused ? 'fill-slate-800' : ''
      }`}
    />

    <!-- Component Header Banner (Clipped cleanly by card-clip for perfect top corners) -->
    <rect
      x="0"
      y="0"
      width={width}
      height="30"
      class={`transition-colors ${
        isDragging
          ? 'fill-slate-900 cursor-grabbing'
          : 'fill-slate-900/95 group-hover:fill-slate-850 cursor-grab'
      }`}
      onpointerdown={(e) => {
        if (e.button === 0) onStartDrag?.(e);
      }}
    />

    <!-- Header Bottom Divider Edge -->
    <line
      x1="0"
      y1="30"
      x2={width}
      y2="30"
      class="stroke-slate-700/80"
      stroke-width="1"
    />
  </g>

  <!-- Outer Box Card Surface (Drawn on top with pointer-events="all" for crisp, unbroken edges and dragging) -->
  <rect
    data-testid="component-card"
    width={width}
    height={height}
    rx="8"
    fill="transparent"
    pointer-events="all"
    class={`transition-colors ${
      isDragging
        ? 'stroke-sky-400 stroke-2 cursor-grabbing'
        : isFocused
          ? 'stroke-blue-400 stroke-2 cursor-grab'
          : 'stroke-slate-600 group-hover:stroke-blue-400 stroke-[1.5] cursor-grab'
    }`}
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

  <!-- Comparison Mode: New Component Badge -->
  {#if isAddedInComparison}
    <rect
      x={width - 116}
      y="6"
      width="46"
      height="18"
      rx="4"
      class="fill-emerald-950/90 stroke-emerald-500/70 stroke-[0.75] pointer-events-none"
    />
    <text
      x={width - 93}
      y="19"
      text-anchor="middle"
      class="fill-emerald-300 font-mono text-[9px] font-bold pointer-events-none select-none"
    >
      +NEW
    </text>
  {/if}

  <!-- Clean Architecture Ring Badge -->
  <rect
    x={width - 64}
    y="6"
    width="52"
    height="18"
    rx="4"
    class="fill-slate-800/90 stroke-slate-700/70 stroke-[0.75] cursor-grab pointer-events-none"
  />
  <text x={width - 38} y="19" text-anchor="middle" class="fill-slate-300 font-mono text-[10px] font-medium pointer-events-none select-none">
    {rankBadge}
  </text>

  <!-- Contained Classes / Modules with Health Heatmap Badges OR Compact Semantic LOD -->
  {#if diagramStore.declutterMode !== 'CLASSES'}
    {#if isCompactLOD}
      <!-- Compact Semantic LOD View (Zoom < 0.55) -->
      {@const highCrapClasses = component.classes.filter((c) => c.crap.mu > 15)}
      {@const avgCoverage = component.classes.length > 0 
        ? Math.round((component.classes.reduce((acc, c) => acc + c.coverage, 0) / component.classes.length) * 100) 
        : 100}
      <g transform="translate(12, 40)" class="pointer-events-none select-none">
        <!-- Class Count Banner -->
        <rect
          width={width - 24}
          height="34"
          rx="6"
          class="fill-slate-900/80 stroke-slate-700/60"
          stroke-width="1"
        />
        <text x="12" y="21" class="fill-slate-200 font-mono text-[12px] font-semibold">
          {component.classes.length} {component.classes.length === 1 ? 'class' : 'classes'}
        </text>

        <!-- High CRAP Alert or Clean Health Pill -->
        {#if highCrapClasses.length > 0}
          <rect
            x={width - 128}
            y="7"
            width="92"
            height="20"
            rx="4"
            fill="#ef4444"
            fill-opacity="0.2"
            stroke="#ef4444"
            stroke-width="1"
          />
          <text
            x={width - 82}
            y="21"
            text-anchor="middle"
            fill="#f87171"
            class="font-mono text-[10px] font-bold"
          >
            {highCrapClasses.length} HIGH CRAP
          </text>
        {:else}
          <rect
            x={width - 96}
            y="7"
            width="60"
            height="20"
            rx="4"
            fill="#10b981"
            fill-opacity="0.18"
            stroke="#10b981"
            stroke-width="1"
          />
          <text
            x={width - 66}
            y="21"
            text-anchor="middle"
            fill="#34d399"
            class="font-mono text-[10px] font-bold"
          >
            HEALTHY
          </text>
        {/if}

        <!-- Macro Package Metadata -->
        <text x="4" y="58" class="fill-slate-400 font-mono text-[11px]">
          {component.packages?.[0] || component.label}
        </text>
        <text x="4" y="78" class="fill-slate-500 font-mono text-[10px]">
          Avg Test Coverage: {avgCoverage}%
        </text>
      </g>
    {:else}
      <!-- Detailed Micro View with Class Rows & Stepwise Paging -->
      <g transform="translate(10, 36)">
        {#each displayedClasses as cls, i (cls.id)}
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

        {#if totalClasses > 6}
          <!-- Interactive Stepwise Pager & Inspector Link -->
          <g transform="translate(0, 120)">
            <!-- Prev Button -->
            <!-- svelte-ignore a11y_click_events_have_key_events -->
            <!-- svelte-ignore a11y_no_static_element_interactions -->
            <g
              class="cursor-pointer group/prev"
              onpointerdown={(e) => e.stopPropagation()}
              onclick={(e) => {
                e.stopPropagation();
                classPage = (classPage - 1 + totalPages) % totalPages;
              }}
              aria-label="Previous classes"
            >
              <rect
                x="0"
                y="0"
                width="22"
                height="18"
                rx="3"
                class="fill-slate-800 hover:fill-slate-700 stroke-slate-700 hover:stroke-slate-500 transition-colors"
                stroke-width="0.8"
              />
              <text
                x="11"
                y="13"
                text-anchor="middle"
                class="fill-slate-300 group-hover/prev:fill-white font-mono text-[12px] font-bold select-none"
              >
                ‹
              </text>
            </g>

            <!-- Page Range & Inspector Trigger -->
            <!-- svelte-ignore a11y_click_events_have_key_events -->
            <!-- svelte-ignore a11y_no_static_element_interactions -->
            <g
              class="cursor-pointer group/pager"
              onpointerdown={(e) => e.stopPropagation()}
              onclick={(e) => {
                e.stopPropagation();
                diagramStore.setFocusedNode(component.id);
              }}
            >
              <title>Click to view complete class inventory in Inspector</title>
              <rect
                x="26"
                y="0"
                width={width - 20 - 52}
                height="18"
                rx="3"
                class="fill-slate-800/80 hover:fill-blue-950/60 stroke-slate-700/80 hover:stroke-blue-500/50 transition-colors"
                stroke-width="0.8"
              />
              <text
                x={(width - 20) / 2}
                y="12"
                text-anchor="middle"
                class="fill-slate-400 group-hover/pager:fill-blue-200 font-mono text-[9px] font-medium select-none"
              >
                {classPage * pageSize + 1}–{Math.min((classPage + 1) * pageSize, totalClasses)} of {totalClasses} (inspect ↗)
              </text>
            </g>

            <!-- Next Button -->
            <!-- svelte-ignore a11y_click_events_have_key_events -->
            <!-- svelte-ignore a11y_no_static_element_interactions -->
            <g
              class="cursor-pointer group/next"
              onpointerdown={(e) => e.stopPropagation()}
              onclick={(e) => {
                e.stopPropagation();
                classPage = (classPage + 1) % totalPages;
              }}
              aria-label="Next classes"
            >
              <rect
                x={width - 20 - 22}
                y="0"
                width="22"
                height="18"
                rx="3"
                class="fill-slate-800 hover:fill-slate-700 stroke-slate-700 hover:stroke-slate-500 transition-colors"
                stroke-width="0.8"
              />
              <text
                x={width - 20 - 11}
                y="13"
                text-anchor="middle"
                class="fill-slate-300 group-hover/next:fill-white font-mono text-[12px] font-bold select-none"
              >
                ›
              </text>
            </g>
          </g>
        {/if}
      </g>
    {/if}
  {/if}
</g>
