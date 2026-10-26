---
target: frontend/src/App.svelte
total_score: 33
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 1
target_identity: "file:/Users/fady/workspace/labs/archlens/frontend/src/App.svelte"
target_fingerprint: "sha256:306c5543121620cde747d1a761a3a3447ebb8f5a553ac309bd58322a66eba366"
target_path: /Users/fady/workspace/labs/archlens/frontend/src/App.svelte
timestamp: 2026-10-06T19-53-59Z
slug: frontend-src-app-svelte
---
# Design Critique: frontend/src/App.svelte (and Canvas/Inspector Shell)

Method: dual-agent (A: ef55c4d4 · B: 058aa936)

## Design Health Score

| Heuristic | Score (0-4) | Status | Key Observation |
|:---|:---:|:---:|:---|
| 1. Visibility of System Status | 4 | Pass | Live SSE indicator, file watcher sync pill, agent re-indexing pulse, GSAP-animated violation counters, and live mailbox telemetry. |
| 2. Match Between System & Real World | 4 | Pass | Concentric clean architecture rings, Uncle Bob Robert C. Martin metrics (A, I, D, Instability), Martin DIP semantics, Screaming Arch (Ch. 21). |
| 3. User Control & Freedom | 3 | Pass | Smooth pan/zoom, reset camera, sandbox rollback, toggle layers. Minor issue: no immediate revert on individually staged sandbox moves. |
| 4. Consistency & Standards | 3 | Pass | Standard dark IDE theme, Lucide iconography, monospace metrics. Inconsistency: project selector is native HTML `<select>` while others are dark-glass widgets. |
| 5. Error Prevention | 3 | Pass | Sandbox isolates moves in memory before disk write; lacks a dry-run confirmation modal when dispatching `APPLY_PROPOSAL` to agent. |
| 6. Recognition Rather Than Recall | 4 | Pass | Explicit shortcut hint chips (`⌘K`, `S`, `M`, `P`, `L`) visible in buttons and tooltips. Command palette surfaces all actions visually. |
| 7. Flexibility & Efficiency of Use | 4 | Pass | Superb for power users: Single-key hotkeys for declutter modes (`V` for Violation X-Ray, `B` for Edge Bundling, `C` for Compact, `F` for 1-hop neighborhood). |
| 8. Aesthetic & Minimalist Design | 3 | Pass | Toolbar chunking and 3-tab inspector segmented control dramatically reduce cognitive clutter. High defect counts still trigger multiple simultaneous pulses. |
| 9. Error Recovery & Diagnosis | 3 | Pass | Outward dependency violations clearly explain *From* and *To* packages with 1-click DIP port extraction. |
| 10. Help & Documentation | 2 | Needs Work | Legend explains edge colors, but lacks contextual tooltips explaining metrics ($C_a$, Instability, Zone of Pain) or the Dependency Rule to junior architects. |
| **Total** | **33 / 40** | **82.5% (High Craft / Professional B+)** | Up from 30/40 (75%). Toolbar chunking and tabbed inspector resolved choice overload and vertical fatigue. |

## Design Specificity Verdict

- **Classification**: **Deeply Custom Architectural CAD Workbench**.
- **Rationale**: Archlens is uniquely anchored in Uncle Bob's Clean Architecture theory. It avoids generic dashboard tropes: concentric ring gravity, Inward vs Outward dependency vectors, Martin metrics ($C_a, C_e, I, D$), and automated DIP port synthesis are core to every screen.
- **Detector Status**: Clean static audit across `App.svelte`, `Inspector.svelte`, `Canvas.svelte`, and `ComponentBox.svelte` (0 rule violations).
- **Evidence Verification**: Static code audits reveal dense micro-typography (`text-[9px]`) and sub-44px targets, determined to be intentional desktop CAD density patterns rather than defects. Suppressed Svelte SVG accessibility warnings in `ComponentBox.svelte` confirmed.
- **Visual Overlay Status**: Offline standby (localhost HTTP port inaccessible in sandboxed environment).

## What's Working

1. **Tri-Partite Toolbar Semantic Chunking**: The refactored header cleanly separates *Identity* (Project & Source), *Operational Modes* (Sandbox, Scatter, Policy, LLM), and *System Telemetry* (Cycles, SAS, Breaches). It eliminates toolbar choice overload.
2. **Tabbed Progressive Disclosure in Inspector**: Dividing the 600+ line inspector into `[Views] | [Inventory] | [Fitness]` allows architects to focus on one mental mode at a time without endless vertical scrolling.
3. **Interactive Architectural X-Ray (`V`)**: Stripping away conforming links with a single keystroke to isolate structural rot remains an outstanding developer experience.

## Priority Issues

### [P0] Canvas Nodes Inaccessible to Keyboard-Only & Screen Reader Users
- **Location**: `frontend/src/lib/components/ComponentBox.svelte:65-75`
- **Why it matters**: Package nodes and class cards rely solely on pointer interactions (`onpointerdown`) and have Svelte a11y lints suppressed. Keyboard-only engineers cannot tab through the diagram nodes or inspect classes without mouse/trackpad.
- **Fix**: Add `role="button"`, `tabindex="0"`, `aria-label="{component.label}, Ring {component.level}, {component.classes.length} classes"`, and handle `Enter` / `Space` keydown to focus nodes.
- **Suggested command**: `/impeccable a11y` (or `/impeccable harden`)

### [P1] Inspector Tab Hijacking on Component Selection
- **Location**: `frontend/src/lib/components/Inspector.svelte:17-21`
- **Why it matters**: The reactive effect `$effect(() => { if (diagramStore.focusedNodeId) activeTab = 'classes'; });` forcibly overrides the user's tab. If an architect is evaluating Fitness or toggling Views while clicking around the canvas, their view is repeatedly hijacked back to the Inventory tab.
- **Fix**: Only auto-switch to `'classes'` if the user double-clicks a component or if the Inventory tab was already active; preserve user tab choice across single-clicks.
- **Suggested command**: `/impeccable clarify`

### [P2] High-Stakes Dread: Ambiguous Agent "Regen" Dispatch Safety
- **Location**: `frontend/src/lib/components/Inspector.svelte:595-615`
- **Why it matters**: The prominent emerald button labeled `Regen (Wake Agent)` does not explain what will happen. Users hesitate, wondering whether it will overwrite unstaged Git files or trigger an unconstrained LLM refactor.
- **Fix**: Add clear micro-copy or a confirmation diff preview explaining the exact mailbox action (e.g. "Re-index AST" vs "Dispatch Proposal: 12 file changes to .archlens/to-agent.json").
- **Suggested command**: `/impeccable distill`

### [P3] Native `<select>` Breaks Dark Glass Aesthetic
- **Location**: `frontend/src/App.svelte:150-190`
- **Why it matters**: The project selector uses a native HTML `<select>` which triggers an un-themed OS popup menu, clashing with the dark Slate-950 backdrop-blur design language.
- **Fix**: Replace the native `<select>` with a custom dark-glass dropdown menu matching the Git Version Comparator and Command Palette.
- **Suggested command**: `/impeccable polish`

## Persona Red Flags

- **Alex (Impatient Power User)**: Loves the single-key shortcuts, but gets irritated when clicking nodes on the canvas constantly flips the Inspector away from the Fitness tab back to Inventory.
- **Jordan (Junior Architect)**: Understands the concentric rings better with the new tabbed layout, but still lacks inline tooltips explaining what $C_a$, Instability, and Zone of Pain mean without looking up Uncle Bob's book.
- **Sam (Accessibility-Dependent User)**: Still locked out of navigating the SVG diagram nodes directly via keyboard `Tab` or screen reader announcements.

## Minor Observations

- The File Watcher Sync idle text (`text-slate-500` on `bg-slate-900`) has a contrast ratio of ~3.6:1, slightly below WCAG AA 4.5:1.
- No celebratory zero-defect visual state when all architecture violations are resolved.

## Questions to Consider

- Should the Inspector adapt dynamically: showing a high-level "System Architecture Overview" when nothing is selected, and class details only when a node is selected?
- Would adding interactive tooltips to the Robert C. Martin metrics table ($C_a, C_e, I, D$) help bridge the gap for junior developers?
