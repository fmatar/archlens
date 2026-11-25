/**
 * Archlens Backend-First Headless Architecture Checker (#244)
 *
 * Delegates `archlens check` to the Quarkus polyglot scanner engine (reusing an active
 * authenticated loopback server or launching a short-lived loopback binary/JAR and terminating
 * it cleanly on exit), falling back to `cli/src/checker.js` when `--offline` is passed or no
 * local backend runtime is available.
 * Zero external dependencies; zero shell interpolation.
 */

import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as net from 'node:net';
import * as path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import {
  DEFAULT_DOCKER_IMAGE,
  DEFAULT_SERVER_URL,
  checkServerHealth,
  findNativeBinary,
  findNativeJar,
  isDockerAvailable,
  isJavaAvailable,
  serverLaunchEnv
} from './docker-runner.js';
import {
  calculateArchitectureFitness,
  calculateScreamingMetric,
  findMatchingFiles,
  loadProjectPolicy,
  suggestFeatureClusters,
  validateNumericOptions
} from './checker.js';
import {
  generateSessionToken,
  isSafeLoopbackServerUrl,
  resolveSessionToken,
  withAuth
} from './session.js';

const TIER_NAMES = [
  'Domain Core (L0)',
  'Application (L1)',
  'Adapters (L2)',
  'Infrastructure (L3)'
];

const IGNORED_WALK_DIRS = new Set([
  '.git',
  '.svn',
  '.hg',
  '.archlens',
  'node_modules',
  'target',
  'build',
  'dist',
  'out',
  'vendor',
  'venv',
  '.venv',
  '__pycache__'
]);

function safeRealPath(targetPath) {
  try {
    return fs.realpathSync(targetPath);
  } catch {
    return path.resolve(targetPath);
  }
}

function isConfinedRelativePath(rel) {
  return Boolean(
    rel &&
      rel !== '..' &&
      !rel.startsWith(`..${path.sep}`) &&
      !rel.startsWith('../') &&
      !path.isAbsolute(rel)
  );
}

/**
 * Resolves a backend `ClassNode.filePath` (host absolute, container `/workspace/...`, or
 * root-relative as emitted by Rust/Go scanners) to a canonical host file confined strictly
 * inside `realRoot`. Returns `{ absPath, relPath, exists }` when confined, or `null` if it
 * escapes `realRoot`.
 */
function resolveConfinedClassFile(rawFilePath, root, realRoot, allowContainerRebase = true) {
  if (!rawFilePath || typeof rawFilePath !== 'string' || rawFilePath.includes('\0')) {
    return null;
  }
  const normalizedSlash = rawFilePath.replace(/\\/g, '/').trim();
  if (!normalizedSlash) return null;

  let candidateAbs;
  if (
    allowContainerRebase &&
    (normalizedSlash === '/workspace' || normalizedSlash.startsWith('/workspace/'))
  ) {
    const suffix = normalizedSlash === '/workspace' ? '' : normalizedSlash.slice('/workspace/'.length);
    candidateAbs = path.resolve(root, suffix);
  } else if (path.isAbsolute(rawFilePath) || normalizedSlash.startsWith('/')) {
    candidateAbs = path.resolve(rawFilePath);
  } else {
    candidateAbs = path.resolve(root, rawFilePath);
  }

  try {
    const realCandidate = fs.realpathSync(candidateAbs);
    const rel = path.relative(realRoot, realCandidate);
    if (!isConfinedRelativePath(rel)) {
      return null;
    }
    return { absPath: realCandidate, relPath: rel, exists: true };
  } catch {
    // Non-existent file (e.g. unit test mock): check lexical confinement against root without reading disk
    const lexRel = path.relative(path.resolve(root), candidateAbs);
    if (!isConfinedRelativePath(lexRel)) {
      return null;
    }
    return { absPath: candidateAbs, relPath: lexRel, exists: false };
  }
}

export function findFreeLoopbackPort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.on('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const addr = srv.address();
      const port = typeof addr === 'object' && addr ? addr.port : null;
      srv.close((err) => {
        if (err || !port) reject(err || new Error('Failed to allocate loopback port'));
        else resolve(port);
      });
    });
  });
}

function walkSourceTree(startDir, visitor, maxEntries = 2000) {
  let visited = 0;
  function walk(dir) {
    if (visited >= maxEntries) return;
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch (err) {
      visitor({ path: dir, unreadable: true, error: err });
      return;
    }
    for (const entry of entries) {
      if (visited >= maxEntries) return;
      if (entry.name.startsWith('.') || IGNORED_WALK_DIRS.has(entry.name)) {
        continue;
      }
      const full = path.join(dir, entry.name);
      visited += 1;
      if (entry.isSymbolicLink()) {
        try {
          fs.statSync(full);
        } catch (err) {
          visitor({ path: full, unreadable: true, error: err });
          continue;
        }
      }
      if (entry.isDirectory()) {
        walk(full);
      } else {
        try {
          fs.accessSync(full, fs.constants.R_OK);
          visitor({ path: full, unreadable: false });
        } catch (err) {
          visitor({ path: full, unreadable: true, error: err });
        }
      }
    }
  }
  if (fs.existsSync(startDir)) {
    walk(startDir);
  }
}

export function hasRustOrGoSources(projectRoot, policy = null) {
  const root = path.resolve(projectRoot || '.');
  const effectivePolicy = policy || loadProjectPolicy(root);
  if (effectivePolicy?.lang && ['rust', 'go'].includes(String(effectivePolicy.lang).toLowerCase())) {
    return true;
  }
  const scanDir = path.join(root, effectivePolicy?.src || 'src');
  let found = false;
  walkSourceTree(
    fs.existsSync(scanDir) ? scanDir : root,
    ({ path: filePath, unreadable }) => {
      if (!unreadable && (filePath.endsWith('.rs') || filePath.endsWith('.go'))) {
        found = true;
      }
    },
    1000
  );
  return found;
}

function hasBackendSupportedSourceFiles(root, policy) {
  const scanDir = path.join(root, policy?.src || 'src');
  const target = fs.existsSync(scanDir) ? scanDir : root;
  let found = false;
  walkSourceTree(
    target,
    ({ path: filePath, unreadable }) => {
      if (unreadable) return;
      const base = path.basename(filePath);
      if (
        /\.(?:java|py|ts|tsx|js|jsx|svelte|rs|go)$/i.test(filePath) &&
        !filePath.endsWith('.d.ts') &&
        !filePath.endsWith('_test.go') &&
        !filePath.endsWith('_test.py') &&
        !base.startsWith('test_')
      ) {
        found = true;
      }
    },
    500
  );
  return found;
}

function collectUnreadableRelativePaths(root, policy) {
  const scanDir = path.join(root, policy?.src || 'src');
  const target = fs.existsSync(scanDir) ? scanDir : root;
  const skipped = [];
  walkSourceTree(target, ({ path: entryPath, unreadable }) => {
    if (unreadable) {
      skipped.push(path.relative(root, entryPath));
    }
  });
  return skipped;
}

async function waitForEphemeralBackend(serverUrl, fetchFn, timeoutMs = 20000, earlyAbort = null) {
  const versionUrl = new URL('/api/version', serverUrl).toString();
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (earlyAbort?.exited) {
      return false;
    }
    try {
      const res = await fetchFn(versionUrl, {
        redirect: 'error',
        signal: AbortSignal.timeout(1200)
      });
      if (res && res.ok) {
        return true;
      }
    } catch {
      // Backend still starting up
    }
    await new Promise((resolve) => {
      const t = setTimeout(resolve, 150);
      t.unref?.();
    });
  }
  return false;
}

async function terminateChildProcess(child) {
  if (!child || typeof child.exitCode === 'number' || child.killed) {
    return;
  }
  await new Promise((resolve) => {
    let settled = false;
    let killTimer = null;
    const finish = () => {
      if (!settled) {
        settled = true;
        if (killTimer) clearTimeout(killTimer);
        resolve();
      }
    };
    child.once('exit', finish);
    try {
      child.kill('SIGTERM');
    } catch {
      finish();
      return;
    }
    killTimer = setTimeout(() => {
      try {
        child.kill('SIGKILL');
      } catch {
        // ignore
      }
      finish();
    }, 1500);
    killTimer.unref?.();
  });
}

function registerSignalCleanup(cleanupFn) {
  const onSigint = () => {
    try {
      cleanupFn();
    } catch {
      // ignore
    }
  };
  const onSigterm = () => {
    try {
      cleanupFn();
    } catch {
      // ignore
    }
  };
  process.once('SIGINT', onSigint);
  process.once('SIGTERM', onSigterm);
  return () => {
    process.removeListener('SIGINT', onSigint);
    process.removeListener('SIGTERM', onSigterm);
  };
}

async function fetchBackendPayloads(serverUrl, queryRoot, options, token, fetchFn) {
  const init = withAuth({ redirect: 'error', signal: AbortSignal.timeout(15000) }, token);

  const graphUrl = new URL('/api/graph', serverUrl);
  graphUrl.searchParams.set('projectRoot', queryRoot);
  const graphRes = await fetchFn(graphUrl.toString(), init);
  if (!graphRes || !graphRes.ok) {
    return null;
  }
  const graph = await graphRes.json();

  let fitness = null;
  const fitUrl = new URL('/api/fitness', serverUrl);
  fitUrl.searchParams.set('projectRoot', queryRoot);
  const fitRes = await fetchFn(fitUrl.toString(), init);
  if (fitRes && fitRes.ok) {
    fitness = await fitRes.json();
  }

  let migrationProposal = null;
  if (options.suggestFeatures) {
    const migUrl = new URL('/api/screaming/migration-proposal', serverUrl);
    migUrl.searchParams.set('projectRoot', queryRoot);
    const migRes = await fetchFn(migUrl.toString(), init);
    if (migRes && migRes.ok) {
      migrationProposal = await migRes.json();
    }
  }

  return { graph, fitness, migrationProposal };
}

/**
 * Finds the 1-based line number of a violating import in `confinedFile.absPath` ONLY when
 * `confinedFile.exists === true` (verified via `fs.realpathSync` to lie inside `realRoot`).
 * Returns `0` when no matching import line is found so callers can distinguish matched vs unmatched.
 */
function findMatchingImportLineInConfinedFile(confinedFile, toImport, toPkg) {
  if (!confinedFile || !confinedFile.exists) {
    return 0;
  }
  try {
    const content = fs.readFileSync(confinedFile.absPath, 'utf8');
    const lines = content.split(/\r?\n/);
    const rawLeaf = toImport ? String(toImport).split(/[./:]/).pop() : '';
    const lowPkg = toPkg ? String(toPkg).toLowerCase() : '';
    let inBlockComment = false;
    let inGoImportBlock = false;

    for (let idx = 0; idx < lines.length; idx++) {
      let line = lines[idx];
      if (inBlockComment) {
        const closeIdx = line.indexOf('*/');
        if (closeIdx === -1) continue;
        line = line.slice(closeIdx + 2);
        inBlockComment = false;
      }
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('#')) {
        continue;
      }
      if (trimmed.startsWith('/*') && !trimmed.includes('*/')) {
        inBlockComment = true;
        continue;
      }
      if (/^import\s*\(/.test(trimmed)) {
        inGoImportBlock = !trimmed.includes(')');
      } else if (inGoImportBlock) {
        if (trimmed.includes(')')) {
          inGoImportBlock = false;
        }
        if (
          trimmed.includes('"') &&
          ((rawLeaf && trimmed.includes(rawLeaf)) ||
            (lowPkg && trimmed.toLowerCase().includes(lowPkg)))
        ) {
          return idx + 1;
        }
        continue;
      }

      if (!/\b(?:import|from|require|use)\b/.test(trimmed)) {
        continue;
      }
      if (
        (rawLeaf && trimmed.includes(rawLeaf)) ||
        (lowPkg && trimmed.toLowerCase().includes(lowPkg))
      ) {
        return idx + 1;
      }
    }
  } catch {
    return 0;
  }
  return 0;
}

export function mapBackendResultsToCliReport(
  projectRoot,
  policy,
  options,
  graph,
  fitnessEval = null,
  migrationProposal = null
) {
  const root = path.resolve(projectRoot || '.');
  const realRoot = safeRealPath(root);
  const levels = policy.levels || [];
  const maxViolations = options.maxViolations !== undefined ? Number(options.maxViolations) : 0;
  const isProposalGraph = Boolean(graph.isProposal);

  const pkgToLevel = new Map();
  const lowerToCanonicalPkg = new Map();
  levels.forEach((tier, lvl) => {
    (tier || []).forEach((p) => {
      const low = String(p).toLowerCase().replace(/(?:::+|[\\/]+)+/g, '.');
      pkgToLevel.set(low, lvl);
      lowerToCanonicalPkg.set(low, p);
    });
  });
  const sortedPkgEntries = Array.from(pkgToLevel.entries()).sort(
    (a, b) => b[0].length - a[0].length
  );

  // Mirror DependencyRuleValidator.resolveRank: exact -> longest root-prefix -> longest inner/suffix segment
  function canonicalizePkg(rawPkg, fallbackLevel = null) {
    if (!rawPkg) {
      return { pkg: fallbackLevel !== null ? `L${fallbackLevel}` : null, level: fallbackLevel };
    }
    const low = String(rawPkg).toLowerCase().replace(/(?:::+|[\\/]+)+/g, '.');
    if (pkgToLevel.has(low)) {
      return { pkg: lowerToCanonicalPkg.get(low) || rawPkg, level: pkgToLevel.get(low) };
    }
    for (const [candidateLow, lvl] of sortedPkgEntries) {
      if (low.startsWith(`${candidateLow}.`)) {
        return { pkg: lowerToCanonicalPkg.get(candidateLow) || candidateLow, level: lvl };
      }
    }
    for (const [candidateLow, lvl] of sortedPkgEntries) {
      if (low.endsWith(`.${candidateLow}`) || low.includes(`.${candidateLow}.`)) {
        return { pkg: lowerToCanonicalPkg.get(candidateLow) || candidateLow, level: lvl };
      }
    }
    return { pkg: rawPkg, level: fallbackLevel };
  }

  const classById = new Map();
  const moduleFallbackById = new Map();
  const compById = new Map();
  const uniqueSourceFiles = new Set();

  for (const comp of graph.components || []) {
    compById.set(comp.id, comp);
    for (const childPkg of comp.childPackageIds || []) {
      compById.set(childPkg, comp);
    }
    for (const cls of comp.classes || []) {
      classById.set(cls.id, { comp, cls, exactClass: true });
      if (!cls.isForeign && cls.filePath) {
        const confined = resolveConfinedClassFile(cls.filePath, root, realRoot, true);
        if (confined) {
          uniqueSourceFiles.add(confined.relPath);
        }
      }
      if (
        cls.id &&
        cls.name &&
        cls.id.endsWith(`.${cls.name}`) &&
        cls.id.length > cls.name.length + 1
      ) {
        const modPrefix = cls.id.slice(0, -(cls.name.length + 1));
        if (!moduleFallbackById.has(modPrefix)) {
          moduleFallbackById.set(modPrefix, { comp, cls, exactClass: false });
        }
      }
    }
  }

  function resolveEndpoint(identifier) {
    if (!identifier) return { comp: null, cls: null, exactClass: false };
    if (classById.has(identifier)) return classById.get(identifier);
    if (moduleFallbackById.has(identifier)) return moduleFallbackById.get(identifier);
    if (compById.has(identifier)) {
      const comp = compById.get(identifier);
      const firstCls = (comp.classes || []).find((c) => !c.isForeign) || null;
      return { comp, cls: firstCls, exactClass: false };
    }
    let bestComp = null;
    let bestLen = -1;
    for (const [key, comp] of compById.entries()) {
      if (identifier.startsWith(`${key}.`) && key.length > bestLen) {
        bestComp = comp;
        bestLen = key.length;
      }
    }
    if (bestComp) {
      const firstCls = (bestComp.classes || []).find((c) => !c.isForeign) || null;
      return { comp: bestComp, cls: firstCls, exactClass: false };
    }
    return { comp: null, cls: null, exactClass: false };
  }

  function pickSourceFileAndLine(srcEnd, toImport, toPkg, fallbackPkg) {
    if (!srcEnd.exactClass && Array.isArray(srcEnd.comp?.classes) && srcEnd.comp.classes.length > 1) {
      for (const candidateCls of srcEnd.comp.classes) {
        if (candidateCls.isForeign || !candidateCls.filePath) continue;
        const confined = resolveConfinedClassFile(candidateCls.filePath, root, realRoot, true);
        if (confined) {
          const matchedLine = findMatchingImportLineInConfinedFile(confined, toImport, toPkg);
          if (matchedLine > 0) {
            return { fromFile: confined.relPath, line: matchedLine };
          }
        }
      }
    }
    if (srcEnd.cls?.filePath) {
      const confined = resolveConfinedClassFile(srcEnd.cls.filePath, root, realRoot, true);
      if (confined) {
        const matchedLine = findMatchingImportLineInConfinedFile(confined, toImport, toPkg);
        return { fromFile: confined.relPath, line: matchedLine > 0 ? matchedLine : 1 };
      }
    }
    return { fromFile: fallbackPkg || 'unknown', line: 1 };
  }

  const packageEdges = new Map();
  const rawViolations = [];

  for (const edge of graph.edges || []) {
    const srcEnd = resolveEndpoint(edge.from);
    const dstEnd = resolveEndpoint(edge.to);
    const srcPkgInput = isProposalGraph
      ? srcEnd.cls?.packageName || srcEnd.comp?.id || edge.from
      : srcEnd.comp?.id || srcEnd.cls?.packageName || edge.from;
    const dstPkgInput = isProposalGraph
      ? dstEnd.cls?.packageName || dstEnd.comp?.id || edge.to
      : dstEnd.comp?.id || dstEnd.cls?.packageName || edge.to;

    const srcMapped = canonicalizePkg(srcPkgInput, srcEnd.comp?.level ?? null);
    const dstMapped = canonicalizePkg(dstPkgInput, dstEnd.comp?.level ?? null);

    if (srcMapped.pkg && dstMapped.pkg && srcMapped.pkg !== dstMapped.pkg) {
      if (!packageEdges.has(srcMapped.pkg)) {
        packageEdges.set(srcMapped.pkg, new Set());
      }
      packageEdges.get(srcMapped.pkg).add(dstMapped.pkg);
    }

    if (edge.isViolating) {
      const fromLevel = isProposalGraph
        ? (srcEnd.comp?.level ?? srcMapped.level ?? 0)
        : (srcMapped.level ?? srcEnd.comp?.level ?? 0);
      const toLevel = isProposalGraph
        ? (dstEnd.comp?.level ?? dstMapped.level ?? 1)
        : (dstMapped.level ?? dstEnd.comp?.level ?? 1);
      const fromPkg = srcMapped.pkg || `L${fromLevel}`;
      const toPkg = dstMapped.pkg || `L${toLevel}`;
      const { fromFile, line } = pickSourceFileAndLine(srcEnd, edge.to, toPkg, fromPkg);

      rawViolations.push({
        fromFile,
        fromPkg,
        fromTier: TIER_NAMES[fromLevel] || `Level ${fromLevel}`,
        toImport: edge.to,
        toPkg,
        toTier: TIER_NAMES[toLevel] || `Level ${toLevel}`,
        line,
        exactClass: Boolean(srcEnd.exactClass || dstEnd.exactClass)
      });
    }
  }

  // Drop module-level mirror edges when a class-level edge already exists on the same (fromFile, toPkg)
  const hasClassLevelForFilePkg = new Set();
  for (const v of rawViolations) {
    if (v.exactClass) {
      hasClassLevelForFilePkg.add(`${v.fromFile}::${v.toPkg}`);
    }
  }

  const seenViolationKeys = new Set();
  const violations = [];
  for (const v of rawViolations) {
    const filePkgKey = `${v.fromFile}::${v.toPkg}`;
    if (!v.exactClass && hasClassLevelForFilePkg.has(filePkgKey)) {
      continue;
    }
    const dedupKey = `${v.fromFile}::${v.toPkg}::${v.toImport}`;
    if (seenViolationKeys.has(dedupKey)) {
      continue;
    }
    seenViolationKeys.add(dedupKey);
    const ext = path.extname(v.fromFile);
    const baseName = ext ? path.basename(v.fromFile, ext) : path.basename(v.fromFile);
    violations.push({
      fromFile: v.fromFile,
      fromPkg: v.fromPkg,
      fromTier: v.fromTier,
      toImport: v.toImport,
      toPkg: v.toPkg,
      toTier: v.toTier,
      portName: `${baseName}Port`,
      line: v.line
    });
  }

  const cycles = (graph.cycles || []).map((c) => {
    const pkgs = Array.isArray(c.packages) ? c.packages : [];
    return {
      packages: pkgs,
      formatted: pkgs.join(' -> ')
    };
  });

  const screaming =
    graph.screamingMetric && typeof graph.screamingMetric.score === 'number'
      ? graph.screamingMetric
      : calculateScreamingMetric(Array.from(pkgToLevel.keys()));

  const screamingThreshold =
    options.screamingThreshold !== undefined && options.screamingThreshold !== null
      ? Number(options.screamingThreshold)
      : null;
  const failOnScreaming = screamingThreshold !== null && screaming.score < screamingThreshold;
  const failOnCycles = Boolean(options.detectCycles);

  const fitness =
    fitnessEval && typeof fitnessEval.fitnessScore === 'number'
      ? fitnessEval
      : calculateArchitectureFitness(
          {
            violationsCount: violations.length,
            cyclesCount: cycles.length,
            screamingScore: screaming.score,
            maxDistance: 0
          },
          policy.fitness || {}
        );

  const fitnessThreshold =
    options.fitnessThreshold !== undefined && options.fitnessThreshold !== null
      ? parseFloat(options.fitnessThreshold)
      : typeof policy.fitness?.threshold === 'number'
        ? policy.fitness.threshold
        : null;
  const failOnFitness =
    fitnessThreshold !== null &&
    !Number.isNaN(fitnessThreshold) &&
    fitness.fitnessScore < fitnessThreshold;

  const passed =
    violations.length <= maxViolations &&
    (!failOnCycles || cycles.length === 0) &&
    !failOnScreaming &&
    !failOnFitness;

  const serializedEdges = [];
  for (const [fromPkg, targets] of packageEdges.entries()) {
    for (const toPkg of targets) {
      serializedEdges.push({ from: fromPkg, to: toPkg });
    }
  }

  let featureClusters = null;
  if (options.suggestFeatures) {
    if (migrationProposal && Array.isArray(migrationProposal.clusters)) {
      featureClusters = migrationProposal;
    } else {
      const fullSrc = path.join(root, policy.src || 'src');
      const fallbackFiles = findMatchingFiles(fullSrc, (f) =>
        /\.(?:java|kt|ts|tsx|js|jsx|svelte|py)$/i.test(f)
      );
      featureClusters = suggestFeatureClusters(Array.from(pkgToLevel.keys()), fallbackFiles);
    }
  }

  const skippedFiles = Number(graph.skippedFiles || 0);
  const skippedFilePaths = skippedFiles > 0 ? collectUnreadableRelativePaths(root, policy) : [];

  return {
    passed,
    engine: 'backend',
    projectTitle: policy.title || graph.title || path.basename(root),
    levels,
    edges: serializedEdges,
    sourceFilesCount: uniqueSourceFiles.size,
    skippedFiles,
    skippedFilePaths,
    partial: false,
    allowPartial: Boolean(options.allowPartial),
    maxFiles: null,
    packagesCount: (policy.order || []).length,
    violationsCount: violations.length,
    cyclesCount: cycles.length,
    maxViolations,
    screamingThreshold,
    screaming,
    fitnessThreshold,
    fitness,
    violations,
    cycles,
    featureClusters
  };
}

function isLiveGraphMountedOnRoot(graph, policy, root, realRoot, hostHasSources) {
  if (!graph || (policy.title && graph.title !== policy.title)) {
    return false;
  }
  const nonForeignClasses = [];
  for (const comp of graph.components || []) {
    for (const cls of comp.classes || []) {
      if (!cls.isForeign) {
        nonForeignClasses.push(cls);
      }
    }
  }
  if (hostHasSources && nonForeignClasses.length === 0) {
    return false;
  }
  const sample = nonForeignClasses.slice(0, 10);
  for (const cls of sample) {
    if (!cls.filePath) continue;
    const resolved = resolveConfinedClassFile(cls.filePath, root, realRoot, true);
    if (!resolved || !resolved.exists) {
      return false;
    }
  }
  return true;
}

export async function checkArchitectureViaBackend(projectRoot, options = {}, deps = {}) {
  const root = path.resolve(projectRoot || '.');
  const realRoot = safeRealPath(root);
  const policy = loadProjectPolicy(root);
  if (!policy) {
    return {
      delegated: true,
      mode: 'validation',
      report: {
        passed: false,
        violationsCount: 0,
        violations: [],
        error: `No Archlens policy found in ${root}. Run 'npx @fadym/archlens-skill init' first.`
      }
    };
  }

  const numericErrors = validateNumericOptions(options);
  if (numericErrors.length > 0) {
    return {
      delegated: true,
      mode: 'validation',
      report: { passed: false, violationsCount: 0, violations: [], error: numericErrors.join(' ') }
    };
  }

  if (options.offline) {
    return { delegated: false, reason: 'offline-flag' };
  }
  if (options.maxFiles !== undefined && options.maxFiles !== null) {
    return { delegated: false, reason: 'max-files' };
  }
  if (!fs.existsSync(path.join(root, '.archlens', 'policy.json'))) {
    return { delegated: false, reason: 'legacy-policy-path' };
  }
  if (policy.lang && String(policy.lang).toLowerCase() === 'kotlin') {
    return { delegated: false, reason: 'unsupported-backend-lang' };
  }

  const fetchFn = deps.fetch || fetch;
  const healthChecker = deps.checkServerHealth || checkServerHealth;
  const resolveToken = deps.resolveSessionToken || resolveSessionToken;
  const findBinary = deps.findNativeBinary || findNativeBinary;
  const findJar = deps.findNativeJar || findNativeJar;
  const javaChecker = deps.isJavaAvailable || isJavaAvailable;
  const dockerChecker = deps.isDockerAvailable || isDockerAvailable;
  const portFinder = deps.findFreePort || findFreeLoopbackPort;
  const customSpawn = deps.spawn || spawn;
  const customSpawnSync = deps.spawnSync || spawnSync;

  const configuredServerUrl = options.serverUrl || DEFAULT_SERVER_URL;
  const hostHasSources = hasBackendSupportedSourceFiles(root, policy);

  // 1. Reuse an already-running live LOOPBACK backend if reachable and mounted on `root`
  if (!options.preferDocker && isSafeLoopbackServerUrl(configuredServerUrl)) {
    try {
      const health = await healthChecker(configuredServerUrl);
      if (health && health.ok) {
        const liveToken = resolveToken(configuredServerUrl, deps);
        const payload = await fetchBackendPayloads(
          configuredServerUrl,
          root,
          options,
          liveToken,
          fetchFn
        );
        if (
          payload &&
          payload.graph &&
          isLiveGraphMountedOnRoot(payload.graph, policy, root, realRoot, hostHasSources)
        ) {
          return {
            delegated: true,
            mode: 'live',
            report: mapBackendResultsToCliReport(
              root,
              policy,
              options,
              payload.graph,
              payload.fitness,
              payload.migrationProposal
            )
          };
        }
      }
    } catch {
      // Fall through to ephemeral local process
    }
  }

  // 2. Spawn an isolated ephemeral backend on a free loopback port and clean up in `finally`
  const preferDocker = Boolean(options.preferDocker);
  const binaryPath = !preferDocker
    ? findBinary({ ...options, path: root, workspace: root }, deps)
    : null;
  const jarPath =
    !preferDocker && !binaryPath
      ? findJar({ ...options, path: root, workspace: root }, deps)
      : null;
  const canSpawnJar = Boolean(jarPath && javaChecker(customSpawnSync));
  const canSpawnDocker = Boolean(preferDocker && dockerChecker(customSpawnSync));

  if (!binaryPath && !canSpawnJar && !canSpawnDocker) {
    return { delegated: false, reason: 'backend-unavailable' };
  }

  let freePort;
  try {
    freePort = await portFinder();
  } catch {
    return { delegated: false, reason: 'port-unavailable' };
  }

  const ephemeralUrl = `http://127.0.0.1:${freePort}`;
  const ephemeralToken = generateSessionToken();
  const launchEnv = serverLaunchEnv({ token: ephemeralToken });

  if (canSpawnDocker) {
    const containerName = `archlens-check-${crypto.randomBytes(4).toString('hex')}`;
    const image = options.image || DEFAULT_DOCKER_IMAGE;
    const unregisterSignals = registerSignalCleanup(() => {
      customSpawnSync('docker', ['rm', '-f', containerName], { stdio: 'pipe', timeout: 5000 });
    });
    try {
      const runRes = customSpawnSync(
        'docker',
        [
          'run',
          '-d',
          '--rm',
          '--name',
          containerName,
          '-p',
          `127.0.0.1:${freePort}:8088`,
          '-e',
          'ARCHLENS_TOKEN',
          '-v',
          `${root}:/workspace:ro`,
          image
        ],
        { stdio: 'pipe', timeout: 20000, env: launchEnv }
      );
      if (!runRes || runRes.error || runRes.status !== 0) {
        return { delegated: false, reason: 'docker-launch-failed' };
      }
      const ready = await waitForEphemeralBackend(
        ephemeralUrl,
        fetchFn,
        options.backendTimeoutMs || 25000
      );
      if (!ready) {
        return { delegated: false, reason: 'docker-timeout' };
      }
      const payload = await fetchBackendPayloads(
        ephemeralUrl,
        '/workspace',
        options,
        ephemeralToken,
        fetchFn
      );
      if (!payload || !payload.graph) {
        return { delegated: false, reason: 'docker-query-failed' };
      }
      return {
        delegated: true,
        mode: 'docker',
        report: mapBackendResultsToCliReport(
          root,
          policy,
          options,
          payload.graph,
          payload.fitness,
          payload.migrationProposal
        )
      };
    } finally {
      unregisterSignals();
      try {
        customSpawnSync('docker', ['rm', '-f', containerName], { stdio: 'pipe', timeout: 5000 });
      } catch {
        // ignore cleanup error
      }
    }
  }

  const cmd = binaryPath || 'java';
  const args = binaryPath
    ? [
        '-Dquarkus.http.host=127.0.0.1',
        `-Dquarkus.http.port=${freePort}`,
        '-Darchlens.container.workspace=/nonexistent-archlens-container'
      ]
    : [
        '-Dquarkus.http.host=127.0.0.1',
        `-Dquarkus.http.port=${freePort}`,
        '-Darchlens.container.workspace=/nonexistent-archlens-container',
        '-jar',
        jarPath
      ];

  let child = null;
  const earlyAbort = { exited: false };
  const unregisterSignals = registerSignalCleanup(() => {
    if (child && typeof child.exitCode !== 'number' && !child.killed) {
      child.kill('SIGTERM');
    }
  });
  try {
    child = customSpawn(cmd, args, {
      detached: false,
      stdio: 'ignore',
      env: launchEnv
    });
    child.once('error', () => {
      earlyAbort.exited = true;
    });
    child.once('exit', () => {
      earlyAbort.exited = true;
    });

    const ready = await waitForEphemeralBackend(
      ephemeralUrl,
      fetchFn,
      options.backendTimeoutMs || 25000,
      earlyAbort
    );
    if (!ready) {
      return { delegated: false, reason: 'backend-timeout' };
    }

    const payload = await fetchBackendPayloads(
      ephemeralUrl,
      root,
      options,
      ephemeralToken,
      fetchFn
    );
    if (!payload || !payload.graph) {
      return { delegated: false, reason: 'backend-query-failed' };
    }

    return {
      delegated: true,
      mode: binaryPath ? 'binary' : 'jar',
      report: mapBackendResultsToCliReport(
        root,
        policy,
        options,
        payload.graph,
        payload.fitness,
        payload.migrationProposal
      )
    };
  } catch {
    return { delegated: false, reason: 'backend-exception' };
  } finally {
    unregisterSignals();
    await terminateChildProcess(child);
  }
}
