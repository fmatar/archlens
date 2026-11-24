---
id: ADR-018
title: Phase 3 — Repository Hygiene, Clean Architecture Ports & Constructor DI, and Playwright E2E CI
status: accepted
scope: technical
ticket: #246
supersedes: ADR-017
createdAt: 2026-11-20T00:54:00.000Z
---

# ADR-018: Phase 3 — Repository Hygiene, Clean Architecture Ports & Constructor DI, and Playwright E2E CI

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: `#242`, `#245`, `#246`
- **Supersedes / Amends**: `ADR-017`

## 1. Context & Spec Drift Trigger

Phase 3 of the repository evaluation (`#242`, `#245`, `#246`, tracked under `#247`) surfaced three structural quality, repository hygiene, and CI verification gaps:

1. **Repository Bloat, Duplicate Tooling & Leaked/Redundant Skills (`#242`)**:
   - `frontend/package.json` listed both `typescript` (`^6.0.3`) and `@typescript/native` (`npm:typescript@7.0.2`).
   - Internal dev skill directories (`.agents/skills/impeccable/`, `.github/skills/impeccable/`, `.impeccable/`)—including two `12.76 MB` `darwin-arm64/impeccable` Mach-O binaries—were tracked in Git, causing `npx skills add fmatar/archlens` to leak `impeccable` alongside legacy duplicate skills (`uml-workbench-companion`, `archlens-install-policy`), while oversized media assets (`media/archlens-teaser.webm` `14.41 MB`, `media/archlens-teaser.gif` `7.90 MB`) inflated non-LFS tracked blobs by ~48 MB.
2. **Backend Deprecated Facades, Duplicate CRAP Types, Mixed DI & Use-Case I/O (`#246`)**:
   - `delivery/rest/WorkspacePathResolver.java` remained as a `@Deprecated` forwarding facade duplicating `usecase/WorkspacePathResolver.java`.
   - `metrics/CrapCalculator.java` and `metrics/CrapScoreCalculator.java` overlapped while `JavaAstScanner` injected the concrete `CrapScoreCalculator` directly.
   - REST resources (`delivery/rest/*.java`) and `ArchlensMcpService` mixed `@Inject` fields with manual `new ...UseCase()` fallbacks in no-arg/test constructors, allowing CDI runtime wiring and test wiring to diverge.
   - `ResolveFilesystemUseCase` and `VerifyAndRollbackRefactorUseCase` executed raw filesystem browsing, native OS folder dialogs (`osascript`), and `ProcessBuilder` test-runner commands directly inside the `usecase` ring instead of delegating through Clean Architecture ports.
3. **Frontend Coverage Floor & Missing Playwright E2E in CI (`#245`)**:
   - Frontend Vitest coverage thresholds in `frontend/vite.config.ts` were set well below current coverage (`45%/30%`), and `frontend/e2e/workbench.spec.ts` used `channel: 'chrome'`, lacked session-token authentication (`#225`), referenced retired `v0.0.1-Alpha-*` tags, and never ran in `.github/workflows/ci.yml`.

## 2. Decision

1. **Repository Hygiene & `<= 5 MB` Non-LFS Blob Guard (`#242`)**:
   - Remove `@typescript/native` from `frontend/package.json` and update `frontend/pnpm-lock.yaml`; enforce one lockfile per package (`pnpm-lock.yaml` in `frontend/`) and add `package-lock.json` / `**/skills/*/scripts/bin/` rules to `.gitignore`.
   - Untrack `.agents/skills/impeccable/scripts/bin/darwin-arm64/impeccable`, `.github/skills/impeccable/scripts/bin/darwin-arm64/impeccable`, `media/archlens-teaser.webm`, and `media/archlens-teaser.gif` (retaining lightweight `< 5 MB` `media/archlens-canvas.png`, `media/archlens-teaser.mp4`, and `media/archlens-user-journey.mp4`), and enforce a `<= 5 MB` tracked-blob assertion + single-TypeScript/lockfile check in `scripts/test/repo-hygiene.test.js`.
2. **Clean Architecture Ports, Canonical `CrapScoreCalculator`, and Constructor Injection Only (`#246`)**:
   - Delete `delivery/rest/WorkspacePathResolver.java` (and its duplicate test) and remove the redundant `metrics/CrapCalculator.java` interface, relocating `CrapScoreCalculator` to Ring 0 `io.slixes.archlens.domain.graph` alongside `CrapScore` and retiring the standalone `metrics` package.
   - Extract `FilesystemGateway` and `TestRunnerGateway` ports in `io.slixes.archlens.usecase` and move OS directory listing, native picker execution, confined source-file reads, snapshot/file I/O helpers, and `ProcessBuilder` test-suite execution into outer-ring adapters under `io.slixes.archlens.workspace.fs` (`LocalFilesystemGateway`) and `io.slixes.archlens.verification.runner` (`ProcessTestRunnerGateway`), registered at Ring 2 alongside `mailbox.fs` in `.archlens/policy.json`.
   - Refactor all REST resources/services (`DiagramResource`, `FitnessResource`, `MailboxResource`, `ProjectFilesystemResource`, `SnapshotResource`, `VersionResource`, `JaxRsSessionTokenFilter`, `SessionTokenFilter`, `SessionTokenService`) and `ArchlensMcpService` to use `@Inject` constructor injection exclusively (with CDI-required package-private no-arg proxy constructors that perform zero manual instantiation).
3. **Ratcheted Frontend Coverage Floor & Bundled-Chromium Playwright E2E in CI (`#245`)**:
   - Add unit tests (`ClassCard.test.ts`, `SourceModal.test.ts`, `ProposalDiffBanner.test.ts`, `GitVersionComparator.test.ts`, `EdgeTooltip.test.ts`, and `focusTrap.test.ts`), raising `frontend/vite.config.ts` coverage thresholds to `lines: 60, statements: 58, functions: 60, branches: 45`.
   - Configure `frontend/playwright.config.ts` to use Playwright's bundled Chromium (removing `channel: 'chrome'`), authenticate with `ARCHLENS_TOKEN`, boot the packaged Quarkus server via `webServer`, update `frontend/e2e/workbench.spec.ts` to match current `v1.x` UI contracts, and run both `pnpm --dir frontend run test:coverage` and `pnpm --dir frontend run test:e2e` in `.github/workflows/ci.yml`.

## 3. Consequences & Downstream Impact

- **Repository Weight**: Reduces tracked Git tree size by ~48 MB and guarantees zero tracked non-LFS files exceed `5 MB`.
- **Architectural Integrity**: `ResolveFilesystemUseCase` and `VerifyAndRollbackRefactorUseCase` depend strictly on inward ports (`FilesystemGateway`, `TestRunnerGateway`), REST/MCP delivery adapters have deterministic `@Inject` constructor wiring, and Archlens passes its own zero-violation self-gate.
- **Continuous UI Verification**: Every PR and push runs Vitest coverage enforcement and full Playwright E2E browser tests against the packaged Quarkus workbench.
