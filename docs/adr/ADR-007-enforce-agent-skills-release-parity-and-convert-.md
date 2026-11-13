---
id: ADR-007
title: Enforce Agent Skills Release Parity and Convert Skill Installer to npx skills
status: accepted
scope: technical
ticket: #8238
supersedes: None
createdAt: 2026-11-13T22:06:30.452Z
---

# ADR-007: Enforce Agent Skills Release Parity and Convert Skill Installer to npx skills

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #8238
- **Supersedes / Amends**: None

## 1. Context & Spec Drift Trigger
Release v1.1.1 suffered skill drift (SKILL.md versions out of sync, .claude-plugin/plugin.json pointing to agentic-sdlc instead of archlens, release.yml ignoring **.md and creating GitHub Releases without gating on npm-release, and CLI installer missing --all on npx skills add).

## 2. Decision
Establish skills/ as single source of truth synced via scripts/sync-skills.js (prepack, set-version.js, CI, and release.yml), gate release creation on npm-release with tarball skill verification, and convert CLI local/global skill installation and updates to npx skills.

## 3. Consequences & Downstream Impact
Guarantees 100% version and byte parity across skills/, cli/skills/, and .claude-plugin/plugin.json on every pack and release, while supporting 15+ coding agents via npx skills with zero added dependencies.
