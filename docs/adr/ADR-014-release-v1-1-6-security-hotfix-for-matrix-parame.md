---
id: ADR-014
title: Release v1.1.6 — Security Hotfix for Matrix-Parameter Auth Bypass, Chained RCE, CI Gates & Supply Chain
status: accepted
scope: technical
ticket: #247
supersedes: ADR-013
createdAt: 2026-11-18T22:51:02.470Z
---

# ADR-014: Release v1.1.6 — Security Hotfix for Matrix-Parameter Auth Bypass, Chained RCE, CI Gates & Supply Chain

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #247
- **Supersedes / Amends**: ADR-013

## 1. Context & Spec Drift Trigger
Post-v1.1.5 security re-review identified Critical matrix-parameter session-token bypass (/api;x/...) C-1, chained unauthenticated test execution via GET /api/graph auto-open C-2, unauthenticated mailbox and policy mutations C-3, host directory listing outside allowed roots M-6, token logging in non-TTY startups L-1/L-2, workflow script injection and fork-runner exposure H-1, CI-bypassing release/canary workflows H-2/M-1, synthetic proof files M-3, and supply-chain/documentation gaps M-2/M-4/M-5/M-7.

## 2. Decision
Canonicalize paths across both Vert.x SessionTokenFilter and a defense-in-depth JAX-RS PreMatching JaxRsSessionTokenFilter; require explicit authenticated POST /api/projects/open before mailbox, verify, rollback, policy save, or ADR sync; require confirmRunTests=true for verify runTests; stamp workbench-session origin and tokenFingerprint on to-agent commands; confine /api/fs/directories to allowed roots; gate release/dev-release on verify-ci-gate (wait-for-ci-checks.js); harden CI workflows, Maven SHA-512 verification, Quinoa --frozen-lockfile, non-root 1001:1001 containers, docs, CHANGELOG, and stamp v1.1.6.

## 3. Consequences & Downstream Impact
Blocks all r2 bypass and chained RCE vectors across 1.1.6 artifacts while requiring HTTP API/MCP callers to authenticate explicitly, open projects via POST /api/projects/open, and wait for passing CI before release publication.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @human-operator
- **Rationale**: Reviewed via SDLC Governance Gate
- **Decided At**: 2026-11-18T22:51:03.325Z
