<script lang="ts">
  import { onMount } from 'svelte';
  import { diagramStore } from './lib/state/diagram.svelte';
  import Canvas from './lib/components/Canvas.svelte';
  import Inspector from './lib/components/Inspector.svelte';
  import ClassCard from './lib/components/ClassCard.svelte';
  import SourceModal from './lib/components/SourceModal.svelte';
  import { ShieldCheck, Network, AlertTriangle } from 'lucide-svelte';
  import type { DependencyEdge } from './lib/types/diagram';

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
</script>

<div class="flex flex-col h-screen w-screen bg-slate-950 text-slate-100 overflow-hidden font-sans">
  <!-- Top App Navigation -->
  <header class="h-12 bg-slate-900 border-b border-slate-800 px-4 flex items-center justify-between z-30 select-none">
    <div class="flex items-center gap-3">
      <div class="flex items-center gap-2 font-bold text-sm tracking-wide bg-gradient-to-r from-blue-400 to-indigo-400 bg-clip-text text-transparent">
        <Network size={18} class="text-blue-400" />
        Clean Architecture Workbench
      </div>
      <!-- Project Switcher -->
      <select
        value={diagramStore.projectRoot}
        onchange={(e) => diagramStore.setProjectRoot((e.target as HTMLSelectElement).value)}
        class="bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded px-2.5 py-1 font-medium focus:outline-none focus:ring-1 focus:ring-blue-500 cursor-pointer"
      >
        <option value="/Users/fady/workspace/rootine.ai">📁 rootine.ai (Autonomous Assistant)</option>
        <option value="/Users/fady/workspace/labs/bogzee">📁 bogzee (Quarkus LangChain4j)</option>
        <option value="/Users/fady/workspace/labs/unclebob-design">📁 unclebob-design (Workbench)</option>
      </select>
      <span class="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">
        Java 25 &bull; Svelte 5
      </span>
    </div>

    <!-- Live Architectural Status Badge -->
    <div class="flex items-center gap-4">
      {#if violatingCount > 0}
        <div class="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs font-mono font-medium animate-pulse">
          <AlertTriangle size={14} />
          {violatingCount} Dependency Rule {violatingCount === 1 ? 'Violation' : 'Violations'}
        </div>
      {:else}
        <div class="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-mono font-medium">
          <ShieldCheck size={14} />
          Clean Architecture Conforming
        </div>
      {/if}
    </div>
  </header>

  <!-- Main Workstation Layout -->
  <main class="flex-1 flex overflow-hidden relative">
    <Canvas />
    <Inspector />
  </main>

  <!-- Modals -->
  <ClassCard />
  <SourceModal />
</div>
