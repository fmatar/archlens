<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from './lib/state/diagram.svelte';
  import Canvas from './lib/components/Canvas.svelte';
  import Inspector from './lib/components/Inspector.svelte';
  import ClassCard from './lib/components/ClassCard.svelte';
  import SourceModal from './lib/components/SourceModal.svelte';
  import CommandPalette from './lib/components/CommandPalette.svelte';
  import OpenProjectModal from './lib/components/OpenProjectModal.svelte';
  import LlmPromptModal from './lib/components/LlmPromptModal.svelte';
  import { ShieldCheck, Network, AlertTriangle, Search, FolderOpen, Bot } from '@lucide/svelte';
  import type { DependencyEdge } from './lib/types/diagram';

  let badgeEl: HTMLDivElement | null = $state(null);

  onMount(async () => {
    await diagramStore.loadPolicy();
    await diagramStore.loadGraph();

    // SSE connection for live updates
    const eventSource = new EventSource('/api/events');
    eventSource.onmessage = () => {
      // Periodic heartbeat
    };
  });

  let violatingCount = $derived(
    diagramStore.graph?.edges.filter((e: DependencyEdge) => e.isViolating).length || 0
  );

  $effect(() => {
    const count = violatingCount;
    if (badgeEl) {
      const ctx = gsap.context(() => {
        if (count > 0) {
          gsap.fromTo(
            badgeEl,
            { scale: 0.9, y: -2 },
            { scale: 1, y: 0, duration: 0.45, ease: 'back.out(1.5)' }
          );
        } else {
          gsap.fromTo(
            badgeEl,
            { scale: 0.95 },
            { scale: 1, duration: 0.35, ease: 'back.out(1.4)' }
          );
        }
      });
      return () => ctx.revert();
    }
  });

  function handleGlobalKeydown(e: KeyboardEvent) {
    const target = e.target as HTMLElement;
    const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable;

    // Cmd+K / Ctrl+K / '/' for Command Palette
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      diagramStore.isCommandPaletteOpen = !diagramStore.isCommandPaletteOpen;
      return;
    }

    // Cmd+O / Ctrl+O for Open Project Folder
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'o') {
      e.preventDefault();
      diagramStore.isOpenProjectModalOpen = true;
      return;
    }

    if (isInput) return;

    if (e.key === '/') {
      e.preventDefault();
      diagramStore.isCommandPaletteOpen = true;
    } else if (e.key.toLowerCase() === 'p') {
      e.preventDefault();
      if (diagramStore.activeProposalId) {
        diagramStore.loadGraph(null);
      } else if (diagramStore.policy?.proposals.length) {
        diagramStore.loadGraph(diagramStore.policy.proposals[0].id);
      }
    } else if (e.key.toLowerCase() === 'd') {
      e.preventDefault();
      diagramStore.cycleDeclutter();
    } else if (e.key.toLowerCase() === 'v') {
      e.preventDefault();
      diagramStore.toggleDeclutterFilter('HIDE_CONFORMING_EDGES');
    } else if (e.key.toLowerCase() === 'b') {
      e.preventDefault();
      diagramStore.toggleEdgeBundling();
    } else if (e.key.toLowerCase() === 'c') {
      e.preventDefault();
      diagramStore.toggleDeclutterFilter('HIDE_CLASSES');
    } else if (e.key.toLowerCase() === 'f') {
      e.preventDefault();
      diagramStore.toggleDeclutterFilter('ISOLATE_NEIGHBORHOOD');
    } else if (e.key.toLowerCase() === 't') {
      e.preventDefault();
      diagramStore.isTelemetryDrawerOpen = !diagramStore.isTelemetryDrawerOpen;
    } else if (e.key.toLowerCase() === 'l') {
      e.preventDefault();
      diagramStore.openLlmPromptModal();
    } else if (e.key === '0') {
      e.preventDefault();
      diagramStore.resetZoom();
    } else if (e.key === '+' || e.key === '=') {
      e.preventDefault();
      diagramStore.zoom = Math.min(3.0, diagramStore.zoom * 1.15);
    } else if (e.key === '-') {
      e.preventDefault();
      diagramStore.zoom = Math.max(0.2, diagramStore.zoom * 0.85);
    } else if (e.key === 'Escape') {
      diagramStore.setFocusedNode(null);
      diagramStore.activeEdgeTooltip = null;
    }
  }
</script>

<svelte:window onkeydown={handleGlobalKeydown} />

<div class="flex flex-col h-screen w-screen bg-slate-950 text-slate-100 overflow-hidden font-sans">
  <!-- Top App Navigation -->
  <header class="h-12 bg-slate-900 border-b border-slate-800 px-4 flex items-center justify-between z-30 select-none">
    <div class="flex items-center gap-3">
      <div class="flex items-center gap-2 font-semibold text-sm tracking-tight text-slate-100">
        <Network size={18} class="text-blue-400" />
        Clean Architecture Workbench
      </div>
      <!-- Project Switcher -->
      <div class="flex items-center gap-1.5">
        <select
          value={diagramStore.projectRoot}
          onchange={(e) => {
            const val = (e.target as HTMLSelectElement).value;
            if (val === '__OPEN_MODAL__') {
              diagramStore.isOpenProjectModalOpen = true;
              (e.target as HTMLSelectElement).value = diagramStore.projectRoot;
            } else {
              diagramStore.setProjectRoot(val);
            }
          }}
          class="bg-slate-800 border border-slate-700 hover:border-slate-600 text-slate-200 text-xs rounded px-2.5 py-1 font-medium focus:outline-none focus:ring-1 focus:ring-blue-500 cursor-pointer transition-colors max-w-xs truncate"
          title="Switch active repository"
        >
          <optgroup label="Active Workspace">
            <option value=".">📁 Active Workspace (Local)</option>
          </optgroup>

          {#if diagramStore.availableProjects.length > 0}
            <optgroup label="Discovered Local Projects">
              {#each diagramStore.availableProjects as proj}
                {#if proj.path !== '.'}
                  <option value={proj.path}>📁 {proj.name}</option>
                {/if}
              {/each}
            </optgroup>
          {/if}

          {#if diagramStore.recentProjects.length > 0}
            <optgroup label="Recent Projects">
              {#each diagramStore.recentProjects as proj}
                {#if proj.path !== '.' && !diagramStore.availableProjects.some(p => p.path === proj.path)}
                  <option value={proj.path}>🕒 {proj.name}</option>
                {/if}
              {/each}
            </optgroup>
          {/if}

          <optgroup label="Actions">
            <option value="__OPEN_MODAL__">➕ Open Project Folder... (⌘O)</option>
          </optgroup>
        </select>

        <button
          onclick={() => diagramStore.isOpenProjectModalOpen = true}
          class="p-1 rounded bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white transition-colors cursor-pointer"
          title="Open Project / Directory (⌘O)"
          aria-label="Open Project Folder"
        >
          <FolderOpen size={14} class="text-blue-400" />
        </button>
      </div>
      <span class="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">
        Java 25 &bull; Svelte 5 &bull; GSAP 3.15
      </span>
    </div>

    <!-- Live Architectural Status Badge & Quick Search -->
    <div class="flex items-center gap-3">
      {#if diagramStore.isRegenerating}
        <div
          class="flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-950/80 border border-cyan-500/50 text-cyan-300 text-xs font-mono shadow-sm shadow-cyan-950 animate-pulse"
        >
          <span class="w-2 h-2 rounded-full bg-cyan-400 animate-ping"></span>
          <span>Agent Re-indexing AST...</span>
        </div>
      {/if}

      <button
        onclick={() => diagramStore.isCommandPaletteOpen = true}
        class="flex items-center gap-2 px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white text-xs transition-colors cursor-pointer"
        title="Quick search (Cmd+K)"
      >
        <Search size={13} class="text-blue-400" />
        <span class="text-[11px]">Quick Find</span>
        <kbd class="px-1.5 py-0.5 rounded bg-slate-900 border border-slate-700 text-[9px] font-mono text-slate-400">
          ⌘K
        </kbd>
      </button>

      <button
        onclick={() => diagramStore.openLlmPromptModal()}
        class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-indigo-950/70 hover:bg-indigo-900 border border-indigo-700/60 text-indigo-200 hover:text-white text-xs transition-colors cursor-pointer"
        title="Export LLM Refactoring Prompt Dossier (L)"
      >
        <Bot size={13} class="text-indigo-400" />
        <span class="text-[11px] font-medium">LLM Prompt</span>
        <kbd class="px-1.5 py-0.5 rounded bg-slate-900 border border-slate-700 text-[9px] font-mono text-slate-400">
          L
        </kbd>
      </button>

      {#if violatingCount > 0}
        <div
          bind:this={badgeEl}
          class="flex items-center gap-1.5 px-3 py-1 rounded-full bg-rose-500/15 border border-rose-500/40 text-rose-300 text-xs font-mono font-medium shadow-sm shadow-rose-950"
        >
          <AlertTriangle size={14} class="text-rose-400 animate-pulse" />
          <span>{violatingCount} Dependency Rule {violatingCount === 1 ? 'Violation' : 'Violations'}</span>
        </div>
      {:else}
        <div
          bind:this={badgeEl}
          class="flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/15 border border-emerald-500/40 text-emerald-300 text-xs font-mono font-medium shadow-sm shadow-emerald-950"
        >
          <ShieldCheck size={14} class="text-emerald-400" />
          <span>Clean Architecture Conforming</span>
        </div>
      {/if}
    </div>
  </header>

  <!-- Main Workstation Layout -->
  <main class="flex-1 flex overflow-hidden relative">
    <Canvas />
    <Inspector />
  </main>

  <!-- Modals & Overlays -->
  <ClassCard />
  <SourceModal />
  <CommandPalette />
  <OpenProjectModal />
  <LlmPromptModal />
</div>
