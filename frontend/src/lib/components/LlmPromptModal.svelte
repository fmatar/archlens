<script lang="ts">
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { X, Copy, Check, Download, Bot, Sparkles, AlertTriangle, ShieldCheck } from '@lucide/svelte';

  let isOpen = $derived(diagramStore.isLlmPromptModalOpen);
  let dossier = $derived(diagramStore.llmPromptDossier);
  let isLoading = $derived(diagramStore.isLoadingLlmPrompt);

  let backdropEl: HTMLDivElement | null = $state(null);
  let dialogEl: HTMLDivElement | null = $state(null);
  let isClosing = $state(false);
  let copied = $state(false);

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
      diagramStore.isLlmPromptModalOpen = false;
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
        diagramStore.isLlmPromptModalOpen = false;
      }
    });
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape' && isOpen) {
      handleClose();
    }
  }

  async function copyToClipboard() {
    if (!dossier) return;
    try {
      await navigator.clipboard.writeText(dossier);
      copied = true;
      diagramStore.addTelemetryEvent(
        'SUCCESS',
        'Copied LLM Refactoring Prompt Dossier to clipboard',
        `${dossier.length} characters`
      );
      setTimeout(() => (copied = false), 2500);
    } catch (_) {}
  }

  function downloadMarkdown() {
    if (!dossier) return;
    const blob = new Blob([dossier], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'archlens-refactoring-dossier.md';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    diagramStore.addTelemetryEvent(
      'INFO',
      'Downloaded LLM Refactoring Dossier file',
      'archlens-refactoring-dossier.md'
    );
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if isOpen}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    bind:this={backdropEl}
    onclick={handleClose}
    class="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-6 select-none"
  >
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div
      bind:this={dialogEl}
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      class="bg-slate-950 border border-slate-700/80 rounded-xl shadow-2xl max-w-4xl w-full h-[85vh] flex flex-col overflow-hidden"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Modal Header -->
      <div class="p-4 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
        <div class="flex items-center gap-3 min-w-0">
          <div class="p-2 rounded-lg bg-indigo-950/80 border border-indigo-700/50 text-indigo-400">
            <Bot size={20} />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h2 class="font-semibold text-sm text-slate-100">
                LLM Clean Architecture Refactoring Dossier
              </h2>
              <span class="px-2 py-0.5 rounded text-[10px] font-mono bg-indigo-950/80 text-indigo-300 border border-indigo-800/60">
                Copy-Ready Prompt
              </span>
            </div>
            <p class="text-xs text-slate-400 mt-0.5">
              Copy this diagnostic text directly into Claude Code, Gemini, ChatGPT, or Antigravity for automated refactoring.
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2 shrink-0">
          <button
            onclick={copyToClipboard}
            class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium shadow-sm transition-colors cursor-pointer"
            title="Copy entire markdown dossier to clipboard"
          >
            {#if copied}
              <Check size={14} class="text-white" />
              <span>Copied!</span>
            {:else}
              <Copy size={14} />
              <span>Copy Prompt</span>
            {/if}
          </button>

          <button
            onclick={downloadMarkdown}
            class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs font-medium transition-colors cursor-pointer"
            title="Download markdown dossier file"
          >
            <Download size={14} />
            <span>Download .md</span>
          </button>

          <button
            onclick={handleClose}
            class="p-1.5 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors ml-1 cursor-pointer"
            title="Close (Esc)"
          >
            <X size={18} />
          </button>
        </div>
      </div>

      <!-- Dossier Content Body -->
      <div class="flex-1 overflow-auto p-4 bg-slate-950/95 font-mono text-xs select-text">
        {#if isLoading}
          <!-- Skeleton Shimmer Loader -->
          <div class="space-y-3.5 p-2 animate-pulse">
            <div class="h-6 bg-slate-800/80 rounded w-1/2"></div>
            <div class="h-4 bg-slate-800/50 rounded w-3/4"></div>
            <div class="h-4 bg-slate-800/40 rounded w-2/3"></div>
            <div class="border-t border-slate-800 my-4"></div>
            <div class="h-5 bg-slate-800/70 rounded w-1/3"></div>
            <div class="h-20 bg-slate-900/60 rounded border border-slate-800"></div>
            <div class="h-5 bg-slate-800/70 rounded w-2/5"></div>
            <div class="h-28 bg-slate-900/60 rounded border border-slate-800"></div>
          </div>
        {:else}
          <pre class="text-slate-200 leading-relaxed whitespace-pre-wrap break-words selection:bg-indigo-900 selection:text-white font-mono">{dossier}</pre>
        {/if}
      </div>

      <!-- Footer Info -->
      <div class="p-3 bg-slate-900/80 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
        <div class="flex items-center gap-2">
          <Sparkles size={14} class="text-amber-400" />
          <span>Includes prioritized Dependency Inversion Principle (DIP) interface port specifications.</span>
        </div>
        <div class="flex items-center gap-2 font-mono text-[11px]">
          <span>Press</span>
          <kbd class="px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300">Esc</kbd>
          <span>to dismiss</span>
        </div>
      </div>
    </div>
  </div>
{/if}
