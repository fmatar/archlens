# Changelog

All notable changes to Archlens will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.0.1-Alpha-01] - 2026-09-21

### Added
- **Dynamic Repository Discovery & Project Switcher Modal (`⌘O`)**:
  - Live filesystem discovery of `.uml-viewer/policy.json` files across local workspaces.
  - Quick-switch modal dialog accessible via `⌘O` / `Ctrl+O` or header button.
  - Bi-directional URL synchronization (`?projectRoot=...`) preserving active project state across reloads.
- **Hierarchical Edge Bundling (`B`)**:
  - Catmull-Rom cubic spline routing passing through concentric radial hulls to declutter complex dependency meshes.
  - Toggled via keyboard shortcut `B` or the canvas overlay controls.
- **Architectural Violation X-Ray (`V`)**:
  - High-contrast visual isolation mode dimming compliant dependencies while accentuating illegal outward dependency links in crimson red.
- **Stepwise Inside-Node Micro-Pagination**:
  - Inline pagination controls (`1–5 of N`) directly within canvas component cards for dense components.
  - Clicking a class immediately selects and focuses it within the Inspector drawer.
- **Viewport Frustum Culling & Semantic Macro LOD (`C`)**:
  - Dynamic viewport frustum bounding that halts SVG rendering for off-screen nodes, displaying live visible count telemetry (`X/Y nodes`).
  - Automatic transition to Semantic Macro Level Pills on deep zoom out to maintain 60 FPS performance on multi-hundred class graphs.
- **O(1) Spatial Edge Pre-Indexing & Physics Drag**:
  - Pre-indexed class-to-node coordinate mapping enabling fluid, sub-millisecond arrow recalculations during interactive node dragging with realistic drop shadows and spring reset.
- **Clojure AST Scanner (`ClojureAstScanner`)**:
  - Native scanner parsing Clojure namespaces (`ns`), `(:require ...)`, `def`, `defn`, and protocols.
  - Full architectural policy compliance testing using Uncle Bob's `uml-viewer` codebase as ground truth.
- **Tactile Loading & State Feedback**:
  - **Canvas Radar Scrim**: Frosted glass overlay with animated pulse beacon while AST compilation and layout calculate.
  - **Optimistic Source Code Modal**: Instant modal opening with a multi-line skeleton shimmer during file I/O.
  - **Autonomous Agent Sonar Wave**: Animated cyan sonar sweep across the canvas while AI agents refactor code.
  - **Inspector Class Filter**: Search input with live match counter badge (`X/Y found`) and instant clear (`✕`) action.
- **Comprehensive Playwright E2E Suite**:
  - 9 automated end-to-end integration tests (`frontend/e2e/workbench.spec.ts`) validating canvas rendering, node dragging, keyboard shortcuts, diff proposals, and project switching.
- **Full HD 1080p Teaser Assets**:
  - High-definition 30 FPS video (`media/archlens-teaser.mp4`) encoded with AAC audio and MP4 FastStart flags for zero-buffering playback across LinkedIn, X/Twitter, and web previews.
  - Companion formats in WebM (`media/archlens-teaser.webm`) and animated GIF (`media/archlens-teaser.gif`).

### Changed
- Upgraded backend runtime to Java 25 and Quarkus 3.39.4 with calibrated JaCoCo quality gate thresholds.
- Upgraded frontend reactive architecture to Svelte 5 Runes (`$state`, `$derived`, `$effect`) and Tailwind CSS v4.
- Streamlined architecture violation classification strictly conforming to Robert C. Martin's Clean Architecture Dependency Rule.

### Security
- Enforced strict URI path validation and traversal guards on `/api/source` and `/api/diagram/projects`.
