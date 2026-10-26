---
target: frontend/src/App.svelte
total_score: 33
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 1
target_identity: "file:/Users/fady/workspace/labs/archlens/frontend/src/App.svelte"
target_fingerprint: "sha256:306c5543121620cde747d1a761a3a3447ebb8f5a553ac309bd58322a66eba366"
target_path: /Users/fady/workspace/labs/archlens/frontend/src/App.svelte
timestamp: 2026-10-06T19-57-40Z
slug: frontend-src-app-svelte
---
# Design Critique: frontend/src/App.svelte (Full Workbench Shell & Canvas UI)

Method: dual-agent (A: 7f235064 · B: 984f2a2c)

## Design Health Score

| # | Heuristic | Score (0-4) | Status | Key Observation |
|---|---|:---:|:---:|---|
| **1** | **Visibility of System Status** | **3.5 / 4** | Pass | Real-time SSE indicator (`Live` / `Sync Idle`), agent re-indexing pulse, GSAP-animated violation counters, and Screaming Architecture SAS score. |
| **2** | **Match Between System & Real World** | **4.0 / 4** | Pass | Strictly models Clean Architecture lexicon: Rings 0–3, Robert C. Martin ADP cycles, Instability/Distance metrics, and Chapter 21 Screaming Architecture. |
| **3** | **User Control and Freedom** | **3.0 / 4** | Pass | Smooth pan/zoom, `0` camera reset, `Escape` clears focus/tooltips, isolated in-memory sandbox simulator. |
| **4** | **Consistency and Standards** | **3.5 / 4** | Pass | Standard keybindings (`⌘K`, `⌘O`, `+`, `-`, `0`, `Space`, `Enter`), dark IDE aesthetic, and disciplined monospace typography. |
| **5** | **Error Prevention** | **3.0 / 4** | Pass | Destructive DIP inversions and package re-assignments are isolated in modals and memory sandbox before touching disk. |
| **6** | **Recognition Rather Than Recall** | **3.5 / 4** | Pass | Co-located shortcut key chips (`⌘K`, `S`, `M`, `P`, `L`) on action buttons. 3-tab Inspector clearly segments concerns. |
| **7** | **Flexibility and Efficiency of Use** | **3.5 / 4** | Pass | Single-key hotkeys for declutter modes (`V` for X-Ray, `B` for Bundling, `C` for Compact, `F` for 1-hop), frustum culling, and LOD zooming. |
| **8** | **Aesthetic and Minimalist Design** | **3.0 / 4** | Pass | Toolbar chunking and tabbed Inspector reduce choice overload. High-contrast dark theme with frosted glass surfaces. |
| **9** | **Error Recovery & Diagnosis** | **3.5 / 4** | Pass | Outward dependency violations clearly explain *From* and *To* packages with 1-click DIP port extraction. Empty canvas offers 3 recovery hints. |
| **10**| **Help and Documentation** | **2.5 / 4** | Needs Work | Command palette lists quick actions, but workbench lacks an inline architectural cheat sheet explaining Martin metrics ($A, I, D$). |
| **TOTAL**| **Composite Score** | **33.0 / 40** | **82.5%** | **Grade: B+ (High Craft / Professional)** *(Stable high-craft tier, up from initial 30/40, 75%)* |

## Design Specificity Verdict

- **Classification**: **Deeply Custom Architectural CAD Workbench**.
- **Rationale**: Archlens is unequivocally grounded in Uncle Bob's Clean Architecture theory. It avoids generic dashboard tropes: concentric ring gravity, Inward vs Outward dependency vectors, Martin metrics ($C_a, C_e, I, D$), and automated DIP port synthesis are core to every screen.
- **Detector Status**: Clean static audit across `App.svelte`, `Canvas.svelte`, `ComponentBox.svelte`, and `Inspector.svelte` (0 rule violations).
- **Evidence Verification**: 
  - `ComponentBox.svelte` package nodes now have `role="button"`, `tabindex="0"`, dynamic `aria-label`, and `Enter`/`Space` keyboard navigation.
  - Class items within cards have `role="button"`, `tabindex="0"`, dynamic CRAP/coverage labels, and keyboard activation.
  - `Canvas.svelte` SVG root now carries `role="region"` and accessible diagram label.
  - Tab hijacking resolved: selecting nodes preserves the active Inspector tab during Fitness or Views reviews.
- **Visual Overlay Status**: Offline standby (browser launch restricted by macOS sandbox).

## What's Working

1. **Accessibility & Keyboard Parity**: Engineers can now tab directly into the SVG canvas, navigate through package cards, and inspect individual classes using `Enter` or `Space` without touch or mouse reliance.
2. **Predictable State Preservation**: Navigating across nodes no longer jerks the Inspector view away from the Fitness Invariants tab back to Inventory.
3. **Tri-Partite Toolbar Semantic Chunking**: Clear separation of *Identity* (Project), *Operational Modes* (Sandbox, Scatter, Policy, LLM), and *System Telemetry* (Cycles, SAS, Breaches).

## Remaining Priority Issues

### [P1] Class Micro-Pager Accessibility in SVG Cards
- **Location**: `frontend/src/lib/components/ComponentBox.svelte:354-385`
- **Why it matters**: While classes can be selected via keyboard, packages with >6 classes have pagination chevrons (`<g class="cursor-pointer">`) that lack `tabindex="0"` and keyboard listeners.
- **Fix**: Add `role="button"`, `tabindex="0"`, and keydown handlers to the pagination chevrons, or rely on the Inspector Inventory list when a package has many classes.
- **Suggested command**: `/impeccable a11y`

### [P2] High-Stakes Dread: Ambiguous Agent "Regen" Dispatch Safety
- **Location**: `frontend/src/lib/components/Inspector.svelte:600-614`
- **Why it matters**: The prominent emerald button labeled `Regen (Wake Agent)` does not explain what will happen on disk. Users hesitate, wondering whether it will overwrite unstaged Git files.
- **Fix**: Add clear micro-copy or a confirmation diff preview explaining the exact mailbox action (e.g. "Re-index AST" vs "Dispatch Proposal: 12 file changes to .archlens/to-agent.json").
- **Suggested command**: `/impeccable distill`

### [P3] Top Bar Telemetry Responsive Collapse
- **Location**: `frontend/src/App.svelte:300-340`
- **Why it matters**: When all indicators trigger simultaneously (Cycles, SAS, Violations, Re-indexing, File sync), they crowd the right side on compact 1280px displays.
- **Fix**: Wrap telemetry pills in a responsive overflow container or collapse secondary indicators into an expandable pill on narrower viewports.
- **Suggested command**: `/impeccable adapt`

## Persona Walkthroughs

- **Alex (Staff Architect)**: High-speed keyboard navigation (`⌘K`, `V`, `M`, `S`, `L`) is fluid. Tab stability in the Inspector allows uninterrupted architecture invariant audits.
- **Jordan (Junior Full-Stack Dev)**: Understands the concentric rings and dependency directions better; appreciates the clean semantic toolbar and accessible canvas cards.
- **Sam (Accessibility-Dependent User)**: Can now tab directly into packages on the SVG canvas and inspect classes via keyboard, a massive improvement from previous mouse-only requirements.

## Questions to Consider

- Should canvas cards show only the top 3 high-risk classes or an aggregate sparkline, eliminating tiny SVG chevron clicks altogether in favor of the full virtualized Inspector list?
- Could violating edges have subtle haptic or auditory feedback when dragged across forbidden layer boundaries in Sandbox mode?
