---
target: frontend/src/App.svelte
total_score: 30
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 1
target_identity: "file:/Users/fady/workspace/labs/archlens/frontend/src/App.svelte"
target_fingerprint: "sha256:1d7be431b2edeee9b7d8d558612fcefa83157861cb8c662229af31b09a038e3b"
target_path: /Users/fady/workspace/labs/archlens/frontend/src/App.svelte
timestamp: 2026-10-06T19-49-01Z
slug: frontend-src-app-svelte
---
# Design Critique: frontend/src/App.svelte

Method: dual-agent (A: 5c0de08f · B: 7f46914d)

## Design Health Score

| Heuristic | Score (0-4) | Status | Key Observation |
|:---|:---:|:---:|:---|
| 1. Visibility of System Status | 3 | Pass | Live SSE indicator, snapshot indicator, reactive violation pill. Missing granular progress bar during heavy AST indexing. |
| 2. Match Between System & Real World | 4 | Pass | Concentric clean architecture rings, Uncle Bob Robert C. Martin metrics (A, I, D, Instability), Martin DIP semantics. |
| 3. User Control & Freedom | 3 | Pass | Smooth pan/zoom, reset camera, sandbox rollback, toggle layers. Canvas lacks clear multi-selection box and keyboard canvas unselect escape. |
| 4. Consistency & Standards | 3 | Pass | Standard dark IDE theme, Lucide iconography, monospace metrics. Disparate button paddings and missing aria-label conventions. |
| 5. Error Prevention | 3 | Pass | Destructive DIP / package reorganization requires explicit modal confirmation and displays impact diff. |
| 6. Recognition Rather Than Recall | 2 | Needs Work | 16 discrete single-key hotkeys without an on-canvas HUD cheat sheet (requires opening ⌘K or remembering letters). |
| 7. Flexibility & Efficiency of Use | 4 | Pass | Dense keyboard shortcuts (`⌘K`, `⌘O`, `P`, `D`, `V`, `B`, `C`, `F`, `T`, `L`, `S`, `M`, `G`, `0`), quick find search, fit-to-screen. |
| 8. Aesthetic & Minimalist Design | 2 | Needs Work | Top toolbar packs 10 distinct action buttons horizontally; Inspector panel is a dense 617-line continuous scroll without tabs. |
| 9. Error Recovery & Diagnosis | 3 | Pass | Architecture violations explain exact imported illegal packages with red laser bezier connections and DIP suggestions. |
| 10. Help & Documentation | 3 | Pass | Command palette lists major shortcuts, policy documentation button linked. |
| **Total** | **30 / 40** | **75% (Good)** | Solid architectural workbench with high fidelity, requiring toolbar grouping and inspector tab chunking. |

## Design Specificity Verdict

- **Classification**: **Deeply Custom Architectural CAD Workbench**.
- **Rationale**: Archlens is unequivocally *not* a generic dashboard or generic wireframe canvas. The visual language directly mirrors Robert C. Martin's concentric Clean Architecture rings (Adapters -> Application -> Domain Core), Instability / Abstractness Main Sequence plots, and Dependency Inversion Principle (DIP) refactoring affordances.
- **Detector Status**: Clean static audit (0 violations on App.svelte). Touch targets and GSAP reduced-motion checks identified for high-craft polish.
- **Visual Overlay Status**: Offline standby (headless browser deferred until Vite dev server is spawned).

## What's Working

1. **Uncompromising Domain Fidelity**: Concentric SVG rings with Robert C. Martin metrics ($A, I, D$) and real-time dependency laser links immediately translate abstract architectural code smell concepts into physical geometry.
2. **First-Class Power User Keyboard Ergonomics**: Direct single-key shortcuts (`P` for policy, `D` for dependency matrix, `S` for sandbox, `M` for metrics, `0` for zoom reset) cater to senior engineers who navigate workspaces without mouse reliance.
3. **Reactive State Feedback**: Real-time SSE connection badge, animated violation counter with GSAP spring dynamics, and live agent status pill ensure the user always knows the workbench state.

## Priority Issues

### [P0] Canvas Nodes Lack Screen Reader & Full Keyboard Navigation
- **Location**: `frontend/src/lib/components/Canvas.svelte`, `ComponentBox.svelte`
- **Why it matters**: Screen reader users and keyboard-only engineers cannot navigate through architecture layers or inspect classes using arrow keys / Tab indexing.
- **Fix**: Add `role="region"`, `aria-label="Clean Architecture Canvas"`, and make package nodes and class cards focusable (`tabindex="0"`) with `Enter` / `Space` activation and arrow-key traversal.
- **Suggested command**: `/impeccable a11y`

### [P1] Top Header Bar Monolithism & Choice Overload
- **Location**: `frontend/src/App.svelte:180-280`
- **Why it matters**: 10 distinct action buttons and pills are packed into a single 48px horizontal row, violating cognitive chunking (Miller's Law / Choice Overload).
- **Fix**: Chunk toolbar into 3 semantic groupings:
  1. *Source & Project*: Open folder, project name, SSE status.
  2. *View Modes*: Segmented pill control for Canvas / Sandbox / Main Sequence / Matrix.
  3. *Actions Menu*: Group Help, LLM Prompt, and Export into a consolidated `...` dropdown menu.
- **Suggested command**: `/impeccable distill`

### [P2] Node Dragging vs Class Selection Hitbox Contention
- **Location**: `frontend/src/lib/components/ComponentBox.svelte`
- **Why it matters**: Clicking on a class inside a package box can accidentally trigger a canvas box drag if the cursor moves slightly during mouse-down.
- **Fix**: Isolate dragging to an explicit header drag handle (`cursor-grab`) and leave the class table hitbox dedicated to clean click-selection.
- **Suggested command**: `/impeccable polish`

### [P3] Inspector Panel Dense Vertical Scroll
- **Location**: `frontend/src/lib/components/Inspector.svelte`
- **Why it matters**: The right inspector panel displays metrics, afferent/efferent couplings, classes, and violations in a single 617-line vertical stack, creating high cognitive fatigue.
- **Fix**: Introduce tabbed progressive disclosure: `[Overview & Metrics] | [Couplings & Dependencies] | [Refactor / DIP]`.
- **Suggested command**: `/impeccable clarify`

## Persona Red Flags

- **Alex (Impatient Power User)**: Loves the single-key shortcuts, but gets frustrated when clicking a class in a card accidentally moves the entire package card because there is no isolated drag handle.
- **Jordan (Junior Architect)**: Overwhelmed by 10 buttons on the top bar and a dense wall of Robert C. Martin metrics in the inspector without contextual explanations or tooltips.
- **Sam (Accessibility-Dependent User)**: Blocked from navigating the SVG diagram via screen reader or keyboard focus; decorative Lucide icons lack `aria-hidden="true"`.

## Minor Observations

- Keyboard badge font size (`text-[9px]`) is legible but on the boundary of comfortable reading.
- GSAP badge scale animation lacks `prefers-reduced-motion: reduce` guard.
- Quick Find placeholder could display `⌘K / Type to search...` for higher discoverability.

## Questions to Consider

- Should the Workbench offer a "Guided Walkthrough / Tour" for first-time architects to explain concentric rings and DIP inversion?
- Would splitting the inspector panel into tabs increase focus during architecture reviews?
