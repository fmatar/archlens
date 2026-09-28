# ADR-001: Surgical DIP Inverter for Outward Violation Remediation

## Status
**Accepted & Merged** (PR #112)

## Context
Clean Architecture strictly mandates the **Dependency Rule**: source code dependencies must point solely inward toward higher-level policies. In existing codebases, outward dependencies (e.g. an application service directly referencing an external database adapter or framework utility) frequently arise.

Previously, Archlens identified these violations visually (crimson edges) and in text dossiers, but resolving them required developers or AI agents to manually read the target class, formulate an interface in the caller's package, update the concretion to implement it, and refactor constructor parameters. This multi-step manual process introduced friction, formatting inconsistencies, and risk of syntax errors.

## Decision
We implemented an automated **Surgical DIP Inverter** spanning the Quarkus backend and Svelte 5 frontend:

1. **AST-Driven Synthesis Engine (`DipInversionSynthesizer`)**:
   - Parses the target concrete class using JavaParser.
   - Extracts all public method signatures (parameters, return types, exceptions).
   - Generates an interface port (`<TargetName>Port.java`) positioned in the caller's inward subpackage (`.ports`).
   - Generates unified diffs for the concrete adapter and the caller class.
   - Formulates a surgical Clean Architecture refactoring directive prompt.

2. **Unified Presentation & Dispatch**:
   - Exposed REST endpoint `GET /api/violations/invert-plan`.
   - Exposed MCP tool `synthesizeDipInversion` for direct AI toolchain invocation.
   - Built a 3-tab reactive modal (`InvertDipModal.svelte`) with GSAP animations, syntax highlighting, and 1-click dispatch to `.archlens/to-agent.json` (`INVERT_DEPENDENCY`).

## Consequences

### Positive
- Remediation time for outward violations reduced from 10–15 minutes of manual editing to a single click.
- Consistent interface naming and placement standards adhering to Robert C. Martin's Dependency Inversion Principle.
- Clean separation of concerns: the workbench synthesizes the architecture plan, while companion agents (Claude / Antigravity) execute the code modifications and test suites.

### Negative / Trade-offs
- Port extraction currently targets Java ASTs via JavaParser; polyglot support for Python, TypeScript, and Go interface ports will follow the same pattern in subsequent phases.
