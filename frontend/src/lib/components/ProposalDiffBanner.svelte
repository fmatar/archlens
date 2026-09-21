<script lang="ts">
  import { diagramStore } from '../state/diagram.svelte';
  import { Sparkles, ArrowLeftRight, CheckCircle2, ShieldAlert } from '@lucide/svelte';

  let graph = $derived(diagramStore.graph);
  let policy = $derived(diagramStore.policy);
  let activeProposal = $derived(
    policy?.proposals.find((p) => p.id === diagramStore.activeProposalId)
  );

  let violatingCount = $derived(
    graph?.edges.filter((e) => e.isViolating).length || 0
  );
</script>

{#if graph?.isProposal}
  <div class="absolute top-4 left-6 z-20 flex items-center gap-3 bg-slate-900/90 border border-amber-500/50 shadow-2xl rounded-xl px-4 py-2 backdrop-blur-md font-sans text-xs">
    <!-- Proposal Indicator -->
    <div class="flex items-center gap-2">
      <span class="relative flex h-2.5 w-2.5">
        <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
        <span class="relative inline-flex rounded-full h-2.5 w-2.5 bg-amber-500"></span>
      </span>
      <span class="font-bold text-amber-300 font-mono">
        WHAT-IF PROPOSAL: {activeProposal?.name || 'Clean Core'}
      </span>
    </div>

    <div class="h-4 w-px bg-slate-700"></div>

    <!-- Impact Diff Metric -->
    <div class="flex items-center gap-1.5 font-mono text-[11px]">
      {#if violatingCount === 0}
        <CheckCircle2 size={14} class="text-emerald-400" />
        <span class="text-emerald-300 font-medium">All Architectural Violations Resolved</span>
      {:else}
        <ShieldAlert size={14} class="text-rose-400" />
        <span class="text-rose-300 font-medium">{violatingCount} Violations Remaining</span>
      {/if}
    </div>

    <div class="h-4 w-px bg-slate-700"></div>

    <!-- Switch View Button -->
    <button
      onclick={() => diagramStore.loadGraph(null)}
      class="flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors cursor-pointer text-[11px]"
      title="Return to live code tree (Key: P)"
    >
      <ArrowLeftRight size={13} />
      <span>Compare Real (Live)</span>
    </button>
  </div>
{/if}
