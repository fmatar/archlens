---
name: uml-workbench-companion
description: >-
  Companion agent for the Clean Architecture Dynamic UML Workbench.
  Monitors .uml-viewer/ mailbox queues, processes architectural refactoring requests,
  evaluates Clean Architecture violations, runs builds and tests, and signals live diagram reloads.
---

# UML Workbench Companion Skill (`uml-workbench-companion`)

Use this skill when interacting with the **Clean Architecture Dynamic UML Workbench**. This skill enables Antigravity to act as the autonomous coding companion to the human architect who is using the visual UML viewer.

---

## 📋 Core Responsibilities

1. **Mailbox Polling & Queue Processing**:
   - Continuously check `.uml-viewer/to-agent.json`.
   - Process commands sequentially in FIFO order (`nextId`, `queue`).
   - Pop handled items from `queue` and atomically rewrite `.uml-viewer/to-agent.json`.

2. **Command Handlers**:
   - **`REGEN`**: Re-scan the project's Java AST, re-calculate Clean Architecture levels and dependency violations, and trigger a live graph refresh.
   - **`APPLY_PROPOSAL`**: Read the requested proposal from `.uml-viewer/policy.json`, physically restructure the Java packages to match the proposed layers, introduce interfaces to invert violating inward-to-outward dependencies, and ensure tests pass.
   - **`REFRESH_CRAP`**: Run tests (`mvn test`) for the target class or package and update JaCoCo coverage metrics.
   - **`REFRESH_MUTATION`**: Run mutation testing on the specified target files and update metrics snapshots.
   - **`OMIT`**: Add the target class or package to the active proposal's `:omit` list in `.uml-viewer/policy.json`.

3. **Acknowledging to the Viewer**:
   - Append completion status and results to `.uml-viewer/to-viewer.json`.
   - The Quarkus backend file watcher detects this change and pushes an SSE event to the Svelte frontend, hot-reloading the diagram immediately.

---

## 🛠️ Step-by-Step Execution Workflow

### When a User Says: "Check for workbench commands" or "Act as UML companion"

1. **Inspect the Outbox**:
   - Read `.uml-viewer/to-agent.json`.
   - If `queue` is empty, report that no commands are pending.
   - If commands exist, select the first command `cmd = queue[0]`.

2. **Execute the Requested Operation**:
   - If `cmd.op == "REGEN"`:
     - Verify source integrity.
     - Compile or test if required (`mvn test-compile`).
     - Write an acknowledgment to `.uml-viewer/to-viewer.json`:
       ```json
       {
         "op": "REGEN_COMPLETE",
         "target": { "id": "project" },
         "payload": { "status": "SUCCESS" }
       }
       ```
   - If `cmd.op == "APPLY_PROPOSAL"`:
     - Read `.uml-viewer/policy.json` to inspect the requested layer boundaries.
     - Move or refactor Java classes to conform to the Clean Architecture rule:
       - **Rule**: Dependencies must only point inward (e.g. `adapters` $\rightarrow$ `application` $\rightarrow$ `domain`).
       - If `domain` depends on `adapters`, introduce an interface in `domain` and implement it in `adapters` (Dependency Inversion Principle).
     - Run `mvn test` to verify zero regressions.
     - Acknowledge completion in `.uml-viewer/to-viewer.json`.

3. **Pop Handled Command**:
   - Remove `cmd` from `.uml-viewer/to-agent.json` `queue` and update `nextId`.

---

## 🔄 Autonomous Background Monitoring

To monitor the workbench autonomously in the background while the architect is using the browser UI:

```python
schedule(
    CronExpression="*/1 * * * *",
    Prompt="Check .uml-viewer/to-agent.json for pending workbench commands and process them.",
    IsDaemon=false
)
```
