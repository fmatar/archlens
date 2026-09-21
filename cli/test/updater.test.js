import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import { updateLocalPolicy } from '../src/updater.js';

test('updateLocalPolicy detects and integrates new packages', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-update-test-'));

  try {
    const umlDir = path.join(tmpDir, '.uml-viewer');
    fs.mkdirSync(umlDir, { recursive: true });

    const srcDir = path.join(tmpDir, 'src');
    fs.mkdirSync(path.join(srcDir, 'domain'), { recursive: true });
    fs.mkdirSync(path.join(srcDir, 'application'), { recursive: true });

    // Initial policy with only domain and application
    const initialPolicy = {
      title: 'Custom Service Title',
      src: 'src',
      prefix: '',
      order: ['domain', 'application'],
      levels: [['domain'], ['application'], []],
      foreign: ['custom.library.'],
      omit: ['target']
    };
    fs.writeFileSync(path.join(umlDir, 'policy.json'), JSON.stringify(initialPolicy, null, 2));

    // Developer adds infrastructure and controller
    fs.mkdirSync(path.join(srcDir, 'controller'), { recursive: true });
    fs.mkdirSync(path.join(srcDir, 'infrastructure'), { recursive: true });

    const result = updateLocalPolicy(tmpDir);

    assert.equal(result.newPackages.length, 2);
    assert.ok(result.newPackages.includes('controller'));
    assert.ok(result.newPackages.includes('infrastructure'));

    const updated = JSON.parse(fs.readFileSync(path.join(umlDir, 'policy.json'), 'utf8'));

    // Custom properties are preserved
    assert.equal(updated.title, 'Custom Service Title');
    assert.deepEqual(updated.foreign, ['custom.library.']);
    assert.deepEqual(updated.omit, ['target']);

    // Order includes all 4 packages
    assert.ok(updated.order.includes('domain'));
    assert.ok(updated.order.includes('application'));
    assert.ok(updated.order.includes('controller'));
    assert.ok(updated.order.includes('infrastructure'));

    // Controller placed in Level 2 (Adapters), Infrastructure in Level 3
    assert.ok(updated.levels[2].includes('controller'));
    assert.ok(updated.levels[3].includes('infrastructure'));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('updateLocalPolicy errors when policy does not exist', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-no-policy-test-'));

  try {
    assert.throws(
      () => updateLocalPolicy(tmpDir),
      /Archlens policy not found/
    );
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('updateLocalPolicy respects dryRun mode', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-update-dryrun-test-'));

  try {
    const umlDir = path.join(tmpDir, '.uml-viewer');
    fs.mkdirSync(umlDir, { recursive: true });
    const srcDir = path.join(tmpDir, 'src');
    fs.mkdirSync(path.join(srcDir, 'domain'), { recursive: true });

    const initialPolicy = {
      title: 'Dry Run App',
      src: 'src',
      prefix: '',
      order: ['domain'],
      levels: [['domain'], [], []]
    };
    fs.writeFileSync(path.join(umlDir, 'policy.json'), JSON.stringify(initialPolicy));

    fs.mkdirSync(path.join(srcDir, 'infrastructure'), { recursive: true });

    const result = updateLocalPolicy(tmpDir, { dryRun: true });
    assert.equal(result.dryRun, true);
    assert.ok(result.newPackages.includes('infrastructure'));

    // Policy on disk was not modified
    const onDisk = JSON.parse(fs.readFileSync(path.join(umlDir, 'policy.json'), 'utf8'));
    assert.deepEqual(onDisk.order, ['domain']);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
