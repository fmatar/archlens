<script lang="ts">
  import { diagramStore } from '../state/diagram.svelte';
  import { X, FileCode } from '@lucide/svelte';

  let modal = $derived(diagramStore.sourceFileModal);
  let lines = $derived(modal?.content ? modal.content.split('\n') : []);
</script>

{#if modal}
  <div class="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-6">
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div
      role="dialog"
      aria-modal="true"
      tabindex="-1"
      class="bg-slate-950 border border-slate-700 rounded-xl shadow-2xl max-w-4xl w-full h-[85vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150"
      onclick={(e) => e.stopPropagation()}
    >
      <!-- Title -->
      <div class="p-3.5 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
        <div class="flex items-center gap-2 font-mono text-xs text-slate-200">
          <FileCode size={16} class="text-blue-400" />
          <span>{modal.filePath}</span>
          <span class="text-slate-500">: line {modal.line}</span>
        </div>
        <button
          onclick={() => diagramStore.sourceFileModal = null}
          class="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
        >
          <X size={18} />
        </button>
      </div>

      <!-- Code Viewer -->
      <div class="flex-1 overflow-auto font-mono text-xs p-4 bg-slate-950">
        <table class="w-full border-collapse">
          <tbody>
            {#each lines as line, i}
              {@const lineNum = i + 1}
              {@const isTarget = lineNum === modal.line}
              <tr class={isTarget ? 'bg-blue-950/60 border-l-4 border-blue-400 font-semibold' : 'hover:bg-slate-900/40'}>
                <td class="w-12 text-right pr-4 text-slate-600 select-none">{lineNum}</td>
                <td class="text-slate-300 whitespace-pre">{line}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    </div>
  </div>
{/if}
