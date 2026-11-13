import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import {
  installLocalPolicy,
  installLocalSkills,
  installGlobalSkills,
  buildSkillsCliArgs,
  getCompanionProtocolBlock,
  COMPANION_PROTOCOL_HEADER
} from '../src/installer.js';

test('installLocalPolicy scaffolds policy and workbench configs', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-install-test-'));

  try {
    fs.mkdirSync(path.join(tmpDir, 'src', 'domain'), { recursive: true });
    fs.writeFileSync(path.join(tmpDir, 'package.json'), '{}');

    const result = installLocalPolicy(tmpDir, {
      title: 'Test Service',
      serverUrl: 'http://localhost:9099'
    });

    assert.equal(result.policyCreated, true);
    assert.ok(fs.existsSync(result.policyFile));
    assert.ok(result.policyFile.endsWith(path.join('.archlens', 'policy.json')));
    assert.ok(fs.existsSync(result.configFile));
    assert.ok(result.configFile.endsWith(path.join('.archlens', 'workbench.config.json')));

    const policy = JSON.parse(fs.readFileSync(result.policyFile, 'utf8'));
    assert.equal(policy.title, 'Test Service');

    const config = JSON.parse(fs.readFileSync(result.configFile, 'utf8'));
    assert.equal(config.serverUrl, 'http://localhost:9099');

    // Companions
    const claudeMd = path.join(tmpDir, 'CLAUDE.md');
    const agentsMd = path.join(tmpDir, 'AGENTS.md');
    assert.ok(fs.existsSync(claudeMd));
    assert.ok(fs.existsSync(agentsMd));
    assert.ok(fs.readFileSync(claudeMd, 'utf8').includes(COMPANION_PROTOCOL_HEADER));
    assert.ok(fs.readFileSync(agentsMd, 'utf8').includes(COMPANION_PROTOCOL_HEADER));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('installLocalPolicy preserves existing policy unless force is set', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-preserve-test-'));

  try {
    const umlDir = path.join(tmpDir, '.uml-viewer');
    fs.mkdirSync(umlDir, { recursive: true });
    const policyFile = path.join(umlDir, 'policy.json');
    fs.writeFileSync(policyFile, JSON.stringify({ title: 'Original Policy' }));

    // Run without force
    const resultNoForce = installLocalPolicy(tmpDir, { title: 'New Policy', force: false });
    assert.equal(resultNoForce.policyCreated, false);
    const readNoForce = JSON.parse(fs.readFileSync(policyFile, 'utf8'));
    assert.equal(readNoForce.title, 'Original Policy');

    // Run with force
    const resultForce = installLocalPolicy(tmpDir, { title: 'Overwritten Policy', force: true });
    assert.equal(resultForce.policyCreated, true);
    const readForce = JSON.parse(fs.readFileSync(policyFile, 'utf8'));
    assert.equal(readForce.title, 'Overwritten Policy');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('installLocalPolicy respects dryRun option', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-dryrun-test-'));

  try {
    const result = installLocalPolicy(tmpDir, { dryRun: true });
    assert.equal(result.dryRun, true);
    assert.ok(!fs.existsSync(result.policyFile));
    assert.ok(!fs.existsSync(result.configFile));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('getCompanionProtocolBlock produces valid markdown containing server URL', () => {
  const block = getCompanionProtocolBlock('http://127.0.0.1:8088');
  assert.ok(block.includes('http://127.0.0.1:8088'));
  assert.ok(block.includes('REGEN'));
  assert.ok(block.includes('APPLY_PROPOSAL'));
  assert.ok(block.includes('REFRESH_CRAP'));
});

test('installLocalPolicy invokes MCP configuration when options.mcp is enabled', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-install-mcp-'));
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-install-home-'));

  try {
    fs.mkdirSync(path.join(tmpDir, 'src', 'domain'), { recursive: true });
    fs.writeFileSync(path.join(tmpDir, 'package.json'), '{}');

    const result = installLocalPolicy(tmpDir, {
      title: 'MCP Service',
      mcp: true,
      homedir: tmpHome,
      force: true
    });

    assert.ok(result.mcpResult);
    assert.equal(typeof result.mcpResult, 'object');
    assert.ok(Array.isArray(result.mcpResult.configuredClients));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

test('installGlobalSkills installs all bundled skills into global agent directories', () => {
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-global-skills-'));

  try {
    const res = installGlobalSkills({
      force: true,
      homedir: tmpHome
    });

    assert.ok(Array.isArray(res.installed));
    assert.ok(res.installed.length >= 3);
    const skillsInstalled = res.installed.map((item) => item.skill);
    assert.ok(skillsInstalled.includes('archlens'));
    assert.ok(skillsInstalled.includes('archlens-install-policy'));
    assert.ok(skillsInstalled.includes('uml-workbench-companion'));

    for (const item of res.installed) {
      assert.ok(fs.existsSync(path.join(item.path, 'SKILL.md')));
    }
  } finally {
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

test('installGlobalSkills respects dryRun and fallback mode', () => {
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-global-dryrun-'));

  try {
    const res = installGlobalSkills({
      dryRun: true,
      homedir: tmpHome,
      force: true
    });

    assert.equal(res.dryRun, true);
    assert.equal(res.method, 'direct-copy');
  } finally {
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

test('buildSkillsCliArgs constructs non-interactive npx skills add arguments with --all and --agent', () => {
  const globalArgs = buildSkillsCliArgs({
    sourceDir: '/pkg/cli',
    global: true
  });
  assert.deepEqual(globalArgs, ['--yes', 'skills', 'add', '/pkg/cli', '--all', '-g', '--copy', '-y']);

  const scopedArgs = buildSkillsCliArgs({
    sourceDir: '/pkg/cli',
    global: false,
    agent: 'claude-code,cursor',
    skill: 'archlens'
  });
  assert.deepEqual(scopedArgs, [
    '--yes',
    'skills',
    'add',
    '/pkg/cli',
    '--skill',
    'archlens',
    '--agent',
    'claude-code',
    '--agent',
    'cursor',
    '--copy',
    '-y'
  ]);
});

test('installGlobalSkills delegates to npx skills add --all -g --copy -y against cli package root', () => {
  const calls = [];
  const res = installGlobalSkills({
    agent: 'claude-code',
    spawnSync: (cmd, args, opts) => {
      calls.push({ cmd, args, opts });
      return { status: 0, stdout: 'Installed 3 skills', stderr: '' };
    }
  });

  assert.equal(res.method, 'skills-cli');
  assert.equal(calls.length, 1);
  assert.ok(calls[0].cmd.startsWith('npx'));
  assert.ok(calls[0].args.includes('--all'));
  assert.ok(calls[0].args.includes('-g'));
  assert.ok(calls[0].args.includes('--copy'));
  assert.ok(calls[0].args.includes('-y'));
  assert.ok(calls[0].args.includes('claude-code'));
  // Source directory must be cli package root containing skills/<name>/SKILL.md
  const sourceArg = calls[0].args[3];
  assert.ok(fs.existsSync(path.join(sourceArg, 'skills', 'archlens', 'SKILL.md')));
});

test('installLocalSkills and installLocalPolicy({ skills: true }) install project skills via npx skills', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-local-skills-'));
  const calls = [];

  try {
    fs.mkdirSync(path.join(tmpDir, 'src', 'domain'), { recursive: true });
    fs.writeFileSync(path.join(tmpDir, 'package.json'), '{}');

    const policyRes = installLocalPolicy(tmpDir, {
      skills: true,
      agent: 'cursor',
      spawnSync: (cmd, args, opts) => {
        calls.push({ cmd, args, opts });
        return { status: 0, stdout: 'Installed local skills', stderr: '' };
      }
    });

    assert.ok(policyRes.skillsResult);
    assert.equal(policyRes.skillsResult.method, 'skills-cli');
    assert.equal(calls.length, 1);
    assert.equal(calls[0].opts.cwd, path.resolve(tmpDir));
    assert.ok(calls[0].args.includes('--all'));
    assert.ok(!calls[0].args.includes('-g'));
    assert.ok(calls[0].args.includes('cursor'));

    // Fallback to .agents/skills when npx skills exits non-zero
    const fallbackRes = installLocalSkills(tmpDir, {
      spawnSync: () => ({ status: 1, stdout: '', stderr: 'offline' })
    });
    assert.equal(fallbackRes.method, 'direct-copy');
    assert.ok(fs.existsSync(path.join(tmpDir, '.agents', 'skills', 'archlens', 'SKILL.md')));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('installer handles edge cases: missing root, existing companions, array options, symlink flag, and thrown spawnSync', () => {
  assert.throws(
    () => installLocalPolicy('/nonexistent/archlens-missing-dir-xyz'),
    /Target directory does not exist/
  );

  // Array options + symlink: true + non-string list option
  const symlinkArgs = buildSkillsCliArgs({
    sourceDir: '/pkg/cli',
    agents: ['claude-code', 'gemini-cli'],
    skillsList: ['archlens', 'uml-workbench-companion'],
    symlink: true
  });
  assert.ok(!symlinkArgs.includes('--copy'));
  assert.ok(symlinkArgs.includes('gemini-cli'));
  assert.ok(symlinkArgs.includes('uml-workbench-companion'));

  const nonStringArgs = buildSkillsCliArgs({
    sourceDir: '/pkg/cli',
    agent: 123
  });
  assert.ok(nonStringArgs.includes('--all'));

  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-edge-install-'));
  const tmpHome = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-edge-home-'));
  try {
    // Pre-existing CLAUDE.md without protocol block gets appended; AGENTS.md with protocol block is untouched
    fs.writeFileSync(path.join(tmpDir, 'CLAUDE.md'), '# Existing Rules\n');
    fs.writeFileSync(path.join(tmpDir, 'AGENTS.md'), `# Agents\n\n${getCompanionProtocolBlock()}\n`);

    const res = installLocalPolicy(tmpDir, { title: 'Edge App' });
    assert.deepEqual(res.companionFiles, ['CLAUDE.md']);
    assert.ok(fs.readFileSync(path.join(tmpDir, 'CLAUDE.md'), 'utf8').includes(COMPANION_PROTOCOL_HEADER));

    // Thrown spawnSync falls back cleanly in both local and global skill installers
    const localFallback = installLocalSkills(tmpDir, {
      spawnSync: () => { throw new Error('ENOENT npx'); }
    });
    assert.equal(localFallback.method, 'direct-copy');

    fs.mkdirSync(path.join(tmpHome, '.claude'), { recursive: true });
    const globalFallback = installGlobalSkills({
      homedir: tmpHome,
      preferVercelSkills: true,
      spawnSync: () => { throw new Error('ENOENT npx'); }
    });
    assert.equal(globalFallback.method, 'direct-copy');
    assert.equal(globalFallback.installed.length, 3);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    fs.rmSync(tmpHome, { recursive: true, force: true });
  }
});

