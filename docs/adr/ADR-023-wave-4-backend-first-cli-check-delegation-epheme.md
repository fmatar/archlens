---
id: ADR-023
title: "Wave 4 — Backend-First CLI check Delegation, Ephemeral Loopback Lifecycle & Engine Parity Suite"
scope: architectural
status: accepted
ticket: 244
supersedes: [ADR-022]
createdAt: 2026-11-25T14:19:36.812Z
decidedBy: "@fmatar"
decidedAt: 2026-11-25T14:38:16.812Z
decisionReason: "Approved Wave 4 (#244): backend-first CLI check delegation with ephemeral loopback lifecycle and 6-fixture engine parity suite."
---

# ADR-023: Wave 4 — Backend-First CLI check Delegation, Ephemeral Loopback Lifecycle & Engine Parity Suite

- **Scope**: `architectural`
- **Status**: `accepted`
- **Linked Ticket**: `244`
- **Supersedes / Modifies**: `ADR-022`

## 1. Context & Spec Drift Trigger
Issue #244 noted that `archlens check` always ran the standalone Node.js regex/lexical analyzer (`cli/src/checker.js`), which could diverge from the Quarkus backend scanners (`JavaAstScanner` + `*LexicalScanner`) and lacked Rust/Go import analysis.

## 2. Decision
Introduce `cli/src/backend-checker.js` so `archlens check` delegates to the Quarkus backend engine by default (reusing a live authenticated server when mounted on the target root, or spawning an ephemeral loopback native binary / Quarkus runner JAR and terminating it in `finally`, with optional `--docker` container support), retain `cli/src/checker.js` as the explicit `--offline` / `--max-files` / no-backend fallback with stderr-only notices and `.rs`/`.go` warnings, and enforce a 6-fixture Java/Python/TypeScript backend-vs-offline parity suite in `scripts/engine-parity.js`.

## 3. Consequences & Reversal Criteria
CI and developer workstations with Java/native binaries get identical backend-grade `check` results without leaked background daemons, restricted/offline environments retain zero-dependency pure-JS execution via `--offline` or automatic fallback, and CI smoke tests continuously verify parity across Java, Python, and TypeScript fixtures.
