<script lang="ts">
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { focusTrap } from '../utils/focusTrap';
  import {
    generateMermaidDiagram,
    generateStandaloneSvg,
    formatMermaidMarkdownBlock,
    rasterizeSvgToPngBlob,
    type ExportDiagramType,
    type ExportFormatType,
    type ExportFramingMode,
    type ExportBackgroundMode
  } from '../utils/diagramExporter';
  import {
    X,
    Copy,
    Check,
    Download,
    Share2,
    FileCode,
    Image,
    Layers,
    Box,
    GitCompare
  } from '@lucide/svelte';

  let isOpen = $derived(diagramStore.isExportModalOpen);
  let backdropEl: HTMLDivElement | null = $state(null);
  let dialogEl: HTMLDivElement | null = $state(null);
  let isClosing = $state(false);

  let diagramType = $state<ExportDiagramType>('COMPONENT_RINGS');
  let formatType = $state<ExportFormatType>('MERMAID');
  let framingMode = $state<ExportFramingMode>('FULL_GRAPH');
  let backgroundMode = $state<ExportBackgroundMode>('DARK_SLATE');
  let includeLegend = $state<boolean>(true);
  let wrapMarkdownFence = $state<boolean>(true);

  let copied = $state(false);
  let isExportingPng = $state(false);

  $effect(() => {
    if (isOpen) {
      diagramType = diagramStore.exportInitialDiagramType || 'COMPONENT_RINGS';
    }
  });

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

  let violationsOnlyActive = $derived(
    diagramStore.hasDeclutterFilter('HIDE_CONFORMING_EDGES') ||
      diagramStore.declutterMode === 'ELEMENTS'
  );

  let rawMermaid = $derived.by(() =>
    generateMermaidDiagram(diagramStore.graph, diagramStore.policy, {
      diagramType,
      violationsOnly: violationsOnlyActive,
      focusedNodeId: diagramStore.focusedNodeId,
      dipPlan: diagramStore.activeDipPlan
    })
  );

  let displayedMermaid = $derived.by(() =>
    wrapMarkdownFence
      ? formatMermaidMarkdownBlock(
          rawMermaid,
          diagramStore.graph?.title || diagramStore.policy?.title || 'Clean Architecture',
          diagramType
        )
      : rawMermaid
  );

  let standaloneSvg = $derived.by(() =>
    generateStandaloneSvg(diagramStore.graph, diagramStore.policy, {
      diagramType,
      framing: framingMode,
      background: backgroundMode,
      includeLegend,
      violationsOnly: violationsOnlyActive,
      focusedNodeId: diagramStore.focusedNodeId,
      dipPlan: diagramStore.activeDipPlan,
      componentOffsets: diagramStore.componentOffsets,
      panX: diagramStore.panX,
      panY: diagramStore.panY,
      zoom: diagramStore.zoom
    })
  );

  let svgDataUri = $derived(
    `data:image/svg+xml;utf8,${encodeURIComponent(standaloneSvg)}`
  );

  function handleClose() {
    if (isClosing || !dialogEl || !backdropEl) {
      diagramStore.closeExportModal();
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
        diagramStore.closeExportModal();
      }
    });
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape' && isOpen) {
      handleClose();
    }
  }

  async function handleCopy() {
    try {
      if (formatType === 'MERMAID') {
        await navigator.clipboard.writeText(displayedMermaid);
        diagramStore.addTelemetryEvent(
          'SUCCESS',
          `Copied ${diagramType} Mermaid diagram to clipboard`,
          `${displayedMermaid.length} chars`
        );
      } else if (formatType === 'SVG') {
        await navigator.clipboard.writeText(standaloneSvg);
        diagramStore.addTelemetryEvent(
          'SUCCESS',
          `Copied standalone ${diagramType} SVG to clipboard`,
          `${standaloneSvg.length} bytes`
        );
      } else {
        isExportingPng = true;
        const blob = await rasterizeSvgToPngBlob(standaloneSvg, 2);
        if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
          await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
          diagramStore.addTelemetryEvent(
            'SUCCESS',
            `Copied 2x Retina PNG (${diagramType}) to clipboard`,
            `${Math.round(blob.size / 1024)} KB`
          );
        }
      }
      copied = true;
      setTimeout(() => (copied = false), 2200);
    } catch (_) {
    } finally {
      isExportingPng = false;
    }
  }

  async function handleDownload() {
    const slug = diagramType.toLowerCase().replace(/_/g, '-');
    if (formatType === 'MERMAID') {
      const ext = wrapMarkdownFence ? 'md' : 'mmd';
      const mime = wrapMarkdownFence ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8';
      triggerBlobDownload(
        new Blob([displayedMermaid], { type: mime }),
        `archlens-${slug}.${ext}`
      );
      return;
    }
    if (formatType === 'SVG') {
      triggerBlobDownload(
        new Blob([standaloneSvg], { type: 'image/svg+xml;charset=utf-8' }),
        `archlens-${slug}.svg`
      );
      return;
    }
    isExportingPng = true;
    try {
      const pngBlob = await rasterizeSvgToPngBlob(standaloneSvg, 2);
      triggerBlobDownload(pngBlob, `archlens-${slug}@2x.png`);
    } finally {
      isExportingPng = false;
    }
  }

  function triggerBlobDownload(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    diagramStore.addTelemetryEvent('INFO', `Downloaded architecture diagram`, filename);
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if isOpen}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    bind:this={backdropEl}
    onclick={handleClose}
    class="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 sm:p-6 select-none"
  >
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div
      bind:this={dialogEl}
      use:focusTrap
      role="dialog"
      aria-modal="true"
      aria-labelledby="export-diagram-modal-title"
      tabindex="-1"
      class="bg-slate-950 border border-slate-700/80 rounded-2xl shadow-2xl max-w-5xl w-full h-[88vh] flex flex-col overflow-hidden"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Modal Header -->
      <div class="px-6 py-4 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="p-2 rounded-lg bg-emerald-950/80 border border-emerald-700/50 text-emerald-400">
            <Share2 size={18} />
          </div>
          <div>
            <h2 id="export-diagram-modal-title" class="text-sm font-semibold text-slate-100">
              Export Clean Architecture Diagram
            </h2>
            <p class="text-xs text-slate-400">
              Export Concentric Rings, Class UML, or DIP Refactoring topology as Mermaid, Standalone SVG, or 2x Retina PNG
            </p>
          </div>
        </div>

        <button
          onclick={handleClose}
          class="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
          aria-label="Close Export Modal"
        >
          <X size={18} />
        </button>
      </div>

      <!-- Controls Bar: 1. Diagram View Type + 2. Export Format -->
      <div class="px-6 py-3 bg-slate-900/60 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3">
        <!-- Diagram Type Selector -->
        <div class="flex items-center gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800">
          <button
            onclick={() => (diagramType = 'COMPONENT_RINGS')}
            class={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
              diagramType === 'COMPONENT_RINGS'
                ? 'bg-blue-600/30 border border-blue-500/60 text-blue-200'
                : 'text-slate-400 hover:text-slate-200 border border-transparent'
            }`}
          >
            <Layers size={13} />
            <span>1. Concentric Rings</span>
          </button>

          <button
            onclick={() => (diagramType = 'CLASS_UML')}
            class={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
              diagramType === 'CLASS_UML'
                ? 'bg-blue-600/30 border border-blue-500/60 text-blue-200'
                : 'text-slate-400 hover:text-slate-200 border border-transparent'
            }`}
          >
            <Box size={13} />
            <span>2. Class & Interface UML</span>
          </button>

          <button
            onclick={() => (diagramType = 'DIP_REFACTOR')}
            class={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
              diagramType === 'DIP_REFACTOR'
                ? 'bg-emerald-600/30 border border-emerald-500/60 text-emerald-200'
                : 'text-slate-400 hover:text-slate-200 border border-transparent'
            }`}
          >
            <GitCompare size={13} />
            <span>3. DIP Refactor (Before → After)</span>
          </button>
        </div>

        <!-- Format Selector -->
        <div class="flex items-center gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800">
          <button
            onclick={() => (formatType = 'MERMAID')}
            class={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-mono transition-colors cursor-pointer ${
              formatType === 'MERMAID'
                ? 'bg-indigo-600/30 border border-indigo-500/60 text-indigo-200'
                : 'text-slate-400 hover:text-slate-200 border border-transparent'
            }`}
          >
            <FileCode size={13} />
            <span>Mermaid (.mmd)</span>
          </button>

          <button
            onclick={() => (formatType = 'SVG')}
            class={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-mono transition-colors cursor-pointer ${
              formatType === 'SVG'
                ? 'bg-indigo-600/30 border border-indigo-500/60 text-indigo-200'
                : 'text-slate-400 hover:text-slate-200 border border-transparent'
            }`}
          >
            <Share2 size={13} />
            <span>Vector SVG</span>
          </button>

          <button
            onclick={() => (formatType = 'PNG')}
            class={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-mono transition-colors cursor-pointer ${
              formatType === 'PNG'
                ? 'bg-indigo-600/30 border border-indigo-500/60 text-indigo-200'
                : 'text-slate-400 hover:text-slate-200 border border-transparent'
            }`}
          >
            <Image size={13} />
            <span>Retina PNG (2x)</span>
          </button>
        </div>
      </div>

      <!-- Secondary Options Bar -->
      <div class="px-6 py-2 bg-slate-950 border-b border-slate-800/80 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-400">
        {#if formatType === 'MERMAID'}
          <label class="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              bind:checked={wrapMarkdownFence}
              class="rounded border-slate-700 bg-slate-900 text-indigo-500"
            />
            <span>Wrap in GitHub / ADR Markdown fence (<code>```mermaid</code>)</span>
          </label>
          <span class="font-mono text-[11px] text-slate-500">
            {diagramType === 'CLASS_UML' ? 'Syntax: classDiagram' : 'Syntax: flowchart TB (Concentric Subgraphs)'}
          </span>
        {:else}
          <div class="flex flex-wrap items-center gap-4">
            <label class="flex items-center gap-1.5">
              <span class="text-slate-400">Framing:</span>
              <select
                bind:value={framingMode}
                class="bg-slate-900 border border-slate-700 rounded px-2 py-0.5 text-slate-200 text-xs"
              >
                <option value="FULL_GRAPH">Auto-Fit Full Graph (Unculled)</option>
                <option value="CURRENT_VIEWPORT">Current Viewport</option>
              </select>
            </label>

            <label class="flex items-center gap-1.5">
              <span class="text-slate-400">Background:</span>
              <select
                bind:value={backgroundMode}
                class="bg-slate-900 border border-slate-700 rounded px-2 py-0.5 text-slate-200 text-xs"
              >
                <option value="DARK_SLATE">Archlens Dark Slate (#020617)</option>
                <option value="TRANSPARENT">Transparent</option>
              </select>
            </label>

            <label class="flex items-center gap-1.5 cursor-pointer">
              <input
                type="checkbox"
                bind:checked={includeLegend}
                class="rounded border-slate-700 bg-slate-900 text-emerald-500"
              />
              <span>Include Architecture Health Legend Footer</span>
            </label>
          </div>
        {/if}
      </div>

      <!-- Main Preview Area -->
      <div class="flex-1 overflow-auto p-6 bg-slate-950 flex flex-col">
        {#if formatType === 'MERMAID'}
          <pre
            class="flex-1 w-full p-4 rounded-xl bg-slate-900/90 border border-slate-800 text-slate-200 font-mono text-xs leading-relaxed overflow-auto select-text"
          >{displayedMermaid}</pre>
        {:else}
          <div
            class="flex-1 w-full rounded-xl border border-slate-800 overflow-auto flex items-center justify-center p-4 bg-slate-900/40"
          >
            <img
              src={svgDataUri}
              alt="Architecture Diagram Preview"
              class="max-w-full max-h-full object-contain rounded-lg shadow-xl"
            />
          </div>
        {/if}
      </div>

      <!-- Modal Footer -->
      <div class="px-6 py-3.5 bg-slate-900 border-t border-slate-800 flex items-center justify-between">
        <div class="text-xs text-slate-400 font-mono">
          {#if violationsOnlyActive}
            <span class="text-rose-400">● Violation X-Ray filter active</span>
          {:else if diagramStore.focusedNodeId}
            <span class="text-blue-400">● Focused package: {diagramStore.focusedNodeId}</span>
          {:else}
            <span>● Full workspace topology ({diagramStore.graph?.components?.length || 0} packages)</span>
          {/if}
        </div>

        <div class="flex items-center gap-2.5">
          <button
            onclick={handleCopy}
            disabled={isExportingPng}
            class="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs font-medium flex items-center gap-1.5 transition-colors cursor-pointer"
          >
            {#if copied}
              <Check size={14} class="text-emerald-400" />
              <span class="text-emerald-300">Copied!</span>
            {:else}
              <Copy size={14} />
              <span>Copy {formatType === 'PNG' ? 'PNG Image' : formatType}</span>
            {/if}
          </button>

          <button
            onclick={handleDownload}
            disabled={isExportingPng}
            class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium flex items-center gap-1.5 transition-colors cursor-pointer shadow-lg shadow-emerald-950/50"
          >
            <Download size={14} />
            <span>
              Download {formatType === 'MERMAID' ? (wrapMarkdownFence ? '.md' : '.mmd') : formatType === 'SVG' ? '.svg' : '@2x.png'}
            </span>
          </button>
        </div>
      </div>
    </div>
  </div>
{/if}
