import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const SCRIPT_PATH = path.join(REPO_ROOT, 'scripts', 'ensure-playwright-linux-libs.sh');
const skip = process.platform === 'win32';

function stubBin(dir, name, body) {
  const p = path.join(dir, name);
  fs.writeFileSync(p, `#!/usr/bin/env bash\nset -e\n${body}\n`, { mode: 0o755 });
  return p;
}

describe('ensure-playwright-linux-libs.sh (#245 rootless CI)', { skip }, () => {
  it('is a no-op on non-Linux hosts', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-libs-darwin-'));
    const bin = path.join(tmp, 'bin');
    fs.mkdirSync(bin, { recursive: true });
    stubBin(bin, 'uname', 'echo Darwin');

    const res = spawnSync('bash', [SCRIPT_PATH], {
      env: { PATH: `${bin}:/usr/bin:/bin`, HOME: tmp },
      encoding: 'utf8'
    });
    assert.equal(res.status, 0, res.stderr);
  });

  it('extracts missing shared libraries via rootless apt-get and exports LD_LIBRARY_PATH to GITHUB_ENV', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-libs-linux-'));
    const bin = path.join(tmp, 'bin');
    const browsers = path.join(tmp, 'ms-playwright', 'chromium_headless_shell-1254', 'chrome-headless-shell-linux64');
    const ghEnv = path.join(tmp, 'github.env');
    const aptLog = path.join(tmp, 'apt.log');
    fs.mkdirSync(bin, { recursive: true });
    fs.mkdirSync(browsers, { recursive: true });
    const shellBin = path.join(browsers, 'chrome-headless-shell');
    fs.writeFileSync(shellBin, '#!/bin/sh\nexit 0\n', { mode: 0o755 });
    fs.writeFileSync(ghEnv, '', 'utf8');

    stubBin(bin, 'uname', 'echo Linux');
    // Simulate rootless ARC container: sudo -n fails
    stubBin(bin, 'sudo', 'exit 1');
    // ldd reports libnspr4.so missing until LD_LIBRARY_PATH contains libnspr4.so
    stubBin(
      bin,
      'ldd',
      `IFS=':' read -ra DIRS <<< "\${LD_LIBRARY_PATH:-}"
for d in "\${DIRS[@]}"; do
  if [ -n "$d" ] && [ -f "$d/libnspr4.so" ]; then
    echo "  libnspr4.so => $d/libnspr4.so (0x00007f000000)"
    exit 0
  fi
done
echo "  libnspr4.so => not found"`
    );
    stubBin(
      bin,
      'apt-cache',
      `echo "apt-cache $*" >> "${aptLog}"
for arg in "$@"; do last="$arg"; done
case "$last" in
  *t64) exit 0 ;;
  libasound2|libatk-bridge2.0-0|libatk1.0-0|libatspi2.0-0|libcups2|libglib2.0-0) exit 1 ;;
  *) exit 0 ;;
esac`
    );
    stubBin(
      bin,
      'apt-get',
      `echo "apt-get $*" >> "${aptLog}"
ARCHIVES=""
for arg in "$@"; do
  case "$arg" in
    Dir::Cache::Archives=*) ARCHIVES="\${arg#Dir::Cache::Archives=}" ;;
  esac
done
if [[ "$*" == *"install"* ]] && [ -n "$ARCHIVES" ]; then
  touch "$ARCHIVES/libnspr4_4.35_amd64.deb"
fi`
    );
    stubBin(
      bin,
      'dpkg-deb',
      `echo "dpkg-deb $*" >> "${aptLog}"
DEST="$3"
mkdir -p "$DEST/usr/lib/x86_64-linux-gnu"
touch "$DEST/usr/lib/x86_64-linux-gnu/libnspr4.so"`
    );

    const res = spawnSync('bash', [SCRIPT_PATH], {
      env: {
        PATH: `${bin}:/usr/bin:/bin`,
        HOME: tmp,
        PLAYWRIGHT_BROWSERS_PATH: path.join(tmp, 'ms-playwright'),
        GITHUB_ENV: ghEnv
      },
      encoding: 'utf8'
    });

    assert.equal(res.status, 0, `stdout:\n${res.stdout}\nstderr:\n${res.stderr}`);
    const envContent = fs.readFileSync(ghEnv, 'utf8');
    assert.match(envContent, /^LD_LIBRARY_PATH=.*\/usr\/lib\/x86_64-linux-gnu/m);
    const logContent = fs.readFileSync(aptLog, 'utf8');
    assert.match(logContent, /Dir::State=/);
    assert.match(logContent, /Debug::NoLocking=1/);
    assert.match(logContent, /libasound2t64/);
  });

  it('ci.yml runs ensure-playwright-linux-libs.sh before playwright test', () => {
    const ci = fs.readFileSync(path.join(REPO_ROOT, '.github/workflows/ci.yml'), 'utf8');
    assert.match(ci, /playwright install --only-shell chromium/);
    assert.match(ci, /bash scripts\/ensure-playwright-linux-libs\.sh/);
  });
});
