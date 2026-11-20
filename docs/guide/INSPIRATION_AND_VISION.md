# Architectural Vision

> *"Source code dependencies must point only inward, toward higher-level policies."*

---

## 1. Why Dynamic Architecture Governance Matters

Modern software teams and autonomous AI coding agents ship features rapidly, yet without continuous structural guardrails codebases accumulate outward dependency leaks, circular package dependencies, and tightly coupled framework calls. Static diagrams drift out of date the day after they are drawn, while text-only linters surface raw violations without visual context or concrete remediation paths.

Inspired by Clean Architecture principles, **Archlens** turns concentric ring governance into a live, interactive workbench right in the browser and terminal.

---

## 2. The Enterprise Reality

Production repositories carry concrete operational requirements:

* **Modern Polyglot Stacks**: Enterprise systems span Java 25 (records, sealed types, virtual threads), Python, TypeScript / Svelte 5, Rust, and Go across multi-module builds and workspaces.
* **Scale and Visibility**: Repositories contain hundreds of classes where unreadable or malformed files must be surfaced explicitly (`skippedFiles`) rather than silently omitted.
* **Zero-Friction Toolchains**: Governance must run headlessly in CI/CD (emitting SARIF 2.1.0), launch locally on `127.0.0.1:8088` with per-session token protection, and integrate directly with AI coding assistants over MCP 2.0.

---

## 3. Core Engineering Pillars

### A. Polyglot Scanners & Explicit Telemetry
The Quarkus Java 25 backend combines JavaParser 3.28 AST analysis for Java 25 with statement-normalizing scanners for Python, TypeScript/JS/Svelte 5, Rust, and Go:
- Extracts classes, interfaces, records, method signatures, cyclomatic complexity, and CRAP scores ($CC^2 \cdot (1 - \text{Cov})^3 + CC$).
- Counts and logs unreadable or unparseable files explicitly so scans are never silently incomplete.

### B. Reactive Svelte 5 Concentric Canvas
The frontend pairs Svelte 5 runes with SVG and a high-scale HTML5 2D `<canvas>` renderer:
- **Concentric Ring Layouts**: Components arrange into concentric levels with Level 0 (Domain Core) at the center.
- **Directional Violation X-Ray**: Conforming inward dependencies render subtly while outward breaches stand out in crimson.
- **"What-If" Sandbox & DIP Synthesis**: Prototype class moves in memory and synthesize Dependency Inversion Principle (DIP) interface ports before modifying files on disk.

### C. Closed-Loop Autonomous Agent Collaboration
Through both native Model Context Protocol (MCP 2.0) endpoints and the `.archlens/` mailbox queue (`to-agent.json` / `to-viewer.json`), AI coding agents can:
- Inspect outward breaches and package cycles.
- Synthesize inner-layer ports and refactor outer adapters.
- Verify violation/cycle deltas and test suites, with recoverable rollback via `.archlens/trash/<snapshotId>/`.
