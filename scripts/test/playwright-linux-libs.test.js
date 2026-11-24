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

  it('skips virtual packages (Candidate: (none)) & installed packages via apt-cache policy and exports LD_LIBRARY_PATH', () => {
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
    // Simulate user-namespaced ARC container where sudo exists without CAP_CHOWN: sudo must never be called
    stubBin(bin, 'sudo', 'echo "unexpected sudo invocation" >&2; exit 99');
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
cat <<'EOF'
libasound2t64:
  Installed: (none)
  Candidate: 1.2.11-1build2
libasound2:
  Installed: (none)
  Candidate: (none)
libdbus-1-3:
  Installed: 1.14.10-4ubuntu4.1
  Candidate: 1.14.10-4ubuntu4.1
libnspr4:
  Installed: (none)
  Candidate: 2:4.35-1.1build1
EOF`
    );
    stubBin(
      bin,
      'apt-get',
      `echo "apt-get $*" >> "${aptLog}"
ARCHIVES=""
for arg in "$@"; do
  case "$arg" in
    Dir::Cache::archives=*) ARCHIVES="\${arg#Dir::Cache::archives=}" ;;
    libasound2|libdbus-1-3) echo "virtual or installed pkg $arg should have been filtered!" >&2; exit 100 ;;
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
    assert.match(logContent, /APT::Update::Post-Invoke::=/);
    assert.match(logContent, /Debug::NoLocking=1/);
    assert.match(logContent, /install .* libasound2t64 libnspr4$/m);
  });

  it('ci.yml runs ensure-playwright-linux-libs.sh before playwright test', () => {
    const ci = fs.readFileSync(path.join(REPO_ROOT, '.github/workflows/ci.yml'), 'utf8');
    assert.match(ci, /playwright install --only-shell chromium/);
    assert.match(ci, /bash scripts\/ensure-playwright-linux-libs\.sh/);
  });
});
