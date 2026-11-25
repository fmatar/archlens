---
id: ADR-021
title: "Wave 2 — Frontend Minor/Patch Dependency Updates & Monotonic loadGraph Sequence Guard"
scope: technical
status: accepted
ticket: 268
supersedes: [ADR-020]
createdAt: 2026-11-25T03:29:31.590Z
decidedBy: "@fmatar"
decidedAt: 2026-11-25T04:46:53.247Z
decisionReason: "Approved Wave 1 Sealit Proof Kernel and Wave 2 PR #268 updates"
---

# ADR-021: Wave 2 — Frontend Minor/Patch Dependency Updates & Monotonic loadGraph Sequence Guard

- **Scope**: `technical`
- **Status**: `accepted`
- **Linked Ticket**: `268`
- **Supersedes / Modifies**: `ADR-020`

## 1. Context & Spec Drift Trigger
Dependabot PR #268 bumped 7 minor/patch frontend dependencies (including svelte 5.59.1, vitest 5.1.1, and @playwright/test 1.65.1) and surfaced an out-of-order async race in DiagramState where a slow initial graph scan clobbered an active proposal, showcase preset, sandbox proposal, or policy save. Simultaneously, repository governance is transitioning from the legacy @fmatar/agentic-sdlc v0.1.0 engine to the Sealit Proof Kernel (AGENTS.md, CLAUDE.md, .gitignore, PRODUCT.md).

## 2. Decision
Adopt the 7 minor/patch updates in frontend/package.json and frontend/pnpm-lock.yaml, add lcov coverage reporting in vite.config.ts for Sealit diff coverage, centralize monotonic graph request invalidation via nextGraphRequest(loading) across loadGraph, savePolicy (including 401 authRequired and activeProposalId reset), loadShowcasePreset, and saveSandboxAsProposal, and align AGENTS.md/CLAUDE.md/.gitignore with the Sealit Proof Kernel.

## 3. Consequences & Reversal Criteria
Eliminates stale out-of-order graph state overwrites and isLoading deadlocks across live sessions, SSE live sync, and Playwright E2E, keeps frontend dependencies current, and establishes HMAC-SHA256 Stage 7 Sealit receipts in docs/AUDIT_CHAIN.jsonl.
