---
target: frontend/src/App.svelte
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 6
target_identity: "file:/Users/fady/workspace/labs/archlens/frontend/src/App.svelte"
target_fingerprint: "sha256:14a666e88df9fd053b55b35da86943195b43f1514fbb8088fdf92a5303ac9936"
target_path: /Users/fady/workspace/labs/archlens/frontend/src/App.svelte
timestamp: 2026-11-08T05-48-39Z
slug: frontend-src-app-svelte
closed: true
---
## Design Critique: Archlens Workbench (`frontend/src/App.svelte` + `frontend/src/lib/components/*`)

Method: dual-agent (A: 36623a64-1a27-4b06-9827-dc6b3be4cc97 · B: 6c7e2539-379e-46cb-b111-0a24ca1e97ca)

### Overall Impression
Archlens Workbench is a high-craft dark-mode diagnostic instrument (`#0f172a` canvas, `#1e293b` chrome, JetBrains Mono telemetry) that genuinely avoids generic SaaS bloat and puts Robert C. Martin's Clean Architecture invariants front and center. However, **peripheral toolbar creep, raw unparsed LaTeX strings (`$C_a$`), a z-index collision between the bottom HUD and Agent Drawer, a conflicting `T` keyboard shortcut, a hardcoded emerald mutation dot, and silent Tailwind v4 token mismatches** undermine its otherwise surgical precision.

### Design Health Score

| # | Heuristic | Score | Key Issue |
|---|---|---|---|
| 1 | Visibility of System Status | 3/4 | `ComponentBox.svelte:360` hardcodes mutation dot to `#10b981` (emerald) even when `cls.survived > 0`; `<canvas>` 2D mode (`Canvas.svelte:563`) truncates classes at 5 with no `+N more` indicator or CRAP dots. |
| 2 | Match Between System and Real World | 3/4 | Raw unparsed LaTeX strings (`$C_a$`, `$C_e$`, `$I$`, `$A$`, `$D$`, `$I \to 0$`) leak into UI prose (`Inspector.svelte:347-359`, `SandboxToolbar.svelte:215-237`, `MainSequenceModal.svelte:166-783`); `Canvas.svelte:718` labels a `getContext('2d')` canvas (`L487`) as `"WebGL 60fps"`. |
| 3 | User Control and Freedom | 3/4 | Expanding `AgentDrawer.svelte:36` (`z-30`, `height: 220px`) buries the bottom-left Canvas HUD (`Canvas.svelte:618`, `z-20`) and collides with `SandboxToolbar.svelte:47` (`z-40`); pressing `Escape` inside a modal also fires `App.svelte:126` and clears `focusedNodeId` underneath. |
| 4 | Consistency and Standards | 2/4 | `Inspector.svelte:257` advertises shortcut `T` for `"Hide Tier Backdrops"`, while `App.svelte:98-100` binds `T` to `AgentDrawer`; `Bundled (B)`, `X-Ray (V)`, and `1-Hop (F)` toggles are duplicated across both `Canvas.svelte:670-707` and `Inspector.svelte:170-240`. |
| 5 | Error Prevention | 2/4 | `AgentDrawer.svelte:119-130` (`Revert Snapshot`) triggers an immediate, destructive disk rollback of source files and `.archlens/policy.json` on a single click inside a clickable `36px` header bar with zero confirmation. |
| 6 | Recognition Rather Than Recall | 3/4 | `ComponentBox.svelte:357-360` renders two unlabeled `3.5px` dots side-by-side (coverage vs. mutation) with no tooltip; `Inspector.svelte:156-166` (`Declutter Mode`) omits its `D` shortcut badge. |
| 7 | Flexibility and Efficiency of Use | 3/4 | High-density `<canvas>` mode (`W`, `Canvas.svelte:464-578`) silently disables node dragging, class-row clicking (`ClassCard`), edge hover/click (`EdgeTooltip`), arrowheads, and CRAP dots. |
| 8 | Aesthetic and Minimalist Design | 2/4 | High control crowding in peripheral bars (11 header items in `App.svelte`, 10 HUD items in `Canvas.svelte:618-720`, 8 items in `AgentDrawer.svelte` 36px bar); decorative gradient + wand emoji (`Migrate to Features 🪄`, `Inspector.svelte:537-545`) violates `DESIGN.md:116`. |
| 9 | Help Users Recognize, Diagnose, and Recover from Errors | 4/4 | Exemplary diagnostic empty state (`Canvas.svelte:724-763`) and 3-step `InvertDipModal.svelte` recovery flow. |
| 10 | Help and Documentation | 3/4 | Helpful inline Martin Metrics cheat sheet (`Inspector.svelte:316-343`) and non-destructive mailbox callout (`Inspector.svelte:645-656`), marred only by raw `$C_a$` strings. |
| **Total** | | **28/40** | **Good** |

### Design Specificity Verdict
- **LLM Assessment: Pass (Domain-Authentic Diagnostic Instrument, with localized regressions)**. The canvas, concentric ring guides, sonar sweeps, and monospace telemetry badges feel purpose-built for static analysis and architectural forensics. The only AI-slop leakage is `Inspector.svelte:537-545` (`bg-gradient-to-r from-indigo-900/60 to-purple-900/60` with `🪄`) and raw `$...$` LaTeX fragments pasted into DOM strings.
- **Deterministic Scan (`impeccable detect --json frontend/src`)**: `0` violations found (`[]`, exit code `0`).

### Cognitive Load Assessment (`4/8` Checklist Failures)
1. **Exceeds 4 distinct component groups without scrolling**: At `1280×800`, 5 distinct chrome groups compete around the canvas (Top Header, Bottom-Left 10-control HUD, Bottom `AgentDrawer` + `SandboxToolbar`, Right `Inspector`, and Floating Toast/Tooltip).
2. **Duplicate state controls**: `Bundled (B)`, `X-Ray (V)`, and `1-Hop (F)` exist simultaneously in `Canvas.svelte:670-707` and `Inspector.svelte:170-240`.
3. **Dense walls of text without hierarchy**: Raw `$C_a$`, `$C_e$`, `$I \to 0$` syntax in `Inspector.svelte:347-359` and `MainSequenceModal.svelte:691-780` forces mental parsing of markup characters.
4. **Overstuffed `36px` drawer bar**: `AgentDrawer.svelte:40-156` packs 8 statuses, pills, and destructive action buttons into a single `36px` bar that is itself a giant toggle region.

### Priority Issues (P1–P3)
- **P1 — Raw Unparsed LaTeX Strings (`$C_a$`, `$I \to 0$`) Leak into Live UI**: `Inspector.svelte:347-359`, `SandboxToolbar.svelte:215, 233-237`, `MainSequenceModal.svelte:166, 189, 687-783`.
- **P1 — Z-Index Collision & Occlusion at Bottom Viewport**: `AgentDrawer.svelte:36` (`z-30`, `220px`) covers `Canvas.svelte:618` (`bottom-12 left-6 z-20`) and collides with `SandboxToolbar.svelte:47` (`fixed bottom-6 z-40`).
- **P1 — Keyboard Shortcut Collision on `T` & Modal `Escape` Leak**: `Inspector.svelte:257` claims `T` for `"Hide Tier Backdrops"`, while `App.svelte:98-100` binds `T` to `AgentDrawer`; `App.svelte:126-140` clears `focusedNodeId` on `Escape` even when closing a child modal.
- **P1 — Hardcoded `#10b981` Mutation Dot Hides Survived Mutants**: `ComponentBox.svelte:360` hardcodes `fill="#10b981"` whenever `cls.mutants > 0`, masking `cls.survived > 0`.
- **P2 — Unconfirmed Destructive `"Revert Snapshot"` Action Inside a Clickable Header Bar**: `AgentDrawer.svelte:119-130` immediately overwrites workspace files on a single click.
- **P2 — `"WebGL 60fps"` Mislabeling & Loss of Interactivity in 2D `<canvas>` Mode**: `Canvas.svelte:487` uses `getContext('2d')`, labels it `"WebGL 60fps"` (`L718`), and drops node dragging, class-card inspection, edge clicks, and CRAP dots (`L563`).
- **P3 — Duplicate Canvas View Toggles & Decorative Gradient Button**: `Bundled (B)`, `X-Ray (V)`, and `1-Hop (F)` appear in both `Canvas.svelte:670-707` and `Inspector.svelte:170-240`; `Inspector.svelte:537-545` uses an indigo-to-purple gradient and wand emoji (`🪄`).

### Persona Red Flags
- **Alex (Colorblind Power User, Deuteranopia)**: Cannot distinguish `#ef4444` vs `#10b981` coverage dots at `3.5px` (`ComponentBox.svelte:357-360`), loses keyboard focus due to `focus:outline-none` (`Canvas.svelte:770-783`, `ComponentBox.svelte:68`), and hits the `T` shortcut collision.
- **Jordan (Distracted Mobile User)**: Cannot pan/zoom on touch (`Canvas.svelte:600` lacks touch/pointer events and `touch-none`), loses `320px` to the non-collapsible `Inspector.svelte:53`, and struggles with `14×14px`–`20×20px` targets.
- **Sam (Screen Reader User)**: Blocked by `div[onclick]` with suppressed A11y rules (`AgentDrawer.svelte:40-45`), non-focusable `<g>` dependency edges (`DependencyEdge.svelte:162-168`), and modals without focus traps (`InvertDipModal`, `MainSequenceModal`, `PolicyEditorModal`).
- **Staff Architect (Core Persona)**: Immediately notices raw `$C_a$` LaTeX markup, `"WebGL 60fps"` calling `getContext('2d')`, and the `AgentDrawer` burying the zoom/declutter HUD.
