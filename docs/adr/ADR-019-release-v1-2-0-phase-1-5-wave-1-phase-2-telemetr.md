---
id: ADR-019
title: Release v1.2.0 — Phase 1.5, Wave 1, Phase 2 Telemetry & Phase 3 Clean Architecture Ports
status: accepted
scope: technical
ticket: #247
supersedes: ADR-018
createdAt: 2026-11-24T03:03:17.708Z
---

# ADR-019: Release v1.2.0 — Phase 1.5, Wave 1, Phase 2 Telemetry & Phase 3 Clean Architecture Ports

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #247
- **Supersedes / Amends**: ADR-018

## 1. Context & Spec Drift Trigger
Consolidates Phase 1.5 (#263/#264: GraalVM Native SecureRandom fix, recoverable rollback trash, exportArchitectureDiagram MCP @Tool parity), Wave 1 (#265/#266: MCP syncDipAdr confinement, isolated GIT_INDEX_FILE tag caching, live ProjectFileWatcher root switching), Phase 2 (#237-#241/#267: explicit skippedFiles telemetry across scanners/CLI/UI, spawnSync argument arrays, Clojure/legacy removal, Node >=22 alignment), and Phase 3 (#242,#245,#246/#269: <=5 MB blob guard, FilesystemGateway/TestRunnerGateway ports, constructor DI, ratcheted Vitest coverage, and Playwright E2E CI).

## 2. Decision
Synchronize all 12 Maven/NPM/Quarkus/MCP manifests, unified archlens skill, README.md, C4_ARCHITECTURE.md, CHANGELOG.md, DECISION_MAP.md, and wiki to version 1.2.0 and cut release v1.2.0.

## 3. Consequences & Downstream Impact
Publishes v1.2.0 across npm (@fmatar/archlens-skill@1.2.0), GHCR/Docker Hub multi-arch images, GraalVM Linux native binary, and advances the floating v1 Action tag to v1.2.0.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @human-operator
- **Rationale**: Reviewed via SDLC Governance Gate
- **Decided At**: 2026-11-24T03:03:21.566Z
