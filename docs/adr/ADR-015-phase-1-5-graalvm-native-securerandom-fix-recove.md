---
id: ADR-015
title: Phase 1.5 — GraalVM Native SecureRandom Fix, Recoverable Rollback Trash & Diagram Export MCP Tool Parity
status: accepted
scope: technical
ticket: #263
supersedes: ADR-014
createdAt: 2026-11-19T17:54:39.648Z
---

# ADR-015: Phase 1.5 — GraalVM Native SecureRandom Fix, Recoverable Rollback Trash & Diagram Export MCP Tool Parity

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #263
- **Supersedes / Amends**: ADR-014

## 1. Context & Spec Drift Trigger
SessionTokenService held a static SecureRandom rejected by GraalVM Native Image (#263), VerifyAndRollbackRefactorUseCase deleted post-snapshot files directly (#228), and ArchlensMcpService.exportArchitectureDiagram lacked @Tool and CLI MCP parity (#247).

## 2. Decision
Instantiate SecureRandom lazily in SessionTokenService.generateToken(), move post-snapshot files removed on rollback to .archlens/trash/<snapshotId>/ with reported trashedFiles, and expose exportArchitectureDiagram across Quarkus MCP (@Tool/@ToolArg) and CLI MCP registry/stdio (11 tools).

## 3. Consequences & Downstream Impact
Restores GraalVM native binary builds, preserves post-snapshot user files in .archlens/trash/ on rollback, and achieves 11-tool MCP parity across HTTP/SSE and stdio.
