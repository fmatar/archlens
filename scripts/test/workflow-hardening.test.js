import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const read = (rel) => fs.readFileSync(path.join(REPO_ROOT, rel), 'utf8');
const skip = process.platform === 'win32';

/** Returns the dedented `run: |` block of the workflow step whose name contains `stepName`. */
function extractRunBlock(workflow, stepName) {
  const lines = workflow.split('\n');
  const start = lines.findIndex((l) => l.includes('- name:') && l.includes(stepName));
  assert.ok(start >= 0, `step ${stepName} not found`);
  const runIdx = lines.findIndex((l, i) => i > start && /^\s+run: \|\s*$/.test(l));
  const indent = lines[runIdx + 1].match(/^\s*/)[0].length;
  const body = [];
  for (let i = runIdx + 1; i < lines.length; i++) {
    const l = lines[i];
    if (l.trim() !== '' && l.match(/^\s*/)[0].length < indent) break;
    body.push(l.slice(indent));
  }
  return body.join('\n');
}

function stubBin(dir, name, script) {
  const p = path.join(dir, name);
  fs.writeFileSync(p, `#!/bin/sh\n${script}\n`, { mode: 0o755 });
  return p;
}

describe('release: floating major tag for the Action (#231)', { skip }, () => {
  function runMoveTag(releaseTag) {
    const block = extractRunBlock(read('.github/workflows/release.yml'), 'Point the major tag');
    const bin = fs.mkdtempSync(path.join(os.tmpdir(), 'git-stub-'));
    const log = path.join(bin, 'git.log');
    stubBin(bin, 'git', `echo "$@" >> "${log}"; case "$1" in rev-parse) echo abc1234;; esac`);
    const res = spawnSync('bash', ['-e', '-c', block], {
      env: { PATH: `${bin}:/usr/bin:/bin`, RELEASE_TAG: releaseTag },
      encoding: 'utf8'
    });
    const calls = fs.existsSync(log) ? fs.readFileSync(log, 'utf8').trim().split('\n') : [];
    return { res, calls };
  }

  it('moves v1 to a stable v1.x.y release', () => {
    const { res, calls } = runMoveTag('v1.1.5');
    assert.equal(res.status, 0, res.stderr);
    assert.ok(calls.includes('tag -f v1 v1.1.5^{commit}'), calls.join('\n'));
    assert.ok(calls.includes('push -f origin refs/tags/v1'), calls.join('\n'));
  });

  it('moves v2 for v2 releases and never touches tags for pre-releases', () => {
    assert.ok(runMoveTag('v2.0.0').calls.includes('push -f origin refs/tags/v2'));
    for (const tag of ['v1.2.0-rc.1', '1.1.5', 'v1.1']) {
      const { res, calls } = runMoveTag(tag);
      assert.equal(res.status, 0);
      assert.equal(calls.filter((c) => c.startsWith('tag') || c.startsWith('push')).length, 0, tag);
    }
  });

  it('job is gated to stable v-tags', () => {
    const wf = read('.github/workflows/release.yml');
    assert.match(wf, /move-major-tag:[\s\S]*?if: startsWith\(github\.ref, 'refs\/tags\/v'\) && !contains\(github\.ref_name, '-'\)/);
  });
});

describe('code scanning and Dependabot config (#236)', () => {
  it('CI uploads the Archlens SARIF to code scanning with the needed permission', () => {
    const ci = read('.github/workflows/ci.yml');
    assert.match(ci, /security-events: write/);
    assert.match(ci, /uses: github\/codeql-action\/upload-sarif@v4[\s\S]*?sarif_file: archlens-results\.sarif/);
  });

  it('Dependabot is the single dependency updater (the custom Dependency Pulse is gone, #234)', () => {
    assert.equal(fs.existsSync(path.join(REPO_ROOT, '.github', 'workflows', 'dependency-pulse.yml')), false);
    const workflows = fs.readdirSync(path.join(REPO_ROOT, '.github', 'workflows'));
    for (const wf of workflows) {
      assert.doesNotMatch(read(`.github/workflows/${wf}`), /update-dependencies\.js|ensure-gh\.sh/, wf);
    }
  });

  it('dependabot.yml covers every manifest and targets develop', () => {
    const cfg = read('.github/dependabot.yml');
    assert.match(cfg, /^version: 2$/m);
    for (const eco of ['maven', 'npm', 'github-actions', 'docker']) {
      assert.match(cfg, new RegExp(`package-ecosystem: ${eco}$`, 'm'), eco);
    }
    for (const dir of ['/cli', '/frontend', '/.github/actions/setup-toolchain']) {
      assert.ok(cfg.includes(`- ${dir}\n`), dir);
    }
    const ecosystems = cfg.match(/package-ecosystem:/g).length;
    assert.equal(cfg.match(/target-branch: develop/g).length, ecosystems);
  });
});
