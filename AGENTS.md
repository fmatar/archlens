# Assistant Guidelines

## Archlens Clean Architecture Workbench Companion Protocol

This project is governed by the **Archlens Dynamic Clean Architecture Workbench**.
- **Workbench UI & API**: `http://localhost:8088`
- **Architectural Policy**: `.archlens/policy.json`
- **Mailbox IPC**:
  - Inbound queue: `.archlens/to-agent.json`
  - Outbound response: `.archlens/to-viewer.json`

### Handling Mailbox Commands:
1. **REGEN**: Re-index AST, evaluate package dependency rules, and acknowledge.
2. **APPLY_PROPOSAL**: Reorganize packages to conform to concentric rings (Adapters -> Application -> Domain Core). Where an inner layer relies on an outer layer, apply the **Dependency Inversion Principle** by creating an interface port in the inner layer and implementing it in the outer layer.
3. **REFRESH_CRAP**: Execute test suite and report code coverage metrics.

<!-- AGENTIC-SDLC-RULES:START -->
# Agentic SDLC Framework Invariants & Autonomous Rules
_Generated automatically by @fmatar/agentic-sdlc (Universal Engine v0.1.0)_
_Adheres strictly to Robert C. Martin Clean Code & Clean Architecture_

## 1. Zero Direct Pushes (4-Layer Git Guard — INV-01)
- Direct git commits, merges, or pushes to `develop` or `main` are STRICTLY FORBIDDEN.
- Every commit requires an active session in `.agent/.sdlc-session.json` matching the current branch (`node .agent/scripts/sdlc-engine.mjs stage begin`).
- Every merge into `main` requires a signed Stage 7 SHA-256 Merkle Gate Receipt in `docs/AUDIT_CHAIN.jsonl`.

## 2. PRODUCT.md Constitution & Spec Drift ADR Protocol (INV-03)
- Full-spec project handovers synthesize `PRODUCT.md` (`CAP-XX`, `INV-XX`, `SPEC-XX`) starting at Genesis Ticket `#1`.
- Any mutation to dependencies, C4 architecture boundaries, DB schemas, or API contracts MUST be accompanied by an ADR (`node .agent/scripts/sdlc-engine.mjs adr create`).

## 3. Robert Martin TDD & Dual-Gate Quality Verification (INV-02)
- Diff coverage threshold: >= 85% line and branch coverage.
- Mutation kill score: >= 80% using PITest, Stryker, mutmut, or cargo-mutants.
- Concentric ring Clean Architecture compliance: zero outer-layer imports into domain entities (`.archlens/policy.json`).

## 4. Universal Router + 8 Verb Skills Suite (ADR-004)
- `/sdlc`: Universal 1:1 subcommand router (`/sdlc doctor`, `/sdlc begin #id`, `/sdlc status`, `/sdlc drift`, `/sdlc pre-pr`, `/sdlc gate`, `/sdlc kanban`, `/sdlc ui`).
- `/sdlc-flow`: End-to-end ticket autopilot (`stage begin` -> TDD -> `stage pre-pr` -> parallel review -> `stage complete`).
- `/sdlc-product`: Constitutional `PRODUCT.md` autopilot, `docs/specs/` architect, and topological DAG waves.
- `/sdlc-doctor`: Stack detection, `doctor --fix`, 4-Layer Git hooks, `.archlens/policy.json`, and multi-agent installer.
- `/sdlc-status`: Active session, 8-stage progress, drift check, `KANBAN.md` state, and live Architecture Diff UI.
- `/sdlc-tdd`: Robert Martin Red-Green-Refactor TDD, polyglot test runners, and real coverage/mutation report parsers.
- `/sdlc-gate`: Parallel fork-join PR review subagents, `gate --strict`, and Stage 7 SHA-256 Merkle Gate Receipt.
- `/sdlc-adr`: Mint, approve, or compact Spec-Drift ADRs (`adr compact`) and synchronize `docs/DECISION_MAP.md`.
- `/sdlc-release`: ADR compaction into `docs/SRS.md`, README/Docs/Wiki sync, SemVer tagging, regression proofs, and live canary watchdog.
<!-- AGENTIC-SDLC-RULES:END -->
