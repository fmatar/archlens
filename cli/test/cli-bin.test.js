import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import { parseArgs, main } from '../bin/index.js';

function createSampleProject(prefix = 'archlens-cli-bin-') {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  fs.mkdirSync(path.join(tmpDir, 'src', 'domain'), { recursive: true });
  fs.mkdirSync(path.join(tmpDir, 'src', 'application'), { recursive: true });
  fs.mkdirSync(path.join(tmpDir, 'src', 'infrastructure'), { recursive: true });
  fs.writeFileSync(path.join(tmpDir, 'package.json'), JSON.stringify({ name: 'sample-app', version: '1.0.0' }));
  fs.writeFileSync(
    path.join(tmpDir, 'src', 'domain', 'Order.js'),
    'export class Order { constructor(id) { this.id = id; } }\n'
  );
  fs.writeFileSync(
    path.join(tmpDir, 'src', 'application', 'PlaceOrder.js'),
    "import { Order } from '../domain/Order.js';\nexport function placeOrder(id) { return new Order(id); }\n"
  );
  return tmpDir;
}

test('parseArgs parses all CLI commands and flags accurately', () => {
  const full = parseArgs([
    'check',
    '-p', './app',
    '-t', 'My App',
    '--prefix', 'com.acme',
    '--server-url', 'http://localhost:9090',
    '--port', '9090',
    '-o',
    '-m',
    '--skills',
    '--agent', 'claude-code,cursor',
    '-g',
    '-u',
    '--upgrade',
    '--prompt',
    '--detect-cycles',
    '--screaming-threshold', '0.5',
    '--suggest-features',
    '--fitness-threshold', '0.75',
    '--max-violations', '2',
    '--format', 'SARIF',
    '-O', 'report.sarif',
    '-c',
    '-f',
    '--dry-run',
    '-y',
    '--docker',
    '--native',
    '--jar', '/tmp/app.jar'
  ]);

  assert.equal(full.command, 'check');
  assert.equal(full.check, true);
  assert.equal(full.path, './app');
  assert.equal(full.title, 'My App');
  assert.equal(full.prefix, 'com.acme');
  assert.equal(full.serverUrl, 'http://localhost:9090');
  assert.equal(full.port, '9090');
  assert.equal(full.open, true);
  assert.equal(full.mcp, true);
  assert.equal(full.skills, true);
  assert.equal(full.agent, 'claude-code,cursor');
  assert.equal(full.global, true);
  assert.equal(full.update, true);
  assert.equal(full.upgrade, true);
  assert.equal(full.prompt, true);
  assert.equal(full.detectCycles, true);
  assert.equal(full.screamingThreshold, 0.5);
  assert.equal(full.suggestFeatures, true);
  assert.equal(full.fitnessThreshold, 0.75);
  assert.equal(full.maxViolations, 2);
  assert.equal(full.format, 'sarif');
  assert.equal(full.output, 'report.sarif');
  assert.equal(full.copy, true);
  assert.equal(full.force, true);
  assert.equal(full.dryRun, true);
  assert.equal(full.yes, true);
  assert.equal(full.preferDocker, true);
  assert.equal(full.preferNative, true);
  assert.equal(full.jarPath, '/tmp/app.jar');

  for (const cmd of ['help', 'version', 'start', 'update', 'upgrade', 'skills', 'global', 'prompt', 'mcp']) {
    const res = parseArgs([cmd]);
    assert.equal(res.command, cmd);
  }

  // Flag aliases and trailing default value fallbacks
  const aliases = parseArgs(['--check', '--cycles', '--suggest']);
  assert.equal(aliases.check, true);
  assert.equal(aliases.detectCycles, true);
  assert.equal(aliases.suggestFeatures, true);
  assert.equal(parseArgs(['--screaming']).screamingThreshold, 0.6);
  assert.equal(parseArgs(['--fitness']).fitnessThreshold, 0.8);
  assert.equal(parseArgs(['--max-violations']).maxViolations, 0);
  assert.equal(parseArgs(['--port']).port, '8088');
  assert.equal(parseArgs(['--format']).format, 'pretty');
  assert.equal(parseArgs(['--server-url']).serverUrl, 'http://localhost:8088');
  assert.equal(parseArgs(['--path']).path, '.');
});

test('main handles --help and --version cleanly', async () => {
  let code = null;
  await main(['--help'], { exit: (c) => { code = c; } });
  assert.equal(code, 0);

  code = null;
  await main(['--version'], { exit: (c) => { code = c; } });
  assert.equal(code, 0);
});

test('main executes init --yes --skills --mcp once per integration without duplicate writes', async () => {
  const tmpDir = createSampleProject('archlens-bin-init-');
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bin-home-'));
  fs.mkdirSync(path.join(tmpHome, '.claude'), { recursive: true });

  const skillCalls = [];
  try {
    let exitCode = 0;
    await main(
      ['--yes', '--skills', '--mcp', '--agent', 'claude-code', '-p', tmpDir, '-t', 'Demo'],
      {
        exit: (c) => { exitCode = c; },
        options: {
          homedir: tmpHome,
          spawnSync: (cmd, args, opts) => {
            skillCalls.push({ cmd, args, opts });
            return { status: 0, stdout: 'ok', stderr: '' };
          }
        }
      }
    );

    assert.equal(exitCode, 0);
    assert.equal(skillCalls.length, 1, 'npx skills must run exactly once');
    assert.ok(fs.existsSync(path.join(tmpDir, '.archlens', 'policy.json')));
    assert.ok(fs.existsSync(path.join(tmpDir, '.mcp.json')));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

test('main routes skills, skills -g, global, update, and upgrade commands cleanly', async () => {
  const tmpDir = createSampleProject('archlens-bin-routes-');
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bin-routes-home-'));
  const spawnCalls = [];
  const mockSpawn = (cmd, args) => {
    spawnCalls.push({ cmd, args });
    return { status: 0, stdout: 'ok', stderr: '' };
  };

  try {
    // 1. `skills` local subcommand (both skills-cli and offline direct-copy fallback)
    await main(['skills', '-p', tmpDir], {
      options: { spawnSync: mockSpawn }
    });
    assert.equal(spawnCalls.length, 1);
    assert.ok(!spawnCalls[0].args.includes('-g'));

    await main(['skills', '-p', tmpDir, '--dry-run'], {
      options: { preferVercelSkills: false }
    });

    // `skills -g` subcommand
    await main(['skills', '-g'], {
      options: { spawnSync: mockSpawn }
    });
    assert.equal(spawnCalls.length, 2);
    assert.ok(spawnCalls[1].args.includes('-g'));

    // 2. `--skills -g` flag combination (without --yes)
    await main(['--skills', '-g'], {
      options: { preferVercelSkills: true, spawnSync: mockSpawn }
    });
    assert.equal(spawnCalls.length, 3);
    assert.ok(spawnCalls[2].args.includes('-g'));

    // 3. `global` subcommand in offline fallback mode (empty home vs populated home)
    await main(['global'], {
      options: { homedir: tmpHome, preferVercelSkills: false }
    });
    fs.mkdirSync(path.join(tmpHome, '.claude'), { recursive: true });
    await main(['global', '--dry-run'], {
      options: { homedir: tmpHome, preferVercelSkills: false }
    });

    // 4. `-g` flag without command initializes local policy + global skills
    await main(['-g', '-p', tmpDir], {
      options: { homedir: tmpHome, preferVercelSkills: true, spawnSync: mockSpawn }
    });
    // Calling init again on existing policy exercises policyCreated: false branch
    await main(['--yes', '-p', tmpDir], {});

    // 5. `update` command refreshes policy and global skills
    fs.mkdirSync(path.join(tmpDir, 'src', 'controller'), { recursive: true });
    await main(['update', '-p', tmpDir], {
      options: { homedir: tmpHome, preferVercelSkills: false }
    });
    // Second update with 0 new packages
    await main(['update', '-p', tmpDir], {
      options: { homedir: tmpHome, preferVercelSkills: false }
    });

    // 6. `upgrade` command (already aligned, then legacy .uml-viewer migration)
    await main(['upgrade', '-p', tmpDir], {});
    const legacyDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bin-legacy-'));
    try {
      fs.mkdirSync(path.join(legacyDir, '.uml-viewer'), { recursive: true });
      fs.writeFileSync(path.join(legacyDir, '.uml-viewer', 'policy.json'), JSON.stringify({ title: 'Legacy' }));
      fs.writeFileSync(path.join(legacyDir, 'AGENTS.md'), 'Queue: .uml-viewer/to-agent.json\n');
      await main(['upgrade', '-p', legacyDir], {});
      assert.ok(fs.existsSync(path.join(legacyDir, '.archlens', 'policy.json')));
    } finally {
      fs.rmSync(legacyDir, { recursive: true, force: true });
    }

    // 7. Error handling on non-existent target directory
    let errExit = null;
    await main(['--yes', '-p', path.join(tmpDir, 'does-not-exist')], {
      exit: (c) => { errExit = c; }
    });
    assert.equal(errExit, 1);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

test('main executes prompt, check (pretty/json/sarif/mermaid/fail), mcp, start, and interactive modes', async () => {
  const tmpDir = createSampleProject('archlens-bin-check-');

  try {
    // Initialize policy first
    await main(['--yes', '-p', tmpDir], {});

    // Prompt command (stdout + clipboard success + clipboard fallback)
    let code = null;
    await main(['prompt', '-p', tmpDir], { exit: (c) => { code = c; } });
    assert.equal(code, 0);

    await main(['prompt', '--copy', '-p', tmpDir], {
      exit: (c) => { code = c; },
      copyToSystemClipboard: () => true
    });
    assert.equal(code, 0);

    await main(['prompt', '--copy', '-p', tmpDir], {
      exit: (c) => { code = c; },
      copyToSystemClipboard: () => false
    });
    assert.equal(code, 0);

    // Check passing (pretty + output file, json, sarif, mermaid)
    const reportPath = path.join(tmpDir, 'reports', 'arch.json');
    await main(
      [
        'check',
        '-p', tmpDir,
        '--detect-cycles',
        '--screaming-threshold', '0.0',
        '--fitness-threshold', '0.0',
        '-O', reportPath
      ],
      { exit: (c) => { code = c; } }
    );
    assert.equal(code, 0);
    assert.ok(fs.existsSync(reportPath));

    for (const fmt of ['json', 'sarif', 'mermaid']) {
      await main(['check', '-p', tmpDir, '--format', fmt], { exit: (c) => { code = c; } });
      assert.equal(code, 0);
    }

    // Introduce an outward dependency violation + cycle + high thresholds to test failure reporting
    fs.writeFileSync(
      path.join(tmpDir, 'src', 'domain', 'Order.js'),
      "import { Repo } from '../infrastructure/Repo.js';\nexport class OrderService {}\nexport class OrderController {}\n"
    );
    fs.writeFileSync(
      path.join(tmpDir, 'src', 'infrastructure', 'Repo.js'),
      "import { OrderService } from '../domain/Order.js';\nexport class Repo {}\n"
    );

    await main(
      [
        'check',
        '-p', tmpDir,
        '--detect-cycles',
        '--suggest-features',
        '--screaming-threshold', '0.99',
        '--fitness-threshold', '0.99'
      ],
      { exit: (c) => { code = c; } }
    );
    assert.equal(code, 1);

    // Check on directory without policy returns exit 1
    const emptyDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bin-nopolicy-'));
    try {
      await main(['check', '-p', emptyDir], { exit: (c) => { code = c; } });
      assert.equal(code, 1);
    } finally {
      fs.rmSync(emptyDir, { recursive: true, force: true });
    }

    // MCP stdio server starter
    let mcpStarted = false;
    await main(['mcp'], {
      startMcpStdioServer: (opts) => { mcpStarted = Boolean(opts.version); }
    });
    assert.equal(mcpStarted, true);

    // Start command states: active, spawned jar, spawned docker, offline, error, thrown
    for (const statusPayload of [
      { status: 'running', spawned: false, version: '1.1.1', serverUrl: 'http://localhost:8088' },
      { status: 'running', spawned: true, mode: 'jar', jarPath: 'app.jar', serverUrl: 'http://localhost:8088' },
      { status: 'running', spawned: true, mode: 'docker', serverUrl: 'http://localhost:8088' },
      { status: 'offline', message: 'offline' }
    ]) {
      await main(['start'], {
        exit: (c) => { code = c; },
        executeStart: async () => statusPayload
      });
      assert.equal(code, 0);
    }

    await main(['start'], {
      exit: (c) => { code = c; },
      executeStart: async () => ({ status: 'error', message: 'failed' })
    });
    assert.equal(code, 1);

    await main(['start'], {
      exit: (c) => { code = c; },
      executeStart: async () => { throw new Error('boom'); }
    });
    assert.equal(code, 1);

    // Interactive mode dispatch (real runInteractive with mocked prompts + error branch)
    const tmpInteractiveHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-bin-tty-home-'));
    const missingParentRoot = path.join(tmpInteractiveHome, 'isolated-ws');
    fs.mkdirSync(missingParentRoot, { recursive: true });
    try {
      for (const choice of ['init_local', 'install_global', 'mcp', 'both', 'update', 'upgrade']) {
        await main(['-p', tmpDir], {
          isTTY: true,
          askChoice: async () => choice,
          askQuestion: async () => 'Interactive App',
          options: { homedir: tmpInteractiveHome, preferVercelSkills: false }
        });
      }

      // Thrown error in prompt --copy and corrupted policy JSON in check trigger catch blocks
      await main(['prompt', '--copy', '-p', tmpDir], {
        exit: (c) => { code = c; },
        copyToSystemClipboard: () => { throw new Error('clipboard failed'); }
      });
      assert.equal(code, 1);

      const corruptDir = path.join(tmpInteractiveHome, 'corrupt-ws');
      fs.mkdirSync(path.join(corruptDir, '.archlens'), { recursive: true });
      fs.writeFileSync(path.join(corruptDir, '.archlens', 'policy.json'), '{not-valid-json');

      await main(['check', '-p', corruptDir, '--format', 'mmd', '-O', path.join(corruptDir, 'out.mmd')], {
        exit: (c) => { code = c; }
      });
      assert.equal(code, 1);

      await main(['start', '--port', '9091'], {
        exit: (c) => { code = c; },
        options: { serverUrl: '' },
        executeStart: async () => ({ status: 'running', spawned: false, serverUrl: 'http://localhost:9091' })
      });
      assert.equal(code, 0);
    } finally {
      fs.rmSync(tmpInteractiveHome, { recursive: true, force: true });
    }

    await main([], {
      isTTY: true,
      exit: (c) => { code = c; },
      runInteractive: async () => { throw new Error('tty cancelled'); }
    });
    assert.equal(code, 1);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
