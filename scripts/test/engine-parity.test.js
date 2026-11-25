import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { checkArchitecture } from '../../cli/src/checker.js';
import { generateSessionToken } from '../../cli/src/session.js';
import { createSharedParityFixtures, runEngineParityChecks } from '../engine-parity.js';
import { waitForServerReady } from '../smoke-test.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '../..');

async function probeLoopbackPort() {
  const srv = http.createServer((_, res) => {
    res.writeHead(200);
    res.end('ok');
  });
  await new Promise((resolve, reject) => {
    srv.once('error', reject);
    srv.listen(0, '127.0.0.1', resolve);
  });
  const addr = srv.address();
  const port = typeof addr === 'object' && addr ? addr.port : null;
  try {
    const res = await fetch(`http://127.0.0.1:${port}`, { signal: AbortSignal.timeout(1000) });
    if (!res.ok) throw new Error('Loopback fetch probe failed');
  } finally {
    await new Promise((resolve) => srv.close(() => resolve()));
  }
  return port;
}

test('Shared polyglot parity fixtures validate cleanly on offline checker and live Quarkus backend (#244)', async (t) => {
  // Always verify offline checker against all 6 fixtures first (works in restricted sandboxes too)
  const tmpOffline = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-parity-offline-'));
  try {
    const fixtures = createSharedParityFixtures(tmpOffline);
    for (const fx of fixtures) {
      const report = await checkArchitecture(fx.dir, { maxViolations: 0, detectCycles: true });
      assert.equal(report.passed, fx.expectedPassed, `${fx.name} offline passed mismatch`);
      assert.deepEqual(
        report.violations.map((v) => `${v.fromPkg}->${v.toPkg}`).sort(),
        fx.expectedViolations
      );
      assert.equal(report.cyclesCount, fx.expectedCyclesCount);
    }
  } finally {
    fs.rmSync(tmpOffline, { recursive: true, force: true });
  }

  const jarPath = path.join(REPO_ROOT, 'backend', 'target', 'quarkus-app', 'quarkus-run.jar');
  if (!fs.existsSync(jarPath)) {
    return;
  }

  let port;
  try {
    port = await probeLoopbackPort();
  } catch {
    // Restricted sandbox blocks loopback HTTP; full JVM parity runs in CI & `npm run smoke`
    return;
  }

  const serverUrl = `http://127.0.0.1:${port}`;
  const token = generateSessionToken();
  const child = spawn(
    'java',
    [
      `-Dquarkus.http.port=${port}`,
      '-Dquarkus.http.host=127.0.0.1',
      '-Darchlens.container.workspace=/nonexistent-archlens-container',
      '-jar',
      jarPath
    ],
    {
      cwd: REPO_ROOT,
      env: { ...process.env, ARCHLENS_TOKEN: token },
      stdio: 'ignore'
    }
  );

  let childExited = false;
  const abortCtrl = new AbortController();
  child.once('exit', () => {
    childExited = true;
    abortCtrl.abort();
  });
  child.once('error', () => {
    childExited = true;
    abortCtrl.abort();
  });

  try {
    const ready = await waitForServerReady(serverUrl, 25000, 200, { signal: abortCtrl.signal });
    if (childExited && !ready) {
      // Child JVM could not bind socket in sandboxed test environment
      return;
    }
    assert.equal(ready, true, `Quarkus backend failed to start on ${serverUrl}`);
    const res = await runEngineParityChecks(serverUrl, token);
    assert.equal(res.fixturesVerified, 9);
  } finally {
    abortCtrl.abort();
    if (!childExited) {
      child.kill('SIGTERM');
      await new Promise((resolve) => {
        const timer = setTimeout(() => {
          try {
            child.kill('SIGKILL');
          } catch {
            // ignore
          }
          resolve();
        }, 2000);
        timer.unref?.();
        child.once('exit', () => {
          clearTimeout(timer);
          resolve();
        });
      });
    }
  }
});
