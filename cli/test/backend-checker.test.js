import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { EventEmitter } from 'node:events';

import { parseArgs, main } from '../bin/index.js';
import {
  checkArchitectureViaBackend,
  findFreeLoopbackPort,
  hasRustOrGoSources,
  mapBackendResultsToCliReport
} from '../src/backend-checker.js';
import { resolveSessionToken, writeSessionFile } from '../src/session.js';

function createPolicyFixture(prefix = 'archlens-backend-check-', extraFiles = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  fs.mkdirSync(path.join(dir, '.archlens'), { recursive: true });
  fs.mkdirSync(path.join(dir, 'src', 'domain'), { recursive: true });
  fs.mkdirSync(path.join(dir, 'src', 'adapters'), { recursive: true });
  const policy = {
    title: 'Parity Demo',
    src: 'src',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  };
  fs.writeFileSync(path.join(dir, '.archlens', 'policy.json'), JSON.stringify(policy), 'utf8');
  fs.writeFileSync(
    path.join(dir, 'src', 'domain', 'OrderService.java'),
    'package domain;\nimport adapters.PostgresRepo;\npublic class OrderService {}\n',
    'utf8'
  );
  fs.writeFileSync(
    path.join(dir, 'src', 'adapters', 'PostgresRepo.java'),
    'package adapters;\npublic class PostgresRepo {}\n',
    'utf8'
  );
  for (const [rel, content] of Object.entries(extraFiles)) {
    const full = path.join(dir, rel);
    fs.mkdirSync(path.dirname(full), { recursive: true });
    fs.writeFileSync(full, content, 'utf8');
  }
  return dir;
}

function sampleBackendGraph(dir, overrides = {}) {
  return {
    title: 'Parity Demo',
    isProposal: false,
    activeProposalId: null,
    components: [
      {
        id: 'domain',
        label: 'domain',
        level: 0,
        childPackageIds: ['domain'],
        classes: [
          {
            id: 'domain.OrderService',
            name: 'OrderService',
            packageName: 'domain',
            filePath: path.join(dir, 'src', 'domain', 'OrderService.java'),
            stereotype: 'CLASS',
            isForeign: false
          }
        ]
      },
      {
        id: 'adapters',
        label: 'adapters',
        level: 1,
        childPackageIds: ['adapters'],
        classes: [
          {
            id: 'adapters.PostgresRepo',
            name: 'PostgresRepo',
            packageName: 'adapters',
            filePath: path.join(dir, 'src', 'adapters', 'PostgresRepo.java'),
            stereotype: 'CLASS',
            isForeign: false
          }
        ]
      }
    ],
    edges: [
      {
        from: 'domain.OrderService',
        to: 'adapters.PostgresRepo',
        kind: 'DEPENDENCY',
        label: null,
        isViolating: true
      },
      // Duplicate module-level mirror edge (as emitted by TS lexical scanner) must be deduplicated
      {
        from: 'domain',
        to: 'adapters',
        kind: 'DEPENDENCY',
        label: null,
        isViolating: true
      }
    ],
    cycles: [],
    screamingMetric: {
      score: 0.5,
      domainPackageCount: 1,
      technicalPackageCount: 1,
      totalPackageCount: 2,
      classification: 'HYBRID',
      domainPackages: ['domain'],
      technicalPackages: ['adapters'],
      frameworkGravityHotspots: []
    },
    skippedFiles: 0,
    ...overrides
  };
}

function sampleBackendFitness(score = 0.74) {
  return {
    fitnessScore: score,
    overallPassed: false,
    grade: 'C',
    passedRuleCount: 3,
    totalRuleCount: 4,
    rules: [
      {
        id: 'CONCENTRIC_DEPENDENCY_RULE',
        name: 'Concentric Dependency Rule',
        threshold: 0,
        actualValue: 1,
        passed: false
      }
    ]
  };
}

test('parseArgs parses --offline flag cleanly', () => {
  assert.equal(parseArgs(['check']).offline, false);
  assert.equal(parseArgs(['check', '--offline']).offline, true);
});

test('checkArchitectureViaBackend bypasses backend on --offline, --max-files, and validates missing policy / invalid numeric flags', async () => {
  const dir = createPolicyFixture('archlens-bc-bypass-');
  const emptyDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bc-empty-'));
  try {
    const offlineRes = await checkArchitectureViaBackend(dir, { offline: true });
    assert.equal(offlineRes.delegated, false);
    assert.equal(offlineRes.reason, 'offline-flag');

    const maxFilesRes = await checkArchitectureViaBackend(dir, { maxFiles: 5 });
    assert.equal(maxFilesRes.delegated, false);
    assert.equal(maxFilesRes.reason, 'max-files');

    const missingRes = await checkArchitectureViaBackend(emptyDir, {});
    assert.equal(missingRes.delegated, true);
    assert.match(missingRes.report.error, /No Archlens policy found/);

    const badNumRes = await checkArchitectureViaBackend(dir, { maxViolations: '"0"' });
    assert.equal(badNumRes.delegated, true);
    assert.match(badNumRes.report.error, /Invalid value for --max-violations/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
    fs.rmSync(emptyDir, { recursive: true, force: true });
  }
});

test('checkArchitectureViaBackend reuses live authenticated server and maps graph, fitness, cycles, screaming, and featureClusters', async () => {
  const dir = createPolicyFixture('archlens-bc-live-');
  fs.symlinkSync(
    path.join(dir, 'missing-target.java'),
    path.join(dir, 'src', 'domain', 'BrokenLink.java')
  );

  try {
    const seenHeaders = [];
    const mockFetch = async (url, init = {}) => {
      seenHeaders.push(init.headers || {});
      const u = new URL(url);
      if (u.pathname === '/api/graph') {
        return {
          ok: true,
          status: 200,
          json: async () =>
            sampleBackendGraph(dir, {
              cycles: [{ packages: ['domain', 'adapters', 'domain'] }],
              skippedFiles: 1
            })
        };
      }
      if (u.pathname === '/api/fitness') {
        return { ok: true, status: 200, json: async () => sampleBackendFitness(0.74) };
      }
      if (u.pathname === '/api/screaming/migration-proposal') {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            currentScore: 0.5,
            projectedScore: 1.0,
            currentClassification: 'HYBRID',
            projectedClassification: 'PACKAGE_BY_FEATURE',
            clusters: [
              {
                featureName: 'order',
                proposedPackageName: 'order',
                classNames: ['OrderService'],
                sourcePackages: ['domain'],
                classCount: 1
              }
            ]
          })
        };
      }
      throw new Error(`Unexpected URL: ${url}`);
    };

    const res = await checkArchitectureViaBackend(
      dir,
      {
        serverUrl: 'http://127.0.0.1:8088',
        maxViolations: 0,
        detectCycles: true,
        screamingThreshold: 0.7,
        fitnessThreshold: 0.85,
        suggestFeatures: true
      },
      {
        checkServerHealth: async () => ({ ok: true, version: '1.2.0' }),
        resolveSessionToken: () => 'live-tok-123',
        fetch: mockFetch
      }
    );

    assert.equal(res.delegated, true);
    assert.equal(res.mode, 'live');
    assert.ok(seenHeaders.every((h) => h['X-Archlens-Token'] === 'live-tok-123'));

    const { report } = res;
    assert.equal(report.passed, false);
    assert.equal(report.engine, 'backend');
    assert.equal(report.violationsCount, 1, 'duplicate module mirror edge must be deduplicated');
    assert.equal(report.violations[0].fromPkg, 'domain');
    assert.equal(report.violations[0].toPkg, 'adapters');
    assert.equal(report.violations[0].fromFile, path.join('src', 'domain', 'OrderService.java'));
    assert.equal(report.violations[0].portName, 'OrderServicePort');
    assert.equal(report.violations[0].line, 2);
    assert.equal(report.cyclesCount, 1);
    assert.equal(report.cycles[0].formatted, 'domain -> adapters -> domain');
    assert.equal(report.skippedFiles, 1);
    assert.ok(report.skippedFilePaths.some((p) => p.endsWith('BrokenLink.java')));
    assert.equal(report.featureClusters.clusters.length, 1);
    assert.equal(report.fitness.grade, 'C');
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('checkArchitectureViaBackend rejects unmounted live container (0 classes) and spawns + kills ephemeral JAR in finally', async () => {
  const dir = createPolicyFixture('archlens-bc-ephemeral-jar-');
  try {
    let killedSignal = null;
    let spawnArgsCaptured = null;

    const fakeChild = new EventEmitter();
    fakeChild.kill = (sig) => {
      killedSignal = sig;
      setImmediate(() => fakeChild.emit('exit', 0, sig));
      return true;
    };

    const res = await checkArchitectureViaBackend(
      dir,
      { maxViolations: 1 },
      {
        checkServerHealth: async () => ({ ok: true, version: '1.2.0' }),
        resolveSessionToken: () => 'container-tok',
        findFreePort: async () => 19088,
        findNativeBinary: () => null,
        findNativeJar: () => '/opt/archlens/quarkus-run.jar',
        isJavaAvailable: () => true,
        spawn: (cmd, args, opts) => {
          spawnArgsCaptured = { cmd, args, env: opts.env };
          return fakeChild;
        },
        fetch: async (url) => {
          if (url.startsWith('http://127.0.0.1:8088/api/graph')) {
            // Unmounted container sees 0 classes -> must not trust!
            return {
              ok: true,
              status: 200,
              json: async () => ({ title: 'Clean Architecture', components: [], edges: [] })
            };
          }
          if (url === 'http://127.0.0.1:19088/api/version') {
            return { ok: true, status: 200, json: async () => ({ version: '1.2.0' }) };
          }
          if (url.startsWith('http://127.0.0.1:19088/api/graph')) {
            return { ok: true, status: 200, json: async () => sampleBackendGraph(dir) };
          }
          if (url.startsWith('http://127.0.0.1:19088/api/fitness')) {
            return { ok: true, status: 200, json: async () => sampleBackendFitness(0.92) };
          }
          throw new Error(`Unexpected fetch: ${url}`);
        }
      }
    );

    assert.equal(res.delegated, true);
    assert.equal(res.mode, 'jar');
    assert.equal(res.report.passed, true);
    assert.equal(spawnArgsCaptured.cmd, 'java');
    assert.ok(spawnArgsCaptured.args.includes('-Dquarkus.http.port=19088'));
    assert.ok(spawnArgsCaptured.env.ARCHLENS_TOKEN.length >= 32);
    assert.equal(killedSignal, 'SIGTERM', 'ephemeral child process must be killed in finally');
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('checkArchitectureViaBackend supports ephemeral native binary and --docker cleanup, and falls back cleanly on unavailability', async () => {
  const dir = createPolicyFixture('archlens-bc-modes-');
  try {
    // 1. Native binary mode + error still kills child in finally
    let binKilled = false;
    const binChild = new EventEmitter();
    binChild.kill = () => {
      binKilled = true;
      setImmediate(() => binChild.emit('exit', 0, 'SIGTERM'));
      return true;
    };

    const binFallback = await checkArchitectureViaBackend(
      dir,
      {},
      {
        checkServerHealth: async () => ({ ok: false }),
        findFreePort: async () => 19089,
        findNativeBinary: () => '/opt/archlens/archlens-runner',
        spawn: () => binChild,
        fetch: async (url) => {
          if (url.endsWith('/api/version')) return { ok: true, status: 200 };
          return { ok: false, status: 500 };
        }
      }
    );
    assert.equal(binFallback.delegated, false);
    assert.equal(binKilled, true, 'native binary child must be killed even when graph API fails');

    // 2. Explicit --docker ephemeral container mode cleans up via docker rm -f in finally
    const dockerCalls = [];
    const dockerRes = await checkArchitectureViaBackend(
      dir,
      { preferDocker: true, maxViolations: 1 },
      {
        checkServerHealth: async () => ({ ok: false }),
        findFreePort: async () => 19090,
        isDockerAvailable: () => true,
        spawnSync: (cmd, args) => {
          dockerCalls.push({ cmd, args });
          return { status: 0, stdout: 'cid123\n', stderr: '' };
        },
        fetch: async (url) => {
          if (url.endsWith('/api/version')) return { ok: true, status: 200 };
          if (url.includes('/api/graph')) {
            return {
              ok: true,
              status: 200,
              json: async () => {
                const g = sampleBackendGraph('/workspace');
                return g;
              }
            };
          }
          if (url.includes('/api/fitness')) {
            return { ok: true, status: 200, json: async () => sampleBackendFitness(0.9) };
          }
          throw new Error(`Unexpected URL: ${url}`);
        }
      }
    );
    assert.equal(dockerRes.delegated, true);
    assert.equal(dockerRes.mode, 'docker');
    assert.equal(
      dockerRes.report.violations[0].fromFile,
      path.join('src', 'domain', 'OrderService.java'),
      'container /workspace/... paths must rebase cleanly to host relative paths'
    );
    assert.equal(dockerRes.report.violations[0].portName, 'OrderServicePort');
    assert.equal(dockerRes.report.violations[0].line, 2);
    assert.ok(dockerCalls.some((c) => c.args[0] === 'run' && c.args.includes('--rm')));
    assert.ok(dockerCalls.some((c) => c.args[0] === 'rm' && c.args[1] === '-f'));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('security & edge-case guards: blocks non-loopback token exfiltration, confines traversal filePaths, maps nested/proposal packages, rejects cross-project containers, and handles --offline Go/Rust warnings', async () => {
  const dir = createPolicyFixture('archlens-bc-sec-');
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bc-home-'));
  writeSessionFile('8088', 'secret-local-session-token', { homedir: tmpHome });

  try {
    // 1. resolveSessionToken must NEVER read local session file for non-loopback serverUrl
    assert.equal(
      resolveSessionToken('http://attacker.example:8088', { homedir: tmpHome, env: {} }),
      null
    );
    assert.equal(
      resolveSessionToken('http://127.0.0.1:8088', { homedir: tmpHome, env: {} }),
      'secret-local-session-token'
    );

    // 2. checkArchitectureViaBackend must refuse probing non-loopback --server-url
    let remoteFetched = false;
    const remoteAttempt = await checkArchitectureViaBackend(
      dir,
      { serverUrl: 'http://169.254.169.254:8088' },
      {
        homedir: tmpHome,
        env: {},
        findNativeBinary: () => null,
        findNativeJar: () => null,
        checkServerHealth: async () => {
          remoteFetched = true;
          return { ok: true };
        },
        fetch: async () => {
          remoteFetched = true;
          throw new Error('should not fetch remote serverUrl');
        }
      }
    );
    assert.equal(remoteAttempt.delegated, false);
    assert.equal(remoteFetched, false, 'non-loopback serverUrl must not be probed');

    // 3. Traversal filePath in backend payload must never escape root or read outside root
    const outsideSecret = path.join(tmpHome, 'outside-secret.java');
    fs.writeFileSync(outsideSecret, 'import adapters.PostgresRepo;\n', 'utf8');
    const relEscape = path.relative(dir, outsideSecret);
    const escapedReport = mapBackendResultsToCliReport(
      dir,
      { title: 'Esc', src: 'src', order: ['core', 'service'], levels: [['core'], ['service']] },
      { maxViolations: 0 },
      {
        title: 'Esc',
        isProposal: true,
        components: [
          {
            id: 'domain-ring',
            label: 'Domain Ring',
            level: 0,
            childPackageIds: ['core'],
            classes: [
              {
                id: 'core.orderservice.OrderHandler',
                name: 'OrderHandler',
                packageName: 'core.orderservice',
                filePath: relEscape,
                stereotype: 'CLASS',
                isForeign: false
              }
            ]
          },
          {
            id: 'service-ring',
            label: 'Service Ring',
            level: 1,
            childPackageIds: ['service'],
            classes: [
              {
                id: 'service.BillingAdapter',
                name: 'BillingAdapter',
                packageName: 'service',
                filePath: 'src/adapters/PostgresRepo.java',
                stereotype: 'CLASS',
                isForeign: false
              }
            ]
          }
        ],
        edges: [
          {
            from: 'core.orderservice.OrderHandler',
            to: 'service.BillingAdapter',
            kind: 'DEPENDENCY',
            isViolating: true
          }
        ],
        cycles: []
      }
    );
    assert.ok(
      !escapedReport.violations[0].fromFile.includes('..'),
      `Expected confined fallback fromFile, got: ${escapedReport.violations[0].fromFile}`
    );
    assert.equal(
      escapedReport.violations[0].fromPkg,
      'core',
      'core.orderservice must map to root-prefix core (L0), not substring service (L1)'
    );
    assert.equal(escapedReport.violations[0].fromTier, 'Domain Core (L0)');

    // 4. Legacy <root>/policy.json (without .archlens/policy.json) must bypass backend delegation
    const legacyDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bc-legacy-'));
    try {
      fs.writeFileSync(
        path.join(legacyDir, 'policy.json'),
        JSON.stringify({ title: 'Legacy', src: 'src', levels: [['domain']] }),
        'utf8'
      );
      const legacyRes = await checkArchitectureViaBackend(legacyDir, {});
      assert.equal(legacyRes.delegated, false);
      assert.equal(legacyRes.reason, 'legacy-policy-path');
    } finally {
      fs.rmSync(legacyDir, { recursive: true, force: true });
    }

    // 5. Cross-project live container returning foreign /workspace files not in `dir` must be rejected
    const crossRes = await checkArchitectureViaBackend(
      dir,
      {},
      {
        checkServerHealth: async () => ({ ok: true }),
        resolveSessionToken: () => 'tok',
        findNativeBinary: () => null,
        findNativeJar: () => null,
        fetch: async (url) => {
          if (url.includes('/api/graph')) {
            return {
              ok: true,
              status: 200,
              json: async () => ({
                title: 'Parity Demo',
                components: [
                  {
                    id: 'domain',
                    level: 0,
                    classes: [
                      {
                        id: 'other.App',
                        name: 'App',
                        packageName: 'domain',
                        filePath: '/workspace/nonexistent-other-project/App.java',
                        isForeign: false
                      }
                    ]
                  }
                ],
                edges: []
              })
            };
          }
          return { ok: true, status: 200, json: async () => sampleBackendFitness(1.0) };
        }
      }
    );
    assert.equal(crossRes.delegated, false, 'cross-project unmounted live container must be rejected');

    // 6. --offline suppresses INFO notice while still emitting Go/Rust WARN on stderr only
    const goDir = createPolicyFixture('archlens-bc-go-', {
      'src/domain/service.go': 'package domain\n'
    });
    const stderrOut = [];
    const stdoutOut = [];
    const origErr = process.stderr.write;
    const origOut = process.stdout.write;
    try {
      process.stderr.write = (c) => {
        stderrOut.push(String(c));
        return true;
      };
      process.stdout.write = (c) => {
        stdoutOut.push(String(c));
        return true;
      };
      let code = null;
      await main(['check', '--offline', '-p', goDir, '--max-violations', '5', '--format', 'sarif'], {
        exit: (c) => {
          code = c;
        }
      });
      assert.equal(code, 0);
      assert.doesNotMatch(stderrOut.join(''), /falling back to offline/i);
      assert.match(stderrOut.join(''), /Rust \(\.rs\) or Go \(\.go\)/);
      const sarif = JSON.parse(stdoutOut.join(''));
      assert.equal(sarif.version, '2.1.0');
      assert.ok(!sarif.runs[0].results[0].locations[0].physicalLocation.artifactLocation.uri.includes('\\'));
    } finally {
      process.stderr.write = origErr;
      process.stdout.write = origOut;
      fs.rmSync(goDir, { recursive: true, force: true });
    }
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

test('main(check) delegates to backend first and emits stderr-only notices + Rust/Go warnings on offline fallback', async () => {
  const dir = createPolicyFixture('archlens-bc-main-', {
    'src/domain/lib.rs': 'pub struct DomainOrder;\n'
  });

  const stderrChunks = [];
  const stdoutChunks = [];
  const origStderr = process.stderr.write;
  const origStdout = process.stdout.write;

  try {
    process.stderr.write = (chunk) => {
      stderrChunks.push(String(chunk));
      return true;
    };
    process.stdout.write = (chunk) => {
      stdoutChunks.push(String(chunk));
      return true;
    };

    let code = null;
    await main(['check', '-p', dir, '--max-violations', '5', '--format', 'json'], {
      exit: (c) => {
        code = c;
      },
      checkArchitectureViaBackend: async () => ({
        delegated: false,
        reason: 'backend-unavailable'
      })
    });

    assert.equal(code, 0);
    // stdout must remain valid JSON with zero log pollution
    const parsed = JSON.parse(stdoutChunks.join(''));
    assert.equal(parsed.passed, true);

    const allStderr = stderrChunks.join('');
    assert.match(allStderr, /falling back to offline JS lexical checker/i);
    assert.match(allStderr, /Rust \(\.rs\) or Go \(\.go\)/);
  } finally {
    process.stderr.write = origStderr;
    process.stdout.write = origStdout;
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('covers multi-file Go import line attribution, suggestFeatures/fitness fallbacks, polling retry, and Kotlin policy reason', async () => {
  assert.equal(hasRustOrGoSources('.', { lang: 'rust' }), true);
  try {
    const port = await findFreeLoopbackPort();
    assert.ok(Number.isInteger(port) && port > 0);
  } catch {
    // sandbox may restrict socket listen
  }

  const goDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bc-multigo-'));
  try {
    fs.mkdirSync(path.join(goDir, '.archlens'), { recursive: true });
    fs.mkdirSync(path.join(goDir, 'internal', 'domain'), { recursive: true });
    fs.mkdirSync(path.join(goDir, 'internal', 'adapters'), { recursive: true });
    const goPolicy = {
      title: 'Go Multi',
      src: '.',
      lang: 'go',
      order: ['domain', 'adapters'],
      levels: [['domain'], ['adapters']]
    };
    fs.writeFileSync(path.join(goDir, '.archlens', 'policy.json'), JSON.stringify(goPolicy));
    fs.writeFileSync(path.join(goDir, 'internal', 'domain', 'helper.go'), 'package domain\ntype Helper struct{}\n');
    fs.writeFileSync(
      path.join(goDir, 'internal', 'domain', 'service.go'),
      '/*\nblock comment\n*/\npackage domain\nimport (\n\t"example.com/app/internal/adapters"\n)\ntype OrderService struct{}\n'
    );
    fs.writeFileSync(path.join(goDir, 'internal', 'adapters', 'repo.go'), 'package adapters\ntype OrderRepo struct{}\n');

    const report = mapBackendResultsToCliReport(
      goDir,
      goPolicy,
      { maxViolations: 0, suggestFeatures: true },
      {
        title: 'Go Multi',
        components: [
          {
            id: 'internal.domain',
            level: 0,
            childPackageIds: ['internal.domain'],
            classes: [
              { id: 'internal.domain.Helper', name: 'Helper', packageName: 'internal.domain', filePath: 'internal/domain/helper.go', isForeign: false },
              { id: 'internal.domain.OrderService', name: 'OrderService', packageName: 'internal.domain', filePath: 'internal/domain/service.go', isForeign: false }
            ]
          },
          {
            id: 'internal.adapters',
            level: 1,
            childPackageIds: ['internal.adapters'],
            classes: [
              { id: 'internal.adapters.OrderRepo', name: 'OrderRepo', packageName: 'internal.adapters', filePath: 'internal/adapters/repo.go', isForeign: false }
            ]
          }
        ],
        edges: [{ from: 'internal.domain', to: 'internal.adapters', kind: 'DEPENDENCY', isViolating: true }],
        cycles: []
      },
      null,
      null
    );
    assert.equal(report.violations[0].fromFile, path.join('internal', 'domain', 'service.go'));
    assert.equal(report.violations[0].line, 6);
    assert.ok(report.fitness);
    assert.ok(report.featureClusters);

    // Retry polling on /api/version before succeeding
    let versionAttempts = 0;
    const retryChild = new EventEmitter();
    retryChild.kill = () => {
      setImmediate(() => retryChild.emit('exit', 0, 'SIGTERM'));
      return true;
    };
    const retryRes = await checkArchitectureViaBackend(
      goDir,
      { maxViolations: 5 },
      {
        checkServerHealth: async () => ({ ok: false }),
        findFreePort: async () => 19100,
        findNativeBinary: () => '/opt/archlens/bin',
        spawn: () => retryChild,
        fetch: async (url) => {
          if (url.endsWith('/api/version')) {
            versionAttempts += 1;
            if (versionAttempts === 1) throw new Error('ECONNREFUSED');
            return { ok: true, status: 200 };
          }
          if (url.includes('/api/graph')) {
            return { ok: true, status: 200, json: async () => ({ title: 'Go Multi', components: [], edges: [] }) };
          }
          return { ok: false, status: 404 };
        }
      }
    );
    assert.equal(retryRes.delegated, true);
    assert.equal(versionAttempts, 2);

    // Kotlin policy bypasses backend with tailored stderr reason
    const ktDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bc-kt-'));
    try {
      fs.mkdirSync(path.join(ktDir, '.archlens'), { recursive: true });
      fs.writeFileSync(
        path.join(ktDir, '.archlens', 'policy.json'),
        JSON.stringify({ title: 'Kt', src: 'src', lang: 'kotlin', levels: [['domain']] })
      );
      const ktRes = await checkArchitectureViaBackend(ktDir, {});
      assert.equal(ktRes.delegated, false);
      assert.equal(ktRes.reason, 'unsupported-backend-lang');
    } finally {
      fs.rmSync(ktDir, { recursive: true, force: true });
    }
  } finally {
    fs.rmSync(goDir, { recursive: true, force: true });
  }
});
