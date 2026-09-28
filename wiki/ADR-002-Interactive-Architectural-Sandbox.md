# ADR-002: Interactive Architectural Sandbox & Robert C. Martin Metrics Engine

## Status
**Accepted & Merged** (PR #113)

## Context
Refactoring monolithic or layered architectures into Clean Architecture concentric rings requires iterative experimentation. Developers need to know:
- "If I move this class to the domain core, how many violations will it create or resolve?"
- "Will this package become too rigid (*Zone of Pain*) or too speculative (*Zone of Uselessness*)?"

Previously, the only way to test package reorganizations was to physically modify files, update imports, and re-scan the codebase. This high barrier discouraged exploratory architectural prototyping.

## Decision
We implemented an in-memory **Architectural Sandbox** and **Robert C. Martin Coupling & Stability Metrics Engine**:

1. **In-Memory Simulation State (`diagram.svelte.ts`)**:
   - Implemented reactive Svelte 5 runes (`isSandboxActive`, `stagedClassMoves`).
   - Built a dynamic graph simulator in `martinMetrics.ts` that clones the active architecture graph, relocates classes to candidate tiers, and recalculates edge validity and violation deltas in sub-millisecond time.

2. **Robert C. Martin Metrics Suite**:
   - Implemented exact mathematical calculations for Afferent Coupling ($C_a$), Efferent Coupling ($C_e$), Instability ($I = \frac{C_e}{C_a + C_e}$), Abstractness ($A = \frac{N_a}{N_c}$), and Distance from the Main Sequence ($D = |A + I - 1|$).
   - Classified components into architectural health zones: *Zone of Pain*, *Zone of Uselessness*, and *Balanced*.

3. **Floating Sandbox Command Dock & Agent Dispatch**:
   - Created `SandboxToolbar.svelte` floating amber dock with staged moves counter, real-time violation delta badge (`⚡ ±N Violations`), metrics drawer, and Reset/Save actions.
   - Wired `Save Proposal` into `.archlens/policy.json` (`proposals` section).
   - Wired `Dispatch to Agent` into `.archlens/to-agent.json` with command `APPLY_PROPOSAL`.

## Consequences

### Positive
- Zero-disk risk-free architectural prototyping.
- Instant quantitative feedback on stability and coupling metrics.
- Direct conversion of visual prototypes into executable agent refactoring directives.

### Negative / Trade-offs
- Graph simulation operates on the client side in Svelte 5 memory; projects with $>5,000$ classes will require WebWorker offloading for graph cloning in future iterations.
