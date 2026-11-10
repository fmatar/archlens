---
id: ADR-004
title: Multi-Format Architecture Diagram Export (Mermaid, SVG, PNG) & v1.1.0 Release
status: accepted
scope: technical
ticket: #4772
supersedes: CAP-01, CAP-02, CAP-03, CAP-04
createdAt: 2026-11-10T06:14:02.734Z
---

# ADR-004: Multi-Format Architecture Diagram Export (Mermaid, SVG, PNG) & v1.1.0 Release

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #4772
- **Supersedes / Amends**: CAP-01, CAP-02, CAP-03, CAP-04

## 1. Context & Spec Drift Trigger
Users and CI pipelines need to export Concentric Ring topologies, Class UML diagrams, and Before/After DIP refactoring blueprints across the Workbench UI, Living ADRs, REST API, MCP server, and headless CLI.

## 2. Decision
Add pure Ring L0 MermaidDiagramGenerator and DiagramExportType, Ring L1 ExportDiagramUseCase and SyncAdrUseCase diagram embedding, Ring L3 REST/MCP/CLI endpoints, and Svelte 5 SVG/PNG/Mermaid exporter, and cut release v1.1.0.

## 3. Consequences & Downstream Impact
Zero Clean Architecture violations, 87.4% backend test coverage, and full multi-format diagram export across all surfaces.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @fmatar
- **Rationale**: Approved for v1.1.0 release
- **Decided At**: 2026-11-10T06:14:03.633Z
