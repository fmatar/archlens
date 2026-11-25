# Model Context Protocol (MCP) Integration

Archlens natively implements an embedded **Model Context Protocol (MCP)** server built on Quarkus. It provides AI coding assistants (Claude Desktop, Google Antigravity, Claude Code, Cursor, Windsurf, VS Code) with direct, structured access to codebase architecture models, dependency violation graphs, and automated DIP refactoring synthesizers.

---

## 🔌 Connection Endpoints

The Archlens container and native server expose four MCP transports:
- **Zero-Dependency CLI Stdio Bridge (Recommended for shared repos)**: `npx -y @fadym/archlens-skill mcp`
- **Pure Ephemeral Docker STDIO Container (`mcp-stdio`, Zero Host Node/Java)**: `docker run -i --rm -e QUARKUS_PROFILE=mcp-stdio -e ARCHLENS_HOST_WORKSPACE=/path/to/workspace -v /path/to/workspace:/workspace ghcr.io/fmatar/archlens:latest`
- **Streamable HTTP (`MCP 2.0`)**: `http://127.0.0.1:8088/mcp` *(requires `X-Archlens-Token: <token>` or `Authorization: Bearer <token>`)*
- **Server-Sent Events (`SSE`)**: `http://127.0.0.1:8088/mcp/sse` *(requires `X-Archlens-Token: <token>` or `Authorization: Bearer <token>`)*

> **Session Token Note:** All `/api/*` and `/mcp*` HTTP endpoints require the per-launch session token from `~/.archlens/session-8088.json` (or `ARCHLENS_TOKEN` if pinned). The `@fadym/archlens-skill mcp` stdio bridge injects this header automatically; direct HTTP MCP clients must include `-H "X-Archlens-Token: <token>"`.

---

## 🔄 Zero-Touch Startup: The 4-Tier Auto-Resurrection Waterfall

When configured via the CLI stdio bridge (`npx -y @fadym/archlens-skill mcp`), you never need to start the Archlens server manually before prompting **Google Antigravity**, **Claude Code**, **Claude Desktop**, or **Cursor**. Every MCP tool call traverses a deterministic 4-tier resolution waterfall (`ensureServerRunning` in `cli/src/docker-runner.js`):

```mermaid
flowchart TD
    Call["AI Agent Invokes MCP Tool\n(e.g. inspectArchitecture, exportLlmDossier)"] --> T1{"Tier 1: Live Server Health\nGET http://127.0.0.1:8088/api/version"}
    T1 -->|"200 OK (< 10ms)"| Proxy["Route Request to Active Quarkus Server"]
    T1 -->|"Unreachable"| T2{"Tier 2: Native Runner JAR\nJava 21+ & quarkus-run.jar?"}
    T2 -->|"Available"| SpawnJar["Spawn Detached JVM Process\njava -Dquarkus.http.port=8088 -jar ..."]
    SpawnJar --> Poll["Poll /api/version Until Ready"] --> Proxy
    T2 -->|"Not Found"| T3{"Tier 3: Docker Daemon\ndocker info running?"}
    T3 -->|"Running"| SpawnDocker["Resurrect or Run Container\nghcr.io/fmatar/archlens:latest (:8088)"]
    SpawnDocker --> Poll
    T3 -->|"Docker Offline"| T4["Tier 4: Offline Static Lexical Fallback\nPure Node.js Polyglot Scanner (Zero Dependencies)"]
    T4 --> Result["Return Concentric Ring Graph, Violations & DIP Ports"]
    Proxy --> Result
```

1. **Tier 1 — Live Server Reuse**: Pings `http://127.0.0.1:8088/api/version`. If the Quarkus server is already running, routes the request immediately.
2. **Tier 2 — Native Runner JAR**: If `java -version` succeeds and a native runner JAR exists (`ARCHLENS_JAR`, `~/.archlens/runner/quarkus-app/quarkus-run.jar`, or `target/quarkus-app/quarkus-run.jar`), launches a detached background JVM process on port `8088`.
3. **Tier 3 — Docker Container Auto-Resurrection**: If Docker is running (`docker info`), restarts an existing `archlens-server` container or launches `ghcr.io/fmatar/archlens:latest` detached on port `8088`.
4. **Tier 4 — Offline Static Lexical Fallback**: If neither Java nor Docker is available, executes the built-in Node.js static lexical analyzer directly on disk—ensuring zero downtime in restricted sandboxes or offline environments.

---

## 🛠️ Available MCP Tools

### 1. `synthesizeDipInversion`
Synthesizes a complete Dependency Inversion Principle (DIP) refactoring plan to eliminate an outward architectural violation.

- **Parameters**:
  - `fromClass` *(string, required)*: Fully qualified class name of the caller class in the inner tier (e.g. `com.example.service.OrderService`).
  - `toClass` *(string, required)*: Fully qualified class name of the concrete class in the outer tier (e.g. `com.example.db.PostgresOrderRepo`).
  - `projectRoot` *(string, optional, default: `.`)*: Path to the target project directory.
- **Returns**: `DipInversionPlan` JSON object:
  - `portName`: Synthesized interface name (e.g. `OrderRepositoryPort`).
  - `portPackage`: Inward package for the port (e.g. `com.example.service.ports`).
  - `portInterfaceCode`: Complete Java interface source code.
  - `adapterRefactorPreview`: Code diff for the concrete adapter.
  - `callerRefactorPreview`: Code diff for caller constructor injection.
  - `surgicalPrompt`: Actionable refactoring directive for the AI agent.

---

### 2. `inspectArchitecture`
Extracts the complete Clean Architecture model of the target project.

- **Parameters**:
  - `projectRoot` *(string, optional, default: `.`)*: Path to target project.
- **Returns**: `ArchitectureGraph` JSON object:
  - `components`: Array of concentric rings and package groups.
  - `edges`: Array of dependency connections, each marked with `isViolating: boolean`.
  - `unassigned`: List of classes not yet assigned to an architectural tier.

---

### 3. `exportLlmDossier` & `exportArchitectureDiagram`
Generates a markdown refactoring prompt dossier diagnosing all concentric ring breaches and prescribing concrete interface ports, or exports deterministic Mermaid architecture diagrams.

- `exportLlmDossier`:
  - `projectRoot` *(string, optional, default: `.`)*: Path to target project.
  - `proposalId` *(string, optional)*: Optional structural proposal ID.
  - **Returns**: Formatted Markdown string containing architecture overview, breach tables, and refactoring instructions.
- `exportArchitectureDiagram`:
  - `projectRoot` *(string, optional, default: `.`)*, `diagramType` *(`COMPONENT_RINGS` | `CLASS_UML` | `DIP_REFACTOR`)*, `proposalId` *(string, optional)*, `violationsOnly` *(boolean, default: `false`)*.
  - **Returns**: Deterministic Mermaid diagram source.

---

### 4. `listSnapshots` & `getSnapshot`
Enables historical architectural comparisons against Git release tags and pre-compiled snapshots governed by `.archlens/policy.json`.
- `listSnapshots`: Lists available historical snapshots (`projectRoot`).
- `getSnapshot`: Compiles or loads a historical snapshot by `snapshotId` (calls `GET /api/snapshots/{snapshotId}`) for release-over-release diffing.

---

### 5. `evaluateFitness` & `getFitnessHistory`
Computes the Composite Architecture Fitness Index (AFI, `0`–`100`, Grade `A`–`F`), Main Sequence Distance, Screaming Architecture Score, ADP cycle count, and historical fitness trend (`STABLE`, `IMPROVING`, `DEGRADING`).

---

### 6. `proposeScreamingMigration`, `getPolicy` & `syncDipAdr`
- `proposeScreamingMigration`: Generates a Screaming Architecture package-by-feature migration plan (`projectRoot`).
- `getPolicy`: Returns the active `.archlens/policy.json` concentric ring hierarchy (`projectRoot`).
- `syncDipAdr`: Mints a numbered Markdown ADR in `docs/adr/` for a Dependency Inversion Principle refactoring (`fromClass`, `toClass`, `portName`, `portPackage`, optional `reason`, `projectRoot`).

---

## 📚 Available MCP Resources & Prompts

- **Resources**: `archlens://llms.txt` (LLM Dossier), `archlens://policy` (Active Policy JSON), and `archlens://fitness` (Live AFI Report).
- **Prompts**: `architectural-review` and `dip-refactoring-guide`.

---

## ⚙️ AI Client Configuration Guides

### ⚡ Automated 1-Command Setup (All Detected Clients)
```bash
# Portable NPX bridge (repo + global clients):
npx @fadym/archlens-skill --mcp

# Ephemeral Docker mcp-stdio container (zero host Node/Java):
npx @fadym/archlens-skill --mcp --docker

# Global skills + global MCP manifests only:
npx @fadym/archlens-skill global [--docker]
```

### 1. Google Antigravity (`~/.gemini/antigravity/mcp_config.json`)
`npx @fadym/archlens-skill --mcp` automatically populates `~/.gemini/antigravity/mcp/archlens/*.json` and `instructions.md` as well as `~/.gemini/antigravity/mcp_config.json` (pass `--docker` to generate the ephemeral Docker `mcp-stdio` entry):
```json
{
  "mcpServers": {
    "archlens": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e",
        "QUARKUS_PROFILE",
        "-e",
        "ARCHLENS_HOST_WORKSPACE",
        "-v",
        "/path/to/workspace:/workspace",
        "ghcr.io/fmatar/archlens:latest"
      ],
      "env": {
        "QUARKUS_PROFILE": "mcp-stdio",
        "ARCHLENS_HOST_WORKSPACE": "/path/to/workspace"
      }
    }
  }
}
```
Or portable NPX stdio bridge:
```json
{
  "mcpServers": {
    "archlens": {
      "command": "npx",
      "args": ["-y", "@fadym/archlens-skill", "mcp"],
      "env": {
        "ARCHLENS_SERVER_URL": "http://127.0.0.1:8088"
      }
    }
  }
}
```

### 2. Gemini CLI (`@google/gemini-cli`)
```bash
gemini mcp add archlens -- npx -y @fadym/archlens-skill mcp
```
Or in `~/.gemini/settings.json` / `.gemini/settings.json`:
```json
{
  "mcpServers": {
    "archlens": {
      "command": "npx",
      "args": ["-y", "@fadym/archlens-skill", "mcp"],
      "env": {
        "ARCHLENS_SERVER_URL": "http://127.0.0.1:8088"
      }
    }
  }
}
```

### 3. Claude Code (CLI) & Claude Desktop
```bash
# Stdio bridge (online + offline static fallback; reads ~/.archlens/session-8088.json automatically):
claude mcp add archlens -- npx -y @fadym/archlens-skill mcp

# Direct Streamable HTTP / SSE (pin ARCHLENS_TOKEN or read token from ~/.archlens/session-8088.json):
TOKEN=$(node -p "JSON.parse(require('fs').readFileSync(require('os').homedir()+'/.archlens/session-8088.json','utf8')).token")
claude mcp add --transport http archlens http://127.0.0.1:8088/mcp -H "X-Archlens-Token: $TOKEN"
```
Or in `.mcp.json` / `~/Library/Application Support/Claude/claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "archlens": {
      "command": "npx",
      "args": ["-y", "@fadym/archlens-skill", "mcp"],
      "env": {
        "ARCHLENS_SERVER_URL": "http://127.0.0.1:8088"
      }
    }
  }
}
```

### 4. Grok CLI (`xAI`)
Configure in `.grok/mcp.json` or `~/.grok/mcp.json`:
```json
{
  "mcpServers": {
    "archlens": {
      "command": "npx",
      "args": ["-y", "@fadym/archlens-skill", "mcp"],
      "env": {
        "ARCHLENS_SERVER_URL": "http://127.0.0.1:8088"
      }
    }
  }
}
```

### 5. Cursor, Windsurf & VS Code (GitHub Copilot)
Configure in `.cursor/mcp.json` or `~/.codeium/windsurf/mcp_config.json`:
```json
{
  "mcpServers": {
    "archlens": {
      "command": "npx",
      "args": ["-y", "@fadym/archlens-skill", "mcp"]
    }
  }
}
```
And in `.vscode/mcp.json`:
```json
{
  "servers": {
    "archlens": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@fadym/archlens-skill", "mcp"]
    }
  }
}
```
