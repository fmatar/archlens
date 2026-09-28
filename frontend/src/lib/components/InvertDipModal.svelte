<script lang="ts">
  import gsap from 'gsap';
  import { diagramStore } from '../state/diagram.svelte';
  import { X, Copy, Check, Bot, Sparkles, AlertTriangle, ArrowRight, ShieldCheck, FileCode, CheckCircle2, Loader2 } from '@lucide/svelte';

  let isOpen = $derived(diagramStore.isDipModalOpen);
  let plan = $derived(diagramStore.activeDipPlan);
  let isLoading = $derived(diagramStore.isLoadingDipPlan);
  let isDispatching = $derived(diagramStore.isDispatchingDip);
  let notice = $derived(diagramStore.dipDispatchNotice);

  let backdropEl: HTMLDivElement | null = $state(null);
  let dialogEl: HTMLDivElement | null = $state(null);
  let isClosing = $state(false);
  let activeTab = $state<'port' | 'refactor' | 'prompt'>('port');
  let copiedPrompt = $state(false);
  let copiedCode = $state(false);

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
      diagramStore.closeDipModal();
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
        diagramStore.closeDipModal();
      }
    });
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape' && isOpen) {
      handleClose();
    }
  }

  async function copyPrompt() {
    if (!plan?.surgicalPrompt) return;
    try {
      await navigator.clipboard.writeText(plan.surgicalPrompt);
      copiedPrompt = true;
      diagramStore.addTelemetryEvent(
        'SUCCESS',
        `Copied surgical DIP prompt for ${plan.portName}`,
        `${plan.surgicalPrompt.length} chars`
      );
      setTimeout(() => (copiedPrompt = false), 2500);
    } catch (_) {}
  }

  async function copyPortCode() {
    if (!plan?.portInterfaceCode) return;
    try {
      await navigator.clipboard.writeText(plan.portInterfaceCode);
      copiedCode = true;
      setTimeout(() => (copiedCode = false), 2000);
    } catch (_) {}
  }

  function getTierLabel(level: number | null): string {
    if (level === 0) return 'Ring 0: Domain Core';
    if (level === 1) return 'Ring 1: Application';
    if (level === 2) return 'Ring 2: Adapters';
    if (level === 3) return 'Ring 3: Infrastructure';
    return `Ring ${level ?? '?'}`;
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
      onclick={(e) => e.stopPropagation()}
      role="dialog"
      tabindex="-1"
      aria-modal="true"
      aria-labelledby="dip-modal-title"
      class="bg-slate-900 border border-slate-700/80 rounded-2xl w-full max-w-3xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden"
    >
      <!-- Header -->
      <div class="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
        <div class="flex items-center gap-3">
          <div class="p-2.5 rounded-xl bg-rose-500/15 border border-rose-500/30 text-rose-400">
            <Sparkles size={20} class="animate-pulse" />
          </div>
          <div>
            <h2 id="dip-modal-title" class="text-base font-bold text-slate-100 flex items-center gap-2">
              Surgical DIP Inverter
              <span class="text-[10px] font-mono font-semibold px-2 py-0.5 rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/40">
                Uncle Bob DIP
              </span>
            </h2>
            <p class="text-xs text-slate-400 mt-0.5">
              Invert outward dependency by synthesizing an interface port in the inner layer.
            </p>
          </div>
        </div>
        <button
          onclick={handleClose}
          class="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
          aria-label="Close dialog"
        >
          <X size={18} />
        </button>
      </div>

      <!-- Violation Context Banner -->
      {#if plan}
        <div class="px-6 py-3 bg-slate-950/40 border-b border-slate-800/80 flex items-center justify-between gap-4 font-mono text-xs">
          <div class="flex-1 bg-slate-900/90 p-2.5 rounded-lg border border-slate-800">
            <div class="text-[9px] uppercase tracking-wider text-slate-500">{getTierLabel(plan.fromLevel)}</div>
            <div class="font-bold text-slate-200 truncate mt-0.5" title={plan.fromClass}>
              {plan.fromClass.split('.').pop()}
            </div>
            <div class="text-[9px] text-slate-500 truncate">{plan.fromClass}</div>
          </div>

          <div class="flex flex-col items-center shrink-0">
            <div class="flex items-center gap-1 text-rose-400 font-bold text-[10px]">
              <AlertTriangle size={13} class="animate-pulse" />
              <span>OUTWARD BREACH</span>
            </div>
            <ArrowRight size={18} class="text-rose-500 mt-0.5" />
          </div>

          <div class="flex-1 bg-slate-900/90 p-2.5 rounded-lg border border-slate-800">
            <div class="text-[9px] uppercase tracking-wider text-slate-500">{getTierLabel(plan.toLevel)}</div>
            <div class="font-bold text-slate-200 truncate mt-0.5" title={plan.toClass}>
              {plan.toClass.split('.').pop()}
            </div>
            <div class="text-[9px] text-slate-500 truncate">{plan.toClass}</div>
          </div>
        </div>
      {/if}

      <!-- Navigation Tabs -->
      <div class="px-6 pt-3 border-b border-slate-800 flex items-center gap-2 bg-slate-900/50">
        <button
          onclick={() => activeTab = 'port'}
          class="px-3 py-1.5 text-xs font-mono font-medium rounded-t-lg transition-colors border-b-2 cursor-pointer {activeTab === 'port' ? 'border-rose-500 text-rose-300 bg-slate-800/60' : 'border-transparent text-slate-400 hover:text-slate-200'}"
        >
          1. Synthesized Port
        </button>
        <button
          onclick={() => activeTab = 'refactor'}
          class="px-3 py-1.5 text-xs font-mono font-medium rounded-t-lg transition-colors border-b-2 cursor-pointer {activeTab === 'refactor' ? 'border-rose-500 text-rose-300 bg-slate-800/60' : 'border-transparent text-slate-400 hover:text-slate-200'}"
        >
          2. Adapter & Caller Diff
        </button>
        <button
          onclick={() => activeTab = 'prompt'}
          class="px-3 py-1.5 text-xs font-mono font-medium rounded-t-lg transition-colors border-b-2 cursor-pointer {activeTab === 'prompt' ? 'border-rose-500 text-rose-300 bg-slate-800/60' : 'border-transparent text-slate-400 hover:text-slate-200'}"
        >
          3. Surgical LLM Prompt
        </button>
      </div>

      <!-- Main Body Content -->
      <div class="flex-1 overflow-y-auto p-6 space-y-4">
        {#if isLoading}
          <div class="py-16 flex flex-col items-center justify-center gap-3 text-slate-400">
            <Loader2 size={24} class="animate-spin text-rose-400" />
            <p class="text-xs font-mono">Synthesizing Dependency Inversion Interface...</p>
          </div>
        {:else if plan}
          {#if activeTab === 'port'}
            <!-- Tab 1: Port Interface -->
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <FileCode size={15} class="text-rose-400" />
                  <span class="text-xs font-mono font-bold text-slate-200">{plan.portName}.java</span>
                  <span class="text-[10px] text-slate-500 font-mono">({plan.portFilePath})</span>
                </div>
                <button
                  onclick={copyPortCode}
                  class="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white font-mono text-[11px] flex items-center gap-1.5 transition-colors cursor-pointer"
                >
                  {#if copiedCode}
                    <Check size={12} class="text-emerald-400" />
                    <span class="text-emerald-400">Copied</span>
                  {:else}
                    <Copy size={12} />
                    <span>Copy Code</span>
                  {/if}
                </button>
              </div>

              <div class="relative bg-slate-950 rounded-xl p-4 border border-slate-800 font-mono text-xs text-slate-200 overflow-x-auto shadow-inner">
                <pre class="leading-relaxed"><code>{plan.portInterfaceCode}</code></pre>
              </div>

              <div class="p-3 rounded-lg bg-blue-950/30 border border-blue-900/40 text-blue-200/90 text-[11px] leading-relaxed flex items-start gap-2">
                <ShieldCheck size={15} class="shrink-0 mt-0.5 text-blue-400" />
                <span>
                  <strong>Architectural Contract:</strong> Declaring this port in <code>{plan.portPackage}</code> ensures the inner layer remains pure and independent of outer database, framework, or transport technology.
                </span>
              </div>
            </div>
          {:else if activeTab === 'refactor'}
            <!-- Tab 2: Adapter & Caller Changes -->
            <div class="space-y-4">
              <!-- Adapter Update -->
              <div class="space-y-2">
                <div class="text-xs font-mono font-semibold text-slate-300 flex items-center gap-2">
                  <span class="px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300 text-[10px]">Adapter</span>
                  {plan.toClass.split('.').pop()} implements {plan.portName}
                </div>
                <div class="bg-slate-950 rounded-xl p-4 border border-slate-800 font-mono text-xs text-slate-200 overflow-x-auto">
                  <pre class="leading-relaxed"><code>{plan.adapterRefactorPreview}</code></pre>
                </div>
              </div>

              <!-- Caller Update -->
              <div class="space-y-2">
                <div class="text-xs font-mono font-semibold text-slate-300 flex items-center gap-2">
                  <span class="px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300 text-[10px]">Caller</span>
                  {plan.fromClass.split('.').pop()} Inverted Dependency Injection
                </div>
                <div class="bg-slate-950 rounded-xl p-4 border border-slate-800 font-mono text-xs text-slate-200 overflow-x-auto">
                  <pre class="leading-relaxed"><code>{plan.callerRefactorPreview}</code></pre>
                </div>
              </div>
            </div>
          {:else if activeTab === 'prompt'}
            <!-- Tab 3: Complete Surgical Prompt -->
            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <span class="text-xs font-mono text-slate-400">Copy-ready prompt for Claude Code, Antigravity, or Cursor:</span>
                <button
                  onclick={copyPrompt}
                  class="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white font-mono text-[11px] flex items-center gap-1.5 transition-colors cursor-pointer"
                >
                  {#if copiedPrompt}
                    <Check size={12} class="text-emerald-400" />
                    <span class="text-emerald-400">Copied</span>
                  {:else}
                    <Copy size={12} />
                    <span>Copy Prompt</span>
                  {/if}
                </button>
              </div>
              <div class="bg-slate-950 rounded-xl p-4 border border-slate-800 font-mono text-xs text-slate-300 max-h-64 overflow-y-auto leading-relaxed whitespace-pre-wrap select-text">
                {plan.surgicalPrompt}
              </div>
            </div>
          {/if}
        {/if}
      </div>

      <!-- Action Footer -->
      <div class="px-6 py-4 border-t border-slate-800 bg-slate-950/70 flex items-center justify-between gap-3">
        <div>
          {#if notice}
            <div class="flex items-center gap-2 text-xs font-mono text-emerald-400">
              <CheckCircle2 size={14} class="animate-bounce" />
              <span>{notice}</span>
            </div>
          {:else}
            <span class="text-[11px] text-slate-500 font-mono">
              Uncle Bob Rule: High-level policies depend on abstractions.
            </span>
          {/if}
        </div>

        <div class="flex items-center gap-3">
          <button
            onclick={copyPrompt}
            disabled={!plan || isLoading}
            class="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-750 text-slate-200 text-xs font-medium flex items-center gap-2 border border-slate-700 transition-colors cursor-pointer disabled:opacity-50"
          >
            {#if copiedPrompt}
              <Check size={14} class="text-emerald-400" />
              <span class="text-emerald-400 font-bold">Copied Prompt</span>
            {:else}
              <Copy size={14} />
              <span>Copy Surgical Prompt</span>
            {/if}
          </button>

          <button
            onclick={() => plan && diagramStore.dispatchDipToAgent(plan)}
            disabled={!plan || isLoading || isDispatching}
            class="px-4 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 disabled:bg-slate-800 text-white font-medium text-xs flex items-center gap-2 shadow-lg shadow-rose-950/50 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {#if isDispatching}
              <Loader2 size={14} class="animate-spin text-white" />
              <span>Dispatching Task...</span>
            {:else}
              <Bot size={14} />
              <span>Dispatch to AI Agent</span>
            {/if}
          </button>
        </div>
      </div>
    </div>
  </div>
{/if}
