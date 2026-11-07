<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { Terminal, ChevronUp, ChevronDown, Trash2, Cpu, CheckCircle2, AlertCircle, Play, ShieldCheck, RotateCcw, FileText } from '@lucide/svelte';

  let isOpen = $derived(diagramStore.isTelemetryDrawerOpen);
  let events = $derived(diagramStore.telemetryEvents);
  let activeSnapshotId = $derived(diagramStore.lastRefactorSnapshotId);
  let verificationResult = $derived(diagramStore.lastVerificationResult);
  let drawerEl: HTMLDivElement | null = $state(null);

  $effect(() => {
    if (drawerEl) {
      gsap.to(drawerEl, {
        height: isOpen ? 220 : 36,
        duration: 0.35,
        ease: 'power3.out'
      });
    }
  });

  function getBadgeClass(type: string): string {
    switch (type) {
      case 'SUCCESS': return 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50';
      case 'TASK': return 'text-sky-400 bg-sky-950/60 border-sky-800/50';
      case 'WARNING': return 'text-rose-400 bg-rose-950/60 border-rose-800/50';
      default: return 'text-slate-400 bg-slate-800/60 border-slate-700/50';
    }
  }
</script>

<!-- Collapsible Agent Telemetry Drawer -->
<div
  bind:this={drawerEl}
  class="absolute bottom-0 left-0 right-0 z-30 bg-slate-950/95 border-t border-slate-800 shadow-2xl backdrop-blur-md flex flex-col overflow-hidden font-mono select-none"
  style="height: 36px;"
>
  <!-- Bar Header -->
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    onclick={() => diagramStore.isTelemetryDrawerOpen = !diagramStore.isTelemetryDrawerOpen}
    class="h-9 px-4 flex items-center justify-between bg-slate-900/90 border-b border-slate-800/80 cursor-pointer hover:bg-slate-850 transition-colors"
  >
    <div class="flex items-center gap-2.5 text-xs text-slate-300">
      <div class="relative flex h-2 w-2">
        <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
        <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
      </div>
      <Terminal size={14} class="text-blue-400" />
      <span class="font-bold text-slate-100">Agent Mailbox Telemetry</span>
      <span class="text-[10px] text-slate-500">(.archlens/ IPC)</span>

      {#if !isOpen && events.length > 0}
        <span class="text-slate-500 mx-2">&bull;</span>
        <span class="text-[11px] text-slate-400 truncate max-w-md">
          [{events[0].timestamp}] {events[0].message}
        </span>
      {/if}
    </div>

    <div class="flex items-center gap-2">
      {#if activeSnapshotId}
        {#if verificationResult}
          <span
            class="px-2 py-0.5 rounded border text-[10px] font-bold {verificationResult.passed
              ? 'bg-emerald-950/60 border-emerald-700/60 text-emerald-300'
              : 'bg-rose-950/60 border-rose-700/60 text-rose-300'}"
            title={verificationResult.summary}
          >
            {verificationResult.passed
              ? `Verified (Δ -${verificationResult.violationDelta})`
              : `Verification Failed (${verificationResult.remainingViolations} violations)`}
          </span>
        {/if}

        <button
          onclick={(e) => {
            e.stopPropagation();
            diagramStore.verifyLastRefactoring();
          }}
          disabled={diagramStore.isVerifyingRefactor}
          class="flex items-center gap-1 px-2 py-0.5 rounded bg-sky-900/40 hover:bg-sky-800/60 border border-sky-700/50 text-sky-300 text-[10px] transition-colors cursor-pointer disabled:opacity-50"
          title="Re-compile AST and verify violation delta & zero ADP cycle regressions"
        >
          <ShieldCheck size={11} />
          <span>{diagramStore.isVerifyingRefactor ? 'Verifying...' : 'Verify Refactor'}</span>
        </button>

        {#if verificationResult?.passed && diagramStore.lastRefactorContext}
          <button
            onclick={(e) => {
              e.stopPropagation();
              diagramStore.syncVerifiedRefactorAdr();
            }}
            disabled={diagramStore.isSyncingAdr}
            class="flex items-center gap-1 px-2 py-0.5 rounded bg-indigo-900/40 hover:bg-indigo-800/60 border border-indigo-700/50 text-indigo-300 text-[10px] transition-colors cursor-pointer disabled:opacity-50"
            title="Mint a living Markdown ADR in docs/adr/ for this verified refactoring"
          >
            <FileText size={11} />
            <span>{diagramStore.lastSyncedAdrFilename ? diagramStore.lastSyncedAdrFilename : 'Record ADR'}</span>
          </button>
        {/if}

        <button
          onclick={(e) => {
            e.stopPropagation();
            diagramStore.rollbackLastRefactoring();
          }}
          disabled={diagramStore.isRollingBackRefactor}
          class="flex items-center gap-1 px-2 py-0.5 rounded bg-rose-900/40 hover:bg-rose-800/60 border border-rose-700/50 text-rose-300 text-[10px] transition-colors cursor-pointer disabled:opacity-50"
          title="1-click transactional rollback of source files and .archlens/policy.json to pre-refactor snapshot"
        >
          <RotateCcw size={11} />
          <span>{diagramStore.isRollingBackRefactor ? 'Reverting...' : 'Revert Snapshot'}</span>
        </button>
      {/if}

      {#if isOpen}
        <button
          onclick={(e) => {
            e.stopPropagation();
            diagramStore.triggerRegen();
          }}
          class="flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-900/40 hover:bg-emerald-800/60 border border-emerald-700/50 text-emerald-300 text-[10px] transition-colors"
          title="Dispatch refactoring task to agent"
        >
          <Play size={10} />
          <span>Dispatch Task</span>
        </button>

        <button
          onclick={(e) => {
            e.stopPropagation();
            diagramStore.telemetryEvents = [];
          }}
          class="p-1 text-slate-400 hover:text-slate-200 rounded transition-colors"
          title="Clear Log"
        >
          <Trash2 size={12} />
        </button>
      {/if}

      <div class="text-slate-400 p-1">
        {#if isOpen}
          <ChevronDown size={14} />
        {:else}
          <ChevronUp size={14} />
        {/if}
      </div>
    </div>
  </div>

  <!-- Expanded Event Log Stream -->
  <div class="flex-1 overflow-y-auto p-3 space-y-1 text-xs select-text">
    {#each events as evt (evt.id)}
      <div class="flex items-start gap-2.5 py-1 px-1.5 rounded hover:bg-slate-900/60 transition-colors">
        <span class="text-slate-500 text-[11px] shrink-0 select-none">[{evt.timestamp}]</span>
        <span class={`text-[9px] px-1.5 py-0.5 rounded border uppercase font-bold shrink-0 ${getBadgeClass(evt.type)}`}>
          {evt.type}
        </span>
        <div class="flex-1 min-w-0">
          <div class="text-slate-200 font-medium">{evt.message}</div>
          {#if evt.details}
            <div class="text-slate-400 text-[11px] mt-0.5">{evt.details}</div>
          {/if}
        </div>
      </div>
    {/each}

    {#if events.length === 0}
      <div class="text-slate-500 text-center py-6 italic text-[11px]">
        No mailbox telemetry events recorded.
      </div>
    {/if}
  </div>
</div>
