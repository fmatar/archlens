import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const ENTRYPOINT = path.join(REPO_ROOT, 'action', 'run-gate.sh');
const FIXTURES = path.join(REPO_ROOT, 'action', 'fixtures');
const skip = process.platform === 'win32';

function runGate(env, cwd) {
  return spawnSync('bash', [ENTRYPOINT], {
    cwd: cwd || fs.mkdtempSync(path.join(os.tmpdir(), 'archlens action ws ')),
    env: { ...process.env, ...env },
    encoding: 'utf8'
  });
}

/** A fake CLI that records its argv so we can assert exactly what the Action passes. */
function argvRecorder() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-argv-'));
  const out = path.join(dir, 'argv.json');
  const cli = path.join(dir, 'fake-cli.js');
  fs.writeFileSync(cli, `require('fs').writeFileSync(${JSON.stringify(out)}, JSON.stringify(process.argv.slice(2)));\n`);
  return { cli, read: () => JSON.parse(fs.readFileSync(out, 'utf8')) };
}

describe('GitHub Action entrypoint (#230, #231)', { skip }, () => {
  it('passes inputs with spaces and quotes to the CLI verbatim, as separate arguments', () => {
    const rec = argvRecorder();
    const res = runGate({
      ARCHLENS_CLI: rec.cli,
      INPUT_PATH: 'my "quoted" project dir',
      INPUT_MAX_VIOLATIONS: '0',
      INPUT_DETECT_CYCLES: 'true',
      INPUT_SCREAMING_THRESHOLD: '0.7',
      INPUT_FITNESS_THRESHOLD: '0.85',
      INPUT_SARIF_FILE: 'reports/arch lens.sarif'
    });
    assert.equal(res.status, 0, res.stdout + res.stderr);
    assert.deepEqual(rec.read(), [
      'check',
      '-p',
      'my "quoted" project dir',
      '--max-violations',
      '0',
      '--format',
      'sarif',
      '-O',
      'reports/arch lens.sarif',
      '--detect-cycles',
      '--screaming-threshold',
      '0.7',
      '--fitness-threshold',
      '0.85'
    ]);
  });

  it('omits optional flags when inputs are empty or false', () => {
    const rec = argvRecorder();
    const res = runGate({
      ARCHLENS_CLI: rec.cli,
      INPUT_PATH: '.',
      INPUT_MAX_VIOLATIONS: '3',
      INPUT_DETECT_CYCLES: 'false',
      INPUT_SCREAMING_THRESHOLD: '',
      INPUT_FITNESS_THRESHOLD: '',
      INPUT_SARIF_FILE: 'out.sarif'
    });
    assert.equal(res.status, 0, res.stderr);
    assert.deepEqual(rec.read(), ['check', '-p', '.', '--max-violations', '3', '--format', 'sarif', '-O', 'out.sarif']);
  });

  it('passes on a conforming fixture and writes SARIF (real bundled CLI, path with spaces)', () => {
    const ws = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens action ws '));
    const res = runGate(
      {
        ARCHLENS_CLI: '',
        INPUT_PATH: path.join(FIXTURES, 'conforming app'),
        INPUT_MAX_VIOLATIONS: '0',
        INPUT_DETECT_CYCLES: 'true',
        INPUT_SARIF_FILE: 'arch results.sarif'
      },
      ws
    );
    assert.equal(res.status, 0, res.stdout + res.stderr);
    assert.match(res.stdout, /cli\/bin\/index\.js/, 'runs the CLI bundled with the Action, not npx @latest');
    const sarif = JSON.parse(fs.readFileSync(path.join(ws, 'arch results.sarif'), 'utf8'));
    assert.equal(sarif.runs[0].results.length, 0);
  });

  it('fails on a violating fixture', () => {
    const res = runGate({
      ARCHLENS_CLI: '',
      INPUT_PATH: path.join(FIXTURES, 'violating app'),
      INPUT_MAX_VIOLATIONS: '0',
      INPUT_DETECT_CYCLES: 'true',
      INPUT_SARIF_FILE: 'out.sarif'
    });
    assert.equal(res.status, 1, res.stdout + res.stderr);
    assert.match(res.stdout, /::error title=Archlens Clean Architecture Gate Failed::/);
  });

  it('rejects a literally quoted numeric input with a usage error instead of a silent NaN', () => {
    const res = runGate({
      ARCHLENS_CLI: '',
      INPUT_PATH: path.join(FIXTURES, 'conforming app'),
      INPUT_MAX_VIOLATIONS: '"0"',
      INPUT_DETECT_CYCLES: 'true',
      INPUT_SARIF_FILE: 'out.sarif'
    });
    assert.equal(res.status, 2, res.stdout + res.stderr);
    assert.match(res.stdout + res.stderr, /Invalid numeric value for --max-violations/);
  });

  it('action.yml delegates to the entrypoint and never runs the CLI from @latest', () => {
    const yml = fs.readFileSync(path.join(REPO_ROOT, 'action.yml'), 'utf8');
    assert.match(yml, /action\/run-gate\.sh/);
    assert.doesNotMatch(yml, /archlens-skill@latest/);
    assert.doesNotMatch(yml, /\$ARGS\b/, 'no unquoted string-built argument list');
  });
});
