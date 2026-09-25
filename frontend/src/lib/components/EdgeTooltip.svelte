<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { ShieldCheck, AlertTriangle, ArrowRight, Lightbulb, X } from '@lucide/svelte';

  let info = $derived(diagramStore.activeEdgeTooltip);
  let cardEl: HTMLDivElement | null = $state(null);

  $effect(() => {
    if (info && cardEl) {
      const ctx = gsap.context(() => {
        gsap.fromTo(
          cardEl,
          { opacity: 0, scale: 0.94, y: 8 },
          { opacity: 1, scale: 1, y: 0, duration: 0.22, ease: 'back.out(1.4)' }
        );
      });
      return () => ctx.revert();
    }
  });

  function getRingName(level: number | null): string {
    if (level === 0) return 'Ring 0: Domain Core';
    if (level === 1) return 'Ring 1: Application';
    if (level === 2) return 'Ring 2: Adapters';
    if (level === 3) return 'Ring 3: Frameworks';
    return 'Unassigned';
  }
</script>

{#if info}
  <!-- Floating Tooltip Container positioned near cursor/edge midpoint -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    role="tooltip"
    bind:this={cardEl}
    onmouseenter={() => diagramStore.cancelDismissEdgeTooltip()}
    onmouseleave={() => diagramStore.scheduleDismissEdgeTooltip(180)}
    style={`left: ${Math.min(window.innerWidth - 400, Math.max(20, info.x - 190))}px; top: ${info.y + 260 > window.innerHeight ? Math.max(20, info.y - 250) : info.y + 16}px;`}
    class="fixed z-50 w-96 rounded-xl bg-slate-900/95 border border-slate-700/90 shadow-2xl backdrop-blur-md p-4 text-xs font-sans select-none pointer-events-auto"
  >
    <!-- Header -->
    <div class="flex items-center justify-between pb-2 border-b border-slate-800">
      <div class="flex items-center gap-1.5">
        {#if info.edge.isViolating}
          <div class="flex items-center gap-1 text-rose-400 font-semibold font-mono text-[11px]">
            <AlertTriangle size={14} class="animate-pulse" />
            {info.isBundled ? `VIOLATING CORRIDOR (${info.violationCount || 1}/${info.bundleCount || 1} BREACHES)` : 'DEPENDENCY RULE VIOLATION'}
          </div>
        {:else}
          <div class="flex items-center gap-1 text-emerald-400 font-semibold font-mono text-[11px]">
            <ShieldCheck size={14} />
            {info.isBundled ? `CONFORMING CORRIDOR (${info.bundleCount || 1} DEPENDENCIES)` : 'CONFORMING DEPENDENCY'}
          </div>
        {/if}

        {#if diagramStore.isComparing && diagramStore.snapshotGraph}
          {@const wasInSnapshot = diagramStore.snapshotGraph.edges.find(e => e.from === info.edge.from && e.to === info.edge.to)}
          {#if info.edge.isViolating && !wasInSnapshot?.isViolating}
            <span class="px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300 font-mono text-[9px] font-bold border border-rose-500/40">
              NEW BREACH
            </span>
          {:else if !info.edge.isViolating && wasInSnapshot?.isViolating}
            <span class="px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-mono text-[9px] font-bold border border-emerald-500/40">
              RESOLVED
            </span>
          {/if}
        {/if}
      </div>
      <button
        onclick={() => diagramStore.clearEdgeTooltip()}
        class="text-slate-400 hover:text-white p-0.5 rounded transition-colors"
      >
        <X size={14} />
      </button>
    </div>

    <!-- Flow Diagram Nodes -->
    <div class="py-3 flex items-center justify-between gap-2 font-mono text-[11px]">
      <div class="flex-1 bg-slate-950/70 p-2 rounded border border-slate-800">
        <div class="text-slate-400 text-[9px] uppercase tracking-wider">{getRingName(info.fromLevel)}</div>
        <div class="font-bold text-slate-200 truncate mt-0.5">{info.fromLabel}</div>
      </div>

      <div class="flex flex-col items-center shrink-0">
        <ArrowRight size={16} class={info.edge.isViolating ? 'text-rose-500 animate-pulse' : 'text-blue-400'} />
        <span class="text-[9px] text-slate-500 mt-0.5">{info.edge.kind}</span>
      </div>

      <div class="flex-1 bg-slate-950/70 p-2 rounded border border-slate-800">
        <div class="text-slate-400 text-[9px] uppercase tracking-wider">{getRingName(info.toLevel)}</div>
        <div class="font-bold text-slate-200 truncate mt-0.5">{info.toLabel}</div>
      </div>
    </div>

    <!-- Rule Explanation -->
    <div class="space-y-2 text-[11px] leading-relaxed">
      {#if info.edge.isViolating}
        <p class="text-rose-200 bg-rose-950/40 p-2 rounded border border-rose-900/50">
          Source layer (<strong>{info.fromLabel}</strong>) points outward to an outer ring (<strong>{info.toLabel}</strong>).
          In Clean Architecture, inner rings must have no knowledge of outer rings.
        </p>
        <div class="flex items-start gap-1.5 text-amber-300/90 bg-amber-950/30 p-2 rounded border border-amber-900/40">
          <Lightbulb size={14} class="shrink-0 mt-0.5 text-amber-400" />
          <span>
            <strong>DIP Fix:</strong> Declare an Interface in <em>{info.fromLabel}</em>'s ring and implement it in <em>{info.toLabel}</em>.
          </span>
        </div>
      {:else}
        <p class="text-slate-300">
          Dependency flows inward toward the core domain entities (Level {info.fromLevel} &rarr; Level {info.toLevel}).
          This maintains stability and testability.
        </p>
      {/if}
    </div>
  </div>
{/if}
