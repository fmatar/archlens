---
id: ADR-008
title: Release v1.1.2 — Agent Skills Release Parity & npx skills Ecosystem Publication
status: accepted
scope: technical
ticket: #9283
supersedes: ADR-007
createdAt: 2026-11-13T23:06:56.408Z
---

# ADR-008: Release v1.1.2 — Agent Skills Release Parity & npx skills Ecosystem Publication

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #9283
- **Supersedes / Amends**: ADR-007

## 1. Context & Spec Drift Trigger
v1.1.1 is already published and immutable on registry.npmjs.org; cutting patch release v1.1.2 publishes @fmatar/archlens-skill@1.1.2 with synchronized skills/, cli/skills/, and .claude-plugin/plugin.json (#8238 / ADR-007).

## 2. Decision
Synchronize all 15+ Maven, npm, Quarkus, MCP, REST, skill, README, and workflow version manifests to 1.1.2 via set-version.js + sync-skills.js, roll CHANGELOG.md ([1.1.2] - 2026-11-13), and generate cryptographic delivery regression proof.

## 3. Consequences & Downstream Impact
Tag v1.1.2 builds and publishes @fmatar/archlens-skill@1.1.2 to public npm first, followed by multi-arch GHCR container images and GraalVM native binaries, enabling npx skills add fmatar/archlens on main.
