# Archlens — Dynamic Clean Architecture Workbench: User Guide

Welcome to **Archlens**. This workbench bridges high-level Clean Architecture governance with physical source code across **Java 25, Python, TypeScript/JS/Svelte 5, Rust, Go, and Clojure**, enabling architects and developers to visually inspect, simulate, and direct autonomous AI coding agents.

---

## 1. System Architecture Overview

```mermaid
flowchart TD
    User["Software Architect"] <-->|Interacts via Browser Canvas| UI["Svelte 5 UI (Port 8088 / 5173)"]
    UI <-->|REST & SSE Streams (/api/events)| Backend["Quarkus 3.40 Backend (Java 25 Virtual Threads)"]
    
    subgraph Engine ["Polyglot AST & Governance Engine"]
        Backend --> AST["Polyglot AST Scanners (Java, Python, TS/Svelte, Rust, Go, Clojure)"]
        Backend --> RuleEngine["Concentric Ring & ADP Cycle Engine (.archlens/policy.json)"]
        Backend --> MetricsEngine["Martin Metrics (Ca, Ce, I, A, D), Screaming Score (SAS) & Fitness (AFI)"]
    end

    subgraph Protocols ["Dual AI Agent Protocols"]
        UI -->|Writes APPLY_PROPOSAL / INVERT_DEPENDENCY| Outbox[".archlens/to-agent.json"]
        Inbox[".archlens/to-viewer.json"] -->|SSE Hot Reload| Backend
        MCP["Quarkus MCP 2.0 Server (/mcp, /mcp/sse) & CLI Stdio Bridge"] <--> Backend
    end

    Agent["Autonomous AI Agent\n(Antigravity / Gemini / Claude / Grok / Cursor)"] <--> Outbox
    Agent <-->|10 Tools, 3 Resources, 2 Prompts| MCP
    Agent -->|Synthesizes DIP Ports, Moves Classes & Runs Tests| Disk["Source Code & Test Suites"]
    Agent -->|Writes Completion ACK| Inbox
```

---

## 2. Launching the Workbench

### Option A: 1-Command CLI Launcher (Recommended)
```bash
cd /path/to/your-project
npx @fmatar/archlens-skill
npx @fmatar/archlens-skill start --open
```

### Option B: Bare-Metal Development Mode
```bash
# Terminal 1: Start the Quarkus Backend (Java 25, Port 8088)
cd backend
./mvnw clean quarkus:dev

# Terminal 2: Start the Svelte 5 Frontend (Port 5173)
cd frontend
pnpm install && pnpm dev
```

---

## 3. Core Visual Workflows & Keyboard Shortcuts

| Key | Mode / Action | Purpose |
| :--- | :--- | :--- |
| `⌘O` / `Ctrl+O` | **Project Switcher** | Browse and switch active repositories (`nativePickerSupported` container-aware explorer) |
| `V` | **Violation X-Ray** | Dim conforming edges and isolate outward Clean Architecture breaches in crimson |
| `B` | **Edge Bundling** | Route edges along concentric Catmull-Rom spline corridors |
| `D` | **Declutter Mode** | Cycle through `ARROWS`, `REMOVE_ARROWS`, `CLASSES`, and `NONE` |
| `S` | **"What-If" Sandbox** | Simulate moving classes across concentric rings in memory and view live violation deltas ($\Delta \pm N$) |
| `M` | **Main Sequence** | Open the 2D scatter plot of Abstractness ($A$) vs. Instability ($I$) with Zone of Pain/Uselessness overlays |
| `C` | **Compact Cards** | Collapse class lists into macro package summaries for 100k+ LoC codebases |
| `F` | **1-Hop Focus** | Isolate direct callers and dependencies of the selected component |
| `P` | **Policy Designer** | Open the interactive Concentric Ring & Policy Editor modal |
| `L` | **LLM Dossier** | Export a copy-ready Markdown refactoring prompt with DIP prescriptions |
| `T` | **Telemetry Drawer** | View live `.archlens/` Mailbox IPC and SSE events |
| `G` | **Release Comparator** | Compare architectural health against Git release tags and `.archlens/snapshots/` |
| `⌘K` / `/` | **Quick Find** | WAI-ARIA combobox search across packages, classes, and commands |

---

## 4. Surgical DIP Inverter & Transactional Rollback

1. Click any crimson violating edge or select a violation in the Inspector's **Fitness** tab.
2. Click **`⚡ Invert Dependency (DIP)`** to synthesize an idiomatic interface port in the inner ring:
   - **Java**: `public interface <Target>Port`
   - **Python**: `class <Target>Port(Protocol):`
   - **TypeScript**: `export interface <Target>Port`
   - **Go**: `type <Target>Port interface`
   - **Rust**: `pub trait <Target>Port`
3. Click **`Dispatch to AI Agent`**:
   - Archlens captures a pre-flight snapshot in `.archlens/snapshots/` and writes `INVERT_DEPENDENCY` to `.archlens/to-agent.json`.
   - Your AI assistant applies the port extraction, runs the test suite, and verifies the violation count decreased via `/api/mailbox/verify` (or rolls back in 1 click via `/api/mailbox/rollback`).
