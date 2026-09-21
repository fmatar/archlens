<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { Search, Box, Code2, Sparkles, FolderTree, RefreshCw, Eye, Maximize2, X } from '@lucide/svelte';
  import type { ComponentNode, ClassNode } from '../types/diagram';

  let isOpen = $derived(diagramStore.isCommandPaletteOpen);
  let searchQuery = $state('');
  let selectedIndex = $state(0);
  let dialogEl: HTMLDivElement | null = $state(null);
  let backdropEl: HTMLDivElement | null = $state(null);
  let inputEl: HTMLInputElement | null = $state(null);

  // Collect all searchable items
  interface PaletteItem {
    id: string;
    type: 'COMPONENT' | 'CLASS' | 'ACTION';
    title: string;
    subtitle: string;
    badge?: string;
    action: () => void;
  }

  let allItems = $derived.by(() => {
    const items: PaletteItem[] = [];
    const graph = diagramStore.graph;

    // 1. Actions
    items.push({
      id: 'act-real',
      type: 'ACTION',
      title: 'Switch to Real Diagram (Live Tree)',
      subtitle: 'View raw AST dependencies from filesystem',
      badge: 'Action',
      action: () => diagramStore.loadGraph(null)
    });

    if (diagramStore.policy?.proposals) {
      diagramStore.policy.proposals.forEach((p) => {
        items.push({
          id: `act-prop-${p.id}`,
          type: 'ACTION',
          title: `Switch to Proposal: ${p.name}`,
          subtitle: `What-If restructuring proposal`,
          badge: 'Proposal',
          action: () => diagramStore.loadGraph(p.id)
        });
      });
    }

    items.push({
      id: 'act-declutter',
      type: 'ACTION',
      title: `Cycle Declutter Mode (Current: ${diagramStore.declutterMode})`,
      subtitle: 'Toggle arrow and element clutter reduction',
      badge: 'View',
      action: () => diagramStore.cycleDeclutter()
    });

    items.push({
      id: 'act-reset-zoom',
      type: 'ACTION',
      title: 'Reset Zoom & Camera View',
      subtitle: 'Center workstation canvas at 100%',
      badge: 'View',
      action: () => diagramStore.resetZoom()
    });

    items.push({
      id: 'act-regen',
      type: 'ACTION',
      title: 'Wake AI Agent (Regen)',
      subtitle: 'Trigger background refactoring IPC task',
      badge: 'Agent',
      action: () => diagramStore.triggerRegen()
    });

    // 2. Components
    if (graph?.components) {
      graph.components.forEach((c: ComponentNode) => {
        items.push({
          id: `comp-${c.id}`,
          type: 'COMPONENT',
          title: c.label,
          subtitle: `Level ${c.level !== null ? c.level : 'Unassigned'} • ${c.classes.length} classes`,
          badge: `Ring ${c.level !== null ? c.level : '?'}`,
          action: () => diagramStore.panToComponent(c.id)
        });

        // 3. Classes
        c.classes.forEach((cls: ClassNode) => {
          items.push({
            id: `cls-${cls.id}`,
            type: 'CLASS',
            title: cls.name,
            subtitle: `${cls.packageName} (in ${c.label})`,
            badge: cls.stereotype,
            action: () => {
              diagramStore.panToComponent(c.id);
              diagramStore.selectedClass = cls;
            }
          });
        });
      });
    }

    return items;
  });

  let filteredItems = $derived.by(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return allItems.slice(0, 10);
    return allItems.filter(
      (item) => item.title.toLowerCase().includes(q) || item.subtitle.toLowerCase().includes(q)
    ).slice(0, 12);
  });

  $effect(() => {
    if (isOpen && dialogEl && backdropEl) {
      const ctx = gsap.context(() => {
        gsap.fromTo(backdropEl, { opacity: 0 }, { opacity: 1, duration: 0.2 });
        gsap.fromTo(
          dialogEl,
          { scale: 0.94, y: -20, opacity: 0 },
          { scale: 1, y: 0, opacity: 1, duration: 0.3, ease: 'back.out(1.4)' }
        );
      });
      setTimeout(() => inputEl?.focus(), 50);
      return () => ctx.revert();
    }
  });

  function closePalette() {
    diagramStore.isCommandPaletteOpen = false;
    searchQuery = '';
    selectedIndex = 0;
  }

  function handleKeydown(e: KeyboardEvent) {
    if (!isOpen) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      selectedIndex = (selectedIndex + 1) % Math.max(1, filteredItems.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      selectedIndex = (selectedIndex - 1 + filteredItems.length) % Math.max(1, filteredItems.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = filteredItems[selectedIndex];
      if (item) {
        item.action();
        closePalette();
      }
    } else if (e.key === 'Escape') {
      e.preventDefault();
      closePalette();
    }
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if isOpen}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    bind:this={backdropEl}
    onclick={closePalette}
    class="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-start justify-center pt-24 p-4 select-none"
  >
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div
      bind:this={dialogEl}
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      class="bg-slate-900 border border-slate-700/90 rounded-xl shadow-2xl max-w-xl w-full flex flex-col overflow-hidden font-sans"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Search Input -->
      <div class="p-3.5 border-b border-slate-800 flex items-center gap-3 bg-slate-950/60">
        <Search size={18} class="text-blue-400 shrink-0" />
        <input
          bind:this={inputEl}
          bind:value={searchQuery}
          placeholder="Type to search classes, components, or actions..."
          class="bg-transparent text-slate-100 placeholder-slate-500 text-sm w-full outline-none font-mono"
        />
        <kbd class="px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono text-[10px] border border-slate-700">
          ESC
        </kbd>
      </div>

      <!-- Results List -->
      <div class="max-h-80 overflow-y-auto p-2 space-y-1">
        {#each filteredItems as item, idx}
          {@const isSelected = idx === selectedIndex}
          <!-- svelte-ignore a11y_click_events_have_key_events -->
          <!-- svelte-ignore a11y_no_static_element_interactions -->
          <div
            onclick={() => {
              item.action();
              closePalette();
            }}
            onmouseenter={() => selectedIndex = idx}
            class="px-3 py-2.5 rounded-lg flex items-center justify-between cursor-pointer transition-colors"
            class:palette-row-selected={isSelected}
            class:palette-row-idle={!isSelected}
          >
            <div class="flex items-center gap-2.5 min-w-0">
              {#if item.type === 'COMPONENT'}
                <Box size={16} class="text-sky-400 shrink-0" />
              {:else if item.type === 'CLASS'}
                <Code2 size={16} class="text-emerald-400 shrink-0" />
              {:else}
                <Sparkles size={16} class="text-amber-400 shrink-0" />
              {/if}
              <div class="truncate">
                <div class="font-medium text-xs font-mono">{item.title}</div>
                <div class="text-[11px] truncate" class:text-blue-200={isSelected} class:text-slate-400={!isSelected}>{item.subtitle}</div>
              </div>
            </div>

            {#if item.badge}
              <span
                class="text-[10px] px-2 py-0.5 rounded font-mono shrink-0 border"
                class:badge-selected={isSelected}
                class:badge-idle={!isSelected}
              >
                {item.badge}
              </span>
            {/if}
          </div>
        {/each}

        {#if filteredItems.length === 0}
          <div class="text-center py-8 text-slate-500 text-xs font-mono">
            No matches found for "{searchQuery}".
          </div>
        {/if}
      </div>

      <!-- Footer Quick Keys -->
      <div class="px-3.5 py-2 bg-slate-950/80 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-500 font-mono">
        <div class="flex items-center gap-3">
          <span>&uarr;&darr; Navigate</span>
          <span>&crarr; Select</span>
          <span>ESC Close</span>
        </div>
        <div>
          {filteredItems.length} results
        </div>
      </div>
    </div>
  </div>
{/if}

<style>
  .palette-row-selected {
    background-color: rgba(37, 99, 235, 0.25);
    border: 1px solid rgba(59, 130, 246, 0.5);
    color: #ffffff;
  }
  .palette-row-idle {
    color: #cbd5e1;
    border: 1px solid transparent;
  }
  .palette-row-idle:hover {
    background-color: rgba(30, 41, 59, 0.6);
  }
  .badge-selected {
    background-color: rgba(30, 58, 138, 0.5);
    color: #bfdbfe;
    border-color: rgba(59, 130, 246, 0.4);
  }
  .badge-idle {
    background-color: #1e293b;
    color: #cbd5e1;
    border-color: rgba(51, 65, 85, 0.8);
  }
</style>
