<script lang="ts">
  import { fly } from 'svelte/transition';
  import { diagramStore } from '../state/diagram.svelte';
  import { Layers, RefreshCw, Eye, Sparkles, FolderTree, Radio, Box, Search, X, AlertTriangle, Gauge, FlaskConical, Megaphone } from '@lucide/svelte';
  import { calculateComponentMartinMetrics } from '../utils/martinMetrics';
  import type { MartinMetrics } from '../types/diagram';

  let policy = $derived(diagramStore.policy);
  let proposals = $derived(policy?.proposals || []);
  let activeViolations = $derived(diagramStore.graph?.edges.filter((e) => e.isViolating) || []);

  let classSearchQuery = $state('');

  let focusedComponent = $derived(
    diagramStore.focusedNodeId && diagramStore.graph?.components
      ? diagramStore.graph.components.find((c) => c.id === diagramStore.focusedNodeId)
      : null
  );

  let focusedMetrics = $derived<MartinMetrics | null>(
    focusedComponent ? calculateComponentMartinMetrics(focusedComponent, diagramStore.graph?.edges || []) : null
  );

  let filteredFocusedClasses = $derived.by(() => {
    if (!focusedComponent?.classes) return [];
    const q = classSearchQuery.trim().toLowerCase();
    if (!q) return focusedComponent.classes;
    return focusedComponent.classes.filter(
      (cls) => cls.name.toLowerCase().includes(q) || cls.packageName.toLowerCase().includes(q)
    );
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

        <!-- Robert C. Martin Architectural Metrics Card -->
        {#if focusedMetrics}
          <div class="p-2.5 rounded-lg bg-slate-900/90 border border-slate-800 text-[10px] font-mono space-y-1.5">
            <div class="flex items-center justify-between text-slate-400 font-semibold uppercase tracking-wider text-[9px]">
              <span class="flex items-center gap-1 text-slate-300">
                <Gauge size={11} class="text-blue-400" />
                Martin Metrics
              </span>
              {#if focusedMetrics.zone === 'MAIN_SEQUENCE'}
                <span class="text-emerald-400 bg-emerald-950/60 px-1 py-0.2 rounded border border-emerald-500/30">Main Seq</span>
              {:else if focusedMetrics.zone === 'ZONE_OF_PAIN'}
                <span class="text-rose-400 bg-rose-950/60 px-1 py-0.2 rounded border border-rose-500/30">Zone of Pain</span>
              {:else}
                <span class="text-amber-400 bg-amber-950/60 px-1 py-0.2 rounded border border-amber-500/30">Zone of Uselessness</span>
              {/if}
            </div>

            <div class="grid grid-cols-4 gap-1 text-center pt-1 border-t border-slate-800/80">
              <div class="bg-slate-950/50 p-1 rounded" title="Afferent Coupling (Incoming dependencies from external classes)">
                <div class="text-slate-500 text-[8px]">$C_a$</div>
                <div class="font-bold text-slate-200">{focusedMetrics.ca}</div>
              </div>
              <div class="bg-slate-950/50 p-1 rounded" title="Efferent Coupling (Outgoing dependencies to external classes)">
                <div class="text-slate-500 text-[8px]">$C_e$</div>
                <div class="font-bold text-slate-200">{focusedMetrics.ce}</div>
              </div>
              <div class="bg-slate-950/50 p-1 rounded" title="Instability = Ce / (Ca + Ce)">
                <div class="text-slate-500 text-[8px]">$I$</div>
                <div class="font-bold text-amber-300">{focusedMetrics.instability.toFixed(2)}</div>
              </div>
              <div class="bg-slate-950/50 p-1 rounded" title="Normalized Distance from Main Sequence = |A + I - 1|">
                <div class="text-slate-500 text-[8px]">$D$</div>
                <div class="font-bold {focusedMetrics.distance <= 0.25 ? 'text-emerald-400' : 'text-rose-400'}">
                  {focusedMetrics.distance.toFixed(2)}
                </div>
              </div>
            </div>
          </div>
        {/if}

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
            <div class="flex items-center gap-1 w-full">
              <button
                onclick={() => diagramStore.selectedClass = cls}
                class="flex-1 text-left px-2 py-1.5 rounded bg-slate-900/90 hover:bg-blue-950/40 border border-slate-800 hover:border-blue-500/40 flex items-center justify-between transition-colors cursor-pointer group min-w-0"
              >
                <div class="flex items-center gap-1.5 truncate pr-2">
                  <span class="text-[11px] font-mono text-slate-300 group-hover:text-blue-200 truncate">
                    {cls.name}
                  </span>
                  {#if diagramStore.stagedClassMoves.has(cls.id)}
                    <span class="text-[8px] font-mono font-bold px-1 rounded bg-amber-500/20 text-amber-300 shrink-0">
                      Staged
                    </span>
                  {/if}
                </div>
                <div class="flex items-center gap-1.5 shrink-0">
                  <span class="text-[8px] font-mono font-bold px-1 rounded bg-slate-800 text-slate-400">
                    CRAP {Math.round(cls.crap?.mu ?? 0)}
                  </span>
                  <span class="w-1.5 h-1.5 rounded-full" style={`background-color: ${cls.coverage >= 0.8 ? '#10b981' : cls.coverage >= 0.5 ? '#f59e0b' : '#ef4444'}`}></span>
                </div>
              </button>
            </div>
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

    <!-- Screaming Architecture & Domain Cohesion (Uncle Bob Chapter 21) -->
    {#if diagramStore.screamingMetric}
      {@const sm = diagramStore.screamingMetric}
      <div class="p-3 rounded-lg bg-slate-950/70 border border-slate-800 space-y-2.5 text-xs">
        <div class="flex items-center justify-between text-slate-200 font-semibold text-[11px]">
          <span class="flex items-center gap-1.5 font-mono">
            <Megaphone size={13} class="text-amber-400" />
            Screaming Arch (Ch. 21)
          </span>
          <span
            class="text-[9px] font-mono px-1.5 py-0.5 rounded font-semibold border {sm.classification === 'PACKAGE_BY_FEATURE' ? 'bg-emerald-950/60 border-emerald-500/40 text-emerald-300' : sm.classification === 'HYBRID' ? 'bg-amber-950/60 border-amber-500/40 text-amber-300' : 'bg-rose-950/60 border-rose-500/40 text-rose-300'}"
          >
            {sm.classification === 'PACKAGE_BY_FEATURE' ? 'Feature-First' : sm.classification === 'HYBRID' ? 'Hybrid' : 'Layer-Heavy'}
          </span>
        </div>

        <!-- Score Bar -->
        <div class="space-y-1">
          <div class="flex justify-between items-center text-[10px] font-mono text-slate-400">
            <span>Score (SAS)</span>
            <span class="font-bold text-slate-200">{(sm.score * 100).toFixed(0)}%</span>
          </div>
          <div class="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-500 {sm.score >= 0.75 ? 'bg-emerald-500' : sm.score >= 0.40 ? 'bg-amber-500' : 'bg-rose-500'}"
              style="width: {Math.max(4, Math.round(sm.score * 100))}%"
            ></div>
          </div>
        </div>

        <!-- Package Breakdown -->
        <div class="grid grid-cols-2 gap-1.5 text-[10px] font-mono pt-1 border-t border-slate-800/80">
          <div class="bg-slate-900/80 p-1.5 rounded flex items-center justify-between" title="Domain / Feature Packages">
            <span class="text-slate-400">Domain</span>
            <span class="font-bold text-emerald-400">{sm.domainPackageCount}</span>
          </div>
          <div class="bg-slate-900/80 p-1.5 rounded flex items-center justify-between" title="Technical / Framework Packages">
            <span class="text-slate-400">Technical</span>
            <span class="font-bold text-rose-400">{sm.technicalPackageCount}</span>
          </div>
        </div>

        <!-- Framework Gravity Alerts -->
        {#if sm.frameworkGravityWarnings && sm.frameworkGravityWarnings.length > 0}
          <div class="p-2 rounded bg-amber-950/30 border border-amber-900/40 text-[10px] font-mono text-amber-300/90 space-y-1">
            <div class="font-semibold flex items-center gap-1 text-[9px] uppercase tracking-wider text-amber-400">
              <AlertTriangle size={10} />
              Framework Gravity
            </div>
            {#each sm.frameworkGravityWarnings.slice(0, 3) as w}
              <div class="truncate text-[9px] text-amber-200/80" title={w}>&bull; {w}</div>
            {/each}
          </div>
        {/if}

        <!-- Package-by-Feature Migration Wizard Assistant -->
        {#if sm.technicalPackageCount > 0}
          <div class="pt-2 border-t border-slate-800/80 space-y-2">
            <button
              type="button"
              onclick={async () => {
                const prop = await diagramStore.loadScreamingMigrationProposal();
                if (prop) {
                  diagramStore.applyScreamingMigrationToSandbox(prop);
                }
              }}
              disabled={diagramStore.isLoadingScreamingMigration}
              class="w-full flex items-center justify-center gap-1.5 py-1.5 px-2 rounded bg-gradient-to-r from-amber-600/30 to-purple-600/30 hover:from-amber-600/50 hover:to-purple-600/50 border border-amber-500/40 text-amber-200 text-[10px] font-mono font-semibold transition-all shadow-sm hover:shadow cursor-pointer disabled:opacity-50"
              title="Cluster classes across technical layers into package-by-feature domain slices (Uncle Bob Ch. 21)"
            >
              {#if diagramStore.isLoadingScreamingMigration}
                <RefreshCw size={11} class="animate-spin text-amber-400" />
                <span>Synthesizing Features...</span>
              {:else}
                <Sparkles size={11} class="text-amber-400" />
                <span>Migrate to Features 🪄</span>
              {/if}
            </button>

            {#if diagramStore.screamingMigrationProposal}
              {@const prop = diagramStore.screamingMigrationProposal}
              <div class="p-2 rounded bg-slate-900/90 border border-amber-500/30 text-[9px] font-mono space-y-1">
                <div class="flex items-center justify-between text-amber-300 font-semibold">
                  <span>Projected SAS Gain</span>
                  <span class="text-emerald-400">{(prop.currentScore * 100).toFixed(0)}% &rarr; {(prop.projectedScore * 100).toFixed(0)}%</span>
                </div>
                <div class="text-slate-400">
                  {prop.clusters.length} domain feature clusters ({Object.keys(prop.stagedClassMoves).length} staged moves)
                </div>
                {#if prop.clusters.length > 0}
                  <div class="space-y-0.5 pt-1 border-t border-slate-800 max-h-24 overflow-y-auto pr-0.5">
                    {#each prop.clusters as c}
                      <div class="flex items-center justify-between text-slate-300">
                        <span class="truncate text-amber-200 font-medium" title={c.proposedPackageName}>
                          &bull; {c.featureName}
                        </span>
                        <span class="text-slate-500 text-[8px]">{c.classCount} classes</span>
                      </div>
                    {/each}
                  </div>
                {/if}
              </div>
            {/if}
          </div>
        {/if}
      </div>
    {/if}

    <!-- Active Violations & Quick DIP Remediation -->
    {#if activeViolations.length > 0}
      <div class="p-3 rounded-lg bg-rose-950/30 border border-rose-900/50 space-y-2 text-xs">
        <div class="flex items-center justify-between text-rose-300 font-semibold text-[11px]">
          <span class="flex items-center gap-1.5 font-mono">
            <AlertTriangle size={13} class="animate-pulse text-rose-400" />
            Breaches ({activeViolations.length})
          </span>
          <span class="text-[9px] font-mono text-rose-400/90 bg-rose-900/40 px-1.5 py-0.5 rounded">
            DIP Fix
          </span>
        </div>
        <div class="space-y-1.5 max-h-36 overflow-y-auto pr-0.5">
          {#each activeViolations.slice(0, 6) as v (v.from + '->' + v.to)}
            <button
              onclick={() => diagramStore.openDipInversion(v.from, v.to)}
              class="w-full text-left p-1.5 rounded bg-slate-900/90 hover:bg-rose-950/60 border border-rose-900/30 hover:border-rose-500/50 flex items-center justify-between transition-colors cursor-pointer group"
              title={`Invert violation: ${v.from} -> ${v.to}`}
            >
              <div class="min-w-0 pr-1">
                <div class="text-[10px] font-mono text-slate-200 group-hover:text-rose-200 truncate">
                  {v.from.split('.').pop()} ➔ {v.to.split('.').pop()}
                </div>
              </div>
              <span class="text-[9px] font-mono text-rose-400 group-hover:text-white shrink-0 flex items-center gap-0.5 font-semibold">
                ⚡ Invert
              </span>
            </button>
          {/each}
        </div>
      </div>
    {/if}

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
        transition:fly={{ y: -8, duration: 220 }}
        class="p-2.5 rounded-lg bg-blue-500/15 border border-blue-500/30 text-blue-300 text-[11px] font-mono flex items-start gap-2 shadow-sm"
      >
        <Radio size={14} class="text-blue-400 shrink-0 mt-0.5 animate-pulse" />
        <span>{diagramStore.regenNotice}</span>
      </div>
    {/if}
    <button
      disabled={diagramStore.isRegenerating}
      onclick={() => diagramStore.triggerRegen()}
      class="w-full py-2.5 px-3 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700/80 text-white font-medium text-xs flex items-center justify-center gap-2 shadow-lg shadow-emerald-950/50 transition-all cursor-pointer disabled:cursor-not-allowed {diagramStore.isRegenerating ? 'ring-2 ring-emerald-400 ring-offset-2 ring-offset-slate-900 shadow-emerald-500/40 shadow-xl animate-pulse' : ''}"
    >
      <RefreshCw size={14} class={diagramStore.isRegenerating ? 'animate-spin' : ''} />
      {diagramStore.isRegenerating ? 'Agent Synthesizing Code...' : 'Regen (Wake Agent)'}
    </button>
  </div>
</div>
