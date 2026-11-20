---
id: ADR-017
title: Phase 2 — Skipped-File Telemetry, Clojure & Legacy Prototype Removal, and Provenance Hygiene
status: accepted
scope: technical
ticket: #238
supersedes: ADR-016
createdAt: 2026-11-19T23:33:03.723Z
---

# ADR-017: Phase 2 — Skipped-File Telemetry, Clojure & Legacy Prototype Removal, and Provenance Hygiene

- **Status**: `accepted`
- **Blast Radius / Scope**: `technical`
- **Triggered By Ticket**: `#237`, `#238`, `#239`, `#240`, `#241`
- **Supersedes / Amends**: `ADR-016`

## 1. Context & Spec Drift Trigger

Phase 2 of the repository evaluation (`#237`–`#241`, tracked under `#247`) plus explicit maintainer direction surfaced five trust, hygiene, and observability gaps:

1. **Silent File Drops During AST Scanning (`#240`)**: `FileScannerUtil` and individual `LanguageScanner` implementations caught `IOException` / parse errors silently (`catch (IOException ignored)`), hiding unreadable or malformed files from `ArchitectureGraph`, CLI output, and the UI.
2. **Shell-String Execution Branches in `cli/src/docker-runner.js` (`#241`)**: Helper functions retained `execSync` shell-string fallbacks alongside `spawnSync` argument arrays solely for older unit test mocks.
3. **Hardcoded Personal & Legacy Paths (`#238`)**: `WorkspacePathResolver` and `ResolveFilesystemUseCase` contained legacy host path rewrites (`agentlens`, `unclebob-design`, `~/workspace/labs`) and test fixtures referencing `/Users/fady`.
4. **Provenance Clarification & Removal of Clojure / Legacy `.uml-viewer` Artifacts (`#239`)**: Early prototype history (pre-`e05c9bf`) left `.metrics/`, `.grok/rules/uml-viewer.md`, `.uml-viewer/` fallback paths, and `ClojureAstScanner` in the tree even though Archlens is an all-new Java 25 / Quarkus + Svelte 5 + Node CLI implementation.
5. **Documentation, Build Command & Node Engine Drift (`#237`)**: Docs referenced `./mvnw` (not present in repo), inconsistent Node versions (`>=18`, `20+`, `22+`), outdated JaCoCo thresholds, and local `file:///Users/fady/...` links.

## 2. Decision

1. **Explicit `skippedFiles` Telemetry (`#240`)**:
   - Extend `ArchitectureGraph` (preserving 6/7/8-arg backward-compatible constructors) and `LanguageScanner.ScanResult` with `int skippedFiles`.
   - Replace every silent `IOException` / parse catch in `FileScannerUtil`, all 5 language scanners, `MailboxResource`, `ProjectFileWatcher`, `FileMailboxService`, and `VerifyAndRollbackRefactorUseCase` with structured warnings (`LOG.warnf` / `LOG.debugf`) including the file path, and surface `skippedFiles` in REST/MCP responses, CLI `archlens check` output, and the Svelte 5 workbench UI.
2. **Argument-Array-Only Process Execution in `docker-runner.js` (`#241`)**:
   - Remove `execSync` completely from `cli/src/docker-runner.js`; standardize `isJavaAvailable`, `isDockerAvailable`, `isContainerRunning`, `isContainerExisting`, and `startContainer` on `spawnSync(binary, argsArray, opts)`.
3. **Neutral Path Resolution & 5-Language `.archlens`-Only Runtime (`#238`, `#239`)**:
   - Remove `ClojureAstScanner`, `.metrics/`, `.grok/`, `cli/src/upgrader.js`, and all `.uml-viewer`, `agentlens`, `unclebob-design`, and `/Users/fady` rewrites across backend, CLI, skills, and configs. Supported scanner matrix is standardized on **5 languages**: Java 25, Python, TypeScript/JS/Svelte 5, Rust, and Go.
   - Update `NOTICE` to state `Copyright 2026 Fady Matar` plus a factual note that pre-`e05c9bf` prototype commits in early Git history were completely superseded and zero code or assets from them exist in Archlens or its distributed packages; keep only a brief Clean Architecture inspiration note in `README.md`.
4. **Documentation & Node `>=22.0.0` Alignment (`#237`)**:
   - Standardize `engines.node` to `>=22.0.0` across `package.json`, `cli/package.json`, and `frontend/package.json`, replace `./mvnw` with `mvn` across docs, align `JACOCO_COVERAGE_REPORT.md` to actual `backend/pom.xml` bundle (`75%/55%`) + diff (`85%`) gates with repo-relative links, and enforce a regression test guarding against `/Users/fady`, `agentlens`, and `unclebob-design` strings.

## 3. Consequences & Downstream Impact

- **Observability**: Unreadable or unparseable source files are never silently dropped; both CLI gates and the visual workbench report exact `skippedFiles` counts.
- **Security & Portability**: Eliminates shell-string interpolation in `docker-runner.js` and machine-specific path assumptions in `WorkspacePathResolver`.
- **Clean Provenance & Reduced Surface Area**: Removes dead Clojure scanning and `.uml-viewer` legacy migration paths, simplifying maintenance ahead of Phase 3 (`#242`, `#245`, `#246`) and Phase 4 (`#243`, `#244`).
