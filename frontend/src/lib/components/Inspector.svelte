<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { Layers, RefreshCw, Eye, Sparkles, FolderTree, Radio, Box, Search, X } from '@lucide/svelte';

  let policy = $derived(diagramStore.policy);
  let proposals = $derived(policy?.proposals || []);

  let classSearchQuery = $state('');

  let focusedComponent = $derived(
    diagramStore.focusedNodeId && diagramStore.graph?.components
      ? diagramStore.graph.components.find((c) => c.id === diagramStore.focusedNodeId)
      : null
  );

  let filteredFocusedClasses = $derived.by(() => {
    if (!focusedComponent?.classes) return [];
    const q = classSearchQuery.trim().toLowerCase();
    if (!q) return focusedComponent.classes;
    return focusedComponent.classes.filter(
      (cls) => cls.name.toLowerCase().includes(q) || cls.packageName.toLowerCase().includes(q)
    );
  });

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
    <!-- Focused Component Class Inventory (Triggered by clicking node or inspect ↗) -->
    {#if focusedComponent}
      <div class="p-3.5 rounded-xl bg-slate-950/80 border border-blue-500/30 space-y-3 shadow-lg shadow-blue-950/30 animate-in fade-in duration-150">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 min-w-0">
            <Box size={15} class="text-blue-400 shrink-0" />
            <span class="text-xs font-semibold text-slate-100 font-mono truncate">{focusedComponent.label}</span>
          </div>
          <div class="flex items-center gap-1.5 shrink-0">
            <span class="text-[9px] px-1.5 py-0.5 rounded font-mono bg-blue-500/15 border border-blue-500/30 text-blue-300">
              Ring {focusedComponent.level ?? '?'}
            </span>
            <button
              onclick={() => {
                diagramStore.setFocusedNode(null);
                classSearchQuery = '';
              }}
              class="p-0.5 rounded text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
              title="Clear focus"
              aria-label="Clear focus"
            >
              <X size={13} />
            </button>
          </div>
        </div>

        <!-- Class Search Input -->
        <div class="relative flex items-center">
          <Search size={12} class="absolute left-2.5 text-slate-500" />
          <input
            type="text"
            bind:value={classSearchQuery}
            placeholder={`Filter ${focusedComponent.classes.length} classes...`}
            class="w-full bg-slate-900 border border-slate-700/80 focus:border-blue-500 rounded px-2 py-1 pl-7 pr-16 text-[11px] font-mono text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
          {#if classSearchQuery.trim()}
            <div class="absolute right-1.5 flex items-center gap-1">
              <span class="text-[9px] font-mono text-slate-400 bg-slate-800 px-1 rounded">
                {filteredFocusedClasses.length}/{focusedComponent.classes.length}
              </span>
              <button
                type="button"
                onclick={() => classSearchQuery = ''}
                class="p-0.5 rounded hover:bg-slate-700 text-slate-400 hover:text-white transition-colors cursor-pointer"
                title="Clear filter"
              >
                <X size={11} />
              </button>
            </div>
          {/if}
        </div>

        <!-- Scrollable Class List -->
        <div class="space-y-1 max-h-48 overflow-y-auto pr-0.5">
          {#each filteredFocusedClasses as cls (cls.id)}
            <button
              onclick={() => diagramStore.selectedClass = cls}
              class="w-full text-left px-2 py-1.5 rounded bg-slate-900/90 hover:bg-blue-950/40 border border-slate-800 hover:border-blue-500/40 flex items-center justify-between transition-colors cursor-pointer group"
            >
              <span class="text-[11px] font-mono text-slate-300 group-hover:text-blue-200 truncate pr-2">
                {cls.name}
              </span>
              <div class="flex items-center gap-1.5 shrink-0">
                <span class="text-[8px] font-mono font-bold px-1 rounded bg-slate-800 text-slate-400">
                  CRAP {Math.round(cls.crap?.mu ?? 0)}
                </span>
                <span class="w-1.5 h-1.5 rounded-full" style={`background-color: ${cls.coverage >= 0.8 ? '#10b981' : cls.coverage >= 0.5 ? '#f59e0b' : '#ef4444'}`}></span>
              </div>
            </button>
          {/each}
          {#if filteredFocusedClasses.length === 0}
            <div class="text-[10px] text-slate-500 italic text-center py-2">No matching classes</div>
          {/if}
        </div>
      </div>
    {/if}

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
      <div class="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Display & Triage Controls</div>
      <button
        onclick={() => diagramStore.cycleDeclutter()}
        class="w-full px-3 py-2 rounded-lg bg-slate-800/90 hover:bg-slate-750 text-slate-200 text-xs font-medium flex items-center justify-between border border-slate-700/80 transition-all hover:border-slate-600 mb-2.5"
      >
        <span class="flex items-center gap-2">
          <Eye size={14} class="text-slate-400" />
          Declutter
        </span>
        <span class="text-[10px] font-mono uppercase text-blue-400 bg-slate-900/80 px-2 py-0.5 rounded border border-slate-800">
          {diagramStore.declutterMode}
        </span>
      </button>

      <!-- Multi-Select Declutter Filter Matrix -->
      <div class="space-y-1.5">
        <!-- Edge Bundling Toggle -->
        <button
          onclick={() => diagramStore.toggleEdgeBundling()}
          class={`w-full px-2.5 py-1.5 rounded text-xs flex items-center justify-between transition-colors border ${
            diagramStore.isEdgeBundlingEnabled
              ? 'bg-blue-950/40 border-blue-600/50 text-blue-300'
              : 'bg-slate-900/40 border-slate-800/80 text-slate-400 hover:text-slate-200'
          }`}
        >
          <span class="flex items-center gap-1.5 font-mono text-[11px]">
            <span class={diagramStore.isEdgeBundlingEnabled ? 'text-blue-400 font-bold' : 'text-slate-600'}>
              {diagramStore.isEdgeBundlingEnabled ? '[✓]' : '[ ]'}
            </span>
            Edge Bundling (Corridors)
          </span>
          <span class="font-mono text-[9px] text-slate-500 bg-slate-800/60 px-1 rounded">B</span>
        </button>

        <!-- Violation X-Ray Toggle -->
        <button
          onclick={() => diagramStore.toggleDeclutterFilter('HIDE_CONFORMING_EDGES')}
          class={`w-full px-2.5 py-1.5 rounded text-xs flex items-center justify-between transition-colors border ${
            diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES')
              ? 'bg-rose-950/50 border-rose-600/60 text-rose-300 shadow-[0_0_12px_rgba(244,63,94,0.2)]'
              : 'bg-slate-900/40 border-slate-800/80 text-slate-400 hover:text-slate-200'
          }`}
        >
          <span class="flex items-center gap-1.5 font-mono text-[11px]">
            <span class={diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES') ? 'text-rose-400 font-bold' : 'text-slate-600'}>
              {diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES') ? '[✓]' : '[ ]'}
            </span>
            Violation X-Ray Mode
          </span>
          <span class="font-mono text-[9px] text-slate-500 bg-slate-800/60 px-1 rounded">V</span>
        </button>

        <!-- Compact Macro Cards Toggle -->
        <button
          onclick={() => diagramStore.toggleDeclutterFilter('HIDE_CLASSES')}
          class={`w-full px-2.5 py-1.5 rounded text-xs flex items-center justify-between transition-colors border ${
            diagramStore.hasDeclutterFilter('HIDE_CLASSES')
              ? 'bg-indigo-950/40 border-indigo-600/50 text-indigo-300'
              : 'bg-slate-900/40 border-slate-800/80 text-slate-400 hover:text-slate-200'
          }`}
        >
          <span class="flex items-center gap-1.5 font-mono text-[11px]">
            <span class={diagramStore.hasDeclutterFilter('HIDE_CLASSES') ? 'text-indigo-400 font-bold' : 'text-slate-600'}>
              {diagramStore.hasDeclutterFilter('HIDE_CLASSES') ? '[✓]' : '[ ]'}
            </span>
            Compact Macro Cards
          </span>
          <span class="font-mono text-[9px] text-slate-500 bg-slate-800/60 px-1 rounded">C</span>
        </button>

        <!-- 1-Hop Neighborhood Focus Toggle -->
        <button
          onclick={() => diagramStore.toggleDeclutterFilter('ISOLATE_NEIGHBORHOOD')}
          class={`w-full px-2.5 py-1.5 rounded text-xs flex items-center justify-between transition-colors border ${
            diagramStore.hasDeclutterFilter('ISOLATE_NEIGHBORHOOD')
              ? 'bg-emerald-950/40 border-emerald-600/50 text-emerald-300'
              : 'bg-slate-900/40 border-slate-800/80 text-slate-400 hover:text-slate-200'
          }`}
        >
          <span class="flex items-center gap-1.5 font-mono text-[11px]">
            <span class={diagramStore.hasDeclutterFilter('ISOLATE_NEIGHBORHOOD') ? 'text-emerald-400 font-bold' : 'text-slate-600'}>
              {diagramStore.hasDeclutterFilter('ISOLATE_NEIGHBORHOOD') ? '[✓]' : '[ ]'}
            </span>
            1-Hop Neighborhood Focus
          </span>
          <span class="font-mono text-[9px] text-slate-500 bg-slate-800/60 px-1 rounded">F</span>
        </button>

        <!-- Tier Lanes Toggle -->
        <button
          onclick={() => diagramStore.toggleDeclutterFilter('HIDE_TIER_LANES')}
          class={`w-full px-2.5 py-1.5 rounded text-xs flex items-center justify-between transition-colors border ${
            diagramStore.hasDeclutterFilter('HIDE_TIER_LANES')
              ? 'bg-purple-950/40 border-purple-600/50 text-purple-300'
              : 'bg-slate-900/40 border-slate-800/80 text-slate-400 hover:text-slate-200'
          }`}
        >
          <span class="flex items-center gap-1.5 font-mono text-[11px]">
            <span class={diagramStore.hasDeclutterFilter('HIDE_TIER_LANES') ? 'text-purple-400 font-bold' : 'text-slate-600'}>
              {diagramStore.hasDeclutterFilter('HIDE_TIER_LANES') ? '[✓]' : '[ ]'}
            </span>
            Hide Tier Backdrops
          </span>
          <span class="font-mono text-[9px] text-slate-500 bg-slate-800/60 px-1 rounded">T</span>
        </button>
      </div>
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
