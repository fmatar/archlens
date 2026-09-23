import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import { upgradeProject } from '../src/upgrader.js';
import { COMPANION_PROTOCOL_HEADER } from '../src/installer.js';

test('upgradeProject migrates .uml-viewer to .archlens and updates companions', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-upgrade-test-'));

  try {
    const legacyDir = path.join(tmpDir, '.uml-viewer');
    fs.mkdirSync(legacyDir, { recursive: true });

    const legacyPolicy = {
      title: 'Legacy Demo',
      src: 'src',
      prefix: 'com.demo',
      hierarchical: true,
      levels: [['domain']]
    };
    fs.writeFileSync(path.join(legacyDir, 'policy.json'), JSON.stringify(legacyPolicy, null, 2));
    fs.writeFileSync(path.join(legacyDir, 'workbench.config.json'), '{"serverUrl": "http://localhost:8088"}');
    fs.writeFileSync(path.join(legacyDir, 'to-agent.json'), '{"nextId": 1, "queue": []}');

    // Companion file referencing legacy path
    const claudeMd = path.join(tmpDir, 'CLAUDE.md');
    fs.writeFileSync(
      claudeMd,
      `# Instructions\n- Policy: .uml-viewer/policy.json\n- Mailbox: .uml-viewer/to-agent.json\n`
    );

    const result = upgradeProject(tmpDir);

    assert.equal(result.migrated, true);
    assert.equal(fs.existsSync(legacyDir), false);

    const primaryDir = path.join(tmpDir, '.archlens');
    assert.equal(fs.existsSync(primaryDir), true);
    assert.equal(fs.existsSync(path.join(primaryDir, 'policy.json')), true);
    assert.equal(fs.existsSync(path.join(primaryDir, 'workbench.config.json')), true);
    assert.equal(fs.existsSync(path.join(primaryDir, 'to-agent.json')), true);

    const migratedPolicy = JSON.parse(fs.readFileSync(path.join(primaryDir, 'policy.json'), 'utf8'));
    assert.equal(migratedPolicy.title, 'Legacy Demo');

    // Verify CLAUDE.md updated
    const updatedClaude = fs.readFileSync(claudeMd, 'utf8');
    assert.equal(updatedClaude.includes('.uml-viewer'), false);
    assert.equal(updatedClaude.includes('.archlens/policy.json'), true);
    assert.equal(updatedClaude.includes(COMPANION_PROTOCOL_HEADER), true);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('upgradeProject respects dryRun flag', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-upgrade-dryrun-'));

  try {
    const legacyDir = path.join(tmpDir, '.uml-viewer');
    fs.mkdirSync(legacyDir, { recursive: true });
    fs.writeFileSync(path.join(legacyDir, 'policy.json'), '{"title": "Dry Run"}');

    const result = upgradeProject(tmpDir, { dryRun: true });

    assert.equal(result.dryRun, true);
    assert.equal(result.migrated, true);
    assert.equal(fs.existsSync(legacyDir), true);
    assert.equal(fs.existsSync(path.join(tmpDir, '.archlens')), false);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('upgradeProject reports no-op when already on .archlens', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-already-upgraded-'));

  try {
    const primaryDir = path.join(tmpDir, '.archlens');
    fs.mkdirSync(primaryDir, { recursive: true });
    fs.writeFileSync(path.join(primaryDir, 'policy.json'), '{"title": "Current"}');

    const result = upgradeProject(tmpDir);

    assert.equal(result.migrated, false);
    assert.equal(fs.existsSync(primaryDir), true);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('upgradeProject throws error when no configuration exists', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-no-config-'));

  try {
    assert.throws(
      () => upgradeProject(tmpDir),
      /No Archlens configuration found to upgrade/
    );
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
