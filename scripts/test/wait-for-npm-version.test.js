import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { parseArgs, viewVersion, waitForNpmVersion } from '../wait-for-npm-version.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const SCRIPT = path.join(REPO_ROOT, 'scripts', 'wait-for-npm-version.js');

function fakeClock() {
  let t = 0;
  return { now: () => t, sleep: async (ms) => { t += ms; } };
}

/** npm stub that 404s for the first `failures` calls, then returns `version`. */
function flakyExec(failures, version) {
  let calls = 0;
  const exec = () => {
    calls += 1;
    if (calls <= failures) {
      const err = new Error('npm ERR! code E404');
      err.status = 1;
      throw err;
    }
    return `${version}\n`;
  };
  exec.calls = () => calls;
  return exec;
}

describe('wait-for-npm-version (#233)', () => {
  it('retries through registry propagation delay instead of failing on the first E404', async () => {
    const clock = fakeClock();
    const exec = flakyExec(3, '1.1.5');
    const res = await waitForNpmVersion(
      { name: '@fadym/archlens-skill', version: '1.1.5', registry: 'r', timeoutSeconds: 120, initialDelaySeconds: 5, maxDelaySeconds: 30 },
      { execFileSync: exec, ...clock, log: () => {} }
    );
    assert.equal(res.ok, true);
    assert.equal(res.attempts, 4);
    assert.equal(exec.calls(), 4);
  });

  it('times out with a failure after the configured window', async () => {
    const clock = fakeClock();
    const logs = [];
    const res = await waitForNpmVersion(
      { name: 'pkg', version: '9.9.9', registry: 'r', timeoutSeconds: 60, initialDelaySeconds: 5, maxDelaySeconds: 30 },
      { execFileSync: flakyExec(Infinity, ''), ...clock, log: (m) => logs.push(m) }
    );
    assert.equal(res.ok, false);
    assert.ok(clock.now() <= 60000, 'never sleeps past the deadline');
    assert.ok(logs.at(-1).startsWith('::error::'));
  });

  it('backs off exponentially up to the max delay', async () => {
    const sleeps = [];
    let t = 0;
    await waitForNpmVersion(
      { name: 'pkg', version: '1.0.0', registry: 'r', timeoutSeconds: 1000, initialDelaySeconds: 5, maxDelaySeconds: 30 },
      {
        execFileSync: flakyExec(5, '1.0.0'),
        now: () => t,
        sleep: async (ms) => { sleeps.push(ms); t += ms; },
        log: () => {}
      }
    );
    assert.deepEqual(sleeps, [5000, 10000, 20000, 30000, 30000]);
  });

  it('does not accept a different version than requested', () => {
    assert.equal(viewVersion('pkg', '1.0.0', 'r', { execFileSync: () => '1.0.0\n' }), '1.0.0');
    assert.equal(viewVersion('pkg', '1.0.0', 'r', { execFileSync: () => '' }), null);
  });

  it('parses and validates arguments', () => {
    const o = parseArgs(['pkg', '1.2.3', '--registry', 'https://x/', '--timeout-seconds', '10']);
    assert.equal(o.name, 'pkg');
    assert.equal(o.version, '1.2.3');
    assert.equal(o.registry, 'https://x/');
    assert.equal(o.timeoutSeconds, 10);
    assert.throws(() => parseArgs(['pkg']), /Usage/);
    assert.throws(() => parseArgs(['pkg', '1', '--timeout-seconds', 'abc']), /non-negative/);
    assert.throws(() => parseArgs(['pkg', '1', '--bogus']), /Unknown option/);
  });

  it('CLI exits 0 once a stubbed npm starts returning the version', { skip: process.platform === 'win32' }, () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'npm-stub-'));
    const counter = path.join(dir, 'count');
    const stub = path.join(dir, 'npm');
    fs.writeFileSync(
      stub,
      `#!/bin/sh\nn=$(cat "${counter}" 2>/dev/null || echo 0); n=$((n+1)); echo $n > "${counter}"\n` +
        `if [ $n -lt 3 ]; then echo "npm ERR! code E404" >&2; exit 1; fi\necho 1.1.5\n`,
      { mode: 0o755 }
    );
    const ok = spawnSync(process.execPath, [SCRIPT, 'pkg', '1.1.5', '--initial-delay-seconds', '0', '--timeout-seconds', '10'], {
      env: { ...process.env, NPM_BIN: stub },
      encoding: 'utf8'
    });
    assert.equal(ok.status, 0, ok.stdout + ok.stderr);
    assert.equal(fs.readFileSync(counter, 'utf8').trim(), '3');

    const usage = spawnSync(process.execPath, [SCRIPT], { encoding: 'utf8' });
    assert.equal(usage.status, 2);
  });

  it('release workflow publishes only from tags and waits for propagation', () => {
    const wf = fs.readFileSync(path.join(REPO_ROOT, '.github', 'workflows', 'release.yml'), 'utf8');
    const publish = wf.slice(wf.indexOf('- name: Publish @fadym/archlens-skill to Public NPM Registry'));
    const nextStep = publish.indexOf('\n      - name:', 10);
    const step = nextStep === -1 ? publish : publish.slice(0, nextStep);
    assert.match(step, /if: \$\{\{ startsWith\(github\.ref, 'refs\/tags\/'\)/);
    assert.match(step, /node scripts\/wait-for-npm-version\.js/);
    assert.doesNotMatch(step, /PUBLISHED_VER=\$\(npm view/, 'single-shot verification must be gone');
  });
});
