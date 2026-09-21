<script lang="ts">
  import { diagramStore } from '../state/diagram.svelte';
  import { Folder, FolderOpen, X, ArrowRight, History, Compass, AlertCircle } from '@lucide/svelte';

  let inputPath = $state('');
  let validationError = $state<string | null>(null);
  let isTesting = $state(false);

  // Sync with current project when opened
  $effect(() => {
    if (diagramStore.isOpenProjectModalOpen) {
      inputPath = diagramStore.projectRoot === '.' ? '' : diagramStore.projectRoot;
      validationError = null;
    }
  });

  function close() {
    diagramStore.isOpenProjectModalOpen = false;
    validationError = null;
  }

  async function handleOpen(path: string) {
    const clean = path.trim();
    if (!clean) {
      validationError = 'Please enter a valid directory path.';
      return;
    }

    isTesting = true;
    validationError = null;

    try {
      // Validate by attempting to fetch graph payload
      const params = new URLSearchParams();
      if (clean !== '.') params.set('projectRoot', clean);
      const res = await fetch(`/api/graph?${params.toString()}`);
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data = await res.json();
      if (data.error) {
        throw new Error(data.error);
      }

      await diagramStore.setProjectRoot(clean);
      close();
    } catch (e: any) {
      validationError = `Unable to load architecture for "${clean}". Ensure the path exists on this machine.`;
    } finally {
      isTesting = false;
    }
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      close();
    }
  }
</script>

<svelte:window onkeydown={handleKeydown} />

{#if diagramStore.isOpenProjectModalOpen}
  <!-- Backdrop -->
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    class="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 select-none animate-in fade-in duration-150"
    onclick={(e) => {
      if (e.target === e.currentTarget) close();
    }}
  >
    <!-- Modal Card -->
    <div
      class="bg-slate-900 border border-slate-800 rounded-xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col font-sans"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <!-- Header -->
      <div class="px-5 py-4 border-b border-slate-800 flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400">
            <FolderOpen size={18} />
          </div>
          <div>
            <h2 id="modal-title" class="text-sm font-semibold text-slate-100">
              Open Repository or Directory
            </h2>
            <p class="text-xs text-slate-400">
              Analyze architecture across any local workspace on this machine.
            </p>
          </div>
        </div>
        <button
          type="button"
          onclick={close}
          class="p-1 rounded-md text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors cursor-pointer"
          aria-label="Close"
        >
          <X size={16} />
        </button>
      </div>

      <!-- Form -->
      <form
        onsubmit={(e) => {
          e.preventDefault();
          handleOpen(inputPath);
        }}
        class="p-5 space-y-4"
      >
        <div>
          <label for="project-path-input" class="block text-xs font-medium text-slate-300 mb-1.5">
            Filesystem Path
          </label>
          <div class="relative flex items-center">
            <input
              id="project-path-input"
              type="text"
              bind:value={inputPath}
              placeholder="e.g. . or ~/workspace/project or /path/to/repo"
              class="w-full bg-slate-950 border border-slate-700 focus:border-blue-500 rounded-lg px-3.5 py-2 text-xs font-mono text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors pr-24"
              autocomplete="off"
              spellcheck="false"
            />
            <button
              type="submit"
              disabled={isTesting || !inputPath.trim()}
              class="absolute right-1.5 px-3 py-1 bg-blue-600 hover:bg-blue-500 disabled:opacity-40 disabled:pointer-events-none text-white text-xs font-medium rounded-md transition-colors flex items-center gap-1.5 cursor-pointer"
            >
              {#if isTesting}
                <span class="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                <span>Opening</span>
              {:else}
                <span>Open</span>
                <ArrowRight size={12} />
              {/if}
            </button>
          </div>
          <p class="text-[11px] text-slate-500 mt-1.5">
            Tip: Use <code class="font-mono text-slate-400">.</code> for current workspace, relative paths (<code class="font-mono text-slate-400">../repo</code>), or full absolute paths.
          </p>
        </div>

        {#if validationError}
          <div class="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-start gap-2 animate-in fade-in duration-100">
            <AlertCircle size={15} class="shrink-0 text-rose-400 mt-0.5" />
            <span>{validationError}</span>
          </div>
        {/if}

        <!-- Quick Access: Discovered Sibling Projects -->
        {#if diagramStore.availableProjects.length > 0}
          <div class="pt-2 border-t border-slate-800/80 space-y-2">
            <div class="flex items-center gap-1.5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              <Compass size={13} class="text-blue-400" />
              <span>Discovered Local Projects</span>
            </div>
            <div class="flex flex-wrap gap-1.5 max-h-28 overflow-y-auto">
              {#each diagramStore.availableProjects as proj}
                <button
                  type="button"
                  onclick={() => handleOpen(proj.path)}
                  class="px-2.5 py-1 rounded-md bg-slate-800 hover:bg-slate-750 border border-slate-700/80 hover:border-slate-600 text-[11px] font-mono text-slate-300 hover:text-white transition-colors flex items-center gap-1.5 cursor-pointer"
                >
                  <Folder size={12} class="text-blue-400/80" />
                  <span>{proj.name}</span>
                </button>
              {/each}
            </div>
          </div>
        {/if}

        <!-- Quick Access: Recent Projects -->
        {#if diagramStore.recentProjects.length > 0}
          <div class="pt-2 border-t border-slate-800/80 space-y-2">
            <div class="flex items-center gap-1.5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              <History size={13} class="text-amber-400" />
              <span>Recently Opened</span>
            </div>
            <div class="flex flex-wrap gap-1.5 max-h-24 overflow-y-auto">
              {#each diagramStore.recentProjects as proj}
                <button
                  type="button"
                  onclick={() => handleOpen(proj.path)}
                  class="px-2.5 py-1 rounded-md bg-slate-800 hover:bg-slate-750 border border-slate-700/80 hover:border-slate-600 text-[11px] font-mono text-slate-300 hover:text-white transition-colors flex items-center gap-1.5 cursor-pointer"
                >
                  <Folder size={12} class="text-slate-400" />
                  <span>{proj.name}</span>
                </button>
              {/each}
            </div>
          </div>
        {/if}
      </form>

      <!-- Footer -->
      <div class="px-5 py-2.5 bg-slate-950/60 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-500 font-mono">
        <span>ESC to close</span>
        <span>↵ to open</span>
      </div>
    </div>
  </div>
{/if}
