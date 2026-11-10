import type {
  ArchitectureGraph,
  ArchitecturePolicy,
  ClassNode,
  ComponentNode,
  DependencyEdge,
  DipInversionPlan
} from '../types/diagram';

export type ExportDiagramType = 'COMPONENT_RINGS' | 'CLASS_UML' | 'DIP_REFACTOR';
export type ExportFormatType = 'MERMAID' | 'SVG' | 'PNG';
export type ExportFramingMode = 'FULL_GRAPH' | 'CURRENT_VIEWPORT';
export type ExportBackgroundMode = 'DARK_SLATE' | 'TRANSPARENT';

export interface DiagramExportOptions {
  diagramType?: ExportDiagramType;
  framing?: ExportFramingMode;
  background?: ExportBackgroundMode;
  includeLegend?: boolean;
  violationsOnly?: boolean;
  focusedNodeId?: string | null;
  dipPlan?: DipInversionPlan | null;
  componentOffsets?: Record<string, { x: number; y: number }>;
  panX?: number;
  panY?: number;
  zoom?: number;
  viewportWidth?: number;
  viewportHeight?: number;
}

const RING_TITLES = [
  'Ring L0: Domain Core',
  'Ring L1: Application',
  'Ring L2: Adapters',
  'Ring L3: Infrastructure'
];

function sanitizeId(raw: string): string {
  return (raw || 'node').replace(/[^a-zA-Z0-9_]/g, '_');
}

function escapeMermaid(raw: string): string {
  return (raw || '').replace(/"/g, "'").replace(/\n/g, ' ');
}

function escapeXml(raw: string): string {
  return (raw || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function ringTitle(level: number | null | undefined): string {
  const lvl = level ?? 3;
  return RING_TITLES[lvl] || `Ring L${lvl}`;
}

function simpleName(fqcn: string | null | undefined, fallback: string): string {
  if (!fqcn || !fqcn.trim()) return fallback;
  const idx = fqcn.lastIndexOf('.');
  return idx >= 0 ? fqcn.slice(idx + 1) : fqcn;
}

function indexClassToComponent(components: ComponentNode[]): Map<string, ComponentNode> {
  const map = new Map<string, ComponentNode>();
  for (const comp of components) {
    map.set(comp.id, comp);
    for (const cls of comp.classes || []) {
      map.set(cls.id, comp);
      if (!map.has(cls.name)) map.set(cls.name, comp);
    }
  }
  return map;
}

/**
 * Generates a Mermaid diagram (flowchart TB or classDiagram) for the selected diagram type.
 */
export function generateMermaidDiagram(
  graph: ArchitectureGraph | null,
  _policy: ArchitecturePolicy | null,
  options: DiagramExportOptions = {}
): string {
  const diagramType = options.diagramType || 'COMPONENT_RINGS';
  if (diagramType === 'DIP_REFACTOR') {
    return generateDipMermaid(graph, options.dipPlan || null);
  }
  if (!graph || !graph.components || graph.components.length === 0) {
    return diagramType === 'CLASS_UML'
      ? 'classDiagram\n  class Empty\n'
      : 'flowchart TB\n  Empty["No Architecture Components Found"]\n';
  }
  if (diagramType === 'CLASS_UML') {
    return generateClassUmlMermaid(
      graph,
      options.focusedNodeId || null,
      Boolean(options.violationsOnly)
    );
  }
  return generateComponentRingsMermaid(graph);
}

function generateComponentRingsMermaid(graph: ArchitectureGraph): string {
  const lines: string[] = ['flowchart TB'];
  const classToComp = indexClassToComponent(graph.components);
  const byLevel = new Map<number, ComponentNode[]>();

  for (const comp of graph.components) {
    const lvl = comp.level ?? 3;
    const list = byLevel.get(lvl) || [];
    list.push(comp);
    byLevel.set(lvl, list);
  }

  const sortedLevels = Array.from(byLevel.keys()).sort((a, b) => a - b);
  for (const lvl of sortedLevels) {
    lines.push(`  subgraph Ring_L${lvl}["${ringTitle(lvl)}"]`);
    for (const comp of byLevel.get(lvl)!) {
      const count = comp.classes?.length || 0;
      lines.push(
        `    ${sanitizeId('comp_' + comp.id)}["${escapeMermaid(comp.label)} (${count} classes)"]`
      );
    }
    lines.push('  end');
  }

  const bundled = new Map<string, { total: number; violations: number }>();
  for (const edge of graph.edges || []) {
    const fromComp = classToComp.get(edge.from);
    const toComp = classToComp.get(edge.to);
    if (!fromComp || !toComp || fromComp.id === toComp.id) continue;
    const key = `${fromComp.id}->${toComp.id}`;
    const item = bundled.get(key) || { total: 0, violations: 0 };
    item.total += 1;
    if (edge.isViolating) item.violations += 1;
    bundled.set(key, item);
  }

  const violatingIndices: number[] = [];
  const conformingIndices: number[] = [];
  let edgeIndex = 0;

  for (const [key, counts] of bundled.entries()) {
    const [fromIdRaw, toIdRaw] = key.split('->');
    const fromId = sanitizeId('comp_' + fromIdRaw);
    const toId = sanitizeId('comp_' + toIdRaw);
    if (counts.violations > 0) {
      lines.push(`  ${fromId} -->|"❌ VIOLATION (${counts.violations}/${counts.total})"| ${toId}`);
      violatingIndices.push(edgeIndex);
    } else {
      lines.push(`  ${fromId} -->|"${counts.total} refs"| ${toId}`);
      conformingIndices.push(edgeIndex);
    }
    edgeIndex += 1;
  }

  for (const idx of violatingIndices) {
    lines.push(`  linkStyle ${idx} stroke:#ef4444,stroke-width:2.5px,color:#fca5a5`);
  }
  for (const idx of conformingIndices) {
    lines.push(`  linkStyle ${idx} stroke:#64748b,stroke-width:1.5px`);
  }

  return lines.join('\n') + '\n';
}

function generateClassUmlMermaid(
  graph: ArchitectureGraph,
  focusedNodeId: string | null,
  violationsOnly: boolean
): string {
  const lines: string[] = ['classDiagram'];
  const classToComp = indexClassToComponent(graph.components);

  const filteredEdges = (graph.edges || []).filter((edge) => {
    if (violationsOnly && !edge.isViolating) return false;
    if (focusedNodeId) {
      const fc = classToComp.get(edge.from);
      const tc = classToComp.get(edge.to);
      return fc?.id === focusedNodeId || tc?.id === focusedNodeId;
    }
    return true;
  });

  const activeClassIds = new Set<string>();
  const activeCompIds = new Set<string>();
  for (const edge of filteredEdges) {
    activeClassIds.add(edge.from);
    activeClassIds.add(edge.to);
    const fc = classToComp.get(edge.from);
    const tc = classToComp.get(edge.to);
    if (fc) activeCompIds.add(fc.id);
    if (tc) activeCompIds.add(tc.id);
  }
  if (!violationsOnly) {
    for (const comp of graph.components) {
      if (focusedNodeId && comp.id !== focusedNodeId) continue;
      for (const cls of comp.classes || []) {
        activeClassIds.add(cls.id);
      }
    }
  } else {
    for (const comp of graph.components) {
      if (!activeCompIds.has(comp.id)) continue;
      for (const cls of (comp.classes || []).slice(0, 2)) {
        activeClassIds.add(cls.id);
      }
    }
  }

  const aliasMap = new Map<string, string>();
  let counter = 1;
  for (const comp of graph.components) {
    for (const cls of comp.classes || []) {
      if (!activeClassIds.has(cls.id)) continue;
      const alias = sanitizeId(`${cls.name}_${counter++}`);
      aliasMap.set(cls.id, alias);
      if (!aliasMap.has(cls.name)) aliasMap.set(cls.name, alias);
      if (!aliasMap.has(comp.id)) aliasMap.set(comp.id, alias);
      appendMermaidClassBlock(lines, cls, alias);
    }
  }

  for (const edge of filteredEdges) {
    const fromAlias = aliasMap.get(edge.from);
    const toAlias = aliasMap.get(edge.to);
    if (!fromAlias || !toAlias || fromAlias === toAlias) continue;
    const arrow =
      edge.kind === 'IMPLEMENTS' ? '..|>' : edge.kind === 'INHERITANCE' ? '--|>' : '-->';
    const label = edge.isViolating ? 'VIOLATION' : edge.label || 'uses';
    lines.push(`  ${fromAlias} ${arrow} ${toAlias} : ${escapeMermaid(label)}`);
  }

  return lines.join('\n') + '\n';
}

function appendMermaidClassBlock(lines: string[], cls: ClassNode, alias: string): void {
  lines.push(`  class ${alias}["${escapeMermaid(cls.name)}"] {`);
  if (cls.stereotype && cls.stereotype !== 'CLASS') {
    lines.push(`    <<${cls.stereotype.toLowerCase()}>>`);
  }
  for (const field of (cls.fields || []).slice(0, 5)) {
    const vis = field.isPrivate ? '-' : '+';
    lines.push(`    ${vis}${sanitizeId(field.type)} ${sanitizeId(field.name)}`);
  }
  for (const method of (cls.methods || []).slice(0, 6)) {
    const vis = method.isPrivate ? '-' : '+';
    lines.push(`    ${vis}${sanitizeId(method.name)}() ${sanitizeId(method.returnType)}`);
  }
  lines.push('  }');
}

function resolveDipContext(
  graph: ArchitectureGraph | null,
  dipPlan: DipInversionPlan | null
): {
  srcSimple: string;
  tgtSimple: string;
  portName: string;
  srcLevel: number;
  tgtLevel: number;
} {
  if (dipPlan) {
    const srcSimple = simpleName(dipPlan.fromClass, 'CallerService');
    const tgtSimple = simpleName(dipPlan.toClass, 'ConcreteAdapter');
    return {
      srcSimple,
      tgtSimple,
      portName: dipPlan.portName || `${tgtSimple}Port`,
      srcLevel: dipPlan.fromLevel ?? 1,
      tgtLevel: dipPlan.toLevel ?? 3
    };
  }
  const edges = graph?.edges || [];
  const violating = edges.find((e) => e.isViolating) || edges[0];
  const srcSimple = simpleName(violating?.from, 'InnerDomainService');
  const tgtSimple = simpleName(violating?.to, 'OuterAdapter');
  return {
    srcSimple,
    tgtSimple,
    portName: `${tgtSimple}Port`,
    srcLevel: 1,
    tgtLevel: 3
  };
}

function generateDipMermaid(
  graph: ArchitectureGraph | null,
  dipPlan: DipInversionPlan | null
): string {
  const ctx = resolveDipContext(graph, dipPlan);
  return [
    'flowchart TB',
    '  subgraph Before["❌ Before: Outward Dependency Violation"]',
    '    direction TB',
    `    B_Src["${escapeMermaid(ctx.srcSimple)} (Ring L${ctx.srcLevel})"] -->|"❌ Violates L${ctx.srcLevel} → L${ctx.tgtLevel}"| B_Tgt["${escapeMermaid(ctx.tgtSimple)} (Ring L${ctx.tgtLevel})"]`,
    '  end',
    '  subgraph After["✅ After: DIP Port Inversion"]',
    '    direction TB',
    `    A_Src["${escapeMermaid(ctx.srcSimple)} (Ring L${ctx.srcLevel})"] -->|"depends on"| A_Port["<<interface>> ${escapeMermaid(ctx.portName)} (Ring L${ctx.srcLevel})"]`,
    `    A_Tgt["${escapeMermaid(ctx.tgtSimple)} (Ring L${ctx.tgtLevel})"] -.->|"implements"| A_Port`,
    '  end',
    '  linkStyle 0 stroke:#ef4444,stroke-width:2.5px,color:#fca5a5',
    '  linkStyle 1 stroke:#10b981,stroke-width:2px,color:#6ee7b7',
    '  linkStyle 2 stroke:#10b981,stroke-width:2px,color:#6ee7b7',
    ''
  ].join('\n');
}

export function formatMermaidMarkdownBlock(
  mermaidCode: string,
  title: string,
  diagramType: ExportDiagramType
): string {
  const subtitle =
    diagramType === 'CLASS_UML'
      ? 'Class & Interface UML Diagram'
      : diagramType === 'DIP_REFACTOR'
        ? 'Dependency Inversion Principle (DIP) Topology'
        : 'Concentric Ring Topology';
  return `### ${title || 'Clean Architecture'} — ${subtitle}\n\n\`\`\`mermaid\n${mermaidCode.trim()}\n\`\`\`\n`;
}

interface PositionedBox {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  comp: ComponentNode;
}

/**
 * Generates a self-contained, publication-ready standalone vector SVG string.
 */
export function generateStandaloneSvg(
  graph: ArchitectureGraph | null,
  policy: ArchitecturePolicy | null,
  options: DiagramExportOptions = {}
): string {
  const diagramType = options.diagramType || 'COMPONENT_RINGS';
  const background = options.background || 'DARK_SLATE';
  const includeLegend = options.includeLegend ?? true;

  if (diagramType === 'DIP_REFACTOR') {
    return generateDipSvg(graph, options.dipPlan || null, background, includeLegend);
  }

  const components = graph?.components || [];
  if (components.length === 0) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 240" width="600" height="240">
      ${background === 'DARK_SLATE' ? '<rect id="export-bg-rect" width="600" height="240" fill="#020617"/>' : ''}
      <text x="300" y="125" text-anchor="middle" fill="#94a3b8" font-family="ui-sans-serif, system-ui, sans-serif" font-size="14">No Architecture Components Found</text>
    </svg>`;
  }

  const BOX_WIDTH = 260;
  const BOX_HEIGHT = diagramType === 'CLASS_UML' ? 210 : 180;
  const GAP_X = 60;
  const GAP_Y = 120;

  const byLevel = new Map<number, ComponentNode[]>();
  for (const comp of components) {
    if (options.focusedNodeId && comp.id !== options.focusedNodeId && diagramType === 'CLASS_UML') {
      continue;
    }
    const lvl = comp.level ?? 3;
    const row = byLevel.get(lvl) || [];
    row.push(comp);
    byLevel.set(lvl, row);
  }
  const sortedLevels = Array.from(byLevel.keys()).sort((a, b) => a - b);

  const positioned = new Map<string, PositionedBox>();
  const offsets = options.componentOffsets || {};

  sortedLevels.forEach((level, rowIdx) => {
    const row = byLevel.get(level)!;
    row.forEach((comp, colIdx) => {
      const off = offsets[comp.id] || { x: 0, y: 0 };
      const x = 100 + colIdx * (BOX_WIDTH + GAP_X) + off.x;
      const y = 90 + rowIdx * (BOX_HEIGHT + GAP_Y) + off.y;
      positioned.set(comp.id, { id: comp.id, x, y, width: BOX_WIDTH, height: BOX_HEIGHT, comp });
    });
  });

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const box of positioned.values()) {
    minX = Math.min(minX, box.x - 40);
    minY = Math.min(minY, box.y - 45);
    maxX = Math.max(maxX, box.x + box.width + 40);
    maxY = Math.max(maxY, box.y + box.height + 40);
  }
  if (!Number.isFinite(minX)) {
    minX = 0;
    minY = 0;
    maxX = 960;
    maxY = 600;
  }

  const legendHeight = includeLegend ? 64 : 0;
  const pad = 40;
  const viewX = Math.floor(minX - pad);
  const viewY = Math.floor(minY - pad);
  const viewW = Math.max(760, Math.ceil(maxX - minX + pad * 2));
  const viewH = Math.max(420, Math.ceil(maxY - minY + pad * 2 + legendHeight));

  const classToBox = new Map<string, PositionedBox>();
  for (const box of positioned.values()) {
    classToBox.set(box.id, box);
    for (const cls of box.comp.classes || []) {
      classToBox.set(cls.id, box);
      if (!classToBox.has(cls.name)) classToBox.set(cls.name, box);
    }
  }

  const tierElements: string[] = [];
  sortedLevels.forEach((level, rowIdx) => {
    const y = 90 + rowIdx * (BOX_HEIGHT + GAP_Y) - 32;
    const h = BOX_HEIGHT + 64;
    tierElements.push(
      `<g>
        <rect x="${viewX + 24}" y="${y}" width="${viewW - 48}" height="${h}" rx="12" fill="#0f172a" fill-opacity="0.45" stroke="#1e293b" stroke-width="1" stroke-dasharray="8 6"/>
        <text x="${viewX + 44}" y="${y + 22}" fill="#64748b" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="11" font-weight="600">${escapeXml(ringTitle(level).toUpperCase())}</text>
      </g>`
    );
  });

  const edgeElements = renderSvgEdges(graph?.edges || [], classToBox, Boolean(options.violationsOnly));
  const boxElements = Array.from(positioned.values()).map((box) =>
    renderSvgComponentBox(box, diagramType === 'CLASS_UML')
  );

  const violationsCount = (graph?.edges || []).filter((e) => e.isViolating).length;
  const cyclesCount = graph?.cycles?.length || 0;
  const title = graph?.title || policy?.title || 'Clean Architecture Topology';

  const legendMarkup = includeLegend
    ? renderSvgLegendFooter(viewX + 24, viewY + viewH - 54, viewW - 48, title, violationsCount, cyclesCount)
    : '';

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewX} ${viewY} ${viewW} ${viewH}" width="${viewW}" height="${viewH}">
  <defs>
    <marker id="exp-arrow-normal" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#64748b" />
    </marker>
    <marker id="exp-arrow-violating" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#ef4444" />
    </marker>
  </defs>
  ${background === 'DARK_SLATE' ? `<rect id="export-bg-rect" x="${viewX}" y="${viewY}" width="${viewW}" height="${viewH}" fill="#020617"/>` : ''}
  ${tierElements.join('\n  ')}
  ${edgeElements.join('\n  ')}
  ${boxElements.join('\n  ')}
  ${legendMarkup}
</svg>`;
}

function renderSvgEdges(
  edges: DependencyEdge[],
  classToBox: Map<string, PositionedBox>,
  violationsOnly: boolean
): string[] {
  const bundled = new Map<
    string,
    { fromBox: PositionedBox; toBox: PositionedBox; count: number; violations: number }
  >();

  for (const edge of edges) {
    if (violationsOnly && !edge.isViolating) continue;
    const fromBox = classToBox.get(edge.from);
    const toBox = classToBox.get(edge.to);
    if (!fromBox || !toBox || fromBox.id === toBox.id) continue;
    const key = `${fromBox.id}->${toBox.id}`;
    const entry = bundled.get(key) || { fromBox, toBox, count: 0, violations: 0 };
    entry.count += 1;
    if (edge.isViolating) entry.violations += 1;
    bundled.set(key, entry);
  }

  const out: string[] = [];
  for (const item of bundled.values()) {
    const isViolating = item.violations > 0;
    const goingDown = item.fromBox.y < item.toBox.y;
    const offset = isViolating ? -14 : 14;
    const x1 = item.fromBox.x + item.fromBox.width / 2 + offset;
    const y1 = goingDown ? item.fromBox.y + item.fromBox.height : item.fromBox.y;
    const x2 = item.toBox.x + item.toBox.width / 2 + offset;
    const y2 = goingDown ? item.toBox.y : item.toBox.y + item.toBox.height;
    const cy = (y1 + y2) / 2;
    const stroke = isViolating ? '#ef4444' : '#64748b';
    const marker = isViolating ? 'url(#exp-arrow-violating)' : 'url(#exp-arrow-normal)';
    const width = isViolating ? 2.5 : 1.6;
    out.push(
      `<path d="M ${x1} ${y1} C ${x1} ${cy}, ${x2} ${cy}, ${x2} ${y2}" fill="none" stroke="${stroke}" stroke-width="${width}" marker-end="${marker}"/>`
    );
  }
  return out;
}

function renderSvgComponentBox(box: PositionedBox, detailedUml: boolean): string {
  const classes = box.comp.classes || [];
  const maxRows = detailedUml ? 6 : 5;
  const rows: string[] = [];

  for (let i = 0; i < Math.min(classes.length, maxRows); i++) {
    const cls = classes[i];
    const py = box.y + 50 + i * 24;
    const crapMu = cls.crap?.mu ?? 0;
    const dotColor = crapMu <= 4 ? '#10b981' : crapMu <= 15 ? '#f59e0b' : '#ef4444';
    const stereo =
      detailedUml && cls.stereotype && cls.stereotype !== 'CLASS'
        ? `«${cls.stereotype.toLowerCase()}» `
        : '';
    rows.push(
      `<rect x="${box.x + 10}" y="${py}" width="${box.width - 20}" height="20" rx="5" fill="#1e293b" stroke="#334155" stroke-width="0.8"/>
       <text x="${box.x + 16}" y="${py + 14}" fill="#e2e8f0" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">${escapeXml(stereo + cls.name)}</text>
       <circle cx="${box.x + box.width - 20}" cy="${py + 10}" r="3.5" fill="${dotColor}"/>`
    );
  }

  return `<g>
    <rect x="${box.x}" y="${box.y}" width="${box.width}" height="${box.height}" rx="12" fill="#0f172a" stroke="#334155" stroke-width="1.5"/>
    <text x="${box.x + 14}" y="${box.y + 22}" fill="#f8fafc" font-family="ui-sans-serif, system-ui, sans-serif" font-size="13" font-weight="700">${escapeXml(box.comp.label)}</text>
    <text x="${box.x + 14}" y="${box.y + 39}" fill="#94a3b8" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">${classes.length} classes • Ring L${box.comp.level ?? 3}</text>
    ${rows.join('\n    ')}
  </g>`;
}

function generateDipSvg(
  graph: ArchitectureGraph | null,
  dipPlan: DipInversionPlan | null,
  background: ExportBackgroundMode,
  includeLegend: boolean
): string {
  const ctx = resolveDipContext(graph, dipPlan);
  const w = 880;
  const h = includeLegend ? 410 : 350;

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">
  <defs>
    <marker id="dip-arrow-red" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#ef4444" />
    </marker>
    <marker id="dip-arrow-green" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#10b981" />
    </marker>
  </defs>
  ${background === 'DARK_SLATE' ? `<rect id="export-bg-rect" width="${w}" height="${h}" fill="#020617"/>` : ''}
  <!-- BEFORE PANEL -->
  <rect x="28" y="24" width="396" height="296" rx="14" fill="#0f172a" stroke="#7f1d1d" stroke-width="1.5"/>
  <text x="48" y="52" fill="#fca5a5" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="12" font-weight="700">❌ BEFORE: OUTWARD DEPENDENCY VIOLATION</text>
  <rect x="96" y="82" width="260" height="68" rx="10" fill="#1e293b" stroke="#475569" stroke-width="1.2"/>
  <text x="226" y="112" text-anchor="middle" fill="#f8fafc" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="12" font-weight="700">${escapeXml(ctx.srcSimple)}</text>
  <text x="226" y="132" text-anchor="middle" fill="#94a3b8" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">Inner Ring L${ctx.srcLevel}</text>
  <line x1="226" y1="150" x2="226" y2="216" stroke="#ef4444" stroke-width="2.5" marker-end="url(#dip-arrow-red)"/>
  <text x="238" y="188" fill="#fca5a5" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">Violates L${ctx.srcLevel} → L${ctx.tgtLevel}</text>
  <rect x="96" y="220" width="260" height="68" rx="10" fill="#1e293b" stroke="#ef4444" stroke-width="1.5"/>
  <text x="226" y="250" text-anchor="middle" fill="#f8fafc" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="12" font-weight="700">${escapeXml(ctx.tgtSimple)}</text>
  <text x="226" y="270" text-anchor="middle" fill="#fca5a5" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">Outer Ring L${ctx.tgtLevel}</text>

  <!-- AFTER PANEL -->
  <rect x="456" y="24" width="396" height="296" rx="14" fill="#0f172a" stroke="#065f46" stroke-width="1.5"/>
  <text x="476" y="52" fill="#6ee7b7" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="12" font-weight="700">✅ AFTER: DIP INTERFACE PORT INVERSION</text>
  <rect x="480" y="82" width="165" height="68" rx="10" fill="#1e293b" stroke="#475569" stroke-width="1.2"/>
  <text x="562" y="112" text-anchor="middle" fill="#f8fafc" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="11" font-weight="700">${escapeXml(ctx.srcSimple)}</text>
  <text x="562" y="132" text-anchor="middle" fill="#94a3b8" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">Inner Ring L${ctx.srcLevel}</text>

  <rect x="665" y="82" width="165" height="68" rx="10" fill="#064e3b" stroke="#10b981" stroke-width="1.5"/>
  <text x="747" y="108" text-anchor="middle" fill="#6ee7b7" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">«interface»</text>
  <text x="747" y="124" text-anchor="middle" fill="#ecfdf5" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="11" font-weight="700">${escapeXml(ctx.portName)}</text>
  <text x="747" y="140" text-anchor="middle" fill="#a7f3d0" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="9">Inner Ring L${ctx.srcLevel}</text>

  <line x1="645" y1="116" x2="662" y2="116" stroke="#10b981" stroke-width="2" marker-end="url(#dip-arrow-green)"/>

  <rect x="600" y="220" width="230" height="68" rx="10" fill="#1e293b" stroke="#10b981" stroke-width="1.5"/>
  <text x="715" y="250" text-anchor="middle" fill="#f8fafc" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="12" font-weight="700">${escapeXml(ctx.tgtSimple)}</text>
  <text x="715" y="270" text-anchor="middle" fill="#6ee7b7" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">Outer Adapter (Ring L${ctx.tgtLevel})</text>
  <line x1="735" y1="220" x2="745" y2="154" stroke="#10b981" stroke-width="2" stroke-dasharray="5 4" marker-end="url(#dip-arrow-green)"/>
  <text x="756" y="192" fill="#6ee7b7" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="10">implements</text>
  ${includeLegend ? renderSvgLegendFooter(28, 336, 824, 'Dependency Inversion Principle (DIP) Blueprint', 0, 0) : ''}
</svg>`;
}

function renderSvgLegendFooter(
  x: number,
  y: number,
  width: number,
  title: string,
  violations: number,
  cycles: number
): string {
  const statusColor = violations > 0 ? '#fca5a5' : '#6ee7b7';
  const statusText =
    violations > 0 ? `${violations} Outward Violation(s)` : 'Conforming (0 Violations)';
  return `<g>
    <rect x="${x}" y="${y}" width="${width}" height="42" rx="8" fill="#0f172a" stroke="#1e293b" stroke-width="1"/>
    <text x="${x + 16}" y="${y + 25}" fill="#f1f5f9" font-family="ui-sans-serif, system-ui, sans-serif" font-size="12" font-weight="600">Archlens Workbench • ${escapeXml(title)}</text>
    <text x="${x + width - 16}" y="${y + 25}" text-anchor="end" fill="${statusColor}" font-family="ui-monospace, SFMono-Regular, Menlo, monospace" font-size="11">${escapeXml(statusText)} • ${cycles} ADP Cycle(s)</text>
  </g>`;
}

/**
 * Rasterizes a standalone SVG string into a High-DPI (2x Retina) PNG Blob in the browser.
 */
export async function rasterizeSvgToPngBlob(svgString: string, scale = 2): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const svgBlob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(svgBlob);
    const img = new Image();
    img.onload = () => {
      try {
        const width = (img.naturalWidth || 960) * scale;
        const height = (img.naturalHeight || 600) * scale;
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          URL.revokeObjectURL(url);
          reject(new Error('Canvas 2D context unavailable'));
          return;
        }
        ctx.drawImage(img, 0, 0, width, height);
        URL.revokeObjectURL(url);
        canvas.toBlob((blob) => {
          if (blob) resolve(blob);
          else reject(new Error('Failed to encode PNG blob'));
        }, 'image/png');
      } catch (err) {
        URL.revokeObjectURL(url);
        reject(err);
      }
    };
    img.onerror = (err) => {
      URL.revokeObjectURL(url);
      reject(err);
    };
    img.src = url;
  });
}
