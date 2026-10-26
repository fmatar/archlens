<script lang="ts">
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import type { ArchitecturePolicy } from '../types/diagram';
  import {
    X,
    Layers,
    Save,
    RotateCcw,
    Plus,
    Trash2,
    ChevronUp,
    ChevronDown,
    ShieldCheck,
    AlertCircle,
    CheckCircle2,
    Loader2,
    Sparkles,
    MoveRight,
    Sliders
  } from '@lucide/svelte';

  let isOpen = $derived(diagramStore.isPolicyEditorOpen);
  let isSaving = $derived(diagramStore.isSavingPolicy);
  let notice = $derived(diagramStore.policySaveNotice);

  let backdropEl: HTMLDivElement | null = $state(null);
  let dialogEl: HTMLDivElement | null = $state(null);
  let isClosing = $state(false);

  // Local working copy for editing
  let localLevels = $state<string[][]>([]);
  let localTitle = $state<string>('');
  let localPrefix = $state<string>('');
  let localSrc = $state<string>('');
  let newPkgName = $state<string>('');
  let targetTierForNew = $state<number>(0);

  // Tier metadata descriptor
  const TIER_META = [
    {
      name: 'Tier 0: Domain Core',
      role: 'Entities, Aggregates & Enterprise Rules',
      color: 'border-emerald-500/40 bg-emerald-950/20 text-emerald-400',
      badge: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
    },
    {
      name: 'Tier 1: Application Use Cases',
      role: 'Interactors, Ports & Domain Services',
      color: 'border-sky-500/40 bg-sky-950/20 text-sky-400',
      badge: 'bg-sky-500/20 text-sky-300 border-sky-500/40'
    },
    {
      name: 'Tier 2: Interface Adapters',
      role: 'Controllers, Gateways & Presenters',
      color: 'border-amber-500/40 bg-amber-950/20 text-amber-400',
      badge: 'bg-amber-500/20 text-amber-300 border-amber-500/40'
    },
    {
      name: 'Tier 3+: Frameworks & Infrastructure',
      role: 'REST Resources, Database, MCP & CLI Drivers',
      color: 'border-purple-500/40 bg-purple-950/20 text-purple-400',
      badge: 'bg-purple-500/20 text-purple-300 border-purple-500/40'
    }
  ];

  function getTierMeta(idx: number) {
    if (idx < TIER_META.length) return TIER_META[idx];
    return {
      name: `Tier ${idx}: Outer Extensions`,
      role: 'External Utilities & Third-Party Integrations',
      color: 'border-slate-600/40 bg-slate-900/30 text-slate-400',
      badge: 'bg-slate-700/30 text-slate-300 border-slate-600/40'
    };
  }

  // Synchronize local working state when modal opens
  $effect(() => {
    if (isOpen) {
      if (diagramStore.policy) {
        localLevels = diagramStore.policy.levels
          ? diagramStore.policy.levels.map((lvl) => [...lvl])
          : [['domain'], ['usecase'], ['adapter'], ['resource']];
        localTitle = diagramStore.policy.title || 'Clean Architecture';
        localPrefix = diagramStore.policy.prefix || '';
        localSrc = diagramStore.policy.src || 'backend/src/main/java';
      } else {
        localLevels = [['domain'], ['usecase'], ['adapter'], ['resource']];
        localTitle = 'Clean Architecture Policy';
        localPrefix = '';
        localSrc = 'backend/src/main/java';
      }
    }
  });

  // GSAP animation
  $effect(() => {
    if (isOpen && backdropEl && dialogEl && !isClosing) {
      const ctx = gsap.context(() => {
        gsap.fromTo(backdropEl, { opacity: 0 }, { opacity: 1, duration: 0.2, ease: 'power2.out' });
        gsap.fromTo(
          dialogEl,
          { scale: 0.94, y: 16, opacity: 0 },
          { scale: 1, y: 0, opacity: 1, duration: 0.3, ease: 'back.out(1.3)' }
        );
      });
      return () => ctx.revert();
    }
  });

  function handleClose() {
    if (isClosing || !dialogEl || !backdropEl) {
      diagramStore.closePolicyEditor();
      return;
    }
    isClosing = true;
    gsap.to(dialogEl, {
      scale: 0.95,
      y: 10,
      opacity: 0,
      duration: 0.18,
      ease: 'power2.in'
    });
    gsap.to(backdropEl, {
      opacity: 0,
      duration: 0.18,
      ease: 'power2.in',
      onComplete: () => {
        isClosing = false;
        diagramStore.closePolicyEditor();
      }
    });
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape' && isOpen) {
      handleClose();
    }
  }

  // All known packages in graph
  let discoveredPackages = $derived.by<string[]>(() => {
    if (!diagramStore.graph?.components) return [];
    return diagramStore.graph.components.map((c) => c.id).sort();
  });

  // Packages assigned across all localLevels
  let assignedPackages = $derived.by<Set<string>>(() => {
    const s = new Set<string>();
    for (const lvl of localLevels) {
      for (const p of lvl) {
        s.add(p);
      }
    }
    return s;
  });

  // Discovered packages that have not yet been assigned to any ring
  let unassignedPackages = $derived.by<string[]>(() => {
    return discoveredPackages.filter((p) => !assignedPackages.has(p));
  });

  function movePackage(pkg: string, fromLevel: number, toLevel: number) {
    if (toLevel < 0 || toLevel >= localLevels.length || fromLevel === toLevel) return;
    localLevels[fromLevel] = localLevels[fromLevel].filter((p) => p !== pkg);
    if (!localLevels[toLevel].includes(pkg)) {
      localLevels[toLevel] = [...localLevels[toLevel], pkg];
    }
  }

  function removePackage(pkg: string, levelIdx: number) {
    localLevels[levelIdx] = localLevels[levelIdx].filter((p) => p !== pkg);
  }

  function addPackageToTier(pkg: string, levelIdx: number) {
    const trimmed = pkg.trim();
    if (!trimmed) return;
    if (levelIdx < 0 || levelIdx >= localLevels.length) return;

    // Remove from other tiers if already present
    for (let i = 0; i < localLevels.length; i++) {
      localLevels[i] = localLevels[i].filter((p) => p !== trimmed);
    }
    localLevels[levelIdx] = [...localLevels[levelIdx], trimmed];
    newPkgName = '';
  }

  function addTier() {
    localLevels = [...localLevels, []];
  }

  function removeTier(idx: number) {
    if (localLevels.length <= 1) return;
    // Move any existing packages to adjacent lower tier if possible
    const pkgsToMove = localLevels[idx];
    const targetIdx = Math.max(0, idx - 1);
    const updated = localLevels.filter((_, i) => i !== idx);
    if (pkgsToMove.length > 0) {
      updated[targetIdx] = [...updated[targetIdx], ...pkgsToMove];
    }
    localLevels = updated;
  }

  function applyPresetCleanArchitecture() {
    localLevels = [
      ['domain'],
      ['scanner', 'engine', 'metrics', 'usecase'],
      ['adapter'],
      ['resource', 'mcp']
    ];
  }

  function resetToCurrent() {
    if (diagramStore.policy?.levels) {
      localLevels = diagramStore.policy.levels.map((lvl) => [...lvl]);
    }
  }

  async function handleSave() {
    const updatedPolicy: ArchitecturePolicy = {
      title: localTitle || diagramStore.policy?.title || 'Clean Architecture',
      src: localSrc || diagramStore.policy?.src || 'backend/src/main/java',
      prefix: localPrefix || diagramStore.policy?.prefix || '',
      hierarchical: diagramStore.policy?.hierarchical ?? true,
      levels: localLevels,
      order: localLevels.flat(),
      foreign: diagramStore.policy?.foreign || [],
      proposals: diagramStore.policy?.proposals || [],
      omit: diagramStore.policy?.omit || []
    };

    await diagramStore.savePolicy(updatedPolicy);
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if isOpen}
  <div
    bind:this={backdropEl}
    class="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 select-none"
    role="dialog"
    aria-modal="true"
    aria-label="Concentric Architecture Policy Designer"
  >
    <div
      bind:this={dialogEl}
      class="bg-slate-900 border border-slate-700/80 rounded-xl shadow-2xl shadow-slate-950 w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden text-slate-100"
    >
      <!-- Modal Header -->
      <div class="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-900/90 shrink-0">
        <div class="flex items-center gap-3">
          <div class="p-2 rounded-lg bg-blue-500/10 border border-blue-500/30 text-blue-400">
            <Sliders size={20} />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h2 class="text-base font-semibold tracking-tight text-white">Concentric Policy Designer</h2>
              <span class="text-[10px] font-mono px-2 py-0.5 rounded bg-blue-950/70 border border-blue-700/40 text-blue-300">
                .archlens/policy.json
              </span>
            </div>
            <p class="text-xs text-slate-400 mt-0.5">
              Reassign packages across concentric tiers adhering to Uncle Bob's Dependency Rule.
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <button
            onclick={applyPresetCleanArchitecture}
            class="flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white text-xs transition-colors cursor-pointer"
            title="Apply Standard 4-Tier Clean Architecture preset"
          >
            <Sparkles size={13} class="text-amber-400" />
            <span>Preset 4-Tier</span>
          </button>

          <button
            onclick={resetToCurrent}
            class="p-1.5 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
            title="Reset to currently loaded policy"
            aria-label="Reset policy"
          >
            <RotateCcw size={15} />
          </button>

          <button
            onclick={handleClose}
            class="p-1.5 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
            aria-label="Close modal"
          >
            <X size={18} />
          </button>
        </div>
      </div>

      <!-- Feedback / Notice Banner -->
      {#if notice}
        <div
          class="px-6 py-2.5 bg-blue-950/50 border-b border-blue-500/30 text-blue-200 text-xs flex items-center justify-between font-mono animate-in fade-in"
        >
          <div class="flex items-center gap-2">
            {#if isSaving}
              <Loader2 size={14} class="animate-spin text-blue-400" />
            {:else}
              <CheckCircle2 size={14} class="text-emerald-400" />
            {/if}
            <span>{notice}</span>
          </div>
        </div>
      {/if}

      <!-- Modal Body (Scrollable) -->
      <div class="p-6 overflow-y-auto flex-1 space-y-6">
        <!-- Policy Metadata Inputs -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-3 bg-slate-950/60 p-3.5 rounded-lg border border-slate-800 text-xs">
          <div>
            <label for="policy-title" class="block text-slate-400 font-mono text-[11px] mb-1">Architecture Title</label>
            <input
              id="policy-title"
              type="text"
              bind:value={localTitle}
              class="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-slate-200 focus:outline-none focus:border-blue-500 font-medium"
              placeholder="e.g. Archlens Workbench"
            />
          </div>
          <div>
            <label for="policy-src" class="block text-slate-400 font-mono text-[11px] mb-1">Source Path (src)</label>
            <input
              id="policy-src"
              type="text"
              bind:value={localSrc}
              class="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-slate-200 focus:outline-none focus:border-blue-500 font-medium font-mono text-[11px]"
              placeholder="e.g. backend/src/main/java"
            />
          </div>
          <div>
            <label for="policy-prefix" class="block text-slate-400 font-mono text-[11px] mb-1">Package Prefix</label>
            <input
              id="policy-prefix"
              type="text"
              bind:value={localPrefix}
              class="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-slate-200 focus:outline-none focus:border-blue-500 font-medium font-mono text-[11px]"
              placeholder="e.g. io.slixes.archlens"
            />
          </div>
        </div>

        <!-- Concentric Tiers Visual List -->
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Layers size={16} class="text-blue-400" />
              <h3 class="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Concentric Tiers (Inward Core &rarr; Outward Drivers)
              </h3>
            </div>
            <button
              onclick={addTier}
              class="flex items-center gap-1 text-xs text-blue-400 hover:text-blue-300 px-2 py-1 rounded bg-blue-950/40 border border-blue-800/40 hover:bg-blue-900/50 transition-colors cursor-pointer"
            >
              <Plus size={13} />
              <span>Add Outer Tier</span>
            </button>
          </div>

          <div class="space-y-3">
            {#each localLevels as tierPkgs, tierIdx}
              {@const meta = getTierMeta(tierIdx)}
              <div
                class={`rounded-lg border p-4 transition-all duration-200 bg-slate-950/40 ${meta.color}`}
              >
                <!-- Tier Header -->
                <div class="flex items-center justify-between mb-3">
                  <div class="flex items-center gap-2.5">
                    <span class={`text-[11px] font-mono px-2 py-0.5 rounded-full border font-semibold ${meta.badge}`}>
                      Level {tierIdx}
                    </span>
                    <div>
                      <h4 class="text-sm font-semibold text-white">{meta.name}</h4>
                      <p class="text-[11px] text-slate-400">{meta.role}</p>
                    </div>
                  </div>

                  <div class="flex items-center gap-1">
                    {#if tierIdx > 0}
                      <button
                        onclick={() => {
                          const temp = localLevels[tierIdx];
                          localLevels[tierIdx] = localLevels[tierIdx - 1];
                          localLevels[tierIdx - 1] = temp;
                        }}
                        class="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
                        title="Swap tier inward"
                      >
                        <ChevronUp size={14} />
                      </button>
                    {/if}
                    {#if tierIdx < localLevels.length - 1}
                      <button
                        onclick={() => {
                          const temp = localLevels[tierIdx];
                          localLevels[tierIdx] = localLevels[tierIdx + 1];
                          localLevels[tierIdx + 1] = temp;
                        }}
                        class="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
                        title="Swap tier outward"
                      >
                        <ChevronDown size={14} />
                      </button>
                    {/if}
                    {#if localLevels.length > 1}
                      <button
                        onclick={() => removeTier(tierIdx)}
                        class="p-1 rounded hover:bg-rose-950/60 text-slate-400 hover:text-rose-300 transition-colors cursor-pointer ml-1"
                        title="Delete tier"
                      >
                        <Trash2 size={13} />
                      </button>
                    {/if}
                  </div>
                </div>

                <!-- Package Chips within Tier -->
                <div class="flex flex-wrap gap-2 min-h-8 items-center bg-slate-900/60 p-2.5 rounded-md border border-slate-800/80">
                  {#if tierPkgs.length === 0}
                    <span class="text-xs text-slate-500 italic">No packages assigned to this tier yet.</span>
                  {/if}

                  {#each tierPkgs as pkg}
                    <div
                      class="flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 rounded bg-slate-800/90 border border-slate-700 text-slate-200 text-xs font-mono group hover:border-slate-600 transition-colors"
                    >
                      <span>{pkg}</span>
                      <div class="flex items-center gap-0.5 ml-1">
                        {#if tierIdx > 0}
                          <button
                            onclick={() => movePackage(pkg, tierIdx, tierIdx - 1)}
                            class="p-0.5 text-slate-400 hover:text-blue-300 rounded cursor-pointer"
                            title="Move inward to Level {tierIdx - 1}"
                          >
                            <ChevronUp size={11} />
                          </button>
                        {/if}
                        {#if tierIdx < localLevels.length - 1}
                          <button
                            onclick={() => movePackage(pkg, tierIdx, tierIdx + 1)}
                            class="p-0.5 text-slate-400 hover:text-blue-300 rounded cursor-pointer"
                            title="Move outward to Level {tierIdx + 1}"
                          >
                            <ChevronDown size={11} />
                          </button>
                        {/if}
                        <button
                          onclick={() => removePackage(pkg, tierIdx)}
                          class="p-0.5 text-slate-400 hover:text-rose-400 rounded cursor-pointer ml-0.5"
                          title="Remove package"
                        >
                          <X size={11} />
                        </button>
                      </div>
                    </div>
                  {/each}
                </div>
              </div>
            {/each}
          </div>
        </div>

        <!-- Add Custom Package or Assign Discovered Packages -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
          <!-- Manual Package Insertion -->
          <div class="bg-slate-950/60 p-4 rounded-lg border border-slate-800 flex flex-col justify-between">
            <div>
              <h4 class="text-xs font-semibold text-slate-200 mb-1">Add Package by Identifier</h4>
              <p class="text-[11px] text-slate-400 mb-3">Add a sub-package relative to the configured prefix.</p>
              <div class="flex gap-2">
                <input
                  type="text"
                  bind:value={newPkgName}
                  placeholder="e.g. domain.event"
                  class="flex-1 bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-slate-200 text-xs font-mono focus:outline-none focus:border-blue-500"
                  onkeydown={(e) => {
                    if (e.key === 'Enter') addPackageToTier(newPkgName, targetTierForNew);
                  }}
                />
                <select
                  bind:value={targetTierForNew}
                  class="bg-slate-900 border border-slate-700 rounded px-2 py-1 text-slate-200 text-xs cursor-pointer focus:outline-none focus:border-blue-500"
                >
                  {#each localLevels as _, idx}
                    <option value={idx}>Level {idx}</option>
                  {/each}
                </select>
              </div>
            </div>
            <button
              onclick={() => addPackageToTier(newPkgName, targetTierForNew)}
              disabled={!newPkgName.trim()}
              class="mt-3 flex items-center justify-center gap-1.5 px-3 py-1.5 rounded bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-medium transition-colors cursor-pointer"
            >
              <Plus size={13} />
              <span>Add to Tier</span>
            </button>
          </div>

          <!-- Unassigned Discovered Packages Pool -->
          <div class="bg-slate-950/60 p-4 rounded-lg border border-slate-800 flex flex-col justify-between">
            <div>
              <div class="flex items-center justify-between mb-1">
                <h4 class="text-xs font-semibold text-slate-200">Discovered Unassigned Packages</h4>
                <span class="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-300">
                  {unassignedPackages.length} unassigned
                </span>
              </div>
              <p class="text-[11px] text-slate-400 mb-2">Click a package to assign it to Level 0 (Domain) or choose tier.</p>
              
              <div class="flex flex-wrap gap-1.5 max-h-24 overflow-y-auto p-1 bg-slate-900/60 rounded border border-slate-800/80">
                {#if unassignedPackages.length === 0}
                  <span class="text-xs text-emerald-400/80 flex items-center gap-1 p-1">
                    <ShieldCheck size={12} />
                    All discovered packages are assigned to concentric tiers!
                  </span>
                {/if}
                {#each unassignedPackages as pkg}
                  <button
                    onclick={() => addPackageToTier(pkg, 0)}
                    class="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 hover:bg-blue-900/40 border border-slate-700 hover:border-blue-600/50 text-slate-300 hover:text-white transition-colors cursor-pointer flex items-center gap-1"
                    title="Click to assign to Level 0"
                  >
                    <span>{pkg}</span>
                    <Plus size={10} class="text-blue-400" />
                  </button>
                {/each}
              </div>
            </div>

            <div class="text-[11px] text-slate-500 mt-2 font-mono">
              Total assigned: {assignedPackages.size} packages across {localLevels.length} tiers
            </div>
          </div>
        </div>
      </div>

      <!-- Modal Footer -->
      <div class="px-6 py-3 border-t border-slate-800 bg-slate-900/90 flex items-center justify-between shrink-0">
        <div class="flex items-center gap-2 text-xs text-slate-400">
          <ShieldCheck size={14} class="text-emerald-400" />
          <span>Dependency Rule: Outer rings may depend inward; inner rings must never depend outward.</span>
        </div>

        <div class="flex items-center gap-2">
          <button
            onclick={handleClose}
            class="px-3.5 py-1.5 rounded-lg border border-slate-700 hover:bg-slate-800 text-slate-300 hover:text-white text-xs font-medium transition-colors cursor-pointer"
          >
            Cancel
          </button>

          <button
            onclick={handleSave}
            disabled={isSaving}
            class="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-medium transition-colors cursor-pointer shadow-md shadow-blue-950"
          >
            {#if isSaving}
              <Loader2 size={13} class="animate-spin" />
              <span>Saving & Recompiling...</span>
            {:else}
              <Save size={13} />
              <span>Save & Recompile Policy</span>
            {/if}
          </button>
        </div>
      </div>
    </div>
  </div>
{/if}
