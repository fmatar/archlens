<script lang="ts">
  import { diagramStore } from '../state/diagram.svelte';
  import { Layers, RefreshCw, Eye, Sparkles, FolderTree } from '@lucide/svelte';

  let policy = $derived(diagramStore.policy);
  let proposals = $derived(policy?.proposals || []);
</script>

<div class="w-72 bg-slate-900/90 backdrop-blur border-l border-slate-800 flex flex-col h-full select-none">
  <!-- Header -->
  <div class="p-4 border-b border-slate-800">
    <h2 class="text-sm font-bold text-slate-100 uppercase tracking-wider flex items-center gap-2">
      <Layers size={16} class="text-blue-400" />
      Inspector
    </h2>
  </div>

  <!-- Content -->
  <div class="flex-1 overflow-y-auto p-4 space-y-6">
    <!-- Real Diagram vs Proposals -->
    <div>
      <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Architectural Views</div>
      <div class="space-y-1">
        <!-- Real Diagram -->
        <button
          onclick={() => diagramStore.loadGraph(null)}
          class={`w-full text-left px-3 py-2 rounded-lg text-xs font-medium flex items-center justify-between transition-colors ${
            !diagramStore.activeProposalId
              ? 'bg-blue-600/30 text-blue-300 border border-blue-500/50'
              : 'hover:bg-slate-800 text-slate-300'
          }`}
        >
          <span class="flex items-center gap-2">
            <FolderTree size={14} />
            Real Diagram (Tree)
          </span>
          <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400">Live</span>
        </button>

        <!-- Proposals List -->
        {#each proposals as p}
          <button
            onclick={() => diagramStore.loadGraph(p.id)}
            class={`w-full text-left px-3 py-2 rounded-lg text-xs font-medium flex items-center justify-between transition-colors ${
              diagramStore.activeProposalId === p.id
                ? 'bg-amber-500/30 text-amber-300 border border-amber-500/50'
                : 'hover:bg-slate-800 text-slate-300'
            }`}
          >
            <span class="flex items-center gap-2">
              <Sparkles size={14} class="text-amber-400" />
              {p.name}
            </span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300">Proposal</span>
          </button>
        {/each}
      </div>
    </div>

    <!-- Decluttering Controls -->
    <div>
      <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Display Controls</div>
      <button
        onclick={() => diagramStore.cycleDeclutter()}
        class="w-full px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-750 text-slate-200 text-xs font-medium flex items-center justify-between border border-slate-700 transition-colors"
      >
        <span class="flex items-center gap-2">
          <Eye size={14} class="text-slate-400" />
          Declutter
        </span>
        <span class="text-[10px] font-mono uppercase text-blue-400 bg-slate-900 px-2 py-0.5 rounded">
          {diagramStore.declutterMode}
        </span>
      </button>
    </div>

    <!-- Clean Architecture Legend -->
    <div class="p-3 rounded-lg bg-slate-950/60 border border-slate-800/80 space-y-2 text-xs">
      <div class="font-semibold text-slate-300 text-[11px]">Dependency Rule Guide</div>
      <div class="flex items-center gap-2 text-slate-400 text-[10px]">
        <div class="w-3 h-0.5 bg-slate-500"></div>
        <span>Valid (Outer &rarr; Inner)</span>
      </div>
      <div class="flex items-center gap-2 text-rose-400 text-[10px]">
        <div class="w-3 h-0.5 bg-rose-500"></div>
        <span>Violating (Inner &rarr; Outer)</span>
      </div>
      <div class="flex items-center gap-2 text-slate-400 text-[10px]">
        <div class="w-2.5 h-2.5 rounded-full bg-emerald-500"></div>
        <span>CRAP / Mutation Healthy</span>
      </div>
    </div>
  </div>

  <!-- Regen Button -->
  <div class="p-4 border-t border-slate-800 bg-slate-900 space-y-2">
    {#if diagramStore.regenNotice}
      <div class="p-2 rounded bg-blue-500/20 border border-blue-500/40 text-blue-300 text-[11px] font-mono animate-in fade-in duration-150">
        {diagramStore.regenNotice}
      </div>
    {/if}
    <button
      disabled={diagramStore.isRegenerating}
      onclick={() => diagramStore.triggerRegen()}
      class="w-full py-2 px-3 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 text-white font-medium text-xs flex items-center justify-center gap-2 shadow-lg shadow-emerald-950/50 transition-colors"
    >
      <RefreshCw size={14} class={diagramStore.isRegenerating ? 'animate-spin' : ''} />
      {diagramStore.isRegenerating ? 'Regenerating...' : 'Regen (Wake Agent)'}
    </button>
  </div>
</div>
