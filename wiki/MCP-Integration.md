# Model Context Protocol (MCP) Integration

Archlens natively implements an embedded **Model Context Protocol (MCP)** server built on Quarkus. It provides AI coding assistants (Claude Desktop, Google Antigravity, Claude Code, Cursor, Windsurf, VS Code) with direct, structured access to codebase architecture models, dependency violation graphs, and automated DIP refactoring synthesizers.

---

## 🔌 Connection Endpoints

The Archlens container and native server expose dual MCP transports:
- **Server-Sent Events (SSE)**: `http://localhost:8088/mcp/sse`
- **Streamable HTTP**: `http://localhost:8088/mcp`
- **CLI Stdio Runner**: `npx -y @fmatar/archlens-skill mcp`

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

### 3. `exportLlmDossier`
Generates a markdown refactoring prompt dossier diagnosing all concentric ring breaches and prescribing concrete interface ports.

- **Parameters**:
  - `projectRoot` *(string, optional, default: `.`)*: Path to target project.
  - `proposalId` *(string, optional)*: Optional structural proposal ID.
- **Returns**: Formatted Markdown string containing architecture overview, breach tables, and refactoring instructions.

---

### 4. `listSnapshots` & `getSnapshot`
Enables historical architectural comparisons against Git release tags and pre-compiled snapshots stored in `.archlens/snapshots/`.

---

## ⚙️ AI Client Configuration Guides

### Claude Desktop
Add Archlens to your `claude_desktop_config.json` (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "archlens": {
      "url": "http://localhost:8088/mcp/sse"
    }
  }
}
```

### Claude Code (CLI)
Add the MCP endpoint using the Claude CLI:
```bash
claude mcp add archlens -- http://localhost:8088/mcp/sse
```

### Cursor & VS Code
Configure in `.cursor/mcp.json` or `.vscode/mcp.json`:
```json
{
  "mcpServers": {
    "archlens": {
      "url": "http://localhost:8088/mcp/sse"
    }
  }
}
```

### Google Antigravity
Archlens is pre-configured as an MCP toolset. When the Archlens container is active on port 8088, Antigravity agents can invoke `inspectArchitecture` or `synthesizeDipInversion` directly during coding sessions.
