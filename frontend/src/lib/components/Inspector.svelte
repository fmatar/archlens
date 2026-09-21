<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { Layers, RefreshCw, Eye, Sparkles, FolderTree, Radio } from '@lucide/svelte';

  let policy = $derived(diagramStore.policy);
  let proposals = $derived(policy?.proposals || []);

  let noticeEl: HTMLDivElement | null = $state(null);
  let regenBtnEl: HTMLButtonElement | null = $state(null);

  $effect(() => {
    const isRegen = diagramStore.isRegenerating;
    const ctx = gsap.context(() => {
      if (isRegen && regenBtnEl) {
        gsap.to(regenBtnEl, {
          boxShadow: '0 0 22px rgba(16, 185, 129, 0.65)',
          scale: 1.02,
          duration: 0.75,
          repeat: -1,
          yoyo: true,
          ease: 'sine.inOut'
        });
      } else if (regenBtnEl) {
        gsap.to(regenBtnEl, {
          boxShadow: '0 0 0px rgba(0, 0, 0, 0)',
          scale: 1,
          duration: 0.3,
          ease: 'power2.out'
        });
      }
    });

    return () => ctx.revert();
  });

  $effect(() => {
    const notice = diagramStore.regenNotice;
    if (notice && noticeEl) {
      const ctx = gsap.context(() => {
        gsap.fromTo(
          noticeEl,
          { y: -10, opacity: 0, scale: 0.96 },
          { y: 0, opacity: 1, scale: 1, duration: 0.35, ease: 'back.out(1.5)' }
        );
      });
      return () => ctx.revert();
    }
  });
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
      <div class="space-y-1.5">
        <!-- Real Diagram -->
        <button
          onclick={() => diagramStore.loadGraph(null)}
          class={`w-full text-left px-3 py-2 rounded-lg text-xs font-medium flex items-center justify-between transition-all duration-200 ${
            !diagramStore.activeProposalId
              ? 'bg-blue-600/25 text-blue-200 border border-blue-500/50 shadow-sm shadow-blue-950'
              : 'hover:bg-slate-800/80 text-slate-400 hover:text-slate-200 border border-transparent'
          }`}
        >
          <span class="flex items-center gap-2">
            <FolderTree size={14} class={!diagramStore.activeProposalId ? 'text-blue-400' : 'text-slate-400'} />
            Real Diagram (Tree)
          </span>
          <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">Live</span>
        </button>

        <!-- Proposals List -->
        {#each proposals as p}
          <button
            onclick={() => diagramStore.loadGraph(p.id)}
            class={`w-full text-left px-3 py-2 rounded-lg text-xs font-medium flex items-center justify-between transition-all duration-200 ${
              diagramStore.activeProposalId === p.id
                ? 'bg-amber-500/25 text-amber-200 border border-amber-500/50 shadow-sm shadow-amber-950'
                : 'hover:bg-slate-800/80 text-slate-400 hover:text-slate-200 border border-transparent'
            }`}
          >
            <span class="flex items-center gap-2">
              <Sparkles size={14} class={diagramStore.activeProposalId === p.id ? 'text-amber-400' : 'text-slate-400'} />
              {p.name}
            </span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 font-mono">Proposal</span>
          </button>
        {/each}
      </div>
    </div>

    <!-- Decluttering Controls -->
    <div>
      <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Display Controls</div>
      <button
        onclick={() => diagramStore.cycleDeclutter()}
        class="w-full px-3 py-2 rounded-lg bg-slate-800/90 hover:bg-slate-750 text-slate-200 text-xs font-medium flex items-center justify-between border border-slate-700/80 transition-all hover:border-slate-600"
      >
        <span class="flex items-center gap-2">
          <Eye size={14} class="text-slate-400" />
          Declutter
        </span>
        <span class="text-[10px] font-mono uppercase text-blue-400 bg-slate-900/80 px-2 py-0.5 rounded border border-slate-800">
          {diagramStore.declutterMode}
        </span>
      </button>
    </div>

    <!-- Clean Architecture Legend -->
    <div class="p-3 rounded-lg bg-slate-950/60 border border-slate-800/80 space-y-2.5 text-xs">
      <div class="font-semibold text-slate-300 text-[11px] tracking-wide">Clean Architecture Rules</div>
      <div class="flex items-center gap-2 text-slate-300 text-[10px]">
        <div class="w-4 h-0.5 bg-sky-400 rounded-full"></div>
        <span>Valid (Inward Flow &rarr; Domain)</span>
      </div>
      <div class="flex items-center gap-2 text-rose-300 text-[10px]">
        <div class="w-4 h-1 bg-rose-500 rounded-full shadow-[0_0_8px_rgba(244,63,94,0.6)]"></div>
        <span>Violating (Outward Flow Breach)</span>
      </div>
      <div class="flex items-center gap-2 text-slate-400 text-[10px]">
        <div class="w-2.5 h-2.5 rounded-full bg-emerald-500"></div>
        <span>CRAP / Mutation Coverage Healthy</span>
      </div>
    </div>
  </div>

  <!-- Regen Button -->
  <div class="p-4 border-t border-slate-800 bg-slate-900 space-y-2">
    {#if diagramStore.regenNotice}
      <div
        bind:this={noticeEl}
        class="p-2.5 rounded-lg bg-blue-500/15 border border-blue-500/30 text-blue-300 text-[11px] font-mono flex items-start gap-2 shadow-sm"
      >
        <Radio size={14} class="text-blue-400 shrink-0 mt-0.5 animate-pulse" />
        <span>{diagramStore.regenNotice}</span>
      </div>
    {/if}
    <button
      bind:this={regenBtnEl}
      disabled={diagramStore.isRegenerating}
      onclick={() => diagramStore.triggerRegen()}
      class="w-full py-2.5 px-3 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700/80 text-white font-medium text-xs flex items-center justify-center gap-2 shadow-lg shadow-emerald-950/50 transition-all cursor-pointer disabled:cursor-not-allowed"
    >
      <RefreshCw size={14} class={diagramStore.isRegenerating ? 'animate-spin' : ''} />
      {diagramStore.isRegenerating ? 'Agent Synthesizing Code...' : 'Regen (Wake Agent)'}
    </button>
  </div>
</div>
