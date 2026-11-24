# Wave 1 (`v1.1.7`) Correctness, Tag Comparison & MCP Confinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 5 untracked correctness, live-watching, historical tag-comparison, and MCP/mailbox path-confinement bugs on a gated SDLC feature branch, merge to `develop`, promote `v1.1.7` to `main`, and publish `GHSA-pc6m-876p-9hp9`.

**Architecture:** Keep every fix inside its existing Clean Architecture ring: Ring 3 CLI/MCP/REST adapters (`cli/src/mcp-server.js`, `ArchlensMcpService`, `DiagramResource`, `ProjectFilesystemResource`) enforce session/workspace boundaries and delegate to Ring 1 use cases/engines (`SyncAdrUseCase`, `ManageSnapshotsUseCase`, `GetFitnessHistoryUseCase`, `ProjectFileWatcher`) and Ring 3 filesystem adapters (`FileMailboxService`). Historical Git tags are materialized into ephemeral temp directories via an isolated `GIT_INDEX_FILE` (`git read-tree` + `git checkout-index`) without mutating `.git/HEAD` or `.git/index`, compiled once, and cached at `.archlens/cache/<tag>.json`.

**Tech Stack:** Java 25, Quarkus 3.31 (`quarkus-mcp-server`, REST), JUnit 5, JaCoCo / SpotBugs / PMD / Spotless, Node.js ESM (`node:test`), Svelte 5 (`vitest`, `svelte-check`), Git CLI.

**Spec:** `docs/superpowers/specs/2026-11-19-wave-1-correctness-and-auth-design.md`

## Global Constraints

- Zero direct commits, merges, or pushes on `develop` or `main` (`INV-01`); every commit requires an active SDLC session branch in `.agent/.sdlc-session.json`.
- Robert C. Martin TDD (`Red -> Green -> Refactor`) with `>= 85%` diff line & branch coverage (`INV-02`), `0` SpotBugs/PMD/CPD violations, and `0` Clean Architecture ring breaches.
- Mint and synchronize an accepted technical ADR via `node .agent/scripts/sdlc-engine.mjs adr create` (`INV-03`).
- Tag IDs must match `^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$` (rejecting leading `-` option flags and path separators), and Git tag extraction must never rely on `tar` or shell string interpolation, nor touch `.git/HEAD` or `.git/index`.
- Headless `mcp-stdio` invocations targeting the active launch workspace (`.`) must continue working without prior HTTP calls.

## Review Focus

1. **Tag ID starting with `-` (e.g., `--output=/tmp/pwn` or `-v`)**: `ManageSnapshotsUseCase.getSnapshot` rejects any ID failing `^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$` with `IllegalArgumentException` before spawning `git` (tested in Task 3).
2. **Symlinked `docs` or `docs/adr` inside an opened project pointing outside `projectRoot`**: `SyncAdrUseCase.recordDipInversion` rejects symlink components with `IOException` and writes nothing outside the canonical project root (tested in Task 2).
3. **Historical Git tag predating `.archlens/policy.json`**: `ManageSnapshotsUseCase.getSnapshot` copies `<projectRoot>/.archlens/policy.json` into the temporary tag checkout when absent so historical classes are mapped into concentric rings rather than `unassigned` (tested in Task 3).
4. **Non-existent tag requested in a `.git` repository (e.g., `getSnapshot("v99.99.99", gitRepo)`)**: `ManageSnapshotsUseCase.getSnapshot` does **not** write today's live graph into `.archlens/cache/v99.99.99.json` (no cache poisoning; tested in Task 3).
5. **Repeated `ProjectFileWatcher.start(sameRoot, callback)` calls from multiple SSE tabs**: `ProjectFileWatcher` keeps the active `WatchService` running without restarting when canonical `rootPath` is unchanged, and restarts only when `rootPath` changes (tested in Task 4).

---

### Task 1: Issue Tracking, SDLC Branch Bootstrap & CLI MCP `archlens://fitness` Fix

**Files:**
- Modify: `cli/src/mcp-server.js:116-162, 661-680`
- Test: `cli/test/mcp-server.test.js`
- Create (ADR via engine): `docs/adr/ADR-016-*.md`

**Interfaces:**
- Produces: `export async function readMcpResource(uri, options = {}, deps = {}) -> Promise<{ contents: Array<{ uri: string, mimeType: string, text: string }> }>` in `cli/src/mcp-server.js`.

- [ ] **Step 1: Open the Wave 1 GitHub issue, update epic `#247`, start the SDLC feature branch, and mint `ADR-016`**

Run:
```bash
gh issue create --repo fmatar/archlens \
  --title "Wave 1 (v1.1.7): Fix MCP fitness resource, tag comparator caching, watcher root switch, and MCP/mailbox path confinement" \
  --label "type:bug,priority:p1" \
  --body "Tracks the 5 correctness and path-confinement fixes from docs/superpowers/specs/2026-11-19-wave-1-correctness-and-auth-design.md ahead of v1.1.7 and GHSA-pc6m-876p-9hp9."
```
Then start the SDLC session on the returned `<ISSUE_NUM>`, verify `git checkout -b` switched onto the session branch, and mint the technical ADR (`node .agent/scripts/sdlc-engine.mjs adr create --title "Wave 1 MCP Confinement, Pure-Git Tag Caching, and Watcher Root Switching" --scope technical && node .agent/scripts/sdlc-engine.mjs decision-graph sync`).

- [ ] **Step 2: Write failing tests for `archlens://fitness` in `cli/test/mcp-server.test.js`**

```js
test('handleMcpMessage resources/read awaits async checkArchitecture for archlens://fitness', async () => {
  const resp = await handleMcpMessage(
    { jsonrpc: '2.0', id: 51, method: 'resources/read', params: { uri: 'archlens://fitness' } },
    { path: '/tmp/proj' },
    {
      checkArchitecture: async () => ({
        passed: true,
        fitness: { fitnessScore: 94.5, grade: 'A', passed: true }
      })
    }
  );
  assert.equal(resp.id, 51);
  const parsed = JSON.parse(resp.result.contents[0].text);
  assert.equal(parsed.fitnessScore, 94.5);
  assert.equal(parsed.grade, 'A');
});
```

- [ ] **Step 3: Run Node MCP test to verify RED failure**

Run: `node --test cli/test/mcp-server.test.js`
Expected: FAIL (`parsed.fitnessScore` is `undefined` because `JSON.stringify(Promise)` produced `"{}"`).

- [ ] **Step 4: Make `readMcpResource` `async` and `await` both `checker(...)` and `readMcpResource(...)` in `cli/src/mcp-server.js`**

Update `export async function readMcpResource(uri, options = {}, deps = {})` (`L116`), `const report = await checker(projectRoot, { detectCycles: true });` (`L149`), and `const readResult = await readMcpResource(uri, options, deps);` (`L664`).

- [ ] **Step 5: Run Node MCP tests to verify GREEN**

Run: `node --test cli/test/mcp-server.test.js`
Expected: PASS (`0` failures).

- [ ] **Step 6: Commit Task 1**

```bash
git add docs/superpowers/specs/2026-11-19-wave-1-correctness-and-auth-design.md \
        docs/superpowers/plans/2026-11-19-wave-1-correctness-and-auth.md \
        docs/adr/ docs/DECISION_MAP.md cli/src/mcp-server.js cli/test/mcp-server.test.js
git commit -m "fix(mcp): await async architecture check in archlens://fitness resource"
```

---

### Task 2: MCP `syncDipAdr` Confinement, Symlink-Safe ADR Writes & Read-Only Mailbox `GET`

**Files:**
- Modify: `backend/src/main/java/io/slixes/archlens/mcp/ArchlensMcpService.java:65-93, 296-319`
- Modify: `backend/src/main/java/io/slixes/archlens/usecase/SyncAdrUseCase.java:84-131`
- Modify: `backend/src/main/java/io/slixes/archlens/mailbox/fs/FileMailboxService.java:29-112`
- Test: `backend/src/test/java/io/slixes/archlens/mcp/ArchlensMcpServiceTest.java`
- Test: `backend/src/test/java/io/slixes/archlens/usecase/SyncAdrUseCaseTest.java`
- Test: `backend/src/test/java/io/slixes/archlens/mailbox/fs/FileMailboxServiceTest.java`

**Interfaces:**
- Produces:
  - `ArchlensMcpService.getOpenedProjects() -> OpenedProjectRegistry`
  - `ArchlensMcpService.syncDipAdr(...)` throws `IllegalArgumentException` when `projectRoot` is neither the canonical launch workspace (`.`) nor registered in `OpenedProjectRegistry`.
  - `SyncAdrUseCase.recordDipInversion(...)` / `recordPackageReorganization(...)` throws `IOException` if `projectRoot` is invalid or any segment along `docs/adr/<filename>` is a symlink.
  - `FileMailboxService.readMailbox(String projectRoot, boolean isToAgent)` never creates `.archlens` directories on disk.

- [ ] **Step 1: Write failing tests in `FileMailboxServiceTest.java`, `SyncAdrUseCaseTest.java`, and `ArchlensMcpServiceTest.java`**

```java
// FileMailboxServiceTest.java
@Test
void testReadMailboxDoesNotCreateDirectoryOnDisk(@TempDir Path tempDir) {
  Path unopened = tempDir.resolve("unopened-project");
  MailboxEnvelope env = service.readMailbox(unopened.toString(), true);
  assertTrue(env.queue().isEmpty());
  assertFalse(java.nio.file.Files.exists(unopened.resolve(".archlens")));
}

// SyncAdrUseCaseTest.java
@Test
void testRecordDipInversionRejectsSymlinkedDocsAdrDirectory(@TempDir Path tempDir) throws IOException {
  Path project = Files.createDirectories(tempDir.resolve("project"));
  Path outside = Files.createDirectories(tempDir.resolve("outside"));
  Files.createDirectories(project.resolve("docs"));
  Files.createSymbolicLink(project.resolve("docs/adr"), outside);

  SyncAdrUseCase useCase = new SyncAdrUseCase();
  assertThrows(
      IOException.class,
      () -> useCase.recordDipInversion(project.toString(), "a.Foo", "b.Bar", "BarPort", "reason"));
  try (var entries = Files.list(outside)) {
    assertEquals(0L, entries.count());
  }
}

// ArchlensMcpServiceTest.java
@Test
void testSyncDipAdrRequiresActiveWorkspaceOrOpenedProject(@TempDir Path tempDir) throws IOException {
  assertThrows(
      IllegalArgumentException.class,
      () ->
          mcpService.syncDipAdr(
              tempDir.toString(),
              "com.example.OrderService",
              "com.example.PaymentGateway",
              "PaymentGatewayPort",
              "reason"));

  mcpService.getOpenedProjects().markOpened(tempDir.toString());
  SyncAdrUseCase.AdrDocument adr =
      mcpService.syncDipAdr(
          tempDir.toString(),
          "com.example.OrderService",
          "com.example.PaymentGateway",
          "PaymentGatewayPort",
          "Eliminate outward adapter coupling");
  assertNotNull(adr);
}
```
*(Update existing `testSyncDipAdrTool` in `ArchlensMcpServiceTest` or merge with the test above, and also assert active workspace `"."` is allowed only on a temp symlink/check or helper method `isAllowedWriteRoot` without writing into repo `docs/adr` during tests.)*

- [ ] **Step 2: Run targeted backend tests to verify RED failure**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=FileMailboxServiceTest,SyncAdrUseCaseTest,ArchlensMcpServiceTest test`
Expected: FAIL (`unopened.resolve(".archlens")` exists, symlink write succeeds into `outside`, and unopened `syncDipAdr` does not throw).

- [ ] **Step 3: Implement mailbox read-only resolution, ADR symlink guard, and MCP opened-project check**

1. In `FileMailboxService.java`, change `private File getFile(String projectRoot, String fileName, boolean createDir)`: only run `Files.createDirectories(dir.toPath())` when `createDir` is `true` (`appendCommand` and `popOldest`); pass `false` in `readMailbox`.
2. In `SyncAdrUseCase.java`, resolve `Path canonicalRoot = ProjectPathGuard.canonicalRoot(projectRoot);`, walk every prefix between `canonicalRoot` and `targetFile` checking `!Files.isSymbolicLink(cursor)` (throwing `IOException("Refusing to write ADR across symlink: " + cursor)`), and verify `targetFile.normalize().startsWith(canonicalRoot)`.
3. In `ArchlensMcpService.java`, inject `@Inject OpenedProjectRegistry openedProjects;` (initialized in constructor fallback too) with `OpenedProjectRegistry getOpenedProjects()`. Add `String requireAuthorizedWriteRoot(String projectRoot)` that resolves `Path target = ProjectPathGuard.canonicalRoot(WorkspacePathResolver.normalizeRoot(projectRoot))` and permits it iff `target.equals(ProjectPathGuard.canonicalRoot(".")) || getOpenedProjects().isOpened(target.toString())`; otherwise throw `new IllegalArgumentException("Project root is not opened in this session: " + projectRoot)`. Call `requireAuthorizedWriteRoot(projectRoot)` in `syncDipAdr`.

- [ ] **Step 4: Format and run targeted backend tests to verify GREEN**

Run: `mvn -f backend/pom.xml -B -ntp spotless:apply -Dtest=FileMailboxServiceTest,SyncAdrUseCaseTest,ArchlensMcpServiceTest,MailboxResourceTest test`
Expected: PASS (`BUILD SUCCESS`).

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/io/slixes/archlens/mailbox/fs/FileMailboxService.java \
        backend/src/main/java/io/slixes/archlens/usecase/SyncAdrUseCase.java \
        backend/src/main/java/io/slixes/archlens/mcp/ArchlensMcpService.java \
        backend/src/test/java/io/slixes/archlens/mailbox/fs/FileMailboxServiceTest.java \
        backend/src/test/java/io/slixes/archlens/usecase/SyncAdrUseCaseTest.java \
        backend/src/test/java/io/slixes/archlens/mcp/ArchlensMcpServiceTest.java
git commit -m "fix(security): confine MCP syncDipAdr, block ADR symlinks, and keep mailbox GET read-only"
```

---

### Task 3: Pure-Git Historical Tag Extraction, Cache Persistence & Real Fitness History

**Files:**
- Modify: `backend/src/main/java/io/slixes/archlens/usecase/ManageSnapshotsUseCase.java`
- Modify: `backend/src/main/java/io/slixes/archlens/usecase/GetFitnessHistoryUseCase.java:72-118`
- Test: `backend/src/test/java/io/slixes/archlens/usecase/ManageSnapshotsUseCaseTest.java`
- Create: `backend/src/test/java/io/slixes/archlens/usecase/GetFitnessHistoryUseCaseTest.java`

**Interfaces:**
- Produces:
  - `ManageSnapshotsUseCase.isValidSnapshotId(String snapshotId) -> boolean` matching `^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$`.
  - `ManageSnapshotsUseCase.listSnapshots(String projectRoot)` merges `.archlens/snapshots/*.json` and up to 10 Git tags from `git for-each-ref --sort=-creatordate --count=10 --format=%(refname:short)|%(creatordate:short) refs/tags` (deduped by `id`, carrying real `date`).
  - `ManageSnapshotsUseCase.getSnapshot(String snapshotId, String projectRoot)` compiles real Git tags via isolated `GIT_INDEX_FILE`, falls back to workspace `.archlens/policy.json` if absent in the tag, and writes `.archlens/cache/<snapshotId>.json` (symlink-guarded). Does **not** cache nonexistent tags.
  - `GetFitnessHistoryUseCase.getHistory(String projectRoot)` evaluates each snapshot/tag via `manageSnapshotsUseCase.getSnapshot(id, root)` instead of substituting `currentGraph`.

- [ ] **Step 1: Write failing tests in `ManageSnapshotsUseCaseTest.java` and `GetFitnessHistoryUseCaseTest.java`**

Test real temporary `git init` repositories with two tags (`v0.1.0` with an outward dependency violation and no `.archlens/policy.json`, then `v0.2.0` after fixing the violation and adding `.archlens/policy.json`):
1. `listSnapshots` returns both manual `.archlens/snapshots/*.json` AND Git tags (`v0.2.0`, `v0.1.0`) with `YYYY-MM-DD` `date` fields.
2. `getSnapshot("v0.1.0", repoRoot)` compiles the `v0.1.0` tree (copying workspace `.archlens/policy.json` into the temp checkout so rings classify properly), returns the historical graph with the violation (different from current `v0.2.0` working tree!), writes `.archlens/cache/v0.1.0.json`, and leaves `git status --porcelain` completely empty (zero `.git/index` or working-tree mutation).
3. `getSnapshot("-o", repoRoot)` and `getSnapshot("--output=/tmp/x", repoRoot)` throw `IllegalArgumentException`.
4. `getSnapshot("v99.99.99", repoRoot)` does **not** create `.archlens/cache/v99.99.99.json`.
5. `GetFitnessHistoryUseCaseTest` verifies `getHistory(repoRoot)` reports lower fitness / higher violations on `v0.1.0` than on `v0.2.0` / `current` (`IMPROVING` trend rather than a fake flat `STABLE` clone of `currentGraph`).

- [ ] **Step 2: Run targeted snapshot & fitness tests to verify RED failure**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=ManageSnapshotsUseCaseTest,GetFitnessHistoryUseCaseTest test`
Expected: FAIL (`getSnapshot("v0.1.0", ...)` returns current working tree graph, `-o` is accepted, `.archlens/cache/v0.1.0.json` is not created, and `GetFitnessHistoryUseCaseTest` reports identical scores).

- [ ] **Step 3: Implement pure-`git` tag listing, isolated `GIT_INDEX_FILE` extraction + caching, and `GetFitnessHistoryUseCase` delegation**

Key algorithm in `ManageSnapshotsUseCase.compileAndCacheGitTag(Path canonicalRoot, String tag)`:
1. Run `git rev-parse --verify --quiet refs/tags/<tag>^{tree}` in `canonicalRoot` (drain stdout, `waitFor(5, SECONDS)`). Return `null` if exit code != `0`.
2. Create `Path tempDir = Files.createTempDirectory("archlens-tag-");` and `Path worktree = Files.createDirectories(tempDir.resolve("tree")); Path tempIndex = tempDir.resolve("index");`.
3. In `try { ... } finally { deleteRecursively(tempDir); }`:
   - Run `git read-tree refs/tags/<tag>` with env `GIT_INDEX_FILE=tempIndex.toString()` in `canonicalRoot`.
   - Run `git checkout-index -a -f --prefix=<worktree.toString() + File.separator>` with env `GIT_INDEX_FILE=tempIndex.toString()` in `canonicalRoot`.
   - If `!Files.exists(worktree.resolve(".archlens/policy.json")) && Files.isRegularFile(canonicalRoot.resolve(".archlens/policy.json"))`, copy `policy.json` into `worktree.resolve(".archlens/policy.json")`.
   - Compile `ArchitectureGraph graph = architectureCompiler.compileGraph(worktree.toString(), null);`.
   - Verify no symlinks along `canonicalRoot.resolve(".archlens/cache/" + tag + ".json")`, create `.archlens/cache`, and write `graph` atomically via `.tmp` + `Files.move(..., REPLACE_EXISTING)`.
4. In `GetFitnessHistoryUseCase.java`, replace lines `85-91` with `ArchitectureGraph graph = manageSnapshotsUseCase.getSnapshot(id, root);`.

- [ ] **Step 4: Format and run snapshot & fitness tests to verify GREEN**

Run: `mvn -f backend/pom.xml -B -ntp spotless:apply -Dtest=ManageSnapshotsUseCaseTest,GetFitnessHistoryUseCaseTest,SnapshotResourceTest,FitnessResourceTest,DiagramResourceTest test`
Expected: PASS (`BUILD SUCCESS`).

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/src/main/java/io/slixes/archlens/usecase/ManageSnapshotsUseCase.java \
        backend/src/main/java/io/slixes/archlens/usecase/GetFitnessHistoryUseCase.java \
        backend/src/test/java/io/slixes/archlens/usecase/ManageSnapshotsUseCaseTest.java \
        backend/src/test/java/io/slixes/archlens/usecase/GetFitnessHistoryUseCaseTest.java
git commit -m "fix(snapshots): compile and cache historical git tags via isolated GIT_INDEX_FILE"
```

---

### Task 4: `ProjectFileWatcher` Root-Switch Restart & Single `loadFitness()` on Project Open

**Files:**
- Modify: `backend/src/main/java/io/slixes/archlens/engine/ProjectFileWatcher.java:40-114`
- Modify: `backend/src/main/java/io/slixes/archlens/delivery/rest/DiagramResource.java:404-424`
- Modify: `frontend/src/lib/state/diagram.svelte.ts:1125-1150`
- Test: `backend/src/test/java/io/slixes/archlens/engine/ProjectFileWatcherTest.java`
- Test: `frontend/src/lib/state/diagram_extra.test.ts`

**Interfaces:**
- Produces:
  - `ProjectFileWatcher.getWatchedRoot() -> Path` (canonical/normalized path currently watched, or `null` when stopped).
  - `ProjectFileWatcher.start(Path rootPath, Consumer<Path> onChange)` keeps running if canonical `rootPath` equals `watchedRoot`, and calls `stop()` then starts on `rootPath` when `rootPath` differs.
  - `diagramStore.setProjectRoot(root, updateUrl)` awaits `this.loadFitness()` inside `loadGraph()` once rather than firing `loadFitness()` twice back-to-back.

- [ ] **Step 1: Write failing tests in `ProjectFileWatcherTest.java` and `diagram_extra.test.ts`**

```java
// ProjectFileWatcherTest.java
@Test
@DisplayName("start switches watched root when called with a new project directory and stays put on same root")
void testSwitchesWatchedRoot(@TempDir Path tempDir) throws Exception {
  Path projectA = Files.createDirectories(tempDir.resolve("project-a"));
  Path projectB = Files.createDirectories(tempDir.resolve("project-b"));
  ProjectFileWatcher watcher = new ProjectFileWatcher(50);

  CountDownLatch latchB = new CountDownLatch(1);
  watcher.start(projectA, p -> {});
  Path firstRoot = watcher.getWatchedRoot();
  assertEquals(projectA.toRealPath(), firstRoot);

  // Calling start with the same root is a no-op
  watcher.start(projectA, p -> {});
  assertEquals(firstRoot, watcher.getWatchedRoot());

  // Switching to projectB stops watching projectA and watches projectB
  watcher.start(projectB, p -> latchB.countDown());
  assertEquals(projectB.toRealPath(), watcher.getWatchedRoot());

  Files.writeString(projectB.resolve("SwitchedService.java"), "class SwitchedService {}");
  assertTrue(latchB.await(5, TimeUnit.SECONDS));
  watcher.stop();
  assertNull(watcher.getWatchedRoot());
}
```
And in `frontend/src/lib/state/diagram_extra.test.ts`, mock `fetch` and assert `await diagramStore.setProjectRoot('/workspace/proj-b', false)` calls `/api/fitness?` **once** (not twice).

- [ ] **Step 2: Run backend watcher test & frontend Vitest to verify RED failure**

Run:
- `mvn -f backend/pom.xml -B -ntp -Dtest=ProjectFileWatcherTest test`
- `npm --prefix frontend exec vitest run src/lib/state/diagram_extra.test.ts`
Expected: FAIL (`getWatchedRoot` missing / `projectB` never watched; `/api/fitness` fetched `2` times).

- [ ] **Step 3: Implement watcher root-tracking restart & deduplicate `loadFitness()`**

1. In `ProjectFileWatcher.java`, add `private volatile Path watchedRoot;` and `public Path getWatchedRoot() { return watchedRoot; }`. In `synchronized void start(Path rootPath, Consumer<Path> onChange)`, guard `rootPath == null || !Files.exists(rootPath)` first, normalize via `Path normalized = toNormalizedRoot(rootPath)` (`toRealPath()` fallback `toAbsolutePath().normalize()`), return immediately if `running.get() && normalized.equals(watchedRoot)`, call `if (running.get()) { stop(); }` when switching roots, set `this.watchedRoot = normalized;` on start, and clear `this.watchedRoot = null;` in `stop()`.
2. In `DiagramResource.ensureWatcherRunning(String projectRoot)`, remove the outer `if (!watcher.isRunning())` check (or skip only when `Paths.get(WorkspacePathResolver.normalizeRoot(projectRoot))` already matches `watcher.getWatchedRoot()`) so switching `projectRoot` on `/api/graph` or `/api/events` switches the watcher.
3. In `frontend/src/lib/state/diagram.svelte.ts`, `await this.loadFitness()` inside `loadGraph` (`L1145`) and delete the duplicate `await this.loadFitness();` on `L1130`.

- [ ] **Step 4: Run backend & frontend checks to verify GREEN**

Run:
- `mvn -f backend/pom.xml -B -ntp spotless:apply -Dtest=ProjectFileWatcherTest,DiagramResourceTest test`
- `npm --prefix frontend run check && npm --prefix frontend exec vitest run --coverage`
Expected: PASS (`0` errors, all Vitest suites passing).

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/src/main/java/io/slixes/archlens/engine/ProjectFileWatcher.java \
        backend/src/main/java/io/slixes/archlens/delivery/rest/DiagramResource.java \
        frontend/src/lib/state/diagram.svelte.ts \
        backend/src/test/java/io/slixes/archlens/engine/ProjectFileWatcherTest.java \
        frontend/src/lib/state/diagram_extra.test.ts
git commit -m "fix(watcher,ui): switch file watcher on projectRoot change and deduplicate fitness scan"
```

---

### Task 5: Docs Sync, Full Quality Gates, Stage 7 PR Merge (`develop`) & `v1.1.7` Release

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `wiki/` (if AST exports change via `sdlc-engine.mjs wiki sync`)

**Interfaces:**
- Consumes: All deliverables from Tasks 1–4.
- Produces: Merged PR on `develop` with signed Stage 7 receipt, followed by `v1.1.7` release PR (`develop -> main`) and `GHSA-pc6m-876p-9hp9` publication upon owner confirmation.

- [ ] **Step 1: Update `CHANGELOG.md` (`[Unreleased]` / `1.1.7`) and sync wiki**

Document the 5 Wave 1 fixes in `CHANGELOG.md` and run `node .agent/scripts/sdlc-engine.mjs wiki sync && node .agent/scripts/sdlc-engine.mjs wiki verify`.

- [ ] **Step 2: Run full polyglot verification + diff coverage gate (`>= 85%`)**

Run:
```bash
mvn -f backend/pom.xml -B -ntp clean spotless:check verify
python3 scripts/check_diff_cov.py
npm --prefix frontend run check
npm --prefix frontend exec vitest run --coverage
npm test
npm run smoke
node .agent/scripts/sdlc-engine.mjs stage pre-pr
```
Expected: `220+` Java tests pass, `>= 85%` diff line & branch coverage, `0` SpotBugs/PMD/CPD errors, `0` svelte-check errors, Vitest/CLI/scripts/smoke all pass, `0` spec drift.

- [ ] **Step 3: Push feature branch, open PR against `develop`, run parallel `sdlc-gate` peer review, post Stage 7 receipt, wait for green GitHub CI, and merge**

Dispatch `PR Peer Reviewer` + `Architecture & Security Gatekeeper` `research` subagents on the diff, run `node .agent/scripts/sdlc-engine.mjs gate --strict` + `review record`, post the 4-section Stage 7 receipt to the PR, confirm all 3 GitHub Actions checks pass, merge to `develop`, update `#247`, and run `node .agent/scripts/sdlc-engine.mjs stage complete`.

- [ ] **Step 4: Cut `v1.1.7` (`develop -> main`) and confirm publishing `GHSA-pc6m-876p-9hp9`**

Bump SemVer `1.1.6 -> 1.1.7` across manifests via the release workflow / PR to `main`, verify the `v1.1.7` release pipeline (including the `#263` GraalVM native binary build), and confirm with the owner right before flipping `GHSA-pc6m-876p-9hp9` from `draft` to `published`.
