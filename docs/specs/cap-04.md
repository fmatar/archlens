# SPEC-04: What-If Sandbox Simulation, Class-Move Precedence & Proposal Persistence

- **Linked Capability**: `CAP-04`
- **Status**: `ACTIVE`
- **Depends On**: `CAP-01`, `CAP-02`
- **Source Reference**: [Polyglot AST Scanners & Surgical DIP Inverter Problem Report — Issue 4](https://gist.github.com/djulen/2ddfd48bbc3792d42856d984d6afbb32)

## 1. Domain Contract & Acceptance Criteria

1. **Non-Java Edge & Multi-Class Module Simulation in `martinMetrics.ts`**:
   - Ensure `calculateComponentMartinMetrics()` and `simulateSandboxGraph()` in `frontend/src/lib/utils/martinMetrics.ts` accurately resolve class-level and module-level edges across Java, Python, TypeScript, Go, and Rust.
   - When a class is reassigned in Sandbox mode, ensure its moved level takes precedence over its original package/module prefix so `edge.isViolating`, `violationDelta`, and component $C_a, C_e, I, A, D$ metrics update in real time.
2. **Proposal Layer Class Serialization & Disk Persistence (`diagram.ts`, `diagram.svelte.ts`)**:
   - Add `classes?: string[]` to `ProposalLayer` in `frontend/src/lib/types/diagram.ts`.
   - Update `saveSandboxAsProposal()` in `frontend/src/lib/state/diagram.svelte.ts` to populate `classes: (c.classes || []).map((cls) => cls.id)` in each `ProposalLayer`.
   - Persist the updated policy (including the newly created `Proposal`) to `.archlens/policy.json` via `POST /api/policy` (`this.savePolicy(this.policy)` / `fetch`) and reload the graph with `activeProposalId = proposalId` so the saved reorganization remains active after exiting Sandbox mode.
3. **Explicit Class-Move Precedence in `GraphCompiler.java` & `DependencyRuleValidator.java`**:
   - In `GraphCompiler.compileGraph()`, when building virtual proposal components (`activeProposal != null`), collect all explicitly assigned `layer.classes()` across all proposal layers first so a class moved to Layer B is excluded from Layer A's `layer.packages()` expansion (preventing duplicate class cards across layers).
   - In `DependencyRuleValidator.fromProposal(Proposal)`, index explicit `layer.classes()` overrides separately with higher lookup precedence than package-level ranks in `resolveRank(String id)` so moved classes evaluate dependency rules against their new proposal layer rank rather than their original package prefix.
