<script lang="ts">
  import { diagramStore } from '../state/diagram.svelte';
  import { formatCrap, formatCoverage } from '../utils/colors';
  import { X, Code, ShieldAlert, Cpu } from 'lucide-svelte';

  let cls = $derived(diagramStore.selectedClass);
</script>

{#if cls}
  <div class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      class="bg-slate-900 border border-slate-700 rounded-xl shadow-2xl max-w-2xl w-full max-h-[85vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Header -->
      <div class="p-4 bg-slate-800/80 border-b border-slate-700 flex items-center justify-between">
        <div>
          <div class="flex items-center gap-2">
            <span class="text-xs px-2 py-0.5 rounded bg-blue-500/20 text-blue-300 font-mono">
              «{cls.stereotype}»
            </span>
            <h2 class="text-lg font-bold text-slate-100 font-mono">{cls.name}</h2>
          </div>
          <p class="text-xs text-slate-400 font-mono mt-0.5">{cls.packageName}</p>
        </div>
        <button
          onclick={() => diagramStore.selectedClass = null}
          class="p-1 rounded hover:bg-slate-700 text-slate-400 hover:text-slate-200 transition-colors"
        >
          <X size={20} />
        </button>
      </div>

      <!-- Metrics Bar -->
      <div class="px-4 py-2.5 bg-slate-950/60 border-b border-slate-800 flex items-center justify-between text-xs font-mono">
        <div class="text-emerald-400">
          {formatCrap(cls.crap.mu, cls.crap.max, cls.crap.sigma)}
        </div>
        <div class="text-slate-300">
          Coverage: <span class="text-emerald-400">{formatCoverage(cls.coverage)}</span>
        </div>
        <div class="text-slate-300">
          Mutants: <span class="text-emerald-400">{cls.killed} killed</span> / <span class="text-rose-400">{cls.survived} survived</span>
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
                class="w-full text-left p-2 hover:bg-slate-800/40 cursor-pointer flex items-center justify-between transition-colors group"
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
          class="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-xs transition-colors"
        >
          <Code size={14} />
          View Source Code
        </button>
      </div>
    </div>
  </div>
{/if}
