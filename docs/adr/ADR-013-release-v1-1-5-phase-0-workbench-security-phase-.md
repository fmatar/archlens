---
id: ADR-013
title: Release v1.1.5 — Phase 0 Workbench Security & Phase 1 CLI, GitHub Action & CI Hardening
status: accepted
scope: technical
ticket: #247
supersedes: ADR-012
createdAt: 2026-11-18T21:53:04.006Z
---

# ADR-013: Release v1.1.5 — Phase 0 Workbench Security & Phase 1 CLI, GitHub Action & CI Hardening

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #247
- **Supersedes / Amends**: ADR-012

## 1. Context & Spec Drift Trigger
Tracking epic #247 completed Phase 0 (loopback-by-default binding #224, per-launch session token authentication #225, canonical project-root path guard #226/#258, opened-project verify/rollback restriction #227, snapshot ID and manifest validation #228, and process-tree test timeout #235) and Phase 1 (JSONC/backup MCP installer #229, Action bash-array quoting and numeric flag validation #230, bundled Action CLI and floating v1 tag job #231, uncapped file scan with opt-in --max-files #232, tag-only npm release with registry propagation backoff #233, and Dependabot + CodeQL SARIF upload #234/#236).

## 2. Decision
Synchronize all 12 Maven/NPM/Quarkus/MCP manifests, 3 bundled skills, README.md, release.yml, and wiki/Codebase-Map.md to version 1.1.5 and publish release v1.1.5.

## 3. Consequences & Downstream Impact
Ships hardened v1.1.5 artifacts across npm (@fmatar/archlens-skill@1.1.5), GHCR/Docker Hub, GraalVM Linux native binary, and establishes the floating v1 tag for fmatar/archlens@v1.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @human-operator
- **Rationale**: Reviewed via SDLC Governance Gate
- **Decided At**: 2026-11-18T21:53:05.597Z
