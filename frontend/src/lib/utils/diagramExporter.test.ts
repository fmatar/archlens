import { describe, it, expect } from 'vitest';
import {
  generateMermaidDiagram,
  generateStandaloneSvg,
  formatMermaidMarkdownBlock
} from './diagramExporter';
import { DEMO_GRAPH_REAL, DEMO_POLICY, SHOWCASE_PRESETS } from '../data/demoData';

describe('diagramExporter', () => {
  const violatingGraph = SHOWCASE_PRESETS[0].graph;
  const violatingPolicy = SHOWCASE_PRESETS[0].policy;

  it('generates Concentric Ring Component Mermaid flowchart TB with ring subgraphs and violation linkStyle', () => {
    const mmd = generateMermaidDiagram(violatingGraph, violatingPolicy, {
      diagramType: 'COMPONENT_RINGS'
    });

    expect(mmd).toMatch(/^flowchart TB/);
    expect(mmd).toContain('subgraph Ring_L0["Ring L0: Domain Core"]');
    expect(mmd).toContain('VIOLATION');
    expect(mmd).toContain('linkStyle');
    expect(mmd).toContain('stroke:#ef4444');
  });

  it('handles null or empty graph in generateMermaidDiagram gracefully', () => {
    const emptyMmd = generateMermaidDiagram(null, null, {
      diagramType: 'COMPONENT_RINGS'
    });
    expect(emptyMmd).toContain('flowchart TB');
    expect(emptyMmd).toContain('No Architecture Components Found');
  });

  it('generates native Mermaid classDiagram honoring violationsOnly and focusedNodeId filters', () => {
    const fullUml = generateMermaidDiagram(DEMO_GRAPH_REAL, DEMO_POLICY, {
      diagramType: 'CLASS_UML',
      violationsOnly: false
    });
    expect(fullUml).toMatch(/^classDiagram/);
    expect(fullUml).toContain('-->');

    const xRayUml = generateMermaidDiagram(violatingGraph, violatingPolicy, {
      diagramType: 'CLASS_UML',
      violationsOnly: true
    });
    expect(xRayUml).toMatch(/^classDiagram/);
    expect(xRayUml).toContain('VIOLATION');

    const firstCompId = DEMO_GRAPH_REAL.components[0]?.id;
    const focusedUml = generateMermaidDiagram(DEMO_GRAPH_REAL, DEMO_POLICY, {
      diagramType: 'CLASS_UML',
      focusedNodeId: firstCompId
    });
    expect(focusedUml).toMatch(/^classDiagram/);
  });

  it('generates Before vs After DIP Refactoring Mermaid flowchart from explicit DipInversionPlan or graph violation', () => {
    const autoDip = generateMermaidDiagram(DEMO_GRAPH_REAL, DEMO_POLICY, {
      diagramType: 'DIP_REFACTOR'
    });
    expect(autoDip).toMatch(/^flowchart TB/);
    expect(autoDip).toContain('subgraph Before');
    expect(autoDip).toContain('subgraph After');
    expect(autoDip).toContain('stroke:#ef4444');
    expect(autoDip).toContain('stroke:#10b981');

    const customDip = generateMermaidDiagram(DEMO_GRAPH_REAL, DEMO_POLICY, {
      diagramType: 'DIP_REFACTOR',
      dipPlan: {
        fromClass: 'com.acme.domain.BillingService',
        fromPackage: 'com.acme.domain',
        fromLevel: 0,
        toClass: 'com.acme.infra.StripeClient',
        toPackage: 'com.acme.infra',
        toLevel: 3,
        portName: 'StripeClientPort',
        portPackage: 'com.acme.domain',
        portInterfaceCode: 'interface StripeClientPort {}',
        adapterRefactorPreview: '',
        callerRefactorPreview: '',
        surgicalPrompt: ''
      }
    });
    expect(customDip).toContain('BillingService');
    expect(customDip).toContain('StripeClientPort');
    expect(customDip).toContain('StripeClient');
  });

  it('wraps Mermaid diagram in fenced Markdown block with title', () => {
    const mmd = 'flowchart TB\n  A --> B\n';
    const md = formatMermaidMarkdownBlock(mmd, 'Sample Architecture', 'COMPONENT_RINGS');
    expect(md).toContain('### Sample Architecture — Concentric Ring Topology');
    expect(md).toContain('```mermaid\nflowchart TB\n  A --> B\n```');
  });

  it('generates standalone vector SVG across COMPONENT_RINGS, CLASS_UML, and DIP_REFACTOR with Dark Slate and Transparent themes', () => {
    const svgRings = generateStandaloneSvg(DEMO_GRAPH_REAL, DEMO_POLICY, {
      diagramType: 'COMPONENT_RINGS',
      framing: 'FULL_GRAPH',
      background: 'DARK_SLATE',
      includeLegend: true
    });
    expect(svgRings).toContain('<svg');
    expect(svgRings).toContain('xmlns="http://www.w3.org/2000/svg"');
    expect(svgRings).toContain('#020617');
    expect(svgRings).toContain('Clean Architecture');

    const svgTransparent = generateStandaloneSvg(DEMO_GRAPH_REAL, DEMO_POLICY, {
      diagramType: 'CLASS_UML',
      framing: 'CURRENT_VIEWPORT',
      background: 'TRANSPARENT',
      includeLegend: false,
      violationsOnly: true
    });
    expect(svgTransparent).toContain('<svg');
    expect(svgTransparent).not.toContain('id="export-bg-rect"');

    const svgDip = generateStandaloneSvg(DEMO_GRAPH_REAL, DEMO_POLICY, {
      diagramType: 'DIP_REFACTOR',
      framing: 'FULL_GRAPH',
      background: 'DARK_SLATE',
      includeLegend: true
    });
    expect(svgDip).toContain('<svg');
    expect(svgDip).toContain('BEFORE');
    expect(svgDip).toContain('AFTER');
  });
});
