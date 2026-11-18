import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync, execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const read = (rel) => fs.readFileSync(path.join(REPO_ROOT, rel), 'utf8');
const skip = process.platform === 'win32';
const systemGh = ['/usr/bin/gh', '/bin/gh'].some((p) => fs.existsSync(p));

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

describe('ensure-gh.sh (#234)', { skip }, () => {
  const script = path.join(REPO_ROOT, 'scripts', 'ensure-gh.sh');

  it('is a no-op when gh is already on PATH', () => {
    const bin = fs.mkdtempSync(path.join(os.tmpdir(), 'gh-present-'));
    stubBin(bin, 'gh', 'echo "gh version 9.9.9"');
    const res = spawnSync('bash', [script], { env: { PATH: `${bin}:/usr/bin:/bin`, HOME: bin }, encoding: 'utf8' });
    assert.equal(res.status, 0, res.stderr);
    assert.match(res.stdout, /gh already available: gh version 9\.9\.9/);
  });

  function fakeRelease(version, { corrupt = false } = {}) {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gh-release-'));
    const arch = ['arm64', 'aarch64'].includes(os.arch()) ? 'arm64' : 'amd64';
    const name = `gh_${version}_linux_${arch}`;
    fs.mkdirSync(path.join(dir, name, 'bin'), { recursive: true });
    stubBin(path.join(dir, name, 'bin'), 'gh', `echo "gh version ${version} (fake)"`);
    const asset = `${name}.tar.gz`;
    execFileSync('tar', ['-czf', path.join(dir, asset), '-C', dir, name]);
    const sum = execFileSync('sha256sum', [path.join(dir, asset)], { encoding: 'utf8' }).split(' ')[0];
    const recorded = corrupt ? '0'.repeat(64) : sum;
    fs.writeFileSync(path.join(dir, `gh_${version}_checksums.txt`), `${recorded}  ${asset}\n`);
    return dir;
  }

  it('installs a verified release when gh is missing and exports it to GITHUB_PATH', { skip: os.platform() !== 'linux' || systemGh }, () => {
    const release = fakeRelease('1.2.3');
    const home = fs.mkdtempSync(path.join(os.tmpdir(), 'gh-home-'));
    const ghPath = path.join(home, 'github_path');
    const res = spawnSync('bash', [script], {
      env: { PATH: '/usr/bin:/bin', HOME: home, GH_VERSION: '1.2.3', GH_DOWNLOAD_BASE: `file://${release}`, GITHUB_PATH: ghPath },
      encoding: 'utf8'
    });
    assert.equal(res.status, 0, res.stdout + res.stderr);
    assert.match(res.stdout, /Installed gh version 1\.2\.3 \(fake\)/);
    assert.equal(fs.readFileSync(ghPath, 'utf8').trim(), path.join(home, '.local', 'gh-1.2.3', 'bin'));
  });

  it('refuses a download whose checksum does not match', { skip: os.platform() !== 'linux' || systemGh }, () => {
    const release = fakeRelease('1.2.4', { corrupt: true });
    const home = fs.mkdtempSync(path.join(os.tmpdir(), 'gh-home-'));
    const res = spawnSync('bash', [script], {
      env: { PATH: '/usr/bin:/bin', HOME: home, GH_VERSION: '1.2.4', GH_DOWNLOAD_BASE: `file://${release}` },
      encoding: 'utf8'
    });
    assert.notEqual(res.status, 0);
    assert.match(res.stdout, /Checksum mismatch/);
    assert.equal(fs.existsSync(path.join(home, '.local', 'gh-1.2.4')), false);
  });

  it('dependency pulse installs gh before using it and reuses one branch', () => {
    const wf = read('.github/workflows/dependency-pulse.yml');
    const ensure = wf.indexOf('bash scripts/ensure-gh.sh');
    assert.ok(ensure > 0, 'workflow must call ensure-gh.sh');
    assert.ok(ensure < wf.indexOf('gh pr create'), 'gh must be ensured before gh pr create');
    assert.match(wf, /BRANCH_NAME="chore\/dependency-pulse"$/m);
    assert.doesNotMatch(wf, /dependency-pulse-\$\(date/);
    assert.match(wf, /gh pr list --head "\$BRANCH_NAME"/);
  });
});

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
