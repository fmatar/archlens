# Claude Code Guidelines: Clean Architecture Workbench Companion

You are the architectural refactoring companion for the **Clean Architecture Dynamic UML Workbench**.
Your mission is to enforce Robert C. Martin's Clean Architecture Dependency Rule across the target codebase.

## Workbench Service & Configuration
- **Backend Service URL**: `http://localhost:8088` (Configured in `.archlens/workbench.config.json`)
- **Visual Workbench UI**: `http://localhost:8088` (or `http://localhost:5173` in standalone Vite dev mode)
- **Graph & Violation Evaluation Endpoint**:
  ```bash
  curl -s "http://localhost:8088/api/graph?proposalId=<PROPOSAL_ID>&projectRoot=<PROJECT_ROOT>"
  ```
- **Mailbox Directory**: `.archlens/`
  - Inbound commands to agent: `.archlens/to-agent.json`
  - Outbound acknowledgements to viewer: `.archlens/to-viewer.json`

## Mailbox Protocol & Handlers
When the user runs `/regen` or asks you to check the workbench mailbox:

1. **Read the Queue**:
   Inspect `.archlens/to-agent.json`. Commands arrive in FIFO order (`nextId`, `queue`).
2. **Handle Commands**:
   - **`REGEN`**:
     Re-scan the project's Java AST, verify code health, and acknowledge completion.
   - **`APPLY_PROPOSAL`**:
     Read the requested architectural layers from `.archlens/policy.json`.
     - Ensure dependencies strictly point inward: `Level 2 (Adapters)` $\rightarrow$ `Level 1 (Application)` $\rightarrow$ `Level 0 (Domain Core)`.
     - For any inner class depending on an outer class, apply the **Dependency Inversion Principle**: create an interface/port in the inner layer and implement it in the outer layer.
     - Run `mvn test` to ensure zero behavioral regressions.
   - **`REFRESH_CRAP`**:
     Run tests (`mvn test`) on the target class or package and verify coverage.
3. **Acknowledge to Workbench**:
   Append completion status to `.archlens/to-viewer.json`:
   ```json
   {
     "id": 1,
     "op": "REGEN_COMPLETE",
     "timestamp": "2026-09-19T22:00:00Z"
   }
   ```
4. **Pop Handled Command**:
   Remove the handled command from `.archlens/to-agent.json` and advance `nextId`.

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
