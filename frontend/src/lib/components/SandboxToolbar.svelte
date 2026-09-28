<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import {
    FlaskConical,
    RotateCcw,
    Send,
    Save,
    X,
    ChevronUp,
    ChevronDown,
    AlertTriangle,
    ShieldCheck,
    ArrowRight,
    Gauge,
    Check,
    Sparkles
  } from '@lucide/svelte';

  let isDetailsExpanded = $state<boolean>(false);
  let toolbarEl: HTMLElement | null = $state(null);

  let sim = $derived(diagramStore.activeSandboxSimulation);
  let stagedMoves = $derived(sim?.stagedMoves || []);
  let movesCount = $derived(stagedMoves.length);
  let violationDelta = $derived(sim?.violationDelta ?? 0);
  let baselineViolations = $derived(sim?.baselineViolations ?? 0);
  let simulatedViolations = $derived(sim?.simulatedViolations ?? 0);

  $effect(() => {
    if (diagramStore.isSandboxActive && toolbarEl) {
      gsap.fromTo(
        toolbarEl,
        { y: 30, opacity: 0, scale: 0.95 },
        { y: 0, opacity: 1, scale: 1, duration: 0.35, ease: 'back.out(1.4)' }
      );
    }
  });
</script>

{#if diagramStore.isSandboxActive}
  <aside
    bind:this={toolbarEl}
    aria-label="Architectural Sandbox Control Bar"
    class="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 w-11/12 max-w-4xl bg-slate-900/95 backdrop-blur-xl border border-amber-500/50 rounded-2xl shadow-2xl shadow-amber-950/40 p-3 flex flex-col gap-3 transition-all"
  >
    <!-- Top Action Bar -->
    <div class="flex items-center justify-between gap-3 flex-wrap">
      <!-- Left: Sandbox Mode Indicator & Badges -->
      <div class="flex items-center gap-2.5">
        <div class="flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/20 border border-amber-500/50 text-amber-300 text-xs font-semibold tracking-wide">
          <FlaskConical size={14} class="text-amber-400 animate-pulse" />
          <span>SANDBOX "WHAT-IF" SIMULATION</span>
        </div>

        <div class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800/90 border border-slate-700 text-xs font-mono text-slate-300">
          <span class="text-slate-400">Staged:</span>
          <span class="font-bold text-amber-300">{movesCount} {movesCount === 1 ? 'move' : 'moves'}</span>
        </div>

        <!-- Violation Delta Badge -->
        {#if movesCount > 0}
          {#if violationDelta < 0}
            <div class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-500/20 border border-emerald-500/50 text-xs font-mono text-emerald-300">
              <ShieldCheck size={13} class="text-emerald-400" />
              <span>{violationDelta} Violations ({baselineViolations} &rarr; {simulatedViolations})</span>
            </div>
          {:else if violationDelta > 0}
            <div class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-rose-500/20 border border-rose-500/50 text-xs font-mono text-rose-300">
              <AlertTriangle size={13} class="text-rose-400" />
              <span>+{violationDelta} Violations ({baselineViolations} &rarr; {simulatedViolations})</span>
            </div>
          {:else}
            <div class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700 text-xs font-mono text-slate-400">
              <span>{simulatedViolations} Violations (No &Delta;)</span>
            </div>
          {/if}
        {/if}
      </div>

      <!-- Right: Action Buttons -->
      <div class="flex items-center gap-2">
        <!-- Toggle Details Drawer -->
        <button
          onclick={() => isDetailsExpanded = !isDetailsExpanded}
          class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white text-xs transition-colors cursor-pointer"
          title="Toggle staged reassignments & Martin metrics drawer"
        >
          <Gauge size={13} class="text-blue-400" />
          <span>Metrics & Staged</span>
          {#if isDetailsExpanded}
            <ChevronDown size={14} />
          {:else}
            <ChevronUp size={14} />
          {/if}
        </button>

        {#if movesCount > 0}
          <!-- Reset Staged Moves -->
          <button
            onclick={() => diagramStore.resetSandbox()}
            class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white text-xs transition-colors cursor-pointer"
            title="Clear all staged moves"
          >
            <RotateCcw size={12} class="text-slate-400" />
            <span>Reset</span>
          </button>

          <!-- Save as Proposal -->
          <button
            onclick={() => diagramStore.saveSandboxAsProposal()}
            class="flex items-center gap-1 px-3 py-1 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium transition-colors shadow-sm cursor-pointer"
            title="Save current simulation as an Archlens proposal"
          >
            <Save size={12} />
            <span>Save Proposal</span>
          </button>

          <!-- Dispatch to AI Agent -->
          <button
            onclick={() => diagramStore.dispatchSandboxToAgent()}
            disabled={diagramStore.isDispatchingSandboxProposal}
            class="flex items-center gap-1 px-3 py-1 rounded-lg bg-amber-600 hover:bg-amber-500 disabled:opacity-50 text-white text-xs font-medium transition-colors shadow-sm cursor-pointer"
            title="Dispatch APPLY_PROPOSAL refactoring task to AI agent mailbox (.archlens/to-agent.json)"
          >
            <Send size={12} class={diagramStore.isDispatchingSandboxProposal ? 'animate-spin' : ''} />
            <span>{diagramStore.isDispatchingSandboxProposal ? 'Queueing...' : 'Dispatch to Agent'}</span>
          </button>
        {/if}

        <!-- Exit Sandbox -->
        <button
          onclick={() => diagramStore.exitSandbox()}
          class="p-1 rounded-lg bg-slate-800 hover:bg-rose-950/60 border border-slate-700 hover:border-rose-500/50 text-slate-400 hover:text-rose-300 transition-colors cursor-pointer"
          title="Exit Sandbox and restore active architecture tree"
          aria-label="Exit Sandbox"
        >
          <X size={15} />
        </button>
      </div>
    </div>

    <!-- Dispatch Notice Banner -->
    {#if diagramStore.sandboxDispatchNotice}
      <div class="px-3 py-1.5 rounded-lg bg-amber-950/80 border border-amber-500/50 text-amber-200 text-xs flex items-center justify-between">
        <div class="flex items-center gap-2">
          <Sparkles size={14} class="text-amber-400" />
          <span>{diagramStore.sandboxDispatchNotice}</span>
        </div>
      </div>
    {/if}

    <!-- Expandable Details Drawer: Staged Moves & Martin Metrics -->
    {#if isDetailsExpanded}
      <div class="pt-2 border-t border-slate-800 flex flex-col gap-3 max-h-72 overflow-y-auto pr-1">
        <!-- Staged Moves Section -->
        <div>
          <h4 class="text-[11px] font-semibold tracking-wider text-slate-400 uppercase mb-1.5 flex items-center gap-1.5">
            <span>Staged Class Reassignments</span>
            <span class="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-amber-300 font-mono">{movesCount}</span>
          </h4>

          {#if movesCount === 0}
            <div class="text-xs text-slate-400 italic py-2 px-3 bg-slate-950/50 rounded-lg border border-slate-800/80">
              No classes staged yet. Click on any class in the diagram or Inspector panel to reassign it to a different tier.
            </div>
          {:else}
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {#each stagedMoves as move}
                <div class="flex items-center justify-between gap-2 p-2 rounded-lg bg-slate-950/60 border border-slate-800 text-xs">
                  <div class="flex items-center gap-1.5 min-w-0">
                    <span class="font-mono font-medium text-slate-200 truncate">{move.className}</span>
                    <ArrowRight size={11} class="text-slate-400 shrink-0" />
                    <span class="px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 font-mono text-[10px] font-semibold shrink-0">
                      {move.targetComponentName}
                    </span>
                  </div>

                  <button
                    onclick={() => diagramStore.unstageClassMove(move.classId)}
                    class="p-0.5 rounded hover:bg-slate-800 text-slate-400 hover:text-rose-400 transition-colors cursor-pointer"
                    title="Remove staged move"
                  >
                    <X size={12} />
                  </button>
                </div>
              {/each}
            </div>
          {/if}
        </div>

        <!-- Robert C. Martin Architectural Metrics Section -->
        {#if sim && Object.keys(sim.componentMetrics).length > 0}
          <div>
            <h4 class="text-[11px] font-semibold tracking-wider text-slate-400 uppercase mb-1.5 flex items-center gap-1.5">
              <span>Robert C. Martin Coupling & Stability Metrics</span>
              <span class="text-[10px] text-slate-400 font-normal">($C_a, C_e, I, A, D$)</span>
            </h4>

            <div class="overflow-x-auto">
              <table class="w-full text-left text-xs font-mono border-collapse">
                <thead>
                  <tr class="border-b border-slate-800 text-slate-400 text-[10px]">
                    <th class="py-1 px-2">Component</th>
                    <th class="py-1 px-2" title="Afferent Coupling (Incoming dependencies from external components)">$C_a$</th>
                    <th class="py-1 px-2" title="Efferent Coupling (Outgoing dependencies to external components)">$C_e$</th>
                    <th class="py-1 px-2" title="Instability = Ce / (Ca + Ce)">$I$</th>
                    <th class="py-1 px-2" title="Abstractness = Interfaces / Total Classes">$A$</th>
                    <th class="py-1 px-2" title="Normalized Distance from Main Sequence = |A + I - 1|">$D$</th>
                    <th class="py-1 px-2">Zone</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-800/60">
                  {#each Object.entries(sim.componentMetrics) as [compId, metrics]}
                    <tr class="hover:bg-slate-800/40 transition-colors">
                      <td class="py-1 px-2 text-slate-200 font-sans font-medium">{compId}</td>
                      <td class="py-1 px-2 text-slate-300">{metrics.ca}</td>
                      <td class="py-1 px-2 text-slate-300">{metrics.ce}</td>
                      <td class="py-1 px-2 text-amber-300">{metrics.instability.toFixed(2)}</td>
                      <td class="py-1 px-2 text-blue-300">{metrics.abstractness.toFixed(2)}</td>
                      <td class="py-1 px-2 font-bold {metrics.distance <= 0.25 ? 'text-emerald-400' : 'text-rose-400'}">
                        {metrics.distance.toFixed(2)}
                      </td>
                      <td class="py-1 px-2 text-[10px] font-sans">
                        {#if metrics.zone === 'MAIN_SEQUENCE'}
                          <span class="px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-300">Main Sequence</span>
                        {:else if metrics.zone === 'ZONE_OF_PAIN'}
                          <span class="px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300">Zone of Pain</span>
                        {:else}
                          <span class="px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300">Zone of Uselessness</span>
                        {/if}
                      </td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          </div>
        {/if}
      </div>
    {/if}
  </aside>
{/if}
