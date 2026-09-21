<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { X, FileCode, Copy, Check } from '@lucide/svelte';

  let modal = $derived(diagramStore.sourceFileModal);
  let lines = $derived(modal?.content ? modal.content.split('\n') : []);

  let backdropEl: HTMLDivElement | null = $state(null);
  let dialogEl: HTMLDivElement | null = $state(null);
  let isClosing = $state(false);
  let copied = $state(false);

  $effect(() => {
    if (modal && backdropEl && dialogEl && !isClosing) {
      const ctx = gsap.context(() => {
        // Backdrop fade
        gsap.fromTo(backdropEl, { opacity: 0 }, { opacity: 1, duration: 0.2, ease: 'power2.out' });

        // Spring physics modal scale
        gsap.fromTo(
          dialogEl,
          { scale: 0.94, y: 16, opacity: 0 },
          { scale: 1, y: 0, opacity: 1, duration: 0.3, ease: 'back.out(1.3)' }
        );

        // Target line pulse highlight and scroll
        setTimeout(() => {
          const targetRow = dialogEl?.querySelector<HTMLTableRowElement>('.target-code-row');
          if (targetRow) {
            targetRow.scrollIntoView({ block: 'center', behavior: 'smooth' });
            gsap.fromTo(
              targetRow,
              { backgroundColor: 'rgba(37, 99, 235, 0.5)' },
              {
                backgroundColor: 'rgba(30, 58, 138, 0.25)',
                duration: 0.7,
                repeat: 3,
                yoyo: true,
                ease: 'sine.inOut'
              }
            );
          }
        }, 100);
      });

      return () => ctx.revert();
    }
  });

  function handleClose() {
    if (isClosing || !dialogEl || !backdropEl) {
      diagramStore.sourceFileModal = null;
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
        diagramStore.sourceFileModal = null;
      }
    });
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      handleClose();
    }
  }

  async function copyPath() {
    if (!modal?.filePath) return;
    try {
      await navigator.clipboard.writeText(modal.filePath);
      copied = true;
      setTimeout(() => copied = false, 2000);
    } catch (_) {}
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if modal}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    bind:this={backdropEl}
    onclick={handleClose}
    class="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-6 select-none"
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
      <!-- Title -->
      <div class="p-3.5 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
        <div class="flex items-center gap-2 font-mono text-xs text-slate-200 min-w-0">
          <FileCode size={16} class="text-blue-400 shrink-0" />
          <span class="font-medium text-slate-100 truncate">{modal.filePath}</span>
          <span class="text-blue-400 bg-blue-950/60 px-1.5 py-0.5 rounded border border-blue-800/50 shrink-0">
            : line {modal.line}
          </span>
          <button
            onclick={copyPath}
            class="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-white transition-colors shrink-0"
            title="Copy path to clipboard"
          >
            {#if copied}
              <Check size={14} class="text-emerald-400" />
            {:else}
              <Copy size={14} />
            {/if}
          </button>
        </div>
        <button
          onclick={handleClose}
          class="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors shrink-0"
          title="Close (Esc)"
        >
          <X size={18} />
        </button>
      </div>

      <!-- Code Viewer: ponytail: pure preformatted code rendering without fragile regex tokenization -->
      <div class="flex-1 overflow-auto font-mono text-xs p-4 bg-slate-950 select-text">
        {#if lines.length > 0}
          <table class="w-full border-collapse">
            <tbody>
              {#each lines as line, i}
                {@const lineNum = i + 1}
                {@const isTarget = lineNum === modal.line}
                <tr
                  class:target-code-row={isTarget}
                  class:code-row-idle={!isTarget}
                >
                  <td class="w-12 text-right pr-4 text-slate-600 select-none align-top">{lineNum}</td>
                  <td class="whitespace-pre font-mono">{line}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        {:else}
          <div class="flex flex-col items-center justify-center h-full text-slate-500 space-y-2">
            <p>No source content available for this file.</p>
            <p class="text-[11px] font-mono text-slate-600">{modal.filePath}</p>
          </div>
        {/if}
      </div>
    </div>
  </div>
{/if}

<style>
  .target-code-row {
    background-color: rgba(30, 58, 138, 0.35);
    color: #bfdbfe;
    font-weight: 500;
  }
  .code-row-idle {
    color: #cbd5e1;
  }
  .code-row-idle:hover {
    background-color: rgba(15, 23, 42, 0.4);
  }
</style>
