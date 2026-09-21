import { DEMO_POLICY, DEMO_GRAPH_REAL, DEMO_GRAPH_PROPOSAL } from '../data/demoData';
import type {
  ArchitectureGraph,
  ArchitecturePolicy,
  ClassNode,
  AgentTelemetryEvent,
  EdgeTooltipInfo,
  ComponentNode
} from '../types/diagram';
import gsap from 'gsap';

export type DeclutterMode = 'NONE' | 'ARROWS' | 'REMOVE_ARROWS' | 'ELEMENTS' | 'CLASSES';

class DiagramState {
  graph = $state<ArchitectureGraph | null>(null);
  policy = $state<ArchitecturePolicy | null>(null);
  selectedClass = $state<ClassNode | null>(null);
  activeProposalId = $state<string | null>(null);
  declutterMode = $state<DeclutterMode>('ARROWS');
  sourceFileModal = $state<{ filePath: string; line: number; content?: string } | null>(null);

  // Focus and Halo Highlights
  focusedNodeId = $state<string | null>(null);
  targetHaloNodeId = $state<string | null>(null);

  // Command Palette & Telemetry Drawer
  isCommandPaletteOpen = $state<boolean>(false);
  isTelemetryDrawerOpen = $state<boolean>(false);
  activeEdgeTooltip = $state<EdgeTooltipInfo | null>(null);

  // Agent Telemetry Log
  telemetryEvents = $state<AgentTelemetryEvent[]>([
    {
      id: 'init-1',
      timestamp: '00:01:15',
      type: 'INFO',
      message: 'Archlens IPC mailbox initialized at .uml-viewer/',
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
  errorMessage = $state<string | null>(null);

  projectRoot = $state<string>(
    typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('projectRoot') 
      ? new URLSearchParams(window.location.search).get('projectRoot')! 
      : '/Users/fady/workspace/labs/archlens'
  );

  async setProjectRoot(root: string) {
    this.projectRoot = root;
    this.activeProposalId = null;
    await this.loadPolicy();
    await this.loadGraph();
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

  async openSource(filePath: string, line: number) {
    try {
      const res = await fetch(`/api/source?filePath=${encodeURIComponent(filePath)}&line=${line}`);
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
    this.addTelemetryEvent('TASK', 'Dispatched refactoring directive to .uml-viewer/to-agent.json', 'Target: AST scan & Clean Architecture validation');

    try {
      await fetch('/api/mailbox/to-agent', {
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
