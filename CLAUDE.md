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
