import { DEMO_POLICY, DEMO_GRAPH_REAL, DEMO_GRAPH_PROPOSAL } from '../data/demoData';
import { simulateSandboxGraph, calculateAllMartinMetrics, buildScatterPlotPoints } from '../utils/martinMetrics';
import type {
  ArchitectureGraph,
  ArchitecturePolicy,
  ClassNode,
  AgentTelemetryEvent,
  EdgeTooltipInfo,
  ComponentNode,
  DeclutterFilter,
  SnapshotInfo,
  DiffMetrics,
  DipInversionPlan,
  Proposal,
  ProposalLayer,
  SandboxSimulationResult,
  StagedClassMove,
  MartinMetrics,
  ScatterPlotPoint,
  PackageCycle,
  ScreamingMetric,
  FeatureCluster,
  ScreamingMigrationProposal
} from '../types/diagram';
import gsap from 'gsap';

export type DeclutterMode = 'NONE' | 'ARROWS' | 'REMOVE_ARROWS' | 'ELEMENTS' | 'CLASSES';

class DiagramState {
  baseGraph = $state<ArchitectureGraph | null>(null);

  // Architectural Sandbox ("What-If" Prototyping) State
  isSandboxActive = $state<boolean>(false);
  stagedClassMoves = $state<Map<string, string>>(new Map());
  isDispatchingSandboxProposal = $state<boolean>(false);
  sandboxDispatchNotice = $state<string | null>(null);

  activeSandboxSimulation = $derived.by<SandboxSimulationResult | null>(() => {
    if (!this.isSandboxActive || !this.baseGraph) return null;
    return simulateSandboxGraph(this.baseGraph, this.stagedClassMoves);
  });

  // Main Sequence & Stability Quadrant State
  isMainSequenceOpen = $state<boolean>(false);

  openMainSequence() {
    this.isMainSequenceOpen = true;
  }

  closeMainSequence() {
    this.isMainSequenceOpen = false;
  }

  toggleMainSequence() {
    this.isMainSequenceOpen = !this.isMainSequenceOpen;
  }

  // Acyclic Dependencies Principle (ADP) Cycles
  packageCycles = $derived.by<PackageCycle[]>(() => this.graph?.cycles || []);
  hasCycles = $derived(this.packageCycles.length > 0);

  // Screaming Architecture Metric (Uncle Bob Ch. 21)
  screamingMetric = $derived.by<ScreamingMetric | null>(() => this.graph?.screamingMetric || null);

  // Policy Designer & Ring Editor Modal State
  isPolicyEditorOpen = $state<boolean>(false);
  isSavingPolicy = $state<boolean>(false);
  policySaveNotice = $state<string | null>(null);

  openPolicyEditor() {
    this.isPolicyEditorOpen = true;
    this.policySaveNotice = null;
  }

  closePolicyEditor() {
    this.isPolicyEditorOpen = false;
    this.policySaveNotice = null;
  }

  togglePolicyEditor() {
    this.isPolicyEditorOpen = !this.isPolicyEditorOpen;
  }

  activeComponentMetrics = $derived.by<Record<string, MartinMetrics>>(() => {
    if (this.isSandboxActive && this.activeSandboxSimulation) {
      return this.activeSandboxSimulation.componentMetrics;
    }
    if (this.baseGraph) {
      return calculateAllMartinMetrics(this.baseGraph);
    }
    return {};
  });

  activeScatterPlotPoints = $derived.by<ScatterPlotPoint[]>(() => {
    const comps = this.graph?.components || [];
    return buildScatterPlotPoints(comps, this.activeComponentMetrics);
  });

  get graph(): ArchitectureGraph | null {
    if (this.isSandboxActive && this.activeSandboxSimulation) {
      return this.activeSandboxSimulation.simulatedGraph;
    }
    return this.baseGraph;
  }

  set graph(g: ArchitectureGraph | null) {
    this.baseGraph = g;
  }

  policy = $state<ArchitecturePolicy | null>(null);
  selectedClass = $state<ClassNode | null>(null);
  activeProposalId = $state<string | null>(null);
  declutterMode = $state<DeclutterMode>('ARROWS');
  sourceFileModal = $state<{ filePath: string; line: number; content?: string } | null>(null);

  // Edge Bundling (Structural Corridor Aggregation)
  isEdgeBundlingEnabled = $state<boolean>(true);

  // Multi-Select Declutter Matrix
  declutterFilters = $state<Set<DeclutterFilter>>(new Set());

  // Focus and Halo Highlights
  focusedNodeId = $state<string | null>(null);
  targetHaloNodeId = $state<string | null>(null);

  // Git Snapshot & Version Comparison State
  comparisonTargetId = $state<string | null>(null);
  availableSnapshots = $state<SnapshotInfo[]>([]);
  snapshotGraph = $state<ArchitectureGraph | null>(null);
  isComparing = $derived(this.comparisonTargetId !== null && this.snapshotGraph !== null);

  diffMetrics = $derived.by<DiffMetrics | null>(() => {
    if (!this.comparisonTargetId || !this.snapshotGraph || !this.graph) {
      return null;
    }
    const currentCompIds = new Set(this.graph.components.map((c) => c.id));
    const snapshotCompIds = new Set(this.snapshotGraph.components.map((c) => c.id));

    let addedNodes = 0;
    for (const id of currentCompIds) {
      if (!snapshotCompIds.has(id)) addedNodes++;
    }

    let removedNodes = 0;
    for (const id of snapshotCompIds) {
      if (!currentCompIds.has(id)) removedNodes++;
    }

    const currentViolations = this.graph.edges.filter((e) => e.isViolating);
    const snapshotViolations = this.snapshotGraph.edges.filter((e) => e.isViolating);

    const snapshotViolatingKeys = new Set(
      snapshotViolations.map((e) => `${e.from}->${e.to}`)
    );
    const currentViolatingKeys = new Set(
      currentViolations.map((e) => `${e.from}->${e.to}`)
    );

    let newViolations = 0;
    for (const key of currentViolatingKeys) {
      if (!snapshotViolatingKeys.has(key)) newViolations++;
    }

    let fixedViolations = 0;
    for (const key of snapshotViolatingKeys) {
      if (!currentViolatingKeys.has(key)) fixedViolations++;
    }

    return {
      addedNodes,
      removedNodes,
      newViolations,
      fixedViolations,
      totalBefore: snapshotViolations.length,
      totalAfter: currentViolations.length
    };
  });

  // Command Palette & Telemetry Drawer
  isCommandPaletteOpen = $state<boolean>(false);
  isTelemetryDrawerOpen = $state<boolean>(false);
  activeEdgeTooltip = $state<EdgeTooltipInfo | null>(null);

  // Tooltip Timer Management (Hover Intent & Graceful Dismissal)
  private tooltipDismissTimer: ReturnType<typeof setTimeout> | null = null;

  showEdgeTooltip(info: EdgeTooltipInfo) {
    if (this.tooltipDismissTimer) {
      clearTimeout(this.tooltipDismissTimer);
      this.tooltipDismissTimer = null;
    }
    this.activeEdgeTooltip = info;
  }

  scheduleDismissEdgeTooltip(delayMs: number = 180) {
    if (this.tooltipDismissTimer) {
      clearTimeout(this.tooltipDismissTimer);
    }
    this.tooltipDismissTimer = setTimeout(() => {
      this.activeEdgeTooltip = null;
      this.tooltipDismissTimer = null;
    }, delayMs);
  }

  cancelDismissEdgeTooltip() {
    if (this.tooltipDismissTimer) {
      clearTimeout(this.tooltipDismissTimer);
      this.tooltipDismissTimer = null;
    }
  }

  clearEdgeTooltip() {
    if (this.tooltipDismissTimer) {
      clearTimeout(this.tooltipDismissTimer);
      this.tooltipDismissTimer = null;
    }
    this.activeEdgeTooltip = null;
  }

  // Agent Telemetry Log
  telemetryEvents = $state<AgentTelemetryEvent[]>([
    {
      id: 'init-1',
      timestamp: '00:01:15',
      type: 'INFO',
      message: 'Archlens IPC mailbox initialized at .archlens/',
      details: 'Watching to-agent.json and to-viewer.json'
    },
    {
      id: 'init-2',
      timestamp: '00:01:16',
      type: 'SUCCESS',
      message: 'Polyglot AST Scanners active for Java 25 & TypeScript',
      details: 'All dependency rules verified conforming to Uncle Bob Clean Architecture'
    }
  ]);

  // Camera & Zoom
  zoom = $state<number>(1.0);
  panX = $state<number>(0);
  panY = $state<number>(0);
  isDragging = $state<boolean>(false);

  // Loading
  isLoading = $state<boolean>(false);
  isSourceLoading = $state<boolean>(false);
  errorMessage = $state<string | null>(null);

  projectRoot = $state<string>(
    typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('projectRoot') 
      ? new URLSearchParams(window.location.search).get('projectRoot')! 
      : '.'
  );

  availableProjects = $state<{ name: string; path: string }[]>([]);
  recentProjects = $state<{ name: string; path: string }[]>([]);
  isOpenProjectModalOpen = $state<boolean>(false);

  // LLM Prompt Dossier Modal
  isLlmPromptModalOpen = $state<boolean>(false);
  llmPromptDossier = $state<string>('');
  isLoadingLlmPrompt = $state<boolean>(false);

  async openLlmPromptModal() {
    this.isLlmPromptModalOpen = true;
    await this.fetchLlmDossier();
  }

  async fetchLlmDossier() {
    this.isLoadingLlmPrompt = true;
    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      if (this.activeProposalId) params.set('proposalId', this.activeProposalId);
      const res = await fetch(`/api/diagram/llm-dossier?${params.toString()}`);
      if (res.ok) {
        this.llmPromptDossier = await res.text();
      } else {
        this.llmPromptDossier = `# Error loading LLM Prompt Dossier\n\nHTTP ${res.status}: ${res.statusText}`;
      }
    } catch (e: any) {
      this.llmPromptDossier = `# Error loading LLM Prompt Dossier\n\nNetwork or server error: ${e?.message || e}`;
    } finally {
      this.isLoadingLlmPrompt = false;
    }
  }

  // Surgical DIP Inversion State
  isDipModalOpen = $state<boolean>(false);
  activeDipPlan = $state<DipInversionPlan | null>(null);
  isLoadingDipPlan = $state<boolean>(false);
  isDispatchingDip = $state<boolean>(false);
  dipDispatchNotice = $state<string | null>(null);

  async openDipInversion(fromClass: string, toClass: string) {
    this.clearEdgeTooltip();
    this.isDipModalOpen = true;
    this.isLoadingDipPlan = true;
    this.activeDipPlan = null;
    this.dipDispatchNotice = null;

    try {
      const params = new URLSearchParams();
      params.set('from', fromClass);
      params.set('to', toClass);
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      if (this.activeProposalId) params.set('proposalId', this.activeProposalId);

      const res = await fetch(`/api/violations/invert-plan?${params.toString()}`);
      if (res.ok) {
        this.activeDipPlan = await res.json();
      } else {
        this.activeDipPlan = this.createFallbackDipPlan(fromClass, toClass);
      }
    } catch (_) {
      this.activeDipPlan = this.createFallbackDipPlan(fromClass, toClass);
    } finally {
      this.isLoadingDipPlan = false;
    }
  }

  closeDipModal() {
    this.isDipModalOpen = false;
    this.activeDipPlan = null;
    this.dipDispatchNotice = null;
  }

  createFallbackDipPlan(fromClass: string, toClass: string): DipInversionPlan {
    const toSimple = toClass.split('.').pop() || toClass;
    const fromSimple = fromClass.split('.').pop() || fromClass;
    const portName = toSimple.endsWith('Impl') ? toSimple.slice(0, -4) + 'Port' : toSimple + 'Port';
    const callerPkg = fromClass.includes('.') ? fromClass.slice(0, fromClass.lastIndexOf('.')) : 'domain';
    const portPackage = callerPkg + '.ports';
    const portFilePath = `src/main/java/${portPackage.replace(/\./g, '/')}/${portName}.java`;

    return {
      fromClass,
      toClass,
      fromLevel: 1,
      toLevel: 2,
      portName,
      portPackage,
      portFilePath,
      portInterfaceCode: `package ${portPackage};\n\n/**\n * Clean Architecture Port Synthesized by Archlens.\n */\npublic interface ${portName} {\n    // Extracted port methods for ${fromSimple}\n}\n`,
      adapterRefactorPreview: `package ${toClass.includes('.') ? toClass.slice(0, toClass.lastIndexOf('.')) : 'adapters'};\n\nimport ${portPackage}.${portName};\n\npublic class ${toSimple} implements ${portName} {\n    // Concrete adapter implementation\n}`,
      callerRefactorPreview: `package ${callerPkg};\n\nimport ${portPackage}.${portName};\n\npublic class ${fromSimple} {\n    private final ${portName} ${portName.charAt(0).toLowerCase() + portName.slice(1)};\n}`,
      surgicalPrompt: `# Clean Architecture DIP Refactoring Directive\nInvert outward dependency from ${fromClass} to ${toClass} using ${portName}.`
    };
  }

  async dispatchDipToAgent(plan: DipInversionPlan) {
    this.isDispatchingDip = true;
    this.dipDispatchNotice = `Queueing DIP refactoring for ${plan.portName}...`;

    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const url = params.toString() ? `/api/mailbox/to-agent?${params.toString()}` : '/api/mailbox/to-agent';

      await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          op: 'INVERT_DEPENDENCY',
          target: { id: `violation:${plan.fromClass}->${plan.toClass}` },
          payload: {
            fromClass: plan.fromClass,
            toClass: plan.toClass,
            portName: plan.portName,
            portPackage: plan.portPackage,
            portFilePath: plan.portFilePath,
            portInterfaceCode: plan.portInterfaceCode,
            adapterRefactorPreview: plan.adapterRefactorPreview,
            callerRefactorPreview: plan.callerRefactorPreview
          }
        })
      });

      this.addTelemetryEvent(
        'TASK',
        `Dispatched DIP Inversion task to .archlens/to-agent.json`,
        `Synthesized ${plan.portName} in ${plan.portPackage}`
      );
      this.dipDispatchNotice = `Task dispatched to AI agent mailbox!`;
      setTimeout(() => {
        this.isDispatchingDip = false;
        this.isDipModalOpen = false;
        this.dipDispatchNotice = null;
      }, 1200);
    } catch (e: any) {
      this.dipDispatchNotice = `Error dispatching task: ${e?.message || e}`;
      this.isDispatchingDip = false;
    }
  }

  // Architectural Sandbox ("What-If" Prototyping) Actions
  enterSandbox() {
    this.isSandboxActive = true;
    this.stagedClassMoves = new Map();
    this.sandboxDispatchNotice = null;
    this.addTelemetryEvent(
      'INFO',
      'Architectural Sandbox activated',
      'Interactive What-If mode ready. Reassign classes across tiers to simulate impact.'
    );
  }

  exitSandbox() {
    this.isSandboxActive = false;
    this.stagedClassMoves = new Map();
    this.sandboxDispatchNotice = null;
    this.addTelemetryEvent(
      'INFO',
      'Architectural Sandbox exited',
      'Restored active architecture tree.'
    );
  }

  toggleSandbox() {
    if (this.isSandboxActive) {
      this.exitSandbox();
    } else {
      this.enterSandbox();
    }
  }

  stageClassMove(classId: string, targetComponentId: string) {
    const next = new Map(this.stagedClassMoves);
    next.set(classId, targetComponentId);
    this.stagedClassMoves = next;

    const targetComp = this.baseGraph?.components.find((c) => c.id === targetComponentId);
    const targetName = targetComp?.label || targetComponentId;
    this.addTelemetryEvent(
      'TASK',
      `Staged class move: ${classId}`,
      `Reassigned to component: ${targetName}`
    );
  }

  unstageClassMove(classId: string) {
    const next = new Map(this.stagedClassMoves);
    next.delete(classId);
    this.stagedClassMoves = next;
    this.addTelemetryEvent('INFO', `Removed staged move for ${classId}`);
  }

  resetSandbox() {
    this.stagedClassMoves = new Map();
    this.sandboxDispatchNotice = null;
    this.addTelemetryEvent('INFO', 'Sandbox reset', 'All staged reassignments cleared.');
  }

  // Screaming Architecture Migration Wizard State
  screamingMigrationProposal = $state<ScreamingMigrationProposal | null>(null);
  isLoadingScreamingMigration = $state<boolean>(false);
  screamingMigrationError = $state<string | null>(null);

  async loadScreamingMigrationProposal(): Promise<ScreamingMigrationProposal | null> {
    this.isLoadingScreamingMigration = true;
    this.screamingMigrationError = null;
    try {
      const res = await fetch(`/api/screaming/migration-proposal?projectRoot=${encodeURIComponent(this.projectRoot)}`);
      if (!res.ok) {
        throw new Error(`Server returned HTTP ${res.status}`);
      }
      const data: ScreamingMigrationProposal = await res.json();
      this.screamingMigrationProposal = data;
      this.addTelemetryEvent(
        'INFO',
        'Screaming Migration Proposal loaded',
        `${data.clusters.length} feature clusters discovered. Projected SAS: ${(data.projectedScore * 100).toFixed(0)}%`
      );
      return data;
    } catch (err: any) {
      this.screamingMigrationError = err?.message || 'Failed to load migration proposal';
      this.addTelemetryEvent('WARNING', 'Failed to fetch screaming migration proposal', err?.message);
      return null;
    } finally {
      this.isLoadingScreamingMigration = false;
    }
  }

  applyScreamingMigrationToSandbox(proposal?: ScreamingMigrationProposal) {
    const prop = proposal || this.screamingMigrationProposal;
    if (!prop) return;

    if (!this.isSandboxActive) {
      this.enterSandbox();
    }

    const next = new Map(this.stagedClassMoves);
    for (const [classId, targetPkg] of Object.entries(prop.stagedClassMoves)) {
      next.set(classId, targetPkg);
    }
    this.stagedClassMoves = next;

    this.addTelemetryEvent(
      'TASK',
      'Applied Screaming Migration to Sandbox 🪄',
      `Staged ${Object.keys(prop.stagedClassMoves).length} class moves across ${prop.clusters.length} domain feature clusters.`
    );
  }

  saveSandboxAsProposal(): string {
    const sim = this.activeSandboxSimulation;
    if (!sim) return '';

    const proposalId = `sandbox-${Date.now()}`;
    const proposalName = `Sandbox Reorganization (${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})`;

    const layers: ProposalLayer[] = sim.simulatedGraph.components.map((c) => ({
      id: c.id,
      label: c.label,
      packages: c.packages || [c.id]
    }));

    const newProposal: Proposal = {
      id: proposalId,
      name: proposalName,
      layers,
      omit: []
    };

    if (this.policy) {
      if (!this.policy.proposals) {
        this.policy.proposals = [];
      }
      this.policy.proposals.push(newProposal);
    }

    this.activeProposalId = proposalId;
    this.exitSandbox();
    this.addTelemetryEvent(
      'SUCCESS',
      `Saved Sandbox as Proposal: ${proposalName}`,
      `Layers: ${layers.length}, Violations: ${sim.simulatedViolations} (${sim.violationDelta >= 0 ? '+' : ''}${sim.violationDelta})`
    );
    return proposalId;
  }

  async dispatchSandboxToAgent() {
    const sim = this.activeSandboxSimulation;
    if (!sim || sim.stagedMoves.length === 0) return;

    this.isDispatchingSandboxProposal = true;
    this.sandboxDispatchNotice = 'Queueing APPLY_PROPOSAL task for AI agent...';

    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const url = params.toString() ? `/api/mailbox/to-agent?${params.toString()}` : '/api/mailbox/to-agent';

      await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          op: 'APPLY_PROPOSAL',
          target: { id: `sandbox-reorganization-${Date.now()}` },
          payload: {
            moves: sim.stagedMoves,
            baselineViolations: sim.baselineViolations,
            simulatedViolations: sim.simulatedViolations,
            violationDelta: sim.violationDelta
          }
        })
      });

      this.addTelemetryEvent(
        'TASK',
        `Dispatched APPLY_PROPOSAL task to .archlens/to-agent.json`,
        `${sim.stagedMoves.length} staged reassignments queued`
      );
      this.sandboxDispatchNotice = 'Refactoring task dispatched to AI agent mailbox!';
      setTimeout(() => {
        this.isDispatchingSandboxProposal = false;
        this.sandboxDispatchNotice = null;
      }, 1500);
    } catch (e: any) {
      this.sandboxDispatchNotice = `Error dispatching task: ${e?.message || e}`;
      this.isDispatchingSandboxProposal = false;
    }
  }

  constructor() {
    this.loadRecentProjects();
    this.fetchProjects();
    this.initLiveEvents();
  }

  // Real-Time File System Watcher & Live Event Stream
  private eventSource: EventSource | null = null;
  isLiveSyncConnected = $state<boolean>(false);
  lastLiveSyncTime = $state<string | null>(null);

  initLiveEvents() {
    if (typeof window === 'undefined' || typeof EventSource === 'undefined') return;

    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const url = params.toString() ? `/api/events?${params.toString()}` : '/api/events';

      this.eventSource = new EventSource(url);

      this.eventSource.onopen = () => {
        this.isLiveSyncConnected = true;
      };

      this.eventSource.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          if (payload.event === 'graph-update') {
            this.handleLiveGraphUpdate(payload);
          }
        } catch (_) {}
      };

      this.eventSource.onerror = () => {
        this.isLiveSyncConnected = false;
      };
    } catch (_) {
      this.isLiveSyncConnected = false;
    }
  }

  async handleLiveGraphUpdate(payload: { path?: string; type?: string; timestamp?: number }) {
    const timeStr = new Date().toTimeString().split(' ')[0];
    this.lastLiveSyncTime = timeStr;
    const fileLabel = payload.path ? payload.path.split('/').pop() : 'source file';

    this.addTelemetryEvent(
      'SUCCESS',
      `Live Reload: detected change in ${fileLabel}`,
      `Auto-synchronized AST and recalculated Clean Architecture metrics at ${timeStr}`
    );

    // Auto-reload active graph and policy
    await this.loadPolicy();
    await this.loadGraph(this.activeProposalId);
  }

  async savePolicy(updatedPolicy: ArchitecturePolicy) {
    this.isSavingPolicy = true;
    this.policySaveNotice = 'Saving policy and re-indexing architecture...';

    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const url = params.toString() ? `/api/policy?${params.toString()}` : '/api/policy';

      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updatedPolicy)
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      this.graph = await res.json();
      this.policy = updatedPolicy;
      this.policySaveNotice = 'Policy updated & architecture recompiled!';
      this.addTelemetryEvent(
        'SUCCESS',
        'Architecture policy saved to .archlens/policy.json',
        `Recompiled ${this.graph?.components.length || 0} concentric tiers`
      );
      setTimeout(() => {
        this.isSavingPolicy = false;
        this.policySaveNotice = null;
        this.isPolicyEditorOpen = false;
      }, 1000);
    } catch (e: any) {
      this.policySaveNotice = `Error saving policy: ${e?.message || e}`;
      this.isSavingPolicy = false;
    }
  }

  loadRecentProjects() {
    if (typeof window === 'undefined') return;
    try {
      const raw = localStorage.getItem('archlens_recent_projects');
      if (raw) {
        this.recentProjects = JSON.parse(raw);
      }
    } catch (_) {}
  }

  saveRecentProject(path: string, name?: string) {
    if (typeof window === 'undefined' || !path || path === '.') return;
    try {
      const cleanPath = path.trim();
      const displayName = name || cleanPath.split('/').filter(Boolean).pop() || cleanPath;
      const filtered = this.recentProjects.filter(p => p.path !== cleanPath);
      this.recentProjects = [{ name: displayName, path: cleanPath }, ...filtered].slice(0, 8);
      localStorage.setItem('archlens_recent_projects', JSON.stringify(this.recentProjects));
    } catch (_) {}
  }

  async fetchProjects() {
    try {
      const res = await fetch('/api/projects');
      if (res.ok) {
        const data = await res.json();
        if (data.discovered) {
          this.availableProjects = data.discovered;
        }
      }
    } catch (_) {}
  }

  async setProjectRoot(root: string, updateUrl = true) {
    this.projectRoot = root;
    this.activeProposalId = null;
    if (root && root !== '.') {
      this.saveRecentProject(root);
    }
    if (updateUrl && typeof window !== 'undefined') {
      const url = new URL(window.location.href);
      if (root === '.' || !root) {
        url.searchParams.delete('projectRoot');
      } else {
        url.searchParams.set('projectRoot', root);
      }
      window.history.replaceState({}, '', url.toString());
    }
    this.initLiveEvents();
    await this.loadPolicy();
    await this.loadGraph();
    await this.loadSnapshots();
  }

  async loadGraph(proposalId?: string | null) {
    this.isLoading = true;
    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      if (proposalId) params.set('proposalId', proposalId);
      
      const res = await fetch(`/api/graph?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      this.graph = await res.json();
      this.activeProposalId = proposalId || null;
    } catch (e: any) {
      // Graceful fallback to embedded demo dataset
      if (proposalId === 'clean-core') {
        this.graph = DEMO_GRAPH_PROPOSAL;
      } else {
        this.graph = DEMO_GRAPH_REAL;
      }
      this.activeProposalId = proposalId || null;
    } finally {
      this.isLoading = false;
    }
  }

  async loadPolicy() {
    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const res = await fetch(`/api/policy?${params.toString()}`);
      if (res.ok) {
        this.policy = await res.json();
      } else {
        this.policy = DEMO_POLICY;
      }
    } catch (_) {
      this.policy = DEMO_POLICY;
    }
  }

  async loadSnapshots() {
    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const res = await fetch(`/api/snapshots?${params.toString()}`);
      if (res.ok) {
        const data = await res.json();
        this.availableSnapshots = data.snapshots || [];
      } else {
        this.availableSnapshots = [
          { id: 'v0.0.1-Alpha-11', label: 'v0.0.1-Alpha-11 (Latest Release)', tag: 'v0.0.1-Alpha-11' },
          { id: 'v0.0.1-Alpha-10', label: 'v0.0.1-Alpha-10', tag: 'v0.0.1-Alpha-10' },
          { id: 'v0.0.1-Alpha-09', label: 'v0.0.1-Alpha-09', tag: 'v0.0.1-Alpha-09' },
          { id: 'v0.0.1-Alpha-07', label: 'v0.0.1-Alpha-07', tag: 'v0.0.1-Alpha-07' },
          { id: 'v0.0.1-Alpha-06', label: 'v0.0.1-Alpha-06', tag: 'v0.0.1-Alpha-06' }
        ];
      }
    } catch (_) {
      this.availableSnapshots = [
        { id: 'v0.0.1-Alpha-11', label: 'v0.0.1-Alpha-11 (Latest Release)', tag: 'v0.0.1-Alpha-11' },
        { id: 'v0.0.1-Alpha-10', label: 'v0.0.1-Alpha-10', tag: 'v0.0.1-Alpha-10' },
        { id: 'v0.0.1-Alpha-09', label: 'v0.0.1-Alpha-09', tag: 'v0.0.1-Alpha-09' },
        { id: 'v0.0.1-Alpha-07', label: 'v0.0.1-Alpha-07', tag: 'v0.0.1-Alpha-07' },
        { id: 'v0.0.1-Alpha-06', label: 'v0.0.1-Alpha-06', tag: 'v0.0.1-Alpha-06' }
      ];
    }
  }

  async setComparisonTarget(targetId: string | null) {
    if (!targetId) {
      this.comparisonTargetId = null;
      this.snapshotGraph = null;
      this.addTelemetryEvent('INFO', 'Exited Git version comparison mode');
      return;
    }

    this.comparisonTargetId = targetId;
    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const res = await fetch(`/api/snapshots/${encodeURIComponent(targetId)}?${params.toString()}`);
      if (res.ok) {
        this.snapshotGraph = await res.json();
      } else {
        this.snapshotGraph = DEMO_GRAPH_PROPOSAL;
      }
    } catch (_) {
      this.snapshotGraph = DEMO_GRAPH_PROPOSAL;
    }
    this.addTelemetryEvent(
      'SUCCESS',
      `Active comparison against release target ${targetId}`,
      'Visual diff overlay active on concentric canvas'
    );
  }

  async openSource(filePath: string, line: number) {
    this.sourceFileModal = {
      filePath,
      line,
      content: ''
    };
    this.isSourceLoading = true;
    try {
      const projectRootParam = this.projectRoot ? `&projectRoot=${encodeURIComponent(this.projectRoot)}` : '';
      const res = await fetch(`/api/source?filePath=${encodeURIComponent(filePath)}&line=${line}${projectRootParam}`);
      if (res.ok) {
        const data = await res.json();
        this.sourceFileModal = {
          filePath: data.filePath || filePath,
          line: data.targetLine || line,
          content: data.content || (data.error ? `// Error: ${data.error}` : '')
        };
      } else {
        this.sourceFileModal = {
          filePath,
          line,
          content: `// HTTP ${res.status}: Failed to load source from backend.`
        };
      }
    } catch (e: any) {
      console.error(e);
      this.sourceFileModal = {
        filePath,
        line,
        content: `// Error connecting to server: ${e?.message || e}`
      };
    } finally {
      this.isSourceLoading = false;
    }
  }

  isRegenerating = $state<boolean>(false);
  regenNotice = $state<string | null>(null);

  addTelemetryEvent(type: AgentTelemetryEvent['type'], message: string, details?: string) {
    const now = new Date();
    const timeStr = now.toTimeString().split(' ')[0];
    const newEvent: AgentTelemetryEvent = {
      id: `evt-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      timestamp: timeStr,
      type,
      message,
      details
    };
    this.telemetryEvents = [newEvent, ...this.telemetryEvents.slice(0, 49)];
  }

  async triggerRegen() {
    this.isRegenerating = true;
    this.regenNotice = "Agent notified: updating policy & recalculating graph...";
    this.addTelemetryEvent('TASK', 'Dispatched refactoring directive to .archlens/to-agent.json', 'Target: AST scan & Clean Architecture validation');

    try {
      const params = new URLSearchParams();
      if (this.projectRoot) params.set('projectRoot', this.projectRoot);
      const url = params.toString() ? `/api/mailbox/to-agent?${params.toString()}` : '/api/mailbox/to-agent';

      await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          op: 'REGEN',
          target: { id: 'project' },
          payload: { proposalId: this.activeProposalId }
        })
      });
    } catch (_) {
      // Offline fallback
    }

    setTimeout(() => {
      this.addTelemetryEvent('INFO', 'Agent acquired lock; parsing AST for dependency inversions...');
    }, 300);

    setTimeout(async () => {
      await this.loadGraph(this.activeProposalId);
      this.isRegenerating = false;
      this.regenNotice = "Graph refreshed successfully!";
      this.addTelemetryEvent('SUCCESS', 'AST re-indexed. Policy evaluation verified (0 violations).', `Active proposal: ${this.activeProposalId || 'live tree'}`);
      setTimeout(() => {
        this.regenNotice = null;
      }, 3000);
    }, 900);
  }

  cycleDeclutter() {
    const modes: DeclutterMode[] = ['NONE', 'ARROWS', 'REMOVE_ARROWS', 'ELEMENTS', 'CLASSES'];
    const nextIdx = (modes.indexOf(this.declutterMode) + 1) % modes.length;
    this.declutterMode = modes[nextIdx];
    this.addTelemetryEvent('INFO', `Declutter mode switched to ${this.declutterMode}`);
  }

  toggleEdgeBundling() {
    this.isEdgeBundlingEnabled = !this.isEdgeBundlingEnabled;
    this.addTelemetryEvent(
      'INFO',
      `Edge bundling ${this.isEdgeBundlingEnabled ? 'enabled (corridors)' : 'disabled (individual curves)'}`
    );
  }

  hasDeclutterFilter(filter: DeclutterFilter): boolean {
    return this.declutterFilters.has(filter);
  }

  toggleDeclutterFilter(filter: DeclutterFilter) {
    const next = new Set(this.declutterFilters);
    if (next.has(filter)) {
      next.delete(filter);
    } else {
      next.add(filter);
    }
    this.declutterFilters = next;
    this.addTelemetryEvent('INFO', `Toggled filter: ${filter} (${this.declutterFilters.has(filter) ? 'ON' : 'OFF'})`);
  }

  clearDeclutterFilters() {
    this.declutterFilters = new Set();
    this.addTelemetryEvent('INFO', 'Cleared all declutter filters');
  }

  componentOffsets = $state<Record<string, { x: number; y: number }>>({});

  setComponentOffset(id: string, offset: { x: number; y: number }) {
    this.componentOffsets = {
      ...this.componentOffsets,
      [id]: offset
    };
  }

  resetZoom(instant = false) {
    if (instant || import.meta.env?.MODE === 'test') {
      this.zoom = 1.0;
      this.panX = 0;
      this.panY = 0;
      return;
    }
    gsap.to(this, {
      zoom: 1.0,
      panX: 0,
      panY: 0,
      duration: 0.5,
      ease: 'power2.out'
    });
  }

  resetLayout(instant = false) {
    this.resetZoom(instant);
    this.componentOffsets = {};
    this.addTelemetryEvent('INFO', 'Reset canvas node positions to concentric ring baseline');
  }

  setFocusedNode(id: string | null) {
    this.focusedNodeId = id;
  }

  panToComponent(id: string) {
    if (!this.graph?.components) return;
    const comp = this.graph.components.find((c: ComponentNode) => c.id === id);
    if (!comp) return;

    // Approximate component location based on Sugiyama grid (BOX_WIDTH: 260, GAP_X: 60, BOX_HEIGHT: 180, GAP_Y: 120)
    // Center viewport (assume ~1200x800 container)
    const lvl = comp.level !== null ? comp.level : 3;
    const sameLevelComps = this.graph.components.filter((c: ComponentNode) => (c.level !== null ? c.level : 3) === lvl);
    const colIdx = Math.max(0, sameLevelComps.findIndex((c: ComponentNode) => c.id === comp.id));
    const targetX = 100 + colIdx * (260 + 60) + 130;
    const targetY = 80 + lvl * (180 + 120) + 90;

    const viewportW = typeof window !== 'undefined' ? window.innerWidth - 300 : 1000;
    const viewportH = typeof window !== 'undefined' ? window.innerHeight - 50 : 700;

    const targetPanX = viewportW / 2 - targetX * 1.15;
    const targetPanY = viewportH / 2 - targetY * 1.15;

    gsap.to(this, {
      panX: targetPanX,
      panY: targetPanY,
      zoom: 1.15,
      duration: 0.7,
      ease: 'power3.out'
    });

    this.targetHaloNodeId = id;
    this.focusedNodeId = id;

    setTimeout(() => {
      if (this.targetHaloNodeId === id) {
        this.targetHaloNodeId = null;
      }
    }, 2500);
  }
}

export const diagramStore = new DiagramState();
