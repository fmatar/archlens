<script lang="ts">
  import { diagramStore } from '../state/diagram.svelte';
  import {
    Folder,
    FolderOpen,
    FolderTree,
    X,
    ArrowRight,
    History,
    Compass,
    AlertCircle,
    Search,
    Home,
    ChevronRight,
    CornerLeftUp,
    RefreshCw
  } from '@lucide/svelte';

  interface Breadcrumb {
    name: string;
    path: string;
  }

  interface QuickNav {
    name: string;
    path: string;
    icon?: string;
  }

  interface DirectoryEntry {
    name: string;
    path: string;
    isProject: boolean;
    projectType?: string;
    hasChildren: boolean;
  }

  interface FsDirectoryResponse {
    currentPath: string;
    parentPath?: string;
    breadcrumbs: Breadcrumb[];
    quickNav: QuickNav[];
    directories: DirectoryEntry[];
  }

  let inputPath = $state('');
  let validationError = $state<string | null>(null);
  let isTesting = $state(false);

  // Directory explorer state
  let isExplorerOpen = $state(true);
  let isLoadingFs = $state(false);
  let isNativePickerLoading = $state(false);
  let fsCurrentPath = $state('');
  let fsParentPath = $state<string | null>(null);
  let breadcrumbs = $state<Breadcrumb[]>([]);
  let quickNav = $state<QuickNav[]>([]);
  let directories = $state<DirectoryEntry[]>([]);
  let folderFilter = $state('');

  // Filtered directories matching search query
  let filteredDirectories = $derived(
    folderFilter.trim()
      ? directories.filter((d) => d.name.toLowerCase().includes(folderFilter.trim().toLowerCase()))
      : directories
  );

  // Sync with current project when opened and load filesystem
  $effect(() => {
    if (diagramStore.isOpenProjectModalOpen) {
      inputPath = diagramStore.projectRoot === '.' ? '' : diagramStore.projectRoot;
      validationError = null;
      loadDirectories(inputPath || undefined);
    }
  });

  function close() {
    diagramStore.isOpenProjectModalOpen = false;
    validationError = null;
    folderFilter = '';
  }

  async function loadDirectories(targetPath?: string) {
    isLoadingFs = true;
    try {
      const params = new URLSearchParams();
      if (targetPath && targetPath !== '.') {
        params.set('path', targetPath);
      }
      const res = await fetch(`/api/fs/directories?${params.toString()}`);
      if (res.ok) {
        const data: FsDirectoryResponse = await res.json();
        fsCurrentPath = data.currentPath;
        fsParentPath = data.parentPath || null;
        breadcrumbs = data.breadcrumbs || [];
        quickNav = data.quickNav || [];
        directories = data.directories || [];
      } else {
        validationError = 'Unable to connect to Archlens backend server (http://localhost:8088). Please verify the backend is running.';
      }
    } catch (err) {
      console.error('Failed to load filesystem directories', err);
      validationError = 'Unable to connect to Archlens backend server (http://localhost:8088). Please verify the backend is running.';
    } finally {
      isLoadingFs = false;
    }
  }

  async function handleNativePickDirectory() {
    isNativePickerLoading = true;
    validationError = null;
    try {
      const res = await fetch('/api/fs/pick-directory', { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.path) {
          inputPath = data.path;
          await loadDirectories(data.path);
          return;
        }
        if (data.error) {
          validationError = `Native picker error: ${data.error}`;
        }
      } else {
        validationError = 'Unable to connect to Archlens backend server (http://localhost:8088). Please verify the backend is running.';
      }
      isExplorerOpen = true;
    } catch {
      validationError = 'Unable to connect to Archlens backend server (http://localhost:8088). Please verify the backend is running.';
      isExplorerOpen = true;
    } finally {
      isNativePickerLoading = false;
    }
  }

  function handleSelectDirectory(entry: DirectoryEntry) {
    inputPath = entry.path;
    loadDirectories(entry.path);
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
      class="bg-slate-900 border border-slate-800 rounded-xl shadow-2xl w-full max-w-xl max-h-[90vh] overflow-hidden flex flex-col font-sans animate-in zoom-in-95 duration-150"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <!-- Header -->
      <div class="px-5 py-3.5 border-b border-slate-800 flex items-center justify-between shrink-0">
        <div class="flex items-center gap-2.5">
          <div class="p-2 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400">
            <FolderOpen size={18} />
          </div>
          <div>
            <h2 id="modal-title" class="text-sm font-semibold text-slate-100">
              Open Repository or Directory
            </h2>
            <p class="text-xs text-slate-400">
              Browse and analyze architecture across any local workspace on this machine.
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

      <!-- Main Scrollable Body -->
      <div class="p-5 space-y-4 overflow-y-auto flex-1">
        <!-- Input & Action Row -->
        <div>
          <div class="flex items-center justify-between mb-1.5">
            <label for="project-path-input" class="block text-xs font-medium text-slate-300">
              Filesystem Path
            </label>
            <button
              type="button"
              onclick={() => (isExplorerOpen = !isExplorerOpen)}
              class="text-[11px] text-blue-400 hover:text-blue-300 flex items-center gap-1 font-medium transition-colors cursor-pointer"
            >
              <FolderTree size={12} />
              <span>{isExplorerOpen ? 'Hide Explorer' : 'Explore Folders'}</span>
            </button>
          </div>

          <div class="flex items-center gap-2">
            <div class="relative flex-1 flex items-center">
              <input
                id="project-path-input"
                type="text"
                bind:value={inputPath}
                placeholder="e.g. ~/workspace/project or /path/to/repo"
                class="w-full bg-slate-950 border border-slate-700 focus:border-blue-500 rounded-lg px-3.5 py-2 text-xs font-mono text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-colors"
                autocomplete="off"
                spellcheck="false"
                onkeydown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleOpen(inputPath);
                  }
                }}
              />
            </div>

            <!-- Native OS Folder Picker Button -->
            <button
              type="button"
              onclick={handleNativePickDirectory}
              disabled={isNativePickerLoading}
              title="Browse via native Finder folder dialog"
              class="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 hover:border-slate-600 text-slate-200 hover:text-white text-xs font-medium transition-colors flex items-center gap-1.5 shrink-0 cursor-pointer disabled:opacity-50"
            >
              {#if isNativePickerLoading}
                <RefreshCw size={13} class="animate-spin text-blue-400" />
                <span>Browsing...</span>
              {:else}
                <FolderOpen size={13} class="text-blue-400" />
                <span>Browse...</span>
              {/if}
            </button>

            <!-- Open Project Button -->
            <button
              type="button"
              disabled={isTesting || !inputPath.trim()}
              onclick={() => handleOpen(inputPath)}
              class="px-3.5 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-40 disabled:pointer-events-none text-white text-xs font-medium rounded-lg transition-colors flex items-center gap-1.5 shrink-0 cursor-pointer shadow-sm"
            >
              {#if isTesting}
                <span class="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                <span>Opening</span>
              {:else}
                <span>Open</span>
                <ArrowRight size={13} />
              {/if}
            </button>
          </div>
          <p class="text-[11px] text-slate-500 mt-1">
            Tip: Click <code class="font-mono text-slate-400">Browse...</code> to pick a folder in Finder, or navigate below.
          </p>
        </div>

        {#if validationError}
          <div class="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-start gap-2 animate-in fade-in duration-100">
            <AlertCircle size={15} class="shrink-0 text-rose-400 mt-0.5" />
            <span>{validationError}</span>
          </div>
        {/if}

        <!-- Interactive Directory Explorer Section -->
        {#if isExplorerOpen}
          <div class="rounded-lg border border-slate-800 bg-slate-950/70 overflow-hidden space-y-2 p-3 animate-in fade-in duration-150">
            <!-- Navigation Toolbar: Quick Jumps & Parent -->
            <div class="flex items-center justify-between gap-2 pb-2 border-b border-slate-800/80">
              <!-- Quick Jump Shortcuts -->
              <div class="flex items-center gap-1.5 flex-wrap">
                {#each quickNav as nav}
                  <button
                    type="button"
                    onclick={() => {
                      inputPath = nav.path;
                      loadDirectories(nav.path);
                    }}
                    class="px-2 py-0.5 rounded bg-slate-800/80 hover:bg-slate-700 border border-slate-700/60 text-[10px] font-medium text-slate-300 hover:text-white transition-colors flex items-center gap-1 cursor-pointer"
                  >
                    {#if nav.name === 'Home'}
                      <Home size={10} class="text-blue-400" />
                    {:else}
                      <Folder size={10} class="text-amber-400" />
                    {/if}
                    <span>{nav.name}</span>
                  </button>
                {/each}
              </div>

              <!-- Up Directory Button -->
              {#if fsParentPath}
                <button
                  type="button"
                  onclick={() => {
                    if (fsParentPath) {
                      inputPath = fsParentPath;
                      loadDirectories(fsParentPath);
                    }
                  }}
                  class="px-2 py-0.5 rounded bg-slate-800/80 hover:bg-slate-700 border border-slate-700/60 text-[10px] font-medium text-slate-300 hover:text-white transition-colors flex items-center gap-1 cursor-pointer shrink-0"
                  title="Go to parent directory"
                >
                  <CornerLeftUp size={11} class="text-slate-400" />
                  <span>Up (..)</span>
                </button>
              {/if}
            </div>

            <!-- Breadcrumbs Bar -->
            <div class="flex items-center gap-1 overflow-x-auto py-1 text-xs font-mono scrollbar-thin scrollbar-thumb-slate-800">
              {#each breadcrumbs as crumb, idx}
                {#if idx > 0}
                  <ChevronRight size={11} class="text-slate-600 shrink-0" />
                {/if}
                <button
                  type="button"
                  onclick={() => {
                    inputPath = crumb.path;
                    loadDirectories(crumb.path);
                  }}
                  class="px-1.5 py-0.5 rounded hover:bg-slate-800 text-slate-400 hover:text-blue-300 transition-colors shrink-0 cursor-pointer {idx === breadcrumbs.length - 1 ? 'text-slate-100 font-semibold bg-slate-800/60' : ''}"
                >
                  {crumb.name}
                </button>
              {/each}
            </div>

            <!-- Filter Folders Search Input -->
            <div class="relative flex items-center">
              <Search size={12} class="absolute left-2.5 text-slate-500 pointer-events-none" />
              <input
                type="text"
                bind:value={folderFilter}
                placeholder="Filter folders in this directory..."
                class="w-full bg-slate-900 border border-slate-800 focus:border-blue-500/60 rounded px-2 py-1 pl-7 text-[11px] text-slate-200 placeholder-slate-500 focus:outline-none transition-colors"
              />
              {#if folderFilter}
                <button
                  type="button"
                  onclick={() => (folderFilter = '')}
                  class="absolute right-2 text-slate-500 hover:text-slate-300 text-xs"
                >
                  <X size={11} />
                </button>
              {/if}
            </div>

            <!-- Directory List Container -->
            <div class="max-h-52 overflow-y-auto space-y-0.5 pr-1 divide-y divide-slate-800/40">
              {#if isLoadingFs}
                <div class="py-6 flex flex-col items-center justify-center gap-2 text-slate-500 text-xs font-mono">
                  <RefreshCw size={14} class="animate-spin text-blue-400" />
                  <span>Scanning filesystem...</span>
                </div>
              {:else if filteredDirectories.length === 0}
                <div class="py-6 text-center text-slate-500 text-xs font-mono">
                  {#if folderFilter}
                    No folders match "{folderFilter}"
                  {:else}
                    No subdirectories found in this folder
                  {/if}
                </div>
              {:else}
                {#each filteredDirectories as dir}
                  <div
                    class="group flex items-center justify-between px-2 py-1.5 rounded hover:bg-slate-850 transition-colors cursor-pointer"
                    onclick={() => handleSelectDirectory(dir)}
                    role="button"
                    tabindex="0"
                    onkeydown={(e) => {
                      if (e.key === 'Enter') handleSelectDirectory(dir);
                    }}
                  >
                    <div class="flex items-center gap-2 min-w-0 flex-1">
                      <Folder
                        size={14}
                        class="shrink-0 {dir.isProject ? 'text-blue-400' : 'text-slate-400 group-hover:text-slate-300'}"
                      />
                      <span class="text-xs font-mono text-slate-300 group-hover:text-white truncate">
                        {dir.name}
                      </span>
                      {#if dir.isProject}
                        <span class="px-1.5 py-0.2 rounded text-[9px] font-semibold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 uppercase tracking-wider shrink-0">
                          {dir.projectType || 'Project'}
                        </span>
                      {/if}
                    </div>

                    <div class="flex items-center gap-1.5 shrink-0 opacity-80 group-hover:opacity-100">
                      {#if dir.isProject}
                        <button
                          type="button"
                          onclick={(e) => {
                            e.stopPropagation();
                            handleOpen(dir.path);
                          }}
                          class="px-2 py-0.5 rounded bg-blue-600/20 hover:bg-blue-600 border border-blue-500/40 hover:border-transparent text-blue-300 hover:text-white text-[10px] font-semibold transition-colors cursor-pointer"
                        >
                          Open
                        </button>
                      {/if}
                      <ChevronRight size={12} class="text-slate-600 group-hover:text-slate-400" />
                    </div>
                  </div>
                {/each}
              {/if}
            </div>
          </div>
        {/if}

        <!-- Quick Access: Discovered Sibling Projects -->
        {#if diagramStore.availableProjects.length > 0}
          <div class="pt-2 border-t border-slate-800/80 space-y-2">
            <div class="flex items-center gap-1.5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              <Compass size={13} class="text-blue-400" />
              <span>Discovered Local Projects</span>
            </div>
            <div class="flex flex-wrap gap-1.5 max-h-24 overflow-y-auto">
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
            <div class="flex flex-wrap gap-1.5 max-h-20 overflow-y-auto">
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
      </div>

      <!-- Footer -->
      <div class="px-5 py-2.5 bg-slate-950/60 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-500 font-mono shrink-0">
        <span>ESC to close</span>
        <span>↵ to open</span>
      </div>
    </div>
  </div>
{/if}

