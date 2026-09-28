# System Architecture

Archlens is engineered as a unified, high-performance Clean Architecture analysis and governance platform. It pairs a **reactive Java 25 backend** with a **Svelte 5 frontend**, coupled via high-frequency Server-Sent Events (SSE) and Model Context Protocol (MCP) transports.

---

## 🏛️ High-Level System Architecture

```mermaid
flowchart LR
    subgraph Client ["Developer Workspace"]
        User["Architect / Developer"]
        Browser["Archlens UI<br/>(Svelte 5 + Tailwind v4)"]
    end

    subgraph Runtime ["Archlens Unified Runtime (Port 8088)"]
        Server["Quarkus REST & SSE Server<br/>(Java 25 Runtime)"]
        
        subgraph Core ["Analysis & Governance Engine"]
            Scanner["Polyglot AST Scanners<br/>(Java, TS, Python, Go, Rust, Clojure)"]
            Rules["Clean Architecture Rule Engine<br/>(Concentric Ring Validator)"]
            Synthesizer["Surgical DIP Synthesizer<br/>(JavaParser AST Extractor)"]
            Metrics["Quality Metrics Engine<br/>(CRAP Score & Mutation Stats)"]
        end
        
        subgraph McpServer ["MCP Service (/mcp & /mcp/sse)"]
            Tools["synthesizeDipInversion<br/>inspectArchitecture<br/>exportLlmDossier"]
        end
    end

    subgraph Storage ["Target Project"]
        Code["Source Codebase"]
        Mailbox["Mailbox IPC<br/>(.archlens/)"]
    end

    Agent["AI Agent / Companion<br/>(Antigravity / Claude)"]

    User <-->|Pan, Zoom, Drag & Sandbox| Browser
    Browser <-->|REST & Server-Sent Events| Server
    Server --> Core
    Scanner -->|Parse AST & Deps| Code
    Synthesizer -->|AST Method Extraction| Code
    Server <-->|Queue Commands & Hot Reload| Mailbox
    Server --> McpServer
    McpServer <-->|Live Tool Calls| Agent
    Agent <-->|Read Task & Write ACK| Mailbox
    Agent -->|Refactor Code & Run Tests| Code
```

---

## 🎯 The Concentric Rings

Archlens enforces Robert C. Martin's concentric Clean Architecture rings:

```mermaid
graph TD
    subgraph Ring3 ["Ring 3: Frameworks & Drivers (Level 3)"]
        subgraph Ring2 ["Ring 2: Interface Adapters (Level 2)"]
            subgraph Ring1 ["Ring 1: Application Use Cases (Level 1)"]
                subgraph Ring0 ["Ring 0: Domain Core & Entities (Level 0)"]
                    D["Enterprise Business Rules\nEntities & Value Objects"]
                end
                A["Application Business Rules\nUse Cases & Inward Ports"]
            end
            I["Controllers, Gateways, Presenters\nConcrete Repository Adapters"]
        end
        F["Database, Web Server, UI Framework\nDevices, External APIs"]
    end

    F --> I
    I --> A
    A --> D
```

### The Invariant Dependency Rule
> **Dependencies must point strictly inward.**  
> A class in **Level 0 (Domain Core)** must NEVER depend on **Level 1 (Application)**, **Level 2 (Adapters)**, or **Level 3 (Frameworks)**. Any outward edge ($\text{Level}_{\text{from}} < \text{Level}_{\text{to}}$) is immediately flagged as an **Architectural Breach (Violation)** in neon crimson.

---

## ⚡ Polyglot AST Engine (SPI)

The backend employs an extensible Service Provider Interface (SPI) for scanning multiple languages:
- **Java**: JavaParser 3.26 full AST parsing (records, sealed interfaces, classes, annotations, imports).
- **TypeScript & JavaScript**: Babel AST parser and regex tokenization for ESM modules and barrel exports.
- **Python**: Python AST token visitor for modules, classes, and intra-package imports.
- **Go**: Go standard AST parser detecting packages, structs, and interfaces.
- **Rust**: Syn / Cargo metadata extractor detecting crates, modules, and `use` declarations.
- **Clojure**: EDN reader and namespace dependency parser.

---

## 🔄 Reactive Svelte 5 Frontend

The user interface uses Svelte 5 Runes for state management and UI reactivity:
- **`$state`**: Holds active architecture models, graph components, and sandbox simulation layers.
- **`$derived`**: Reactively recalculates Robert C. Martin metrics ($C_a, C_e, I, A, D$), violation deltas, and edge styles on every staged move.
- **`$effect`**: Drives high-performance GSAP transitions and canvas re-layouts.
- **EventSource (`/api/events`)**: Connects to the backend SSE stream, reacting instantaneously to file saves, agent mailbox updates, and live AST refreshes.
