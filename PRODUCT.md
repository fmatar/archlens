# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Software architects, engineering leaders, and software engineers working extensively with autonomous AI coding agents (Google Antigravity, Claude Code, Cursor, Codex). They need real-time, visual architectural governance to inspect, verify, and steer how AI agents evolve multi-layer, polyglot software systems.

## Product Purpose

Archlens transforms Robert C. Martin's Clean Architecture Dependency Rule into a living, real-time visual feedback loop. It solves a critical vulnerability in modern agentic software development: while frontier AI models generate functional code rapidly, without continuous architectural boundaries they silently introduce outward dependency violations, cross-layer leakage, circular dependencies, and design erosion. Success means architects and developers can instantly identify breaches, prototype structural refactorings in a zero-disk sandbox, and dispatch targeted directives to autonomous AI agents to refactor code back into 100% architectural conformance.

## Positioning

Unlike static UML diagramming tools (PlantUML, Enterprise Architect) that fall out of sync with code, and unlike text-heavy static analysis linters (ArchUnit, SonarQube) that output disconnected error logs, Archlens is an interactive, real-time visual workbench. It couples polyglot AST parsing directly with dynamic concentric ring visualization, "What-If" architectural simulation, Robert C. Martin coupling/stability metrics, and closed-loop AI agent refactoring via both a local mailbox IPC (`.archlens/`) and a native Model Context Protocol (MCP) server.

## Operating Context

- **Local Workstation & Container**: Runs either via a zero-dependency standalone container (`ghcr.io/fmatar/archlens:latest` on port 8088), an auto-launching native JVM runner (`quarkus-run.jar` in ~0.5s), or bare-metal dev mode (`mvn quarkus:dev` + `npm run dev`).
- **AI Agent Pairing (Dual Protocol)**:
  - **Mailbox IPC Companion Protocol**: Asynchronous, file-based IPC in `.archlens/` (`to-agent.json` and `to-viewer.json`) for local paired refactoring tasks (`APPLY_PROPOSAL`, `INVERT_DEPENDENCY`, `REGEN`, `REFRESH_CRAP`).
  - **Model Context Protocol (MCP)**: Native HTTP/SSE (`/mcp`, `/mcp/sse`) and CLI stdio bridge exposing tools (`inspectArchitecture`, `synthesizeDipInversion`, `exportLlmDossier`, `listSnapshots`, `getSnapshot`).
- **Architectural Policy**: Version-controlled `.archlens/policy.json` placed directly inside target repositories, defining concentric tiers (Domain Core, Application, Adapters, Infrastructure), package order, and structural proposals.

## Capabilities and Constraints

- **Polyglot AST Scanners (SPI)**: Native AST parsing and dependency extraction across 6 languages: Java 25 (JavaParser 3.28), TypeScript/JavaScript/Svelte 5 (including runes and `#lib/*` subpath aliases), Python (AST visitor), Rust (Syn / Cargo extractor), Go (AST tree walker), and Clojure (EDN & Regex scanner).
- **Concentric Architecture Rings**: Dynamic 2D SVG canvas rendering components and classes in tiered levels, automatically flagging inward (valid) vs outward (violating) dependencies in real time.
- **Surgical DIP Inverter**: 1-click synthesis of Dependency Inversion Principle interface ports and injection refactoring previews.
- **"What-If" Architectural Sandbox**: Interactive in-memory class reassignment simulator displaying live violation deltas ($\Delta \pm N$) and Martin metrics before writing changes to disk.
- **Robert C. Martin Coupling & Main Sequence**: Automated calculation of Afferent Coupling ($C_a$), Efferent Coupling ($C_e$), Instability ($I$), Abstractness ($A$), and Distance from Main Sequence ($D$) with an interactive 2D Cartesian scatter plot.
- **Screaming Architecture & Feature Clustering**: Calculation of Screaming Architecture Score (SAS) with automated feature clustering and 1-click domain verticalization proposals.
- **Architectural Fitness Functions**: Quality invariants checking zero cyclic dependencies (ADP), breach thresholds, and release snapshot comparisons.

## Brand Commitments

- **Name**: Archlens (Dynamic Clean Architecture Workbench).
- **Philosophical Root**: Robert C. Martin's (Uncle Bob) Clean Architecture principles and his experimental `unclebob/uml-viewer` prototype.
- **License**: Apache 2.0 with full open-source community standards (`CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `NOTICE`).
- **Aesthetic Tone**: Developer-focused, dark-mode first, precise, high-contrast, technical, non-gimmicky workbench aesthetic.

## Evidence on Hand

- **Working Codebase**: Java 25 + Quarkus 3.40.1 backend, Svelte 5 + Tailwind CSS v4 + GSAP 3.15 frontend, and Node.js CLI (`@fmatar/archlens-skill`).
- **Living Self-Analysis**: Self-governing policy (`.archlens/policy.json`) achieving 1.00 (100%) Screaming Architecture Score with 0 violations and 0 cycles.
- **Media Artifacts**:
  - `media/archlens-teaser.mp4` & `.gif`: 9-second high-definition overview highlighting concentric ring reorganization and source code inspection.
  - `media/archlens-user-journey.mp4`: Full interactive walkthrough video.
  - `media/archlens-canvas.png`: High-resolution canvas snapshot.
- **Documentation**: C4 Architecture specification, User Guide, Inspiration & Vision, and SDLC Compliance reports in `docs/`.

## Product Principles

1. **Code is Truth**: Never require manual diagram drawing. All nodes and dependency edges reflect actual AST parsers scanning actual source code files.
2. **Inward Dependencies Only**: The Dependency Rule is immutable; violations are high-visibility alerts that must never be hidden or softened.
3. **Agentic by Design**: Built from day one to treat autonomous AI coding agents as first-class collaborators via clean mailbox and MCP handoffs.
4. **Zero-Friction Test-Drive**: A developer should be able to run `npx @fmatar/archlens-skill` and visualize their architecture in under two minutes without setup friction.

## Accessibility & Inclusion

- Adherence to WCAG 2.1 AA guidelines for high-contrast color coding (ensuring red violation arrows and emerald conforming badges have sufficient contrast on dark slate backgrounds).
- Keyboard-navigable canvas modals, explicit ARIA roles (`role="dialog"`), and legible monospace typography.
