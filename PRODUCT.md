# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Software architects, tech leads, and software engineers working extensively with autonomous AI coding agents (Claude Code, Cursor, Antigravity, etc.). They need real-time, visual architectural governance to inspect, verify, and guide how agents evolve multi-layer systems.

## Product Purpose

Archlens transforms Robert C. Martin's Clean Architecture Dependency Rule into a living, real-time visual feedback loop. It exists to solve a core problem in modern agentic software development: frontier AI models produce functional code quickly, but without continuous architectural boundaries, they silently introduce outward dependency violations, cross-layer leakage, and design erosion. Success means developers and architects can instantly spot violations, evaluate alternative refactoring proposals, and trigger autonomous agents to refactor code back into compliance.

## Positioning

Unlike static UML modeling tools (PlantUML, Enterprise Architect) that fall out of sync with code, and unlike general-purpose static analysis linters (ArchUnit, SonarQube) that output text logs, Archlens is an interactive, real-time workbench that couples polyglot AST parsing directly with dynamic concentric layer visualization, "What-If" virtual proposals, and a dedicated AI agent refactoring loop (`.uml-viewer/` mailbox IPC).

## Operating Context

- **Local Workstation / Container**: Running locally via a single Docker container (`ghcr.io/fmatar/archlens:latest` on port 8088) or bare-metal dev mode (`mvn quarkus:dev` + `npm run dev`).
- **AI Agent Pairing**: Connected to local AI coding agents via a file-based mailbox queue (`.uml-viewer/to-agent.json` and `.uml-viewer/to-viewer.json`). Clicking "Regen (Wake Agent)" dispatches refactoring tasks that agents solve and hot-reload back into the canvas.
- **Architectural Policies**: Version-controlled `.uml-viewer/policy.json` placed directly inside target repositories, defining rings (Domain Core, Application, Adapters/Infrastructure).

## Capabilities and Constraints

- **Polyglot AST Scanners (SPI)**: First-class AST parsing and dependency extraction for Java 25, TypeScript/JavaScript, Python, Rust, and Go.
- **Concentric Architecture Rings**: Dynamic 2D SVG canvas rendering components and classes in tiered levels (Level 0 Core, Level 1 Application, Level 2 Adapters), automatically flagging inward (valid) vs outward (violating) dependencies.
- **Virtual "What-If" Proposals**: Interactive proposal switching to evaluate structural reorganizations without moving files or touching code.
- **Code Quality & Mutation Observability**: Integrated CRAP score calculation, Cyclomatic Complexity, code coverage metrics, mutation testing results, and syntax-highlighted source code inspection down to the exact line number.
- **Local Mailbox Protocol**: Asynchronous, file-based IPC in `.uml-viewer/` requiring zero cloud dependencies or external credentials.

## Brand Commitments

- **Name**: Archlens (derived from Architecture Lens).
- **Philosophical Root**: Robert C. Martin's (Uncle Bob) Clean Architecture principles and his experimental `unclebob/uml-viewer` prototype.
- **License**: Apache 2.0 with full open-source community standards (`CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `NOTICE`).
- **Aesthetic Tone**: Developer-focused, dark-mode first, precise, high-contrast, technical, non-gimmicky workbench aesthetic.

## Evidence on Hand

- **Working Codebase**: Java 25 Quarkus 3.39 backend, Svelte 5 + Tailwind CSS v4 frontend.
- **Living Demo**: Self-analysis policy (`.uml-viewer/policy.json`) analyzing Archlens's own backend architecture out-of-the-box.
- **Media Artifacts**:
  - `media/archlens-teaser.mp4` & `.gif`: 9-second high-definition overview highlighting concentric ring reorganization and source code inspection.
  - `media/archlens-user-journey.mp4`: Full interactive walkthrough video.
  - `media/archlens-canvas.png`: High-resolution canvas snapshot.
- **Documentation**: C4 Architecture specification, User Guide, Inspiration & Vision, JaCoCo Coverage reports in `docs/`.

## Product Principles

1. **Code is Truth**: Never require manual diagram drawing. All nodes and dependency edges must reflect actual AST parsers scanning actual source files.
2. **Inward Dependencies Only**: The Dependency Rule is immutable; violations are high-visibility alerts that must never be hidden or softened.
3. **Agentic by Design**: Built from day one to treat autonomous AI agents as first-class citizens in the development lifecycle via clean IPC handoffs.
4. **Zero-Friction Test-Drive**: A developer should be able to run `docker run -v $(pwd):/workspace ...` and see their system's architecture in seconds without complex setup.

## Accessibility & Inclusion

- Adherence to WCAG 2.1 AA guidelines for high-contrast color coding (ensuring red violation arrows and emerald conforming badges have sufficient contrast against dark slate backgrounds).
- Keyboard-navigable canvas modals, explicit ARIA roles (`role="dialog"`), and legible monospace typography.
