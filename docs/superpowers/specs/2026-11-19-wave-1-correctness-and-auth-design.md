# Wave 1 (`v1.1.7`) Design — Correctness, Tag Comparison & MCP Path Confinement

- **Date**: 2026-11-19
- **Target Release**: `v1.1.7` (followed by publishing `GHSA-pc6m-876p-9hp9`)
- **Status**: Draft — awaiting approval

## 1. Intent & Why
Close the five functional and path-confinement bugs uncovered in the whole-repo audit **before** promoting `develop -> main` as `v1.1.7` and publishing critical security advisory `GHSA-pc6m-876p-9hp9`, so the advisory goes out against a build with consistent REST + MCP workspace confinement, working MCP resources, real historical Git tag comparison, and live file watching across project switches.

## 2. Goals, Non-Goals & Anti-Goals
- **Goals**:
  1. `archlens://fitness` MCP resource in CLI stdio returns real evaluated fitness JSON instead of `"{}"`.
  2. MCP `syncDipAdr` and `SyncAdrUseCase` enforce workspace confinement + symlink-free writes, and read-only `GET /api/mailbox/*` never creates `.archlens/` directories on disk.
  3. Release Comparator (`GET /api/snapshots/{id}` / `getSnapshot`) and Fitness History (`GET /api/fitness/history` / `getFitnessHistory`) compile real historical Git tags and cache them in `.archlens/cache/<tag>.json` with real tag dates.
  4. `ProjectFileWatcher` switches watched directories cleanly when `projectRoot` changes, and opening a project in the UI drops the duplicate `loadFitness()` full-repo AST scan.
  5. Cut `v1.1.7` (validating `#263` GraalVM native binary build in CI) and publish `GHSA-pc6m-876p-9hp9` once the owner confirms.
- **Non-Goals**: Phase 2 (`#237`–`#241`), Phase 3 (`#242`, `#245`, `#246`), and Wave 4 engine unification / Tree-sitter (`#244`, `#243`) remain in their subsequent waves.
- **Anti-Goals**: Mutating `.git/HEAD` or `.git/index` in the user's working tree when inspecting historical Git tags; breaking headless `mcp-stdio` usage on the active workspace (`.`).

## 3. Constraints
- **SDLC Invariants (`AGENTS.md`)**: Zero direct commits on `develop` or `main` (`INV-01`); active SDLC session branch; Robert C. Martin TDD with `>= 85%` diff line & branch coverage (`INV-02`); ADR minted via `sdlc-engine.mjs adr create` if `PRODUCT.md` or public behavior contracts change (`INV-03`); Stage 7 parallel fork-join review receipt before merge.
- **Cross-Platform / Container Safety**: No reliance on external `tar` binaries or shell string interpolation when materializing Git tags.

## 4. Approaches Considered & Chosen
1. **Approach 1 (Chosen) — Risk & Leverage Waves + Option A (`git` tag extraction & `.archlens/cache/<tag>.json`)**: Patch the 5 correctness/confinement bugs in `v1.1.7` first, then run Wave 2 (`#237`–`#241`), Wave 3 (`#242`, `#245`, `#246`), and Wave 4 (`#244` before `#243`). Chosen because it closes MCP path confinement before publishing `GHSA-pc6m-876p-9hp9`, proves `#263` in release CI, and makes Release Comparator & Fitness History work out of the box on existing Git tags.
2. **Approach 2 — Strict `#247` Checklist Order**: Tackle `#237`–`#246` first and leave the 5 newly discovered bugs for last. Rejected because it leaves broken `archlens://fitness`, fake tag diffs, and unguarded MCP `syncDipAdr` live across releases and duplicates parser work.
3. **Approach 3 — Release-First Minimalist (`v1.1.7` from `cb15f25`)**: Ship `cb15f25` immediately and defer all 5 bugs to `v1.2.0`. Rejected because publishing `GHSA-pc6m-876p-9hp9` draws immediate scrutiny to path/auth boundaries while MCP `syncDipAdr` lacks `OpenedProjectRegistry` confinement.
- **Git Tag Strategy — Option A (Chosen) vs. Option B**: Option A extracts historical tags into a temp dir and caches `.archlens/cache/<tag>.json`; Option B drops `git tag` discovery entirely. Option A was selected by the project owner.

## 5. Design & Engineering Calls

### User-Decided
- Execute **Wave 1** first toward `v1.1.7`, implementing **Option A** (historical Git tag compilation cached under `.archlens/cache/<tag>.json`).

### Builder / Engineering Calls (Proposed Defaults)
1. **CLI MCP `archlens://fitness` (`cli/src/mcp-server.js`)**:
   - Make `readMcpResource` `async`, `await checker(projectRoot, { detectCycles: true })`, and `await readMcpResource(...)` in `handleMcpMessage` (`resources/read`).
2. **MCP `syncDipAdr` Confinement, Symlink Guard & Read-Only Mailbox (`ArchlensMcpService.java`, `SyncAdrUseCase.java`, `FileMailboxService.java`)**:
   - Inject `OpenedProjectRegistry` into `ArchlensMcpService`. Allow `syncDipAdr` only when `projectRoot` resolves via `ProjectPathGuard.canonicalRoot` to either the active launch workspace (`.`) or a project registered in `OpenedProjectRegistry` (preserving headless `mcp-stdio` on `.` while blocking arbitrary unopened host directories).
   - In `SyncAdrUseCase.createAdr`, canonicalize `projectRoot` via `ProjectPathGuard.canonicalRoot` and reject symlinks along `docs/adr` and the target ADR file before `Files.createDirectories` / `Files.writeString`.
   - In `FileMailboxService`, split `resolveMailboxFile(projectRoot, fileName, boolean createDir)`: `readMailbox` passes `false` (never mutates disk on `GET`); `appendCommand` and `popOldest` pass `true`.
3. **Historical Git Tag Extraction & Caching (`ManageSnapshotsUseCase.java`, `GetFitnessHistoryUseCase.java`)**:
   - Use `git for-each-ref --sort=-creatordate --count=10 --format=%(refname:short)|%(creatordate:short) refs/tags` (argument array, bounded output, drains stdout before `waitFor(2s)`) to collect both tag names and real `YYYY-MM-DD` tag dates, and merge them (deduped by `id`) after `.archlens/snapshots/*.json` instead of hiding tags whenever one manual snapshot exists.
   - Validate tag IDs with `^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$` (blocking leading `-` option injection and path separators).
   - Materialize uncached Git tags into `Files.createTempDirectory("archlens-tag-")` using pure `git` with an isolated `GIT_INDEX_FILE=<tempIndex>` (`git read-tree refs/tags/<id>` + `git checkout-index -a -f --prefix=<tempWorktree>/`) so `.git/HEAD` and `.git/index` in the live repo are never touched and no `tar` binary is required. If the historical tag predates `.archlens/policy.json`, copy the current workspace's `.archlens/policy.json` into the temp worktree before compiling, persist the compiled `ArchitectureGraph` to `.archlens/cache/<id>.json` (symlink-checked), and delete the temp dir in `finally`.
   - In `GetFitnessHistoryUseCase`, call `manageSnapshotsUseCase.getSnapshot(id, root)` for listed tags/snapshots instead of substituting `currentGraph`.
4. **Live Watcher Restart & Single Fitness Load (`ProjectFileWatcher.java`, `DiagramResource.java`, `diagram.svelte.ts`)**:
   - Track `private Path watchedRoot` (canonical/normalized) in `ProjectFileWatcher`; if `start(newRoot, onChange)` is called while running on a different root, call `stop()` inside the `synchronized` block and start watching `newRoot`. Also call `ensureWatcherRunning(canonical.toString())` on `POST /api/projects/open` / `GET /api/events?projectRoot=...`.
   - Remove the redundant `await this.loadFitness()` on `diagram.svelte.ts:1130` since `await this.loadGraph()` on `L1128` already triggers `loadFitness()` (awaiting it cleanly once).

## 6. Left to the Implementation Plan
- Exact GitHub issue breakdown (1 tracking issue or 5 linked issues under `#247`), exact JUnit 5 / Node / Vitest test cases, and step-by-step TDD task order.
