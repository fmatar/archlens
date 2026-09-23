# @fmatar/archlens-skill

[![npm version](https://img.shields.io/npm/v/@fmatar/archlens-skill.svg)](https://www.npmjs.com/package/@fmatar/archlens-skill)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Node](https://img.shields.io/badge/Node-%3E%3D18.0.0-brightgreen.svg)](https://nodejs.org/)

Zero-dependency CLI, policy scaffolder, and multi-agent skill installer for the **[Archlens Dynamic Clean Architecture Workbench](https://github.com/fmatar/archlens)**.

Analyzes any project repository, detects programming languages and package hierarchies, classifies packages into concentric Clean Architecture tiers, scaffolds `.archlens/policy.json`, and equips AI coding assistants (Claude Code, Gemini CLI, Google Antigravity) with mailbox refactoring protocols.

---

## ⚡ Quickstart

Run directly with `npx` (no installation required):

```bash
npx @fmatar/archlens-skill
```

Or install globally via npm:

```bash
npm install -g @fmatar/archlens-skill
archlens-skill
```

---

## 🚀 Key Features

- **Zero External Dependencies**: Engineered with pure Node.js standard libraries (`node:fs`, `node:path`, `node:readline`, `node:test`, `node:os`). Executes immediately via `npx` without latency.
- **Interactive Terminal UI**: Displays styled ANSI diagnostics, ASCII branding banner, and interactive guided choices when run in a terminal.
- **Polyglot Codebase Analyzer**: Auto-detects project language and layout across Java, Kotlin, TypeScript, JavaScript, Python, Rust, Go, and Clojure.
- **Concentric Tier Classification**: Maps packages automatically to Clean Architecture layers:
  - **Level 0 (Domain Core)**: `domain`, `model`, `entities`, `core`, `types`
  - **Level 1 (Application)**: `usecase`, `application`, `service`, `port`, `interactor`
  - **Level 2 (Adapters)**: `adapter`, `controller`, `gateway`, `presenter`, `repository`, `dto`, `api`
  - **Level 3 (Infrastructure)**: `infrastructure`, `config`, `db`, `web`, `server`, `client`
- **Multi-Agent Skill Deployment**: Installs the `archlens-install-policy` skill globally into Claude Code (`~/.claude/skills/`), Gemini CLI / Antigravity (`~/.gemini/config/skills/`), and custom agent workspaces.
- **Safe Incremental Updates**: Re-scans codebases for newly introduced packages, updating `order` and `levels` while preserving user custom rules and configurations.
- **Seamless Upgrade Migration**: Automatically migrates legacy `.uml-viewer/` folders and companion files to modern `.archlens/` via `archlens-skill upgrade`.
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

#### 3. Global AI Agent Skill Installation
Deploys the skill globally so Claude Code, Gemini CLI, and Antigravity can scaffold policies on demand:
```bash
npx @fmatar/archlens-skill global
```

#### 4. Synchronize Existing Policy
Re-scans code after adding new modules or packages to incorporate them into `.archlens/policy.json`:
```bash
npx @fmatar/archlens-skill update
```

#### 5. Upgrade Legacy Configuration
Migrates older `.uml-viewer/` directories and companions to the standard `.archlens/` layout:
```bash
npx @fmatar/archlens-skill upgrade
```

---

### Command-Line Options

| Flag | Shorthand | Description | Default |
|------|-----------|-------------|---------|
| `--path <DIR>` | `-p` | Target repository directory | `.` (current directory) |
| `--title <NAME>` | `-t` | Project title in visual workbench | Formatted folder name |
| `--prefix <PKG>` | | Common package prefix (e.g. `com.example.service`) | Auto-detected |
| `--server-url <URL>` | | Visual Workbench server endpoint | `http://localhost:8088` |
| `--global` | `-g` | Install skill into global agent directories | `false` |
| `--update` | `-u` | Re-scan code and update existing policy | `false` |
| `--upgrade` | | Migrate legacy `.uml-viewer` to `.archlens` | `false` |
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
