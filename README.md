# Archlens — Dynamic Clean Architecture Workbench

[![CI & Quality Gates](https://github.com/fmatar/archlens/actions/workflows/ci.yml/badge.svg)](https://github.com/fmatar/archlens/actions/workflows/ci.yml)
[![Publish Docker Image & Release](https://github.com/fmatar/archlens/actions/workflows/release.yml/badge.svg)](https://github.com/fmatar/archlens/actions/workflows/release.yml)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Quarkus 3.x](https://img.shields.io/badge/Quarkus-3.39-blue.svg)](https://quarkus.io/)
[![Svelte 5](https://img.shields.io/badge/Svelte-5-red.svg)](https://svelte.dev/)
[![Docker](https://img.shields.io/badge/Docker-Single--Container-2496ED.svg)](Dockerfile)
[![Polyglot](https://img.shields.io/badge/Scanners-Java%20%7C%20Python%20%7C%20Rust%20%7C%20TS%20%7C%20Go%20%7C%20Clojure-emerald.svg)](#polyglot-language-support)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

> *"The Dependency Rule: Source code dependencies must point only inward, toward higher-level policies."*  
> — **Robert C. Martin (Uncle Bob)**

An interactive real-time architectural visualization workbench, polyglot Clean Architecture governance engine, and autonomous AI refactoring studio for modern software systems.

![Archlens Clean Architecture Dynamic Workbench](media/archlens-canvas.png)  
*(High Definition Video: [`media/archlens-teaser.mp4`](media/archlens-teaser.mp4) &bull; Extended Tour: [`media/archlens-user-journey.mp4`](media/archlens-user-journey.mp4) &bull; Animated Preview: [`media/archlens-teaser.gif`](media/archlens-teaser.gif))*

---

## ⚡ 3-Step Quickstart

Experience real-time Clean Architecture governance on any repository in three direct steps:

### 1. Equip Your Repository (Install Skills & Policy)

Scaffold architectural policies and agent refactoring protocols directly into your project:

```bash
# Production Stable Track (default)
npx @fmatar/archlens-skill

# Canary / Development Preview (tracks latest develop commits)
npx @fmatar/archlens-skill@dev

# Direct GitHub Execution (zero registry dependencies)
npx github:fmatar/archlens

# Non-interactive / headless setup accepting defaults
npx @fmatar/archlens-skill --yes

# Target a specific repository directory
npx @fmatar/archlens-skill --path /path/to/my-project --yes
```

*Or install globally via npm:*
```bash
npm install -g @fmatar/archlens-skill
archlens-skill
```

*Or install from a local repository clone:*
```bash
npm install -g ./cli
archlens-skill
```

*Or instruct your AI assistant (Claude Code, Gemini CLI, Google Antigravity):*
> *"Install the Archlens Clean Architecture policy in this project."*

This creates:
- `.archlens/policy.json`: Concentric Clean Architecture tiers (Domain Core $\rightarrow$ Application $\rightarrow$ Adapters $\rightarrow$ Infrastructure).
- `.archlens/workbench.config.json`: Local workbench connection and mailbox configuration.
- `CLAUDE.md` & `AGENTS.md`: Mailbox refactoring protocols for autonomous coding companions.

---

### 2. Launch the Studio (Run Container)

Start the all-in-one container, mounting your local workspace:

```bash
# Production Stable
docker run -d -p 8088:8088 \
  -v "$HOME/workspace:/workspace" \
  ghcr.io/fmatar/archlens:latest

# Canary / Development Track
docker run -d -p 8088:8088 \
  -v "$HOME/workspace:/workspace" \
  ghcr.io/fmatar/archlens:dev
```

Open **`http://localhost:8088`** in your browser.

---

### 3. Inspect, X-Ray & Refactor

1. **Browse Projects (`⌘O` / `Ctrl+O`)**: Press `⌘O` to open the visual filesystem explorer. Select any repository mounted in `/workspace` with automatic build framework detection (`Maven`, `Gradle`, `Node`, `Go`, `Cargo`).
2. **Violation X-Ray (`V`)**: Press `V` to isolate illegal outward dependency violations in neon crimson while compliant inward flows gently fade into the background.
3. **Hierarchical Edge Bundling (`B`)**: Press `B` to route dense cross-package connections along smooth Catmull-Rom spline corridors.
4. **Wake Refactoring Agent (`Regen`)**: Click the green **Regen** action to post tasks into `.archlens/to-agent.json`. Your AI assistant applies the Dependency Inversion Principle, generates abstractions, runs tests, and triggers hot-reloads on the canvas.

---

## 🧭 Keyboard & Interaction Shortcuts

| Key | Action | Description |
| :--- | :--- | :--- |
| `⌘O` / `Ctrl+O` | **Project Switcher** | Open native folder browser to switch active repositories |
| `V` | **Violation X-Ray** | Toggle isolation of illicit outward dependency violations |
| `B` | **Edge Bundling** | Route connections along concentric Catmull-Rom spline corridors |
| `C` | **Compact Cards** | Collapse fine-grained class lists into high-level macro cards |
| `F` | **1-Hop Focus** | Isolate direct inbound and outbound dependencies for selected node |
| `P` | **Proposals** | Toggle between live architecture and hypothetical structural proposals |
| `L` | **LLM Prompt Dossier** | Export copy-ready Clean Architecture refactoring prompt for LLMs |
| `⌘K` / `/` | **Command Palette** | Quick search classes, packages, and trigger workbench actions |
| `+` / `-` / `0` | **Zoom & Pan** | Zoom in, zoom out, or reset canvas viewport |

---

## 🌐 Polyglot Language Support

Archlens features a modular Service Provider Interface (SPI) for language scanners:

| Language | Ecosystem & AST Engine | File Extensions | Capabilities |
| :--- | :--- | :--- | :--- |
| **Java 25** | JavaParser 3.26 | `.java` | Records, Sealed Types, Interfaces, Class Hierarchies, Inward Rules |
| **Python** | Python AST Visitor | `.py` | Modules, Classes, Functions, Imports, Relative Imports |
| **TypeScript / JS** | Babel AST / Regex Scanner | `.ts`, `.tsx`, `.js`, `.jsx` | Classes, Interfaces, Named Imports, ESM Re-exports |
| **Rust** | Syn / Cargo AST Extractor | `.rs` | Structs, Traits, Impls, Module `use` Paths |
| **Go** | Go AST Tree Walker | `.go` | Structs, Interfaces, Package Imports, Type Definitions |
| **Clojure** | EDN & Regex AST Scanner | `.clj`, `.cljs`, `.edn` | Namespaces (`ns`), `(:require ...)`, `def`, `defn`, Protocols |

---

## 🏛️ System Architecture

```mermaid
flowchart LR
    subgraph Client ["Developer Workspace"]
        User["Architect / Developer"]
        Browser["Archlens UI<br/>(Svelte 5 + Tailwind v4)"]
    end

    subgraph Runtime ["Archlens Container (Port 8088)"]
        Server["Quarkus REST & SSE Server<br/>(Java 25 Runtime)"]
        
        subgraph Core ["Analysis & Governance Engine"]
            Scanner["Polyglot AST Scanners<br/>(Java, TS, Python, Go, Rust, Clojure)"]
            Rules["Clean Architecture Rule Engine<br/>(Level Inversion Validator)"]
            Metrics["Quality Metrics Engine<br/>(CRAP Score & Mutation Stats)"]
        end
    end

    subgraph Storage ["Target Project"]
        Code["Source Codebase"]
        Mailbox["Mailbox IPC<br/>(.archlens/)"]
    end

    Agent["AI Agent / Companion<br/>(Antigravity / Claude)"]

    User <-->|Pan, Zoom, Drag & Filter| Browser
    Browser <-->|REST & Server-Sent Events| Server
    Server --> Core
    Scanner -->|Parse AST & Deps| Code
    Server <-->|Queue Commands & Hot Reload| Mailbox
    Agent <-->|Read Task & Write ACK| Mailbox
    Agent -->|Refactor Code & Run Tests| Code
```

---

## 🤖 AI Agent Mailbox Protocol

Archlens provides seamless bidirectional integration with autonomous coding assistants:

1. **Trigger Refactoring**: When clicking **Regen (Wake Agent)** in the workbench, Archlens posts a structured command into `.archlens/to-agent.json`.
2. **Autonomous Execution**: AI assistants (Google Antigravity, Claude Code, Gemini CLI) read the payload, introduce domain interfaces, invert outward dependencies, and execute local test suites.
3. **Hot-Reload Canvas**: The agent writes completion details to `.archlens/to-viewer.json`. Archlens detects the response through Server-Sent Events (SSE) and instantly updates the architecture diagram.

---

## 🛠️ CLI Reference (`@fmatar/archlens-skill`)

| Flag | Shorthand | Description | Default |
| :--- | :--- | :--- | :--- |
| `--path <DIR>` | `-p` | Target repository directory | `.` (current directory) |
| `--title <NAME>` | `-t` | Project title in visual workbench | Formatted folder name |
| `--prefix <PKG>` | | Common package prefix (e.g. `com.example.service`) | Auto-detected |
| `--server-url <URL>` | | Visual Workbench server endpoint | `http://localhost:8088` |
| `--global` | `-g` | Install skill into global agent directories | `false` |
| `--update` | `-u` | Re-scan code and update existing policy | `false` |
| `--upgrade` | | Migrate legacy `.uml-viewer` to standard `.archlens` | `false` |
| `--force` | `-f` | Overwrite existing configuration files | `false` |
| `--dry-run` | | Simulate execution without writing files | `false` |
| `--yes` | `-y` | Accept defaults automatically (non-interactive) | `false` |
| `--version` | `-v` | Display package version | |
| `--help` | `-h` | Display help reference | |

---

## 💡 The Vision & Inspiration

Robert C. Martin’s writings have been a foundational reference point throughout modern software engineering. *Clean Code*, *Clean Architecture*, and the SOLID principles established how we protect core business policies from the volatility of frameworks, delivery mechanisms, and external databases.

When Uncle Bob open-sourced [unclebob/uml-viewer](https://github.com/unclebob/uml-viewer), the vision was captivating: transforming the Dependency Rule from an abstract diagram in a book into a living, tangible feedback loop right on our screens. Seeing that experimental prototype sparked an immediate ambition: bring this exact philosophy into modern polyglot enterprise environments.

Archlens takes Uncle Bob's core thesis and expands it into a comprehensive architecture governance and refactoring workbench supporting six languages, hierarchical edge routing, and autonomous agent collaboration.

---

## 💻 Building from Source

For developers wishing to build and test Archlens locally:

### Prerequisites
- Java 25 (OpenJDK / GraalVM)
- Apache Maven 3.9+
- Node.js 22+ and npm

```bash
# 1. Start the Backend
cd backend
./mvnw clean quarkus:dev

# 2. Start the Frontend
cd ../frontend
npm install
npm run dev

# 3. Run Quality Verification & Tests
mvn clean verify
npm --prefix frontend run check
npm --prefix frontend run test
npm --prefix frontend run test:e2e
```

---

## 📚 Documentation & Resources

- [User Guide](docs/guide/USER_GUIDE.md)
- [C4 Architecture Specification](docs/specs/C4_ARCHITECTURE.md)
- [Inspiration & Vision](docs/guide/INSPIRATION_AND_VISION.md)
- [SDLC Compliance Report](docs/specs/SDLC_COMPLIANCE_REPORT.md)
- [Changelog](CHANGELOG.md)
- [Development Guide](CONTRIBUTING.md)

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).  
See the [NOTICE](NOTICE) file for attribution and acknowledgements.
