import { DEMO_POLICY, DEMO_GRAPH_REAL, DEMO_GRAPH_PROPOSAL } from '../data/demoData';
import type { ArchitectureGraph, ArchitecturePolicy, ClassNode } from '../types/diagram';

export type DeclutterMode = 'NONE' | 'ARROWS' | 'REMOVE_ARROWS' | 'ELEMENTS' | 'CLASSES';

class DiagramState {
  graph = $state<ArchitectureGraph | null>(null);
  policy = $state<ArchitecturePolicy | null>(null);
  selectedClass = $state<ClassNode | null>(null);
  activeProposalId = $state<string | null>(null);
  declutterMode = $state<DeclutterMode>('ARROWS');
  sourceFileModal = $state<{ filePath: string; line: number; content?: string } | null>(null);

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
      : '/Users/fady/workspace/labs/bogzee'
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
          filePath: data.filePath,
          line: data.targetLine,
          content: data.content
        };
      }
    } catch (e) {
      console.error(e);
    }
  }

  isRegenerating = $state<boolean>(false);
  regenNotice = $state<string | null>(null);

  async triggerRegen() {
    this.isRegenerating = true;
    this.regenNotice = "Agent notified: updating policy & recalculating graph...";
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

    setTimeout(async () => {
      await this.loadGraph(this.activeProposalId);
      this.isRegenerating = false;
      this.regenNotice = "Graph refreshed successfully!";
      setTimeout(() => {
        this.regenNotice = null;
      }, 3000);
    }, 600);
  }

  cycleDeclutter() {
    const modes: DeclutterMode[] = ['NONE', 'ARROWS', 'REMOVE_ARROWS', 'ELEMENTS', 'CLASSES'];
    const nextIdx = (modes.indexOf(this.declutterMode) + 1) % modes.length;
    this.declutterMode = modes[nextIdx];
  }

  resetZoom() {
    this.zoom = 1.0;
    this.panX = 0;
    this.panY = 0;
  }
}

export const diagramStore = new DiagramState();
