---
name: archlens
description: >-
  Polyglot Clean Architecture governance, .archlens/policy.json scaffolding,
  dynamic visual workbench companion, mailbox refactoring protocols, LLM prompt
  dossiers, and Model Context Protocol (MCP) diagnostics for Archlens. Use when
  setting up or updating Archlens, initializing .archlens/policy.json, auditing
  layer boundaries, inverting outward dependencies (DIP), or processing workbench
  mailbox commands.
license: Apache-2.0
compatibility: "Node.js >= 22"
version: 1.2.0
metadata:
  author: fmatar
  version: "1.2.0"
---

# Archlens — Clean Architecture Governance, Policy Installer & Workbench Companion

Use this unified skill when setting up, analyzing, governing, or refactoring codebases using **Archlens (Dynamic Clean Architecture Workbench)**.

This skill equips agents (Antigravity, Gemini CLI, Claude Code, Cursor, Windsurf) to:
1. Enforce the **Clean Architecture Dependency Rule** (dependencies strictly point inward).
2. Inspect any polyglot repository and scaffold or update `.archlens/policy.json` concentric ring policies, `.archlens/workbench.config.json`, and `AGENTS.md` / `CLAUDE.md` companion guidelines.
3. Act as the autonomous refactoring companion to the visual workbench via mailbox queues (`.archlens/to-agent.json` and `.archlens/to-viewer.json`).
4. Export LLM Refactoring Prompt Dossiers prescribing concrete Dependency Inversion Principle (DIP) interface ports.
5. Invoke Archlens MCP tools (`inspectArchitecture`, `exportLlmDossier`, `exportArchitectureDiagram`, `synthesizeDipInversion`, `evaluateFitness`, `getFitnessHistory`, `proposeScreamingMigration`, `getPolicy`, `syncDipAdr`, `listSnapshots`, `getSnapshot`), resources (`archlens://llms.txt`, `archlens://policy`, `archlens://fitness`), and prompts (`architectural-review`, `dip-refactoring-guide`).

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

## ⚡ CLI, Policy Scaffolding & Quickstart Commands

Install the unified `archlens` skill across 15+ coding agents (Antigravity, Claude Code, Cursor, Gemini CLI) via `skills.sh`:
```bash
# Add the Archlens skill to your active agent skills library:
npx skills add fmatar/archlens -y

# Equip global skill AND global MCP manifests in one command (add --docker for ephemeral container mode):
npx @fadym/archlens-skill global
```

> [!TIP]
> **First-Run MCP Auto-Bootstrap (for `npx skills` installs)**: Because `npx skills add` only copies `SKILL.md` files without executing scripts, check whether the `archlens` MCP tools (`inspectArchitecture`, `exportLlmDossier`, `evaluateFitness`) are registered in your active session. If missing, run `npx @fadym/archlens-skill --path . --yes --mcp` (portable NPX bridge) or `npx @fadym/archlens-skill global --docker` (ephemeral `mcp-stdio` Docker container when Docker is running).

### Scaffolding & Updating `.archlens/policy.json`

When the user asks to *"Setup Archlens"*, *"Initialize `.archlens/policy.json`"*, or *"Configure Clean Architecture policy"*:

```bash
# Initialize policy (.archlens/policy.json + .archlens/workbench.config.json + AGENTS.md/CLAUDE.md) and register portable MCP manifests:
npx @fadym/archlens-skill --path . --yes --mcp

# Or register MCP as an ephemeral Docker STDIO container:
npx @fadym/archlens-skill --path . --yes --mcp --docker

# Initialize policy only (skip MCP client configuration):
npx @fadym/archlens-skill --path . --yes --no-mcp

# Offline Python fallback if Node/npx is unavailable (run from this skill's directory):
python3 scripts/init_policy.py --path .
```

*Key installer flags:* `--path <DIR>`, `--title <NAME>`, `--prefix <PKG>`, `--server-url <URL>` (default `http://127.0.0.1:8088`), `--mcp` / `--no-mcp`, `--docker`, `--update` (incremental rescan), `--force` (overwrite existing policy). After scaffolding, inspect `.archlens/policy.json` and align `levels` with custom domain terminology if needed.

### Headless CI Gates, Prompts & Workbench Commands

```bash
# Launch Workbench server (opens authenticated ?token=... browser URL; auto-resurrects if offline):
npx @fadym/archlens-skill start --open

# Execute Headless Clean Architecture CI Quality Gate (delegates to Quarkus backend when available; pass --offline for pure JS fallback):
npx @fadym/archlens-skill check --path . --max-violations 0 --detect-cycles --screaming-threshold 0.70 --suggest-features --fitness-threshold 0.85 --format sarif -O archlens-results.sarif

# Export Clean Architecture LLM Refactoring Prompt Dossier (add --copy for system clipboard):
npx @fadym/archlens-skill prompt --path .

# Synchronize policy with newly added packages:
npx @fadym/archlens-skill update --path .

# Launch Model Context Protocol (MCP) stdio server:
npx @fadym/archlens-skill mcp
```

### Launching the Workbench & Auto-Resurrection
When the user asks to start Archlens, launch the studio, or open the visual workbench:
1. Run `npx @fadym/archlens-skill start --open` (or `archlens-skill start`).
2. The CLI inspects whether the server is active on `http://localhost:8088`. If offline, it automatically auto-resurrects:
   - **Native Java Runner (Default)**: If Java 21+ and a runner JAR (`quarkus-run.jar` in `~/.archlens/`, project `target/`, or via `--jar <PATH>`) are detected, it launches the native JVM server in ~0.5s with bare-metal filesystem speed.
   - **Docker Container (Fallback)**: If Java or a local runner is absent, it automatically starts or creates the `archlens-server` Docker container.
   - Use `--docker` to force Docker mode, or `--native` to force native mode.
3. When using MCP tools (`inspectArchitecture`, `exportLlmDossier`), the stdio bridge also auto-resurrects the server if offline.

---

## 🤖 Dynamic Workbench Companion Protocol

Archlens provides a real-time visual canvas (`http://localhost:8088`). When human architects interact with the UI, tasks are dispatched through a bidirectional mailbox IPC protocol:

- **Inbound queue (UI $\rightarrow$ Agent)**: `.archlens/to-agent.json`
- **Outbound events (Agent $\rightarrow$ UI)**: `.archlens/to-viewer.json`

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
5. **`OMIT`**: Add specified package or class to the `omit` list in `.archlens/policy.json`.

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
| `exportArchitectureDiagram` | Export Mermaid architecture diagram (`COMPONENT_RINGS`, `CLASS_UML`, `DIP_REFACTOR`) | `projectRoot`, `diagramType`, `proposalId`, `violationsOnly` |
| `synthesizeDipInversion` | Synthesize polyglot DIP interface port, adapter/caller diffs, and surgical prompt | `fromClass`, `toClass`, `projectRoot` |
| `evaluateFitness` | Compute Composite Architecture Fitness Index (AFI score, grade, rule breakdown) | `projectRoot` |
| `getFitnessHistory` | Retrieve historical fitness snapshots across Git tags and regression trend | `projectRoot` |
| `proposeScreamingMigration` | Propose Screaming Architecture package-by-feature migration clusters | `projectRoot` |
| `getPolicy` | Inspect active `.archlens/policy.json` concentric ring definitions | `projectRoot` |
| `syncDipAdr` | Mint a numbered Markdown ADR in `docs/adr/` for a DIP refactoring | `fromClass`, `toClass`, `portName`, `portPackage`, `reason`, `projectRoot` |
| `listSnapshots` | List historical Git release tags and pre-compiled architecture snapshots | `projectRoot` |
| `getSnapshot` | Retrieve graph model for a historical release or snapshot tag | `snapshotId`, `projectRoot` |

### MCP Resources & Prompts
- **Resources**: `archlens://llms.txt`, `archlens://policy`, `archlens://fitness`
- **Prompts**: `architectural-review` (`projectRoot`), `dip-refactoring-guide` (`fromClass`, `toClass`, `projectRoot`)
