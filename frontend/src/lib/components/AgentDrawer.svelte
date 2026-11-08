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
  let isConfirmingRollback = $state(false);

  $effect(() => {
    if (!activeSnapshotId) {
      isConfirmingRollback = false;
    }
  });

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
  <div
    class="h-9 px-4 flex items-center justify-between bg-slate-900/90 border-b border-slate-800/80 transition-colors"
  >
    <button
      type="button"
      aria-expanded={isOpen}
      onclick={() => diagramStore.isTelemetryDrawerOpen = !diagramStore.isTelemetryDrawerOpen}
      class="flex-1 min-w-0 h-full flex items-center gap-2.5 text-xs text-slate-300 text-left cursor-pointer hover:text-white transition-colors"
      title="Toggle Agent Mailbox Telemetry Drawer (T)"
    >
      <div class="relative flex h-2 w-2 shrink-0">
        <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
        <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
      </div>
      <Terminal size={14} class="text-blue-400 shrink-0" />
      <span class="font-bold text-slate-100 shrink-0">Agent Mailbox Telemetry</span>
      <span class="text-[10px] text-slate-400 shrink-0">(.archlens/ IPC)</span>
      <kbd class="px-1 py-0.5 rounded bg-slate-950 border border-slate-700 text-[9px] font-mono text-slate-400 shrink-0">T</kbd>

      {#if !isOpen && events.length > 0}
        <span class="text-slate-500 mx-1 shrink-0">&bull;</span>
        <span class="text-[11px] text-slate-400 truncate max-w-md">
          [{events[0].timestamp}] {events[0].message}
        </span>
      {/if}
    </button>

    <div class="flex items-center gap-2 shrink-0 ml-2">
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
          type="button"
          aria-pressed={diagramStore.runTestsOnVerify}
          onclick={() => {
            diagramStore.runTestsOnVerify = !diagramStore.runTestsOnVerify;
          }}
          class="min-h-6 flex items-center gap-1 px-2 py-0.5 rounded border text-[10px] cursor-pointer select-none transition-colors {diagramStore.runTestsOnVerify ? 'bg-sky-900/50 border-sky-500/70 text-sky-200' : 'bg-slate-800/80 border-slate-700/60 text-slate-400 hover:text-slate-200'}"
          title="Also execute the project's unit test suite (mvn test / npm test / pytest / go test / cargo test) during verification"
        >
          <span>{diagramStore.runTestsOnVerify ? '✓ + Tests' : '+ Tests'}</span>
        </button>

        <button
          type="button"
          onclick={() => {
            diagramStore.verifyLastRefactoring();
          }}
          disabled={diagramStore.isVerifyingRefactor}
          class="min-h-6 flex items-center gap-1 px-2 py-0.5 rounded bg-sky-900/40 hover:bg-sky-800/60 border border-sky-700/50 text-sky-300 text-[10px] transition-colors cursor-pointer disabled:opacity-50"
          title="Re-compile AST and verify violation delta, zero ADP cycle regressions, and optional test suite"
        >
          <ShieldCheck size={11} />
          <span>{diagramStore.isVerifyingRefactor ? 'Verifying...' : 'Verify Refactor'}</span>
        </button>

        {#if verificationResult?.passed && diagramStore.lastRefactorContext}
          <button
            type="button"
            onclick={() => {
              diagramStore.syncVerifiedRefactorAdr();
            }}
            disabled={diagramStore.isSyncingAdr}
            class="min-h-6 flex items-center gap-1 px-2 py-0.5 rounded bg-indigo-900/40 hover:bg-indigo-800/60 border border-indigo-700/50 text-indigo-300 text-[10px] transition-colors cursor-pointer disabled:opacity-50"
            title="Mint a living Markdown ADR in docs/adr/ for this verified refactoring"
          >
            <FileText size={11} />
            <span>{diagramStore.lastSyncedAdrFilename ? diagramStore.lastSyncedAdrFilename : 'Record ADR'}</span>
          </button>
        {/if}

        {#if isConfirmingRollback}
          <div class="flex items-center gap-1 bg-rose-950/80 border border-rose-500/60 rounded px-1.5 py-0.5 text-[10px]">
            <span class="text-rose-200 font-semibold">Revert files?</span>
            <button
              type="button"
              onclick={() => {
                isConfirmingRollback = false;
                diagramStore.rollbackLastRefactoring();
              }}
              disabled={diagramStore.isRollingBackRefactor}
              class="min-h-5 px-1.5 py-0.5 rounded bg-rose-600 hover:bg-rose-500 text-white font-bold cursor-pointer"
            >
              Yes, Revert
            </button>
            <button
              type="button"
              onclick={() => {
                isConfirmingRollback = false;
              }}
              class="min-h-5 px-1.5 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 cursor-pointer"
            >
              Cancel
            </button>
          </div>
        {:else}
          <button
            type="button"
            onclick={() => {
              isConfirmingRollback = true;
            }}
            disabled={diagramStore.isRollingBackRefactor}
            class="min-h-6 flex items-center gap-1 px-2 py-0.5 rounded bg-rose-900/40 hover:bg-rose-800/60 border border-rose-700/50 text-rose-300 text-[10px] transition-colors cursor-pointer disabled:opacity-50"
            title="Transactional rollback of source files and .archlens/policy.json to pre-refactor snapshot"
          >
            <RotateCcw size={11} />
            <span>{diagramStore.isRollingBackRefactor ? 'Reverting...' : 'Revert Snapshot'}</span>
          </button>
        {/if}
      {/if}

      {#if isOpen}
        <button
          type="button"
          onclick={() => {
            diagramStore.triggerRegen();
          }}
          class="min-h-6 flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-900/40 hover:bg-emerald-800/60 border border-emerald-700/50 text-emerald-300 text-[10px] transition-colors cursor-pointer"
          title="Dispatch refactoring task to agent"
        >
          <Play size={10} />
          <span>Dispatch Task</span>
        </button>

        <button
          type="button"
          onclick={() => {
            diagramStore.telemetryEvents = [];
          }}
          class="min-h-6 min-w-6 p-1 flex items-center justify-center text-slate-400 hover:text-slate-200 rounded transition-colors cursor-pointer"
          title="Clear Log"
          aria-label="Clear Telemetry Log"
        >
          <Trash2 size={12} />
        </button>
      {/if}

      <button
        type="button"
        aria-label={isOpen ? 'Collapse telemetry drawer' : 'Expand telemetry drawer'}
        onclick={() => diagramStore.isTelemetryDrawerOpen = !diagramStore.isTelemetryDrawerOpen}
        class="min-h-6 min-w-6 p-1 flex items-center justify-center text-slate-400 hover:text-white rounded transition-colors cursor-pointer"
      >
        {#if isOpen}
          <ChevronDown size={14} />
        {:else}
          <ChevronUp size={14} />
        {/if}
      </button>
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
