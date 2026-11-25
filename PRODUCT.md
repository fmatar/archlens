# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Software architects, engineering leaders, and software engineers working extensively with autonomous AI coding agents (Google Antigravity, Claude Code, Cursor, Codex). They need real-time, visual architectural governance to inspect, verify, and steer how AI agents evolve multi-layer, polyglot software systems.

## Product Purpose

Archlens transforms the Clean Architecture Dependency Rule into a living, real-time visual feedback loop. It solves a critical vulnerability in modern agentic software development: while frontier AI models generate functional code rapidly, without continuous architectural boundaries they silently introduce outward dependency violations, cross-layer leakage, circular dependencies, and design erosion. Success means architects and developers can instantly identify breaches, prototype structural refactorings in a zero-disk sandbox, and dispatch targeted directives to autonomous AI agents to refactor code back into 100% architectural conformance.

## Positioning

Unlike static UML diagramming tools (PlantUML, Enterprise Architect) that fall out of sync with code, and unlike text-heavy static analysis linters (ArchUnit, SonarQube) that output disconnected error logs, Archlens is an interactive, real-time visual workbench. It couples polyglot code scanning directly with dynamic concentric ring visualization, "What-If" architectural simulation, component coupling/stability metrics, and closed-loop AI agent refactoring via both a local mailbox IPC (`.archlens/`) and a native Model Context Protocol (MCP) server.

## Operating Context

- **Local Workstation & Container**: Runs either via a zero-dependency standalone container (`ghcr.io/fmatar/archlens:latest` on `127.0.0.1:8088`), an auto-launching native binary / JVM runner (`quarkus-run.jar` in ~0.5s), or bare-metal dev mode (`mvn -f backend/pom.xml quarkus:dev` + `pnpm --dir frontend dev`).
- **AI Agent Pairing (Dual Protocol)**:
  - **Mailbox IPC Companion Protocol**: Asynchronous, file-based IPC in `.archlens/` (`to-agent.json` and `to-viewer.json`) for local paired refactoring tasks (`APPLY_PROPOSAL`, `INVERT_DEPENDENCY`, `REGEN`, `REFRESH_CRAP`).
  - **Model Context Protocol (MCP)**: Native HTTP/SSE/STDIO (`/mcp`, `/mcp/sse`, `mcp-stdio`) and CLI stdio bridge exposing 11 tools (`inspectArchitecture`, `synthesizeDipInversion`, `exportLlmDossier`, `exportArchitectureDiagram`, `evaluateFitness`, `getFitnessHistory`, `proposeScreamingMigration`, `getPolicy`, `syncDipAdr`, `listSnapshots`, `getSnapshot`).
- **Architectural Policy**: Version-controlled `.archlens/policy.json` placed directly inside target repositories, defining concentric tiers (Domain Core, Application, Adapters, Infrastructure), package order, and structural proposals.

## Capabilities and Constraints

- **Polyglot Scanners (SPI)**: Dependency extraction across 5 languages: Java 25 (JavaParser 3.28 AST), TypeScript/JavaScript/Svelte 5 (statement-normalizing lexical scanner with runes and `#lib/*` subpath aliases), Python (statement-normalizing lexical scanner), Rust (lexical module & trait scanner), and Go (lexical package & interface scanner), with explicit `skippedFiles` telemetry for unreadable or malformed files.
- **Concentric Architecture Rings**: Dynamic SVG + high-scale 2D `<canvas>` workbench rendering components and classes in tiered levels, automatically flagging inward (valid) vs outward (violating) dependencies in real time.
- **Surgical DIP Inverter**: 1-click synthesis of Dependency Inversion Principle interface ports and injection refactoring previews.
- **"What-If" Architectural Sandbox**: Interactive in-memory class reassignment simulator displaying live violation deltas ($\Delta \pm N$) and coupling metrics before writing changes to disk.
- **Component Coupling & Main Sequence**: Automated calculation of Afferent Coupling ($C_a$), Efferent Coupling ($C_e$), Instability ($I$), Abstractness ($A$), and Distance from Main Sequence ($D$) with an interactive 2D Cartesian scatter plot.
- **Screaming Architecture & Feature Clustering**: Calculation of Screaming Architecture Score (SAS) with automated feature clustering and 1-click domain verticalization proposals.
- **Architectural Fitness Functions**: Quality invariants checking zero cyclic dependencies (ADP), breach thresholds, and release snapshot comparisons.

## Brand Commitments

- **Name**: Archlens (Dynamic Clean Architecture Workbench).
- **Philosophical Root**: Inspired by Clean Architecture concentric layer and Dependency Inversion principles.
- **License**: Apache 2.0 with full open-source community standards (`CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `NOTICE`).
- **Aesthetic Tone**: Developer-focused, dark-mode first, precise, high-contrast, technical, non-gimmicky workbench aesthetic.

## Evidence on Hand

- **Working Codebase**: Java 25 + Quarkus 3.40.1 backend, Svelte 5 + Tailwind CSS v4 + GSAP 3.15 frontend, and Node.js CLI (`@fadym/archlens-skill`).
- **Living Self-Analysis**: Self-governing policy (`.archlens/policy.json`) achieving 1.00 (100%) Screaming Architecture Score with 0 violations and 0 cycles.
- **Media Artifacts**:
  - `media/archlens-teaser.mp4` & `media/archlens-canvas.png`: 9-second high-definition overview and workbench screenshot highlighting concentric ring reorganization and source code inspection.
  - `media/archlens-user-journey.mp4`: Full interactive walkthrough video.
  - `media/archlens-canvas.png`: High-resolution canvas snapshot.
- **Documentation**: C4 Architecture specification, User Guide, Inspiration & Vision, and SDLC Compliance reports in `docs/`.

## Product Principles

1. **Code is Truth**: Never require manual diagram drawing. All nodes and dependency edges reflect actual AST parsers scanning actual source code files.
2. **Inward Dependencies Only**: The Dependency Rule is immutable; violations are high-visibility alerts that must never be hidden or softened.
3. **Agentic by Design**: Built from day one to treat autonomous AI coding agents as first-class collaborators via clean mailbox and MCP handoffs.
4. **Zero-Friction Test-Drive**: A developer should be able to run `npx @fadym/archlens-skill` and visualize their architecture in under two minutes without setup friction.

## Accessibility & Inclusion

- Adherence to WCAG 2.1 AA guidelines for high-contrast color coding (ensuring red violation arrows and emerald conforming badges have sufficient contrast on dark slate backgrounds).
- Keyboard-navigable canvas modals, explicit ARIA roles (`role="dialog"`), and legible monospace typography.

---

## Hard Domain & System Invariants (`INV-XX`)

- **INV-01**: Zero direct commits, merges, or pushes to `main` or `develop` (enforced by 4-Layer Git Guard & Stage 7 Merkle Gate Receipts).
- **INV-02**: 100% of code changes require an active SDLC ticket session and Dual-Gate verification ($\ge 85\%$ diff coverage, $\ge 80\%$ mutation score).
- **INV-03**: Core Domain entities (Ring 0) must remain pure with zero imports from outer Web, DB, or framework layers (Clean Architecture Dependency Rule).
- **INV-04**: Polyglot Scanners must emit class/interface-level `DependencyEdge` endpoints when symbols or classes exist (falling back to module `ClassNode` IDs for function-only modules) and classify abstract/interface stereotypes (`INTERFACE`, `ABSTRACT`) accurately for component coupling metrics ($C_a, C_e, I, A, D$).
- **INV-05**: All architectural or contract deviations from this constitution require a linked ADR (`docs/adr/ADR-00X.md`) synchronized with `PRODUCT.md` and `docs/DECISION_MAP.md`.

---

## Product Capabilities & Spec Traceability Matrix (`CAP-XX`)

| Capability ID | Capability Title | Functional Spec | Depends On | Status |
| :--- | :--- | :--- | :--- | :--- |
| CAP-01 | Python AST Class-Level Edges, ABC/Protocol Stereotypes & Method Signatures | `docs/specs/cap-01.md` | None | `AMENDED (ADR-006)` |
| CAP-02 | TypeScript/JS Multi-Line ESM, Barrel Re-Exports, tsconfig Aliases & CLI Scanner | `docs/specs/cap-02.md` | None | `AMENDED (ADR-006)` |
| CAP-03 | Polyglot Surgical DIP Inverter & CLI Main Sequence Distance | `docs/specs/cap-03.md` | CAP-01, CAP-02 | `AMENDED (ADR-006)` |
| CAP-04 | What-If Sandbox Simulation, Class-Move Precedence & Proposal Persistence | `docs/specs/cap-04.md` | CAP-01, CAP-02 | `AMENDED (ADR-006)` |

---

## Architectural Decision Log Index (`ADR-XXX`)
<!-- ADR_INDEX_START -->
| ADR ID | Title | Scope | Status | Ticket | Supersedes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ADR-001 | Quarkus 3.40 MCP 2.0 Hexagonal Decoupling, Virtual Threads & Main Sequence Calibration | `technical` | `accepted` | 6262 | CAP-01, CAP-02, CAP-03, CAP-04 |
| ADR-002 | Quarkus 3.40.1.1 BOM Upgrade, Ring 1 WorkspacePathResolver Binding & MCP Hibernate Validator | `technical` | `accepted` | 196 | CAP-01, CAP-02, CAP-03, CAP-04 |
| ADR-003 | Multi-Agent Local MCP Registry, CI Hardening & End-to-End Smoke Suite | `technical` | `accepted` | 200 | CAP-01, CAP-02, CAP-03, CAP-04 |
| ADR-004 | Multi-Format Architecture Diagram Export (Mermaid, SVG, PNG) & v1.1.0 Release | `technical` | `accepted` | 4772 | CAP-01, CAP-02, CAP-03, CAP-04 |
| ADR-005 | Quarkus 3.40.1.2, MCP 2.0.4 & Latest GA Dependency Alignment | `technical` | `accepted` | 6157 | CAP-01, CAP-02, CAP-03, CAP-04 |
| ADR-006 | Release v1.1.1 — Manifest Synchronization, Brand Identity & GA Dependency Alignment | `technical` | `accepted` | 222 | CAP-01, CAP-02, CAP-03, CAP-04 |
| ADR-007 | Enforce Agent Skills Release Parity and Convert Skill Installer to npx skills | `technical` | `accepted` | 8238 | None |
| ADR-008 | Release v1.1.2 — Agent Skills Release Parity & npx skills Ecosystem Publication | `technical` | `accepted` | 9283 | ADR-007 |
| ADR-009 | Dual-Transport Quarkus MCP STDIO Profile and Ephemeral Docker Container Support | `technical` | `accepted` | 8714 | None |
| ADR-010 | Release v1.1.3 — Auto-MCP Global Onboarding, Interactive Docker Transport Choice & Documentation Parity | `technical` | `accepted` | 8454 | ADR-009 |
| ADR-011 | Release v1.1.4 — Fix npx Symlink Entrypoint Resolution in @fmatar/archlens-skill | `technical` | `accepted` | 4934 | ADR-010 |
| ADR-012 | Phase 1 Security, Action Self-Test Fixtures & macOS Canonical Root Guard | `technical` | `accepted` | 224 | ADR-011 |
| ADR-013 | Release v1.1.5 — Phase 0 Workbench Security & Phase 1 CLI, GitHub Action & CI Hardening | `technical` | `accepted` | 247 | ADR-012 |
| ADR-014 | Release v1.1.6 — Security Hotfix for Matrix-Parameter Auth Bypass, Chained RCE, CI Gates & Supply Chain | `technical` | `accepted` | 247 | ADR-013 |
| ADR-015 | Phase 1.5 — GraalVM Native SecureRandom Fix, Recoverable Rollback Trash & Diagram Export MCP Tool Parity | `technical` | `accepted` | 263 | ADR-014 |
| ADR-016 | Wave 1 — MCP Write Confinement, Pure-Git Tag Caching, and Watcher Root Switching | `technical` | `accepted` | 265 | ADR-015 |
| ADR-017 | Phase 2 — Skipped-File Telemetry, Clojure & Legacy Prototype Removal, and Provenance Hygiene | `technical` | `accepted` | 238 | ADR-016 |
| ADR-018 | Phase 3 — Repository Hygiene, Clean Architecture Ports & Constructor DI, and Playwright E2E CI | `technical` | `accepted` | 246 | ADR-017 |
| ADR-019 | Release v1.2.0 — Phase 1.5, Wave 1, Phase 2 Telemetry & Phase 3 Clean Architecture Ports | `technical` | `accepted` | 247 | ADR-018 |
| ADR-020 | Migrate NPM Package Scope to @fadym/archlens-skill with OIDC Trusted Publishing | `technical` | `accepted` | 247 | ADR-019 |
| ADR-021 | Wave 2 — Frontend Minor/Patch Dependency Updates & Monotonic loadGraph Sequence Guard | `technical` | `accepted` | 268 | ADR-020 |
<!-- ADR_INDEX_END -->

