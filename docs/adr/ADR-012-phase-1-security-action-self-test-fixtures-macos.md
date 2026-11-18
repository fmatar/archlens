---
id: ADR-012
title: Phase 1 Security, Action Self-Test Fixtures & macOS Canonical Root Guard
status: accepted
scope: technical
ticket: #224
supersedes: ADR-011
createdAt: 2026-11-18T21:05:45.873Z
---

# ADR-012: Phase 1 Security, Action Self-Test Fixtures & macOS Canonical Root Guard

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #224
- **Supersedes / Amends**: ADR-011

## 1. Context & Spec Drift Trigger
Phase 1 security and CI hardening introduced ProjectPathGuard for filesystem confinement and GitHub Action self-test fixture policies (action/fixtures/*/.archlens/policy.json). On macOS, /var/folders is a symlink to /private/var/folders, causing un-canonicalized absolute file paths inside a canonical project root to be rejected by ProjectPathGuard.resolveFileWithinRoot.

## 2. Decision
Rebase absolute candidate paths whose ancestor directory resolves via toRealPath() to the canonical root onto root in ProjectPathGuard.resolveFileWithinRoot without following symlinks inside the root, and record the Phase 1 Action self-test fixture policies and Dependabot configuration.

## 3. Consequences & Downstream Impact
mvn verify passes across both macOS (/var -> /private/var) and Linux while preserving strict confinement against path traversal and symlink escapes.

## 4. Governance Review Decision
- **Decision**: `ACCEPTED`
- **Reviewer**: @human-operator
- **Rationale**: Reviewed via SDLC Governance Gate
- **Decided At**: 2026-11-18T21:05:47.259Z
