---
id: ADR-011
title: Release v1.1.4 — Fix npx Symlink Entrypoint Resolution in @fmatar/archlens-skill
status: accepted
scope: technical
ticket: #4934
supersedes: ADR-010
createdAt: 2026-11-17T18:22:12.031Z
---

# ADR-011: Release v1.1.4 — Fix npx Symlink Entrypoint Resolution in @fmatar/archlens-skill

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #4934
- **Supersedes / Amends**: ADR-010

## 1. Context & Spec Drift Trigger
Launching @fmatar/archlens-skill 1.1.3 via npx or global node_modules/.bin symlinks exited silently with code 0 because cli/bin/index.js compared import.meta.url (real path) against path.resolve(process.argv[1]) (un-resolved symlink).

## 2. Decision
Resolve process.argv[1] via fs.realpathSync(process.argv[1]) guarded by fs.existsSync(process.argv[1]) in cli/bin/index.js, add end-to-end symlink regression coverage in cli/test/cli-bin.test.js, triage unpatched build-only Vite/Vitest devDependency advisory GHSA-fc5q-39xc-vjpf (picomatch <=4.0.7, zero runtime bundle exposure) in frontend/pnpm-workspace.yaml until upstream ships >=4.0.8, synchronize 1.1.4 manifests, and cut release v1.1.4.

## 3. Consequences & Downstream Impact
npx -y @fmatar/archlens-skill mcp, --version, start, init, and global installs launch main() reliably through .bin symlinks while keeping ES module imports safe when argv[1] does not exist on disk.
