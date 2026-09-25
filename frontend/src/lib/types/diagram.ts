export type Stereotype = 'CLASS' | 'INTERFACE' | 'RECORD' | 'ENUM' | 'ABSTRACT';
export type EdgeKind = 'DEPENDENCY' | 'IMPLEMENTS' | 'INHERITANCE' | 'ASSOCIATION' | 'AGGREGATION' | 'COMPOSITION';

export interface CrapScore {
  mu: number;
  max: number;
  sigma: number;
}

export interface FieldNode {
  name: string;
  type: string;
  isPrivate: boolean;
}

export interface MethodNode {
  name: string;
  args: string[];
  returnType: string;
  isPrivate: boolean;
  cc: number;
  coverage: number;
  crap: number;
  killed: number;
  survived: number;
  uncovered: number;
  line: number;
}

export interface ClassNode {
  id: string;
  name: string;
  packageName: string;
  filePath: string;
  stereotype: Stereotype;
  isForeign: boolean;
  level: number | null;
  crap: CrapScore;
  coverage: number;
  cc: number;
  killed: number;
  survived: number;
  uncovered: number;
  fields: FieldNode[];
  methods: MethodNode[];
}

export interface ComponentNode {
  id: string;
  label: string;
  level: number | null;
  crap: CrapScore;
  mutationScore: number;
  childPackageIds: string[];
  packages?: string[];
  classes: ClassNode[];
  x?: number;
  y?: number;
  width?: number;
  height?: number;
}

export interface DependencyEdge {
  from: string;
  to: string;
  kind: EdgeKind;
  label?: string;
  isViolating: boolean;
}

export interface ArchitectureGraph {
  title: string;
  isProposal: boolean;
  activeProposalId?: string;
  components: ComponentNode[];
  edges: DependencyEdge[];
  unassigned: ClassNode[];
}

export interface ProposalLayer {
  id: string;
  label: string;
  packages: string[];
}

export interface Proposal {
  id: string;
  name: string;
  layers: ProposalLayer[];
  omit: string[];
}

export interface ArchitecturePolicy {
  title: string;
  src: string;
  prefix: string;
  hierarchical: boolean;
  order: string[];
  levels: string[][];
  foreign: string[];
  proposals: Proposal[];
  omit: string[];
}

export type TelemetryEventType = 'INFO' | 'SUCCESS' | 'WARNING' | 'TASK';

export interface AgentTelemetryEvent {
  id: string;
  timestamp: string;
  type: TelemetryEventType;
  message: string;
  details?: string;
}

export interface EdgeTooltipInfo {
  edge: DependencyEdge;
  fromLabel: string;
  toLabel: string;
  fromLevel: number | null;
  toLevel: number | null;
  x: number;
  y: number;
  isBundled?: boolean;
  bundleCount?: number;
  violationCount?: number;
}

export type DeclutterFilter =
  | 'HIDE_CONFORMING_EDGES'  // Violation X-Ray ('V')
  | 'HIDE_ALL_EDGES'         // Hide all lines ('A')
  | 'HIDE_CLASSES'           // Compact macro cards ('C')
  | 'HIDE_TIER_LANES'        // Hide background tier rings ('T')
  | 'ISOLATE_NEIGHBORHOOD';  // 1-Hop focus isolation ('F')

export interface BundledEdge {
  id: string;
  fromCompId: string;
  toCompId: string;
  fromNode: { x: number; y: number; width: number; height: number; comp: ComponentNode };
  toNode: { x: number; y: number; width: number; height: number; comp: ComponentNode };
  edges: DependencyEdge[];
  totalCount: number;
  violationCount: number;
  isViolating: boolean;
  representativeEdge: DependencyEdge;
}

export interface SnapshotInfo {
  id: string;
  label: string;
  date?: string;
  tag?: string;
  isLive?: boolean;
}

export interface DiffMetrics {
  addedNodes: number;
  removedNodes: number;
  newViolations: number;
  fixedViolations: number;
  totalBefore: number;
  totalAfter: number;
}
