---
id: ADR-024
title: "Release v1.3.0 — Lexical Scanner Sanitizers, Backend-First CLI Delegation & Sealit Proof Kernel"
scope: technical
status: accepted
ticket: N/A
supersedes: [ADR-023]
createdAt: 2026-11-25T16:02:24.228Z
decidedBy: "@fmatar"
decidedAt: 2026-11-25T16:02:45.672Z
decisionReason: "Approved v1.3.0 release consolidating Waves 1-4."
---

# ADR-024: Release v1.3.0 — Lexical Scanner Sanitizers, Backend-First CLI Delegation & Sealit Proof Kernel

- **Scope**: `technical`
- **Status**: `accepted`
- **Linked Ticket**: `N/A`
- **Supersedes / Modifies**: `ADR-023`

## 1. Context & Spec Drift Trigger
Consolidates Wave 1 (@sealit/cli Proof Kernel diff-coverage and ring gates), Wave 2 (#268/#274 frontend dependency updates and DiagramState.loadGraph monotonic sequence guard), Wave 3 (#243/#276 pure-JVM *LexicalScanner renames and linear comment/docstring/string-literal sanitizers), and Wave 4 (#244/#277 backend-first CLI check delegation, loopback/confinement guards, and 9-fixture polyglot parity suite).

## 2. Decision
Synchronize all 15 Maven, NPM, Quarkus, MCP, and Claude plugin manifests, unified archlens skill, README.md, C4_ARCHITECTURE.md, CHANGELOG.md, DECISION_MAP.md, KANBAN.md, and wiki to version 1.3.0 and cut release v1.3.0.

## 3. Consequences & Reversal Criteria
Publishes @fadym/archlens-skill@1.3.0 via npm OIDC Trusted Publishing with Sigstore SLSA provenance, multi-arch GHCR/Docker Hub :v1.3.0/:latest images, archlens-linux-amd64 GraalVM native binary, and advances the floating v1 Action tag to v1.3.0.
