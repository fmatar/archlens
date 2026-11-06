---
id: ADR-001
title: Quarkus 3.40 MCP 2.0 Hexagonal Decoupling, Virtual Threads & Main Sequence Calibration
status: accepted
scope: technical
ticket: #6262
supersedes: CAP-01, CAP-02, CAP-03, CAP-04
createdAt: 2026-11-06T20:58:40.266Z
---

# ADR-001: Quarkus 3.40 MCP 2.0 Hexagonal Decoupling, Virtual Threads & Main Sequence Calibration

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #6262
- **Supersedes / Amends**: CAP-01, CAP-02, CAP-03, CAP-04

## 1. Context & Spec Drift Trigger
Quarkus MCP audit (3.40.1.1 LTS) and SDLC inspection identified L3-to-L3 coupling between ArchlensMcpService and DiagramResource, missing @RunOnVirtualThread annotations on blocking AST/Git endpoints, MCP tool/resource/prompt asymmetry between Quarkus and CLI, wildcard CORS configuration, and Main Sequence Distance false positives (D=0.86) on pure Ring 0 Java records.

## 2. Decision
1. Decouple ArchlensMcpService from DiagramResource by injecting Ring 1 Use Cases directly and removing manual non-CDI allocations. 2. Annotate blocking MCP and REST endpoints with @RunOnVirtualThread (JEP 491) and add @PreDestroy cleanup to ProjectFileWatcher. 3. Expand Quarkus MCP 2.0 with @Resource, @Prompt, and full Use Case @Tool parity with the CLI stdio server, plus quarkus-mcp-server-hibernate-validator and quarkus-smallrye-health. 4. Calibrate Main Sequence abstractness calculation for immutable Java records/enums and align sdlc-engine.mjs with @fmatar/archlens-skill@0.1.0-Beta-11.

## 3. Consequences & Downstream Impact
Zero L3-to-L3 coupling, full MCP 2.0 tool/resource/prompt parity across HTTP/SSE and CLI stdio, hardened Quarkus 3.40.1.1 configuration, and Grade A (100%) self-AFI score.
