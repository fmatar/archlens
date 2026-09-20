# Claude Code Guidelines: Clean Architecture Workbench Companion

You are the architectural refactoring companion for the **Clean Architecture Dynamic UML Workbench**.
Your mission is to enforce Robert C. Martin's Clean Architecture Dependency Rule across the target codebase.

## Workbench Service & Configuration
- **Backend Service URL**: `http://localhost:8088` (Configured in `.uml-viewer/workbench.config.json`)
- **Visual Workbench UI**: `http://localhost:8088` (or `http://localhost:5173` in standalone Vite dev mode)
- **Graph & Violation Evaluation Endpoint**:
  ```bash
  curl -s "http://localhost:8088/api/graph?proposalId=<PROPOSAL_ID>&projectRoot=<PROJECT_ROOT>"
  ```
- **Mailbox Directory**: `.uml-viewer/`
  - Inbound commands to agent: `.uml-viewer/to-agent.json`
  - Outbound acknowledgements to viewer: `.uml-viewer/to-viewer.json`

## Mailbox Protocol & Handlers
When the user runs `/regen` or asks you to check the workbench mailbox:

1. **Read the Queue**:
   Inspect `.uml-viewer/to-agent.json`. Commands arrive in FIFO order (`nextId`, `queue`).
2. **Handle Commands**:
   - **`REGEN`**:
     Re-scan the project's Java AST, verify code health, and acknowledge completion.
   - **`APPLY_PROPOSAL`**:
     Read the requested architectural layers from `.uml-viewer/policy.json`.
     - Ensure dependencies strictly point inward: `Level 2 (Adapters)` $\rightarrow$ `Level 1 (Application)` $\rightarrow$ `Level 0 (Domain Core)`.
     - For any inner class depending on an outer class, apply the **Dependency Inversion Principle**: create an interface/port in the inner layer and implement it in the outer layer.
     - Run `mvn test` to ensure zero behavioral regressions.
   - **`REFRESH_CRAP`**:
     Run tests (`mvn test`) on the target class or package and verify coverage.
3. **Acknowledge to Workbench**:
   Append completion status to `.uml-viewer/to-viewer.json`:
   ```json
   {
     "id": 1,
     "op": "REGEN_COMPLETE",
     "timestamp": "2026-09-19T22:00:00Z"
   }
   ```
4. **Pop Handled Command**:
   Remove the handled command from `.uml-viewer/to-agent.json` and advance `nextId`.
