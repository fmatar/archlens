---
name: uml-workbench-companion
description: >-
  Companion agent for the Clean Architecture Dynamic UML Workbench.
  Monitors .archlens/ and .uml-viewer/ mailbox queues, processes architectural refactoring requests,
  evaluates Clean Architecture violations, runs builds and tests, and signals live diagram reloads.
version: 1.2.0
license: Apache-2.0
---

# UML Workbench Companion Skill (`uml-workbench-companion`)

Use this skill when interacting with the **Clean Architecture Dynamic UML Workbench**. This skill enables Antigravity or Gemini agents to act as the autonomous coding companion to the human architect who is using the visual UML viewer.

---

## ⚙️ Configuration & Service Endpoints

The workbench service endpoint defaults to `http://localhost:8088`, configurable via `.archlens/workbench.config.json` (or `.uml-viewer/workbench.config.json`) or the `ARCHLENS_SERVER_URL` / `WORKBENCH_URL` environment variables.

- **Graph & Violation API**: `GET http://localhost:8088/api/graph?proposalId=<id>&projectRoot=<root>`
- **Mailbox Files**: Located in `.archlens/` (or legacy `.uml-viewer/`) under the examined repository root.

## 📋 Core Responsibilities

1. **Mailbox Polling & Queue Processing**:
   - Continuously check `.archlens/to-agent.json` (falling back to `.uml-viewer/to-agent.json`).
   - Process commands sequentially in FIFO order (`nextId`, `queue`).
   - Pop handled items from `queue` and atomically rewrite the mailbox file.

2. **Command Handlers**:
   - **`REGEN`**: Re-scan the project's AST, re-calculate Clean Architecture levels and dependency violations, and trigger a live graph refresh.
   - **`APPLY_PROPOSAL`**: Read the requested proposal from `.archlens/policy.json` (or `.uml-viewer/policy.json`), physically restructure packages to match the proposed layers, introduce interfaces to invert violating inward-to-outward dependencies, and ensure tests pass.
   - **`REFRESH_CRAP`**: Run tests (`mvn test` / `npm test`) for the target class or package and update JaCoCo coverage metrics.
   - **`REFRESH_MUTATION`**: Run mutation testing on the specified target files and update metrics snapshots.
   - **`OMIT`**: Add the target class or package to the active proposal's `:omit` list in policy.json.

3. **Acknowledging to the Viewer**:
   - Append completion status and results to `.archlens/to-viewer.json` (or `.uml-viewer/to-viewer.json`).
   - The Quarkus backend file watcher detects this change and pushes an SSE event to the Svelte frontend, hot-reloading the diagram immediately.

---

## 🛠️ Step-by-Step Execution Workflow

### When a User Says: "Check for workbench commands" or "Act as UML companion"

1. **Inspect the Outbox**:
   - Read `.archlens/to-agent.json` (or `.uml-viewer/to-agent.json`).
   - If `queue` is empty, report that no commands are pending.
   - If commands exist, select the first command `cmd = queue[0]`.

2. **Execute the Requested Operation**:
   - If `cmd.op == "REGEN"`:
     - Verify source integrity.
     - Compile or test if required (`mvn test-compile` or `npm run build`).
     - Write an acknowledgment to `.archlens/to-viewer.json`:
       ```json
       {
         "op": "REGEN_COMPLETE",
         "target": { "id": "project" },
         "payload": { "status": "SUCCESS" }
       }
       ```
   - If `cmd.op == "APPLY_PROPOSAL"`:
     - Read `.archlens/policy.json` to inspect the requested layer boundaries.
     - Move or refactor classes to conform to the Clean Architecture rule:
       - **Rule**: Dependencies must only point inward (e.g. `adapters` $\rightarrow$ `application` $\rightarrow$ `domain`).
       - If `domain` depends on `adapters`, introduce an interface in `domain` and implement it in `adapters` (Dependency Inversion Principle).
     - Run tests to verify zero regressions.
     - Acknowledge completion in `.archlens/to-viewer.json`.

3. **Pop Handled Command**:
   - Remove `cmd` from the queue and update `nextId`.

---

## 🔄 Autonomous Background Monitoring

To monitor the workbench autonomously in the background while the architect is using the browser UI:

```python
schedule(
    CronExpression="*/1 * * * *",
    Prompt="Check .archlens/to-agent.json (or .uml-viewer/to-agent.json) for pending workbench commands and process them.",
    IsDaemon=false
)
```
