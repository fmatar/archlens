import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import {
  installLocalPolicy,
  installGlobalSkills,
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
