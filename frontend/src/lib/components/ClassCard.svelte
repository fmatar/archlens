<script lang="ts">
  import { onMount } from 'svelte';
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { formatCrap, formatCoverage } from '../utils/colors';
  import { X, Code, ShieldAlert, Cpu } from '@lucide/svelte';

  let cls = $derived(diagramStore.selectedClass);

  let backdropEl: HTMLDivElement | null = $state(null);
  let dialogEl: HTMLDivElement | null = $state(null);
  let isClosing = $state(false);

  $effect(() => {
    if (cls && backdropEl && dialogEl && !isClosing) {
      const ctx = gsap.context(() => {
        // Backdrop fade
        gsap.fromTo(backdropEl, { opacity: 0 }, { opacity: 1, duration: 0.25, ease: 'power2.out' });

        // Spring physics card entrance
        gsap.fromTo(
          dialogEl,
          { scale: 0.9, y: 24, opacity: 0 },
          { scale: 1, y: 0, opacity: 1, duration: 0.38, ease: 'back.out(1.4)' }
        );

        // Subtle stagger on method items
        gsap.fromTo(
          '.method-item',
          { opacity: 0, x: -10 },
          { opacity: 1, x: 0, duration: 0.2, stagger: 0.02, delay: 0.12, ease: 'power2.out' }
        );
      });

      return () => ctx.revert();
    }
  });

  function handleClose() {
    if (isClosing || !dialogEl || !backdropEl) {
      diagramStore.selectedClass = null;
      return;
    }
    isClosing = true;
    gsap.to(dialogEl, {
      scale: 0.94,
      y: 12,
      opacity: 0,
      duration: 0.2,
      ease: 'power2.in'
    });
    gsap.to(backdropEl, {
      opacity: 0,
      duration: 0.2,
      ease: 'power2.in',
      onComplete: () => {
        isClosing = false;
        diagramStore.selectedClass = null;
      }
    });
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      handleClose();
    }
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if cls}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    bind:this={backdropEl}
    onclick={handleClose}
    class="fixed inset-0 bg-black/65 backdrop-blur-sm z-50 flex items-center justify-center p-4 select-none"
  >
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div
      bind:this={dialogEl}
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      class="bg-slate-900 border border-slate-700/80 rounded-xl shadow-2xl max-w-2xl w-full max-h-[85vh] flex flex-col overflow-hidden"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Header -->
      <div class="p-4 bg-slate-800/80 border-b border-slate-700 flex items-center justify-between">
        <div>
          <div class="flex items-center gap-2">
            <span class="text-xs px-2 py-0.5 rounded bg-blue-500/20 text-blue-300 font-mono font-medium">
              «{cls.stereotype}»
            </span>
            <h2 class="text-lg font-bold text-slate-100 font-mono">{cls.name}</h2>
          </div>
          <p class="text-xs text-slate-400 font-mono mt-0.5">{cls.packageName}</p>
        </div>
        <button
          onclick={handleClose}
          class="p-1 rounded hover:bg-slate-700 text-slate-400 hover:text-slate-200 transition-colors"
          title="Close (Esc)"
        >
          <X size={20} />
        </button>
      </div>

      <!-- Metrics Bar -->
      <div class="px-4 py-2.5 bg-slate-950/60 border-b border-slate-800 flex items-center justify-between text-xs font-mono">
        <div class="text-emerald-400 font-medium">
          {formatCrap(cls.crap.mu, cls.crap.max, cls.crap.sigma)}
        </div>
        <div class="text-slate-300">
          Coverage: <span class="text-emerald-400 font-medium">{formatCoverage(cls.coverage)}</span>
        </div>
        <div class="text-slate-300">
          Mutants: <span class="text-emerald-400 font-medium">{cls.killed} killed</span> / <span class="text-rose-400 font-medium">{cls.survived} survived</span>
        </div>
      </div>

      <!-- Body: Fields & Methods -->
      <div class="p-4 overflow-y-auto flex-1 space-y-4">
        <!-- Fields -->
        {#if cls.fields && cls.fields.length > 0}
          <div>
            <h3 class="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">Fields</h3>
            <div class="bg-slate-950/40 rounded-lg p-2 font-mono text-xs space-y-1">
              {#each cls.fields as f}
                <div class="text-slate-300">
                  <span class="text-slate-500">{f.isPrivate ? '-' : '+'}</span>
                  <span class="text-blue-300">{f.name}</span>: <span class="text-amber-200">{f.type}</span>
                </div>
              {/each}
            </div>
          </div>
        {/if}

        <!-- Methods -->
        <div>
          <h3 class="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">Operations & Methods</h3>
          <div class="bg-slate-950/40 rounded-lg divide-y divide-slate-800 font-mono text-xs">
            {#each cls.methods as m}
              <button
                type="button"
                class="method-item w-full text-left p-2 hover:bg-slate-800/50 cursor-pointer flex items-center justify-between transition-colors group"
                onclick={() => diagramStore.openSource(cls.filePath, m.line)}
              >
                <div class="flex items-center gap-2">
                  <span class="text-slate-500">{m.isPrivate ? '-' : '+'}</span>
                  <span class="text-slate-200 group-hover:text-blue-300 font-medium">{m.name}({m.args.join(', ')})</span>
                  <span class="text-amber-300 text-[10px]">: {m.returnType}</span>
                </div>
                <div class="flex items-center gap-3 text-[11px]">
                  <span class="text-slate-400">CC: {m.cc}</span>
                  <span class="text-emerald-400">CRAP: {m.crap}</span>
                  <Code size={14} class="text-slate-500 group-hover:text-blue-400" />
                </div>
              </button>
            {/each}
            {#if cls.methods.length === 0}
              <div class="p-3 text-slate-500 italic text-center">No methods defined</div>
            {/if}
          </div>
        </div>
      </div>

      <!-- Footer Jump Button -->
      <div class="p-3 bg-slate-800/60 border-t border-slate-700 flex justify-end">
        <button
          onclick={() => diagramStore.openSource(cls.filePath, 1)}
          class="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-xs shadow-lg shadow-blue-900/30 transition-all hover:scale-[1.02] active:scale-[0.98]"
        >
          <Code size={14} />
          View Source Code
        </button>
      </div>
    </div>
  </div>
{/if}
