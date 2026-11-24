---
id: ADR-016
title: Wave 1 — MCP Write Confinement, Pure-Git Tag Caching, and Watcher Root Switching
status: accepted
scope: technical
ticket: #265
supersedes: ADR-015
createdAt: 2026-11-19T20:11:05.115Z
---

# ADR-016: Wave 1 — MCP Write Confinement, Pure-Git Tag Caching, and Watcher Root Switching

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #265
- **Supersedes / Amends**: ADR-015

## 1. Context & Spec Drift Trigger
Whole-repository audit ahead of `v1.1.7` and `GHSA-pc6m-876p-9hp9` uncovered five untracked gaps (#265): CLI MCP `archlens://fitness` serialized an unawaited Promise (`"{}"`), `ArchlensMcpService.syncDipAdr` wrote `docs/adr/*.md` without `OpenedProjectRegistry` or symlink confinement while `FileMailboxService.readMailbox` created `.archlens/` on read-only `GET`, `ManageSnapshotsUseCase` / `GetFitnessHistoryUseCase` substituted today's working tree for uncached Git tags, and `ProjectFileWatcher` never switched roots when `projectRoot` changed while `diagram.svelte.ts` triggered duplicate `loadFitness()` scans.

## 2. Decision
Make CLI `readMcpResource` async and await `checkArchitecture`; restrict `ArchlensMcpService.syncDipAdr` to the canonical active launch workspace (`.`) or roots registered in `OpenedProjectRegistry`, reject symlinks along `docs/adr` in `SyncAdrUseCase`, and make `FileMailboxService.readMailbox` side-effect-free on disk; materialize uncached Git tags via isolated `GIT_INDEX_FILE` (`git read-tree` + `git checkout-index`) into ephemeral temp directories and cache compiled graphs at `.archlens/cache/<tag>.json`; and restart `ProjectFileWatcher` on canonical root changes while deduplicating `loadFitness()` on project open.

## 3. Consequences & Downstream Impact
Aligns MCP and REST filesystem write boundaries before publishing `GHSA-pc6m-876p-9hp9`, enables accurate historical Git tag comparisons and fitness trends without mutating `.git/HEAD` or `.git/index`, and restores live file watching when switching projects in the workbench UI.
