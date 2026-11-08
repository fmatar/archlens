---
target: frontend/src/App.svelte
total_score: 36
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 0
target_identity: "file:/Users/fady/workspace/labs/archlens/frontend/src/App.svelte"
target_fingerprint: "sha256:24492fa82416296a8bbddcd2f547d89513ece6d9f6bac2135aaf223ffce39681"
target_path: /Users/fady/workspace/labs/archlens/frontend/src/App.svelte
timestamp: 2026-11-08T06-15-42Z
slug: frontend-src-app-svelte
closed: true
---
## Design Critique: Archlens Workbench (`frontend/src/App.svelte` + `frontend/src/lib/components/*`)

Method: dual-agent (A: 1c36f8ee-c804-473f-8382-dd59f1cf0041 · B: fbd6c8c7-09f3-4d4e-b474-e17956385dd4)

### Design Health Score

| # | Heuristic | Score | Key Issue |
|---|---|---|---|
| 1 | Visibility of System Status | 4/4 | n/a — Live SSE sync pulse, radar scanline/scrim, frustum-culled node counter, dynamic mutation health dots (`ComponentBox.svelte:64-74`), and violation delta badges are all clear and responsive. |
| 2 | Match System / Real World | 4/4 | n/a — Semantic HTML subscripts (`Cₐ`, `Cₑ`, `N꜀`) and honest `Fast 2D Canvas` labeling (`Canvas.svelte:731`) speak the architect's domain language cleanly. |
| 3 | User Control and Freedom | 3/4 | Opening `SourceModal` from `ClassCard` (`ClassCard.svelte:190, 214`) mounts both modals with window-level `Escape` listeners (`ClassCard.svelte:73-80`, `SourceModal.svelte:86-90`), so pressing `Esc` closes both instead of returning to `ClassCard`. |
| 4 | Consistency and Standards | 3/4 | `Declutter Mode (D)` (`Inspector.svelte:155-167`, `diagram.svelte.ts:1292-1297`) cycles 5 legacy states where `NONE`, `ARROWS`, and `ELEMENTS` have identical visual output and `CLASSES` duplicates `Compact Macro Cards (C)`; stale shortcut tooltips in `ProposalDiffBanner.svelte:48` (`Key: P`) and `GitVersionComparator.svelte:63` (`Shift+V` vs `G`). |
| 5 | Error Prevention | 4/4 | n/a — 2-step inline confirmation on `Revert Snapshot` (`AgentDrawer.svelte:130-155`) and non-destructive mailbox callout (`Inspector.svelte:648-659`) prevent accidental data loss. |
| 6 | Recognition Rather Than Recall | 4/4 | n/a — Inline `<kbd>` shortcut badges (`App.svelte`, `Canvas.svelte`, `Inspector.svelte`), `<title>` tooltips on coverage/mutation dots, and the inline Martin Metrics formula cheat sheet eliminate memory burden. |
| 7 | Flexibility and Efficiency | 4/4 | n/a — Comprehensive single-key accelerators, fuzzy `CommandPalette` (`⌘K`), 1-hop neighborhood isolation (`F`), violation X-Ray (`V`), edge bundling (`B`), and `Fast 2D Canvas` (`W`). |
| 8 | Aesthetic and Minimalist Design | 3/4 | Both `SandboxToolbar.svelte:47` and `Canvas.svelte:631` sit at `bottom-12` (or `bottom-60` when `AgentDrawer` is open), so activating Sandbox Mode (`S`) overlaps the bottom-left Canvas HUD on viewports ≤ 1440px. |
| 9 | Error Recovery | 4/4 | n/a — Actionable diagnostic empty state (`Canvas.svelte:737-776`), Docker mount guidance (`OpenProjectModal.svelte:382-409`), and 3-step `InvertDipModal.svelte` recovery flow. |
| 10 | Help and Documentation | 3/4 | Strong inline Main Sequence and Martin Metrics guides, but global shortcuts (`G`, `0`, `+`, `-`, `/`) lack a unified `?` shortcut sheet or `CommandPalette` help view. |
| **Total** | | **36/40** | **Excellent** |

### Design Specificity Verdict
- **LLM Assessment: Deeply Authored & Domain-Authentic.** Archlens commits 100% of its viewport to a dark-slate (`#0f172a`) Sugiyama-layered concentric ring topology (`Ring 0` through `Ring 3`) with cubic Bezier dependency flows, a 2D Cartesian Main Sequence scatter plot (`A + I = 1`), a 3-step Dependency Inversion synthesizer (`InvertDipModal.svelte`), and a closed-loop `.archlens/` IPC Mailbox Telemetry Drawer. All prior P1 defects (raw `$C_a$` strings, `AgentDrawer` HUD occlusion, `T` vs `R` shortcut conflict, hardcoded `#10b981` mutation dot, missing `:focus-visible` outlines, and unconfirmed `Revert Snapshot`) are resolved.
- **Deterministic Scan (`impeccable detect --json frontend/src`)**: `0` violations found (`[]`, exit code `0`); `svelte-check` passed with `0 errors and 0 warnings`.
- **Visual Overlays**: No reliable user-visible overlay is available (`fallback signal: sandboxed headless environment without mutable [Human] browser tab`).

### Overall Impression
The workbench now operates like a precision static-analysis instrument (**36/40**, up from **28/40**, with **0 P0** and **0 P1** issues remaining). The single biggest remaining opportunity is separating the vertical offset of `SandboxToolbar` (`bottom-24` / `bottom-72`) from the bottom-left Canvas HUD (`bottom-12` / `bottom-60`) and isolating stacked modal `Escape` dismissal between `SourceModal` and `ClassCard`.

### What's Working
1. **Closed-Loop Surgical DIP Remediation** (`EdgeTooltip.svelte` → `InvertDipModal.svelte` → `AgentDrawer.svelte`): Turns every red violation arrow into a 3-step interface extraction preview, sandbox simulation, test-verified refactoring check, ADR minting action, and 2-step confirmed snapshot rollback.
2. **Multi-Scale Semantic Zoom & Spatial Virtualization** (`Canvas.svelte:79-125`, `ComponentBox.svelte:238-500`): Maintains 120 FPS frustum culling while adapting cards between macro health summaries (`< 55%` zoom) and keyboard-accessible class rows with dynamic coverage + mutation health dots and tooltips.
3. **Interactive Main Sequence Quadrant** (`MainSequenceModal.svelte:309-764`): Visualizes Robert C. Martin's $A + I = 1$ corridor with shaded *Zone of Pain* and *Zone of Uselessness* polygons, clean `Cₐ`/`Cₑ` subscript typography, and 1-click canvas focus.

### Priority Issues (P2–P3)
1. **[P2] `SandboxToolbar` (`bottom-12` / `bottom-60`) Vertically Overlaps the Bottom-Left Canvas HUD** (`SandboxToolbar.svelte:47`, `Canvas.svelte:631`) — Suggested command: `/impeccable layout`
2. **[P2] Stacked Modal `Escape` Collision (`SourceModal` + `ClassCard`) & Missing `focusTrap` on `CommandPalette`** (`ClassCard.svelte:73-80`, `SourceModal.svelte:86-90`, `CommandPalette.svelte:221-228`) — Suggested command: `/impeccable harden`
3. **[P2] Redundant Legacy States in `Declutter Mode (D)` vs. Multi-Select Filter Matrix** (`Inspector.svelte:155-262`, `diagram.svelte.ts:1292-1297`) — Suggested command: `/impeccable distill`
4. **[P3] Stale Shortcut Tooltips in `ProposalDiffBanner.svelte:48` (`Key: P`) & `GitVersionComparator.svelte:63` (`Shift+V` vs `G`), Plus `text-slate-500`/`text-slate-600` Muted Contrast in `SourceModal.svelte:204`** — Suggested command: `/impeccable clarify`
5. **[P3] Class Row Click Hit-Testing in `Fast 2D Canvas` Mode (`W`)** (`Canvas.svelte:464-483, 563-582`) — Suggested command: `/impeccable polish`

### Persona Red Flags
- **Alex (Power User)**: Pressing `Esc` inside `SourceModal` closes both `SourceModal` and the underlying `ClassCard`; `GitVersionComparator.svelte:63` tooltip says `Shift+V` instead of `G`; pressing `D` cycles through 3 visually identical states (`NONE`, `ARROWS`, `ELEMENTS`).
- **Jordan (First-Timer)**: Opening Sandbox Mode (`S`) on a 13"–14" laptop covers the bottom-left Canvas zoom and X-Ray controls because both sit at `bottom-12`.
- **Sam (Keyboard / Screen Reader User)**: `CommandPalette.svelte:221` lacks `use:focusTrap`, allowing `Tab` to leave the search dialog; line numbers in `SourceModal.svelte:204` (`text-slate-600` on `bg-slate-950`) have low contrast (~2.8:1).
- **Staff Architect**: Wants access to the bottom-left `X-Ray (V)` and `1-Hop (F)` HUD controls while `SandboxToolbar` is active without the two bars overlapping.
