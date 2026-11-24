# @fmatar/archlens-skill

[![npm version](https://img.shields.io/npm/v/@fmatar/archlens-skill.svg)](https://www.npmjs.com/package/@fmatar/archlens-skill)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Node](https://img.shields.io/badge/Node-%3E%3D22.0.0-brightgreen.svg)](https://nodejs.org/)

Zero-dependency CLI, policy scaffolder, and multi-agent skill installer for the **[Archlens Dynamic Clean Architecture Workbench](https://github.com/fmatar/archlens)**.

Analyzes any project repository, detects programming languages and package hierarchies, classifies packages into concentric Clean Architecture tiers, scaffolds `.archlens/policy.json`, and equips AI coding assistants (Claude Code, Gemini CLI, Google Antigravity) with mailbox refactoring protocols.

---

## ⚡ Quickstart

### Option A: Zero-Touch Autonomous AI Setup (`--mcp` — Recommended for Antigravity & Claude)
Equip **Google Antigravity**, **Claude Code**, **Claude Desktop**, **Cursor**, **Windsurf**, and **Gemini CLI** in one command. Choose the portable NPX stdio bridge (with 4-tier offline AST fallback) or an ephemeral Docker `mcp-stdio` container:
```bash
# Portable / shared-repo setup (.archlens/policy.json + AGENTS.md + skills + NPX MCP bridge):
npx @fmatar/archlens-skill --yes --skills --mcp

# Personal workstation setup (ephemeral docker run -i --rm -e QUARKUS_PROFILE=mcp-stdio container):
npx @fmatar/archlens-skill --yes --skills --mcp --docker

# Equip global skills AND global MCP manifests across all detected coding agents:
npx @fmatar/archlens-skill global
npx @fmatar/archlens-skill global --docker
```

### Option B: Install AI Assistant Skills Directly via `npx skills`
Using the open [Vercel Labs Skills](https://github.com/vercel-labs/skills) standard, you can add Archlens capabilities across 15+ AI coding assistants (Claude Code, Cursor, Antigravity, Copilot, Codex, Windsurf, Zed):
```bash
# Add all 3 Archlens skills to detected AI assistants in project or globally:
npx skills add fmatar/archlens --all -y

# Or add to a specific assistant:
npx skills add fmatar/archlens --all --agent claude-code -y
```

### Option C: Run Interactive Policy Scaffolder via NPX

```bash
# Production Stable (recommended)
npx @fmatar/archlens-skill

# Canary / Development Preview (tracks latest develop branch)
npx @fmatar/archlens-skill@dev

# Direct GitHub Execution (zero registry dependencies)
npx github:fmatar/archlens
```

### Option D: Install Globally via NPM
```bash
npm install -g @fmatar/archlens-skill
archlens-skill
```

### Option E: Run from Local Repository Clone
```bash
# Install globally from the local repository:
npm install -g ./cli
archlens-skill

# Or execute directly with Node:
node ./cli/bin/index.js
```

---

## 🚀 Key Features

- **Zero External Dependencies**: Engineered with pure Node.js standard libraries (`node:fs`, `node:path`, `node:readline`, `node:test`, `node:os`). Executes immediately via `npx` without latency.
- **Interactive Terminal UI**: Displays styled ANSI diagnostics, ASCII branding banner, and interactive guided choices when run in a terminal.
- **Polyglot Codebase Analyzer**: Auto-detects project language and layout across Java, Kotlin, TypeScript, JavaScript, Python, Rust, and Go.
- **Concentric Tier Classification**: Maps packages automatically to Clean Architecture layers:
  - **Level 0 (Domain Core)**: `domain`, `model`, `entities`, `core`, `types`
  - **Level 1 (Application)**: `usecase`, `application`, `service`, `port`, `interactor`
  - **Level 2 (Adapters)**: `adapter`, `controller`, `gateway`, `presenter`, `repository`, `dto`, `api`
  - **Level 3 (Infrastructure)**: `infrastructure`, `config`, `db`, `web`, `server`, `client`
- **Multi-Agent Skill Deployment**: Installs the unified `archlens` skill globally into Claude Code (`~/.claude/skills/`), Gemini CLI / Antigravity (`~/.gemini/config/skills/`), and custom agent workspaces.
- **Safe Incremental Updates**: Re-scans codebases for newly introduced packages, updating `order` and `levels` while preserving user custom rules and configurations.
- **Model Context Protocol (MCP) Client Auto-Configuration**: Detects and configures host AI client manifests (Claude Desktop, Google Antigravity, Claude Code, Cursor, VS Code) in a single command (`--mcp`).
- **On-Demand Container Orchestration & Stdio Bridge**: Automatically spins up the Archlens container when needed, bridges MCP over stdio (`mcp` command), and provides an instant offline AST fallback if Docker is unavailable.
- **Mailbox Protocol Integration**: Injects the Clean Architecture Companion Protocol into `CLAUDE.md` and `AGENTS.md` for seamless asynchronous mailbox refactoring (`to-agent.json`, `to-viewer.json`).

---

## 🛠️ Usage & CLI Reference

### Common Workflows

#### 1. Interactive Setup Wizard
Guides you through repository inspection and option selection:
```bash
npx @fmatar/archlens-skill
```

#### 2. Headless Local Initialization
Initializes Clean Architecture governance in the current repository with default settings:
```bash
npx @fmatar/archlens-skill --yes
```

Target a specific repository directory:
```bash
npx @fmatar/archlens-skill --path /path/to/project --yes
```

#### 3. Global AI Agent Skill & MCP Installation
Deploys skills and global MCP manifests so Claude Code, Gemini CLI, and Antigravity are equipped across all workspaces (pass `--docker` for ephemeral container mode or `--no-mcp` to skip MCP setup):
```bash
npx @fmatar/archlens-skill global
npx @fmatar/archlens-skill global --docker
```

#### 4. Synchronize Existing Policy
Re-scans code after adding new modules or packages to incorporate them into `.archlens/policy.json`:
```bash
npx @fmatar/archlens-skill update
```

#### 5. Export LLM Refactoring Prompt Dossier
Generates copy-ready architectural diagnostics and concrete Dependency Inversion Principle (DIP) instructions for Claude Code, Gemini, ChatGPT, or Antigravity:
```bash
# Print dossier to stdout
npx @fmatar/archlens-skill prompt

# Or copy directly to system clipboard
npx @fmatar/archlens-skill prompt --copy
```

#### 7. Model Context Protocol (MCP) Setup & Stdio Bridge
Configures detected AI client manifests or launches an interactive stdio bridge:
```bash
# Auto-configure Claude Desktop, Google Antigravity, Claude Code, Cursor, and VS Code
npx @fmatar/archlens-skill --mcp

# Auto-configure pure ephemeral Docker MCP container (docker run -i --rm -e QUARKUS_PROFILE=mcp-stdio)
npx @fmatar/archlens-skill --mcp --docker

# Launch MCP stdio transport bridge (spins up Docker container automatically when needed)
npx @fmatar/archlens-skill mcp
```

#### 8. Start Workbench Container & Auto-Resurrection
Starts the Archlens container, mounts the workspace, verifies readiness, and optionally opens your browser. **If the container is killed or stopped, running `start` (or calling an MCP tool) automatically starts it again:**
```bash
# Start container and automatically open workbench in browser:
npx @fmatar/archlens-skill start --open

# Start container in background on default port 8088:
npx @fmatar/archlens-skill start

# Specify a custom port or target workspace directory:
npx @fmatar/archlens-skill start --path /path/to/project --port 8088 --open
```

---

### Command-Line Options

| Flag / Command | Shorthand | Description | Default |
|------|-----------|-------------|---------|
| `check` | `--check` | Run headless Clean Architecture CI quality gate | |
| `start` | | Start Archlens Workbench (Native Binary / JAR / Docker fallback) | |
| `prompt` | `--prompt` | Export LLM Refactoring Prompt Dossier | |
| `mcp` | | Run MCP stdio server bridge (online + offline static fallback) | |
| `update` | `--update`, `-u` | Re-scan code and update existing `.archlens/policy.json` | `false` |
| `global` | `--global`, `-g` | Install skills into global agent directories | `false` |
| `--max-violations <N>` | | Maximum allowed outward layer violations in `check` | `0` |
| `--detect-cycles` | | Fail `check` if package dependency cycles (ADP) are detected | `false` |
| `--screaming-threshold <N>` | | Minimum Screaming Architecture Score (`0.0`–`1.0`) | |
| `--suggest-features` | | Suggest Screaming Architecture feature slice clusters in `check` | `false` |
| `--fitness-threshold <N>` | | Minimum Composite Architecture Fitness Index (`0.0`–`1.0`) | |
| `--format <FMT>` | | Output format for `check` (`text`, `json`, `sarif`) | `text` |
| `--output <FILE>` | `-O` | Write `check` output report (e.g. SARIF) to file | |
| `--native` | | Force native JVM runner mode on `start` | `false` |
| `--docker` | | Force Docker container mode on `start` or configure ephemeral `mcp-stdio` manifests with `--mcp` | `false` |
| `--jar <PATH>` | | Custom path to `quarkus-run.jar` for native mode | Auto-detected |
| `--open` | `-o` | Open visual workbench in default browser upon startup | `false` |
| `--port <PORT>` | | Workbench HTTP port | `8088` |
| `--path <DIR>` | `-p` | Target repository directory | `.` (current directory) |
| `--title <NAME>` | `-t` | Project title in visual workbench | Formatted folder name |
| `--prefix <PKG>` | | Common package prefix (e.g. `com.example.service`) | Auto-detected |
| `--server-url <URL>` | | Visual Workbench server endpoint | `http://localhost:8088` |
| `--mcp` | `-m` | Auto-configure MCP client manifests (Claude, Antigravity, Cursor, Gemini, Grok, Windsurf, VS Code) | `false` |
| `--copy` | `-c` | Copy LLM prompt output directly to system clipboard | `false` |
| `--force` | `-f` | Overwrite existing configuration files | `false` |
| `--dry-run` | | Simulate execution without writing files | `false` |
| `--yes` | `-y` | Accept defaults automatically (non-interactive) | `false` |
| `--version` | `-v` | Display package version | |
| `--help` | `-h` | Display help reference | |

---

## 📁 Artifacts Generated

Running the installer creates the following governance structure:

```text
my-project/
├── .archlens/
│   ├── policy.json             # Clean Architecture concentric tiers & rules
│   └── workbench.config.json   # Workbench endpoint and mailbox IPC configuration
├── CLAUDE.md                   # Clean Architecture Mailbox companion protocol
└── AGENTS.md                   # Agent instructions for DIP refactoring & AST reload
```

### Visualizing in Archlens Workbench

Once configured, launch the Archlens workbench and open your project:

```text
http://localhost:8088/?projectRoot=/absolute/path/to/my-project
```

Or open the visual workbench and press `⌘O` / `Ctrl+O` to browse and select your project root.

---

## 🧪 Testing

Execute the test suite with Node's native test runner:

```bash
npm test
```

---

## 📄 License

Apache-2.0 © [Fady Matar](https://github.com/fmatar)
