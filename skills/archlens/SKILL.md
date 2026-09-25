---
name: archlens
description: >-
  Polyglot Clean Architecture governance, dynamic visual workbench companion,
  policy scaffolding, mailbox refactoring protocols, LLM prompt dossiers,
  and Model Context Protocol (MCP) diagnostics for Archlens.
version: 1.2.0
license: Apache-2.0
---

# Archlens — Clean Architecture Governance & Workbench Companion

Use this skill when analyzing, governing, or refactoring codebases using **Archlens (Dynamic Clean Architecture Workbench)**.

This skill equips agents (Antigravity, Gemini CLI, Claude Code) to:
1. Enforce Uncle Bob's **Dependency Rule** (dependencies strictly point inward).
2. Scaffold and maintain `.archlens/policy.json` architectural layer policies.
3. Act as the autonomous refactoring companion to the visual workbench via mailbox queues (`.archlens/to-agent.json` and `.archlens/to-viewer.json`).
4. Export LLM Refactoring Prompt Dossiers prescribing concrete Dependency Inversion Principle (DIP) interface ports.
5. Invoke Archlens MCP tools (`inspectArchitecture`, `exportLlmDossier`, `listSnapshots`, `getSnapshot`).

---

## 🏛️ Clean Architecture Concentric Layer Hierarchy

Archlens models codebases as concentric circular rings. Source code dependencies (imports, inheritance, calls) MUST point strictly inward:

```
[Level 3: Infrastructure / Web / DB / CLI]
       │
       ▼
[Level 2: Adapters / Controllers / Presenters / Gateways]
       │
       ▼
[Level 1: Application / Use Cases / Ports]
       │
       ▼
[Level 0: Domain Core / Entities / Value Objects]
```

- **Level 0 (Domain Core)**: Pure business entities, primitives, domain logic. Zero outward imports or dependencies on frameworks, databases, or UI.
- **Level 1 (Application)**: Use cases, interaction orchestrators, domain ports. Depends only on Domain Core (Level 0).
- **Level 2 (Adapters)**: HTTP controllers, presenters, REST resources, repository implementations. Translates between external formats and application models.
- **Level 3 (Infrastructure)**: Web frameworks, databases, persistence drivers, third-party libraries, CLI entrypoints, configuration.

> [!IMPORTANT]
> **Dependency Inversion Principle (DIP)**: When an inner layer (e.g. Domain or Application) needs a service provided by an outer layer (e.g. Database or External API), declare an **interface port** in the inner layer. Implement the interface in the outer layer (Adapter), and inject it.

---

## ⚡ CLI & Quickstart Commands

Archlens is executed cross-platform via NPX or Node CLI:

```bash
# Initialize Clean Architecture policy in target project:
npx @fmatar/archlens-skill --path . --yes

# Launch Workbench container (auto-resurrects on demand if stopped or killed):
npx @fmatar/archlens-skill start --open

# Export Clean Architecture LLM Refactoring Prompt Dossier:
npx @fmatar/archlens-skill prompt --path .

# Copy LLM refactoring prompt directly to system clipboard:
npx @fmatar/archlens-skill prompt --path . --copy

# Synchronize policy with newly added packages:
npx @fmatar/archlens-skill update --path .

# Migrate legacy .uml-viewer configuration to modern .archlens layout:
npx @fmatar/archlens-skill upgrade --path .

# Auto-configure MCP manifests for AI assistants (Antigravity, Claude, Cursor):
npx @fmatar/archlens-skill --mcp

# Launch Model Context Protocol (MCP) stdio server:
npx @fmatar/archlens-skill mcp
```

### Launching the Workbench & Auto-Resurrection
When the user asks to start Archlens, launch the studio, or open the visual workbench:
1. Run `npx @fmatar/archlens-skill start --open` (or `archlens-skill start`).
2. The CLI inspects whether the container (`archlens-server`) is active. If stopped or killed, it immediately starts it again on demand and confirms readiness at `http://localhost:8088`.
3. When using MCP tools (`inspectArchitecture`, `exportLlmDossier`), the stdio bridge also auto-resurrects the container if offline.

---

## 🤖 Dynamic Workbench Companion Protocol

Archlens provides a real-time visual canvas (`http://localhost:8088`). When human architects interact with the UI, tasks are dispatched through a bidirectional mailbox IPC protocol:

- **Inbound queue (UI $\rightarrow$ Agent)**: `.archlens/to-agent.json` (or `.uml-viewer/to-agent.json`)
- **Outbound events (Agent $\rightarrow$ UI)**: `.archlens/to-viewer.json` (or `.uml-viewer/to-viewer.json`)

### Mailbox Format (`to-agent.json`)
```json
{
  "nextId": 2,
  "queue": [
    {
      "id": 1,
      "op": "REGEN",
      "target": { "id": "project" },
      "payload": {}
    }
  ]
}
```

### Supported Operations:
1. **`REGEN`**: Re-scan AST, compute violations and instability metrics, compile/test (`mvn test-compile`), and notify UI via `REGEN_COMPLETE`.
2. **`APPLY_PROPOSAL`**: Read requested layers from `.archlens/policy.json`. Physically restructure packages to match proposed layers. Where inner layers import outer layers, apply DIP by introducing interface ports. Run test suite to verify zero regressions.
3. **`REFRESH_CRAP`**: Run tests with code coverage (`mvn test` / `npm test`) and update metric snapshots.
4. **`REFRESH_MUTATION`**: Run mutation testing on target classes.
5. **`OMIT`**: Add specified package or class to the `:omit` list in policy.

### Completing Tasks (`to-viewer.json`):
Write completion acknowledgment to `.archlens/to-viewer.json`. The backend file watcher pushes an SSE event to hot-reload the canvas:
```json
{
  "op": "REGEN_COMPLETE",
  "target": { "id": "project" },
  "payload": { "status": "SUCCESS" }
}
```

---

## 🛠️ MCP Tool Suite Reference

When Archlens is registered as an MCP server, use the following tools:

| MCP Tool | Purpose | Key Arguments |
| :--- | :--- | :--- |
| `inspectArchitecture` | Inspect concentric rings, outward violations, and instability metrics | `projectRoot` (default: `.`) |
| `exportLlmDossier` | Generate copy-ready LLM refactoring prompt with DIP prescriptions | `projectRoot`, `proposalId` |
| `listSnapshots` | List historical Git release tags and pre-compiled architecture snapshots | `projectRoot` |
| `getSnapshot` | Retrieve graph model for a historical release or snapshot tag | `snapshotId`, `projectRoot` |
