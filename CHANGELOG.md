# Changelog

All notable changes to Archlens will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Fixed
- **JavaParser Reflection Elimination in Native Image (Closes #78)**:
  - Configured JavaParser with `LanguageLevel.RAW` in `JavaAstScanner.java`.
  - Eliminates reflective calls to `PropertyMetaModel.getValue()` from semantic AST validators, resolving `NoSuchFieldError: variables` during native graph compilation.
- **Quarkus Native Profile Configuration (Closes #76)**:
  - Corrected native packaging property from `quarkus.package.jar.type` to `quarkus.native.enabled=true` across root `pom.xml` and `backend/pom.xml`.
  - Resolves `IllegalArgumentException: Cannot convert native to enum class JarType` when executing `mvn package -Pnative`.

### Added
- **Recursive Multi-Project Auto-Discovery in Mounted Container Workspaces (Closes #74)**:
  - Enhanced `listProjects()` in `DiagramResource.java` to recursively scan mounted container workspaces (`/workspace` or configurable via `archlens.container.workspace`).
  - Added recursive project discovery identifying nested repositories and monorepos (such as `labs/archlens`, `labs/unclebob-design`, `fluo`, `datarobot`) and their immediate submodules (`backend`, `frontend`).
  - Implemented directory cycle prevention, build folder exclusion (`target`, `node_modules`, `build`, `dist`), and canonical path deduplication.
  - Automatically populates the top-bar repository switcher, the `⌘K` Command Palette, and modal quick-access chips upon mounting container volumes.
  - Added unit test suite `testContainerWorkspaceMultiProjectDiscovery` and `testSingleRepoMountedContainerWorkspace` in `DiagramResourceTest.java`.
- **Adaptive Container Browsing & Path Auto-Population in Docker Mode (Closes #72)**:
  - Exposed `isContainer` and `nativePickerSupported` capability flags in `GET /api/fs/directories` response in `DiagramResource.java`.
  - Added `OpenProjectModal.svelte` auto-population: automatically initializes `inputPath` with active container directory (`/workspace`) on load, enabling immediate one-click opening.
  - Adapted browse action button to **"Browse Folders"** in container runtimes, automatically opening, focusing, and highlighting the in-app directory explorer.
  - Added dedicated **Docker Container Filesystem** guidance card explaining mounted volumes and host volume mounting instructions.
  - Added directory row hover action allowing any folder in the explorer to be opened with one click.
  - Added Vitest component tests in `OpenProjectModal.test.ts` validating container adaptation and path auto-initialization.
- **Docker Workspace Container Auto-Detection & Navigation (Closes #70)**:
  - Enabled automatic project root resolution to `/workspace` in `DiagramResource.java` when running inside containerized runtimes and no local project descriptor exists at `.`.
  - Added dedicated **Mounted Workspace** (`/workspace`) shortcut in directory explorer quick navigation bar.
  - Added container environment detection and graceful fallback guidance in `OpenProjectModal.svelte`: when native OS dialogs are isolated in headless/container runtimes, an informative banner informs the user and automatically focuses the in-app folder explorer.

## [0.0.1-Alpha-04] - 2026-09-22

### Added
- **Consolidated Full-Stack Telemetry Report & GitHub Actions Step Summary**:
  - Implemented standalone reporting engine `scripts/generate-report.js` aggregating JaCoCo, Vitest, Surefire, PMD, CPD, SpotBugs, and CycloneDX telemetry into an offline HTML dashboard (`reports/index.html`).
  - Added GitHub Actions CI step summary integration appending formatted Markdown metrics directly to `$GITHUB_STEP_SUMMARY`.
  - Configured `frontend/pom.xml` to execute `test:coverage` during standard Maven builds.
  - Added unit test suite `scripts/test/generate-report.test.js` validating telemetry parsers and schemas.
- **Build Provenance REST Endpoint & Version Synchronization Script**:
  - Implemented `/api/version` in `VersionResource.java` exposing semantic version, git commit SHA, branch, and build timestamp (Closes #63).
  - Integrated `git-commit-id-maven-plugin` to generate `git.properties` dynamically during build.
  - Added cross-platform version synchronization script `scripts/set-version.sh` updating all 6 project descriptors atomically (Closes #63).
- **CycloneDX Software Bill of Materials (SBOM)**:
  - Integrated `cyclonedx-maven-plugin` to generate standards-compliant SBOM JSON and XML (`target/bom.json`) during packaging (Closes #62).
  - Attached SBOM assets to GitHub release distribution artifacts and CI workflows (Closes #62).
- **Quarkus GraalVM Native Image Profile (`-Pnative`)**:
  - Configured native profile in parent and backend POMs for ahead-of-time compilation of standalone native executable binaries (Closes #61).
- **GitHub Actions Dependency & Toolchain Caching**:
  - Configured caching for `~/.local/share/pnpm/store` and `frontend-maven-plugin` Node/PNPM binaries in `.github/workflows/ci.yml` (Closes #60).
- **Frontend Vitest Integration in Maven Reactor**:
  - Bound `pnpm test` execution to Maven's `test` phase in `frontend/pom.xml`, ensuring `mvn test` exercises frontend unit tests alongside backend suites (Closes #59).
  - Calibrated Vitest coverage thresholds and added `diagram_extra.test.ts` for extended diagram store coverage (Closes #59).
- **Strict Zero-Regression Quality Gates**:
  - Enforced `failOnViolation=true` for PMD/CPD and `failOnError=true` for SpotBugs/FindSecBugs in `backend/pom.xml` (Closes #58).

### Changed
- **AST Scanner Consolidation & Refactoring**:
  - Extracted shared source directory resolution (`LanguageScanner.resolveScanDirectory`) across Go, Python, Rust, and TypeScript AST scanners.
  - Consolidated baseline module and class node generation (`LanguageScanner.addModuleOrTypeClasses`) across Rust and TypeScript AST scanners, eliminating duplicated AST traversal and model construction logic.
- **Frontend Vite Proxy Endpoint Configuration**:
  - Replaced `http://localhost:8088` with explicit IPv4 `http://127.0.0.1:8088` in `frontend/vite.config.ts` to prevent Node.js 18+ Happy Eyeballs IPv6 loopback connection delays and proxy `ECONNREFUSED` errors.

### Fixed
- **Dynamic Dependency Edge Badge Tracking & GSAP Pulse Isolation**:
  - Resolved SVG translation coordinate pinning where continuous GSAP matrix scale animations on `badgeEl` clobbered reactive Bezier midpoint translations during card dragging (PR #67).
  - Replaced the GSAP scale tween with GPU-accelerated CSS keyframe animations configured with `transform-box: fill-box` and `transform-origin: center` on an inner `<g class="edge-badge-pulsing">`, ensuring uninterrupted coordinate synchronization (PR #67).
- **Cross-Workspace Polyglot Source Code Resolution**:
  - Added `projectRoot` query parameter handling to `/api/source` in `DiagramResource.java` to support external workspace paths (PR #67).
  - Stored absolute file paths on `ClassNode` across `PythonAstScanner` and `TypeScriptAstScanner` (PR #67).
  - Derived clean relative `displayPath` in `SourceModal.svelte` for header presentation while preserving full path tooltips and clipboard copy (PR #67).
- **Python AST Package Grouping, Omission Filters & Orphan Edge Pruning**:
  - Resolved browser rendering freezes on large Python codebases by correcting `PythonAstScanner` package assignment to group classes by directory package namespace rather than individual file module, preventing component explosion (Closes #66, PR #67).
  - Added exclusion of default non-production directories (`tests`, `test`, `venv`, `.venv`, `__pycache__`, `dist`, `build`, `target`, `node_modules`, `site-packages`) and test files (`test_*.py`, `*_test.py`) in `PythonAstScanner` (Closes #66, PR #67).
  - Resolved relative Python imports (`.` and `..`) against active package context and filtered standard library modules and multi-line parenthesis tokens from dependency edges (Closes #66, PR #67).
  - Implemented bidirectional orphan edge pruning in `GraphCompiler`, eliminating unresolvable dependency paths targeting omitted classes or undeclared components (Closes #66, PR #67).
  - Added policy-level omit filtering across `LanguageScanner`, `PythonAstScanner`, and `TypeScriptAstScanner` to honor root and proposal `omit` patterns (Closes #66, PR #67).
  - Sorted component nodes by architectural rank and declared policy order for deterministic canvas layout (Closes #66, PR #67).
- **Static Analysis & Code Quality Violations (PMD, CPD, SpotBugs)**:
  - Resolved 11 PMD violations: eliminated useless parentheses, collapsed nested `if` statements, removed redundant package qualifiers, and cleaned up unused method parameters and local variables across backend scanners and resources (PR #55).
  - Resolved 3 CPD (Copy/Paste Detector) duplication blocks through scanner helper consolidation in `LanguageScanner` (PR #55).
  - Resolved 8 SpotBugs warnings: enforced explicit `Locale.ROOT` in case conversions, constrained exception handling to specific checked exceptions (`IOException | InterruptedException`), and modernized home directory resolution (PR #55).

## [0.0.1-Alpha-03] - 2026-09-21

### Added
- **Interactive Filesystem Directory Explorer & Native OS Folder Picker (`⌘O` / `Ctrl+O`)**:
  - Direct integration with native operating system directory selection (Finder on macOS) via `Browse...` dialog action.
  - Interactive in-modal filesystem explorer featuring clickable breadcrumb segments, instant directory filtering, quick jump shortcuts (`Home`, `Current Workspace`, `Labs`), and project classification badges (`Maven`, `Gradle`, `Node`, `Java`, `Git`) (Closes #41, PR #42).
- **Universal Policy Installer Agent Skill (`archlens-install-policy`)**:
  - Standards-compliant agent skill definition (`SKILL.md`) equipping Claude Code, Gemini CLI, and Google Antigravity with autonomous Clean Architecture policy generation capabilities (Closes #44, PR #45).
  - Standalone zero-dependency Python generator `init_policy.py` for automated policy synthesis.
- **NPX Distribution Module (`@fmatar/archlens-skill`)**:
  - Dedicated zero-dependency npm CLI package (`cli/`) providing instant Clean Architecture governance scaffolding and updates via `npx @fmatar/archlens-skill` (Closes #47, PR #49).
  - Polyglot codebase analyzer supporting Java, Kotlin, TypeScript, JavaScript, Python, Rust, Go, and Clojure.
  - Interactive terminal wizard with ANSI color diagnostics and ASCII branding.
  - Concentric Clean Architecture tier classification (Domain Core, Application, Adapters, Infrastructure).
  - Scaffolds `.uml-viewer/policy.json`, `.uml-viewer/workbench.config.json`, and injects mailbox companion protocols into `CLAUDE.md` and `AGENTS.md`.
  - Global agent skill installer deploying to `~/.claude/skills/` and `~/.gemini/config/skills/`.
  - Non-destructive updater synchronizing newly detected packages while preserving custom developer rules.
  - Integrated into root Maven reactor (`cli/pom.xml`) with 13 automated unit tests executed during `mvn clean verify`.
- **Strict Branch Flow Governance**:
  - Added CI policy check ensuring pull requests targeting `main` originate exclusively from `develop` (PR #48).
  - Dedicated GitHub Actions workflow (`.github/workflows/enforce-branch-flow.yml`) with required status check `Enforce Merge From Develop Only` active on `main` branch protection (PR #51, PR #52).

### Fixed
- **Filesystem Path Modal Reactivity Cycle**:
  - Isolated modal initialization in `OpenProjectModal.svelte` using Svelte 5's `untrack()` to eliminate reactive dependency cycles during typing and native folder selection.
  - Activated macOS frontmost application state for native Finder folder dialogs.

## [0.0.1-Alpha-02] - 2026-09-21

### Added
- **Recursive Multi-Module Source Auto-Discovery**:
  - Automatically searches and aggregates nested `**/src/main/java` module source trees in multi-module Maven and Gradle repositories (e.g. `vanguard-api`, `gossip-api`) without requiring manual submodule configuration (Closes #30).
- **Dynamic Package Prefix & Project Title Deduction**:
  - Infers project titles dynamically from the repository folder name or root POM artifact ID.
  - Automatically derives the longest common package namespace across scanned compilation units, stripping repetitive package prefixes to generate concise component identifiers (Closes #31).
- **Interactive Canvas Diagnostic Empty State**:
  - Renders a diagnostic card when zero components are detected in a workspace, providing actionable causes and one-click shortcuts to switch repositories or trigger agent policy synthesis (Closes #32).
- **Direct Submodule Discovery in Workspace Browser**:
  - Recursively indexes submodules in `/api/projects` to enable direct one-click navigation into subprojects.

### Fixed
- **Agent Regen Infinite Reactive Loop**:
  - Eliminated browser thread lockup by replacing the GSAP canvas scanline loop with a GPU-accelerated CSS keyframe animation (`@keyframes radar-scan`), decoupling radar telemetry from Svelte 5 reactive ticks (PR #29).

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
