# 🏛️ Product Constitution: [Product Name]

> **Single Source of Truth (`PRODUCT.md`)**
> Governed by **Agentic SDLC Framework (`sdlc-product`)**.
> Any architectural, dependency, or contract deviation from this constitution requires a linked **ADR (`docs/adr/ADR-00X.md`)**.

---

## 1. Product Vision
[Concise executive summary of the product's mission, target personas, and primary value proposition.]

---

## 2. Hard Domain & System Invariants (`INV-XX`)
- **INV-01**: Zero direct commits, merges, or pushes to `main` or `develop` (enforced by 4-Layer Git Guard & Stage 7 Merkle Gate Receipts).
- **INV-02**: 100% of code changes require an active SDLC ticket session and Dual-Gate verification ($\ge 85\%$ diff coverage, $\ge 80\%$ mutation score).
- **INV-03**: Core Domain entities (Ring 0) must remain pure with zero imports from outer Web, DB, or framework layers.

---

## 3. Product Capabilities & Spec Traceability Matrix (`CAP-XX`)

| Capability ID | Capability Title | Functional Spec | Depends On | Status |
| :--- | :--- | :--- | :--- | :--- |
| CAP-01 | Core Domain Model & Invariants | `docs/specs/cap-01.md` | None | `PLANNED` |
| CAP-02 | Persistence & Repository Ports | `docs/specs/cap-02.md` | CAP-01 | `PLANNED` |
| CAP-03 | Reactive API & Mission Control UI | `docs/specs/cap-03.md` | CAP-01, CAP-02 | `PLANNED` |

---

## 4. Genesis & Milestone Execution Rules
- **Genesis Ticket `#1`**: `feature/issue-#1-product-genesis` initializes `PRODUCT.md`, baseline specs, and project scaffolding.
- **Zero-Bypass Merge Rule**: No PR merges to `main` without a signed Stage 7 SHA-256 Merkle Gate Receipt in `docs/AUDIT_CHAIN.jsonl`.

---

## 5. Architectural Decision Log Index (`ADR-XXX`)
<!-- ADR_INDEX_START -->
| ADR ID | Title | Scope | Status | Ticket | Supersedes |
| :--- | :--- | :--- | :--- | :--- | :--- |
<!-- ADR_INDEX_END -->
