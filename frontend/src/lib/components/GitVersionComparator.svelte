<script lang="ts">
  import { diagramStore } from '../state/diagram.svelte';
  import {
    GitCompare,
    CheckCircle2,
    AlertTriangle,
    PlusCircle,
    MinusCircle,
    X,
    ChevronDown,
    Layers,
    Sparkles
  } from '@lucide/svelte';

  let isOpen = $state(false);
  let dropdownRef: HTMLDivElement | null = $state(null);

  let isComparing = $derived(diagramStore.isComparing);
  let activeSnapshotId = $derived(diagramStore.comparisonTargetId);
  let snapshots = $derived(diagramStore.availableSnapshots);
  let diff = $derived(diagramStore.diffMetrics);

  let activeSnapshot = $derived(
    snapshots.find((s) => s.id === activeSnapshotId) || null
  );

  function toggleDropdown() {
    isOpen = !isOpen;
    if (isOpen && snapshots.length === 0) {
      diagramStore.loadSnapshots();
    }
  }

  function selectSnapshot(id: string | null) {
    isOpen = false;
    diagramStore.setComparisonTarget(id);
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape' && isOpen) {
      isOpen = false;
    }
  }
</script>

<svelte:window onkeydown={handleKeydown} />

<!-- Container with relative positioning for dropdown -->
<div class="relative flex items-center gap-2" bind:this={dropdownRef}>
  {#if !isComparing}
    <!-- Trigger Button when in Live Mode -->
    <button
      onclick={toggleDropdown}
      class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-800/90 hover:bg-slate-750 text-slate-300 hover:text-white border border-slate-700 hover:border-slate-600 transition-colors text-xs font-medium cursor-pointer shadow-sm"
      title="Compare architecture against Git tags and release snapshots (Shift+V)"
      aria-haspopup="true"
      aria-expanded={isOpen}
    >
      <GitCompare size={14} class="text-blue-400" />
      <span>Compare Release</span>
      <ChevronDown size={12} class="text-slate-400 transition-transform {isOpen ? 'rotate-180' : ''}" />
    </button>
  {:else}
    <!-- Active Comparison Diff Pill & Exit Button -->
    <div
      class="flex items-center gap-2 px-3 py-1 rounded-lg bg-blue-950/80 border border-blue-500/50 text-xs shadow-lg backdrop-blur-md animate-fade-in"
    >
      <div class="flex items-center gap-1.5 font-medium text-blue-200">
        <GitCompare size={13} class="text-blue-400" />
        <span class="text-slate-400 text-[11px]">vs</span>
        <span class="font-mono font-semibold text-white">{activeSnapshot?.label || activeSnapshotId}</span>
      </div>

      <div class="h-3.5 w-px bg-blue-700/60"></div>

      <!-- Diff Metric Badges -->
      <div class="flex items-center gap-1.5 font-mono text-[11px]">
        {#if diff}
          {#if diff.newViolations > 0}
            <span
              class="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-300 border border-rose-500/40 font-bold"
              title={`${diff.newViolations} new Clean Architecture rule breach(es)`}
            >
              <AlertTriangle size={11} class="animate-pulse" />
              +{diff.newViolations} breach{diff.newViolations === 1 ? '' : 'es'}
            </span>
          {/if}

          {#if diff.fixedViolations > 0}
            <span
              class="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-bold"
              title={`${diff.fixedViolations} resolved Clean Architecture breach(es)`}
            >
              <CheckCircle2 size={11} />
              ✓{diff.fixedViolations} fixed
            </span>
          {/if}

          {#if diff.addedNodes > 0}
            <span
              class="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-sky-500/20 text-sky-300 border border-sky-500/40"
              title={`${diff.addedNodes} component(s) added`}
            >
              <PlusCircle size={11} />
              +{diff.addedNodes} comp
            </span>
          {/if}

          {#if diff.removedNodes > 0}
            <span
              class="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700"
              title={`${diff.removedNodes} component(s) removed`}
            >
              <MinusCircle size={11} />
              -{diff.removedNodes}
            </span>
          {/if}

          {#if diff.newViolations === 0 && diff.fixedViolations === 0 && diff.addedNodes === 0 && diff.removedNodes === 0}
            <span class="flex items-center gap-1 text-emerald-400 font-sans text-xs">
              <Sparkles size={12} />
              Identical Topology
            </span>
          {/if}
        {/if}
      </div>

      <div class="h-3.5 w-px bg-blue-700/60"></div>

      <!-- Quick Switch Dropdown Trigger -->
      <button
        onclick={toggleDropdown}
        class="text-slate-400 hover:text-white p-0.5 rounded hover:bg-blue-900/40 transition-colors"
        title="Switch comparison target"
        aria-label="Switch comparison target"
      >
        <ChevronDown size={12} class="transition-transform {isOpen ? 'rotate-180' : ''}" />
      </button>

      <!-- Exit Comparison Button -->
      <button
        onclick={() => selectSnapshot(null)}
        class="flex items-center gap-1 px-1.5 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors text-[11px] font-medium"
        title="Exit comparison and return to live workspace (Esc)"
      >
        <X size={12} />
        <span>Exit</span>
      </button>
    </div>
  {/if}

  <!-- Dropdown Popover Menu -->
  {#if isOpen}
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
      class="fixed inset-0 z-40"
      onclick={() => (isOpen = false)}
    ></div>

    <div
      class="absolute top-full right-0 mt-1.5 w-80 rounded-xl bg-slate-900 border border-slate-700/90 shadow-2xl backdrop-blur-md p-2.5 z-50 text-xs font-sans select-none animate-in fade-in slide-in-from-top-1 duration-150"
    >
      <div class="flex items-center justify-between pb-2 mb-1.5 border-b border-slate-800 px-1.5">
        <div class="flex items-center gap-1.5 text-slate-200 font-semibold text-xs">
          <GitCompare size={14} class="text-blue-400" />
          <span>Git Release Comparator</span>
        </div>
        <span class="text-[10px] font-mono text-slate-400">Offline Cached</span>
      </div>

      <p class="text-[11px] text-slate-400 px-1.5 mb-2 leading-relaxed">
        Select a release snapshot or tag to visualize architectural evolution and dependency changes.
      </p>

      <div class="space-y-1 max-h-60 overflow-y-auto pr-1">
        <!-- Live Workspace Option -->
        <button
          onclick={() => selectSnapshot(null)}
          class="w-full text-left px-2.5 py-2 rounded-lg flex items-center justify-between transition-colors cursor-pointer {
            !isComparing
              ? 'bg-blue-600/20 text-blue-200 border border-blue-500/40 font-medium'
              : 'hover:bg-slate-800 text-slate-300 border border-transparent'
          }"
        >
          <div class="flex items-center gap-2 truncate">
            <span class="w-2 h-2 rounded-full {!isComparing ? 'bg-blue-400' : 'bg-slate-600'}"></span>
            <div class="truncate">
              <div class="font-medium text-slate-100">Live Workspace (Current AST)</div>
              <div class="text-[10px] text-slate-400 font-mono">Active codebase on disk</div>
            </div>
          </div>
          {#if !isComparing}
            <span class="text-[10px] font-mono font-bold text-blue-400">[ACTIVE]</span>
          {/if}
        </button>

        <!-- Pre-compiled Release Snapshots -->
        {#each snapshots as s (s.id)}
          {@const isSelected = activeSnapshotId === s.id}
          <button
            onclick={() => selectSnapshot(s.id)}
            class="w-full text-left px-2.5 py-2 rounded-lg flex items-center justify-between transition-colors cursor-pointer {
              isSelected
                ? 'bg-blue-600/20 text-blue-200 border border-blue-500/40 font-medium'
                : 'hover:bg-slate-800 text-slate-300 border border-transparent'
            }"
          >
            <div class="flex items-center gap-2 truncate">
              <Layers size={13} class={isSelected ? 'text-blue-400' : 'text-slate-500'} />
              <div class="truncate">
                <div class="font-mono text-slate-200 text-xs font-semibold">{s.label}</div>
                {#if s.date}
                  <div class="text-[10px] text-slate-500 font-mono">{s.date}</div>
                {:else}
                  <div class="text-[10px] text-slate-500 font-mono">Git Release Tag</div>
                {/if}
              </div>
            </div>
            {#if isSelected}
              <span class="text-[10px] font-mono font-bold text-blue-400">[COMPARING]</span>
            {/if}
          </button>
        {/each}
      </div>

      <div class="mt-2 pt-2 border-t border-slate-800/80 px-1.5 flex items-center justify-between text-[10px] text-slate-500 font-mono">
        <span>Snapshots: {snapshots.length} available</span>
        <span>Esc to close</span>
      </div>
    </div>
  {/if}
</div>
