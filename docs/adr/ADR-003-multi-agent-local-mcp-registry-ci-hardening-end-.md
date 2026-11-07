---
id: ADR-003
title: Multi-Agent Local MCP Registry, CI Hardening & End-to-End Smoke Suite
status: accepted
scope: technical
ticket: #200
supersedes: CAP-01, CAP-02, CAP-03, CAP-04
createdAt: 2026-11-07T17:18:18.848Z
---

# ADR-003: Multi-Agent Local MCP Registry, CI Hardening & End-to-End Smoke Suite

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: #200
- **Supersedes / Amends**: CAP-01, CAP-02, CAP-03, CAP-04

## 1. Context & Spec Drift Trigger

Following the `v0.1.1` production release (`ADR-002`), users and AI coding agents needed a zero-friction way to configure Archlens locally across all major MCP-capable environments (**Google Antigravity**, **Gemini CLI**, **Claude Code**, **Claude Desktop**, **Grok CLI (`xAI`)**, **Cursor**, **Windsurf**, and **VS Code**) and an automated End-to-End (E2E) smoke verification suite that boots the packaged `quarkus-run.jar` and verifies UI, REST, and MCP contracts before PR merges.

## 2. Decision

1. **Multi-Agent MCP Registry (`cli/src/mcp-registry.js` & `cli/src/mcp-server.js`)**:
   - Expanded `ARCHLENS_TOOL_SCHEMAS` and `executeToolCall` to expose all 10 Clean Architecture MCP tools (`inspectArchitecture`, `exportLlmDossier`, `listSnapshots`, `getSnapshot`, `synthesizeDipInversion`, `evaluateFitness`, `getFitnessHistory`, `proposeScreamingMigration`, `getPolicy`, `syncDipAdr`) with verified Quarkus REST contracts (`GET /api/violations/invert-plan`, `POST /api/mailbox/sync-adr?projectRoot=...`, `GET /api/policy`).
   - Expanded `getClientConfigLocations` to auto-configure 9 client targets (`claude-desktop`, `claude-code`, `antigravity-global`, `antigravity-config`, `gemini-cli`, `grok`, `windsurf`, `cursor`, `vscode`).
2. **End-to-End Packaged Workbench & MCP Smoke Suite (`scripts/smoke-test.js`, `package.json`, `.github/workflows/ci.yml`)**:
   - Added `scripts/smoke-test.js` (`npm run smoke` / `npm run smoke:build`) that boots `backend/target/quarkus-app/quarkus-run.jar` on an ephemeral local port, verifies the bundled Svelte 5 UI (`GET /`), all REST endpoints (`/api/version`, `/api/graph`, `/api/fitness`, `/api/fitness/history`, `/api/violations/invert-plan`, `/api/policy`), Quarkus MCP 2.0 SSE (`/mcp/sse`), and the CLI MCP JSON-RPC 2.0 Stdio bridge.
   - Wired `npm run smoke` into `.github/workflows/ci.yml` (`Verify & Quality Gates`).

## 3. Consequences & Downstream Impact

- Every pull request automatically runs both unit tests (`npm test`, `mvn verify`) and a full packaged server + MCP E2E smoke test (`npm run smoke`) in CI.
- Developers can build and verify the entire application locally with a single command (`npm run smoke:build`).

## 4. Invariant Check

- **INV-01**: Enforced (`main` and `develop` branch protection rules active; PR #201 targets `develop`).
- **INV-02**: Enforced (`101/101` CLI & script unit tests passing with `85.89%` overall coverage; `96.30%` on `mcp-server.js` and `98.87%` on `mcp-registry.js`).
- **INV-03**: Enforced (`0` concentric ring violations, `92% [Grade A]` AFI).
- **INV-05**: Enforced (`ADR-003` synchronized with `PRODUCT.md` and `docs/DECISION_MAP.md`).
