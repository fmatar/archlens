import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import {
  detectProjectLanguage,
  detectSourceRoot,
  findCommonPackagePrefix,
  discoverSubpackages,
  classifyLayers,
  generatePolicy,
  formatProjectTitle
} from '../src/analyzer.js';

test('detectProjectLanguage identifies languages based on build manifests', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-lang-test-'));

  try {
    // Java
    fs.writeFileSync(path.join(tmpDir, 'pom.xml'), '<project></project>');
    assert.equal(detectProjectLanguage(tmpDir), 'java');
    fs.unlinkSync(path.join(tmpDir, 'pom.xml'));

    // TypeScript
    fs.writeFileSync(path.join(tmpDir, 'package.json'), '{}');
    assert.equal(detectProjectLanguage(tmpDir), 'typescript');
    fs.unlinkSync(path.join(tmpDir, 'package.json'));

    // Python
    fs.writeFileSync(path.join(tmpDir, 'pyproject.toml'), '');
    assert.equal(detectProjectLanguage(tmpDir), 'python');
    fs.unlinkSync(path.join(tmpDir, 'pyproject.toml'));

    // Rust
    fs.writeFileSync(path.join(tmpDir, 'Cargo.toml'), '');
    assert.equal(detectProjectLanguage(tmpDir), 'rust');
    fs.unlinkSync(path.join(tmpDir, 'Cargo.toml'));

    // Go
    fs.writeFileSync(path.join(tmpDir, 'go.mod'), '');
    assert.equal(detectProjectLanguage(tmpDir), 'go');
    fs.unlinkSync(path.join(tmpDir, 'go.mod'));

    // Clojure
    fs.writeFileSync(path.join(tmpDir, 'deps.edn'), '{}');
    assert.equal(detectProjectLanguage(tmpDir), 'clojure');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('detectSourceRoot finds existing source candidate directories', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-src-test-'));

  try {
    const javaSrc = path.join(tmpDir, 'src', 'main', 'java');
    fs.mkdirSync(javaSrc, { recursive: true });

    assert.equal(detectSourceRoot(tmpDir, 'java'), 'src/main/java');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('findCommonPackagePrefix finds common prefix across Java source files', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-pkg-test-'));

  try {
    const pkgDirA = path.join(tmpDir, 'com', 'example', 'eshop', 'domain');
    const pkgDirB = path.join(tmpDir, 'com', 'example', 'eshop', 'adapter');
    fs.mkdirSync(pkgDirA, { recursive: true });
    fs.mkdirSync(pkgDirB, { recursive: true });

    fs.writeFileSync(
      path.join(pkgDirA, 'Order.java'),
      'package com.example.eshop.domain;\npublic class Order {}\n'
    );
    fs.writeFileSync(
      path.join(pkgDirB, 'OrderController.java'),
      'package com.example.eshop.adapter;\npublic class OrderController {}\n'
    );

    const prefix = findCommonPackagePrefix(tmpDir);
    assert.equal(prefix, 'com.example.eshop');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('classifyLayers sorts packages into concentric Clean Architecture tiers', () => {
  const subpackages = ['domain', 'usecase', 'controller', 'infrastructure', 'config'];
  const { order, levels } = classifyLayers(subpackages);

  // Level 0: Domain
  assert.ok(levels[0].includes('domain'));

  // Level 1: Application
  assert.ok(levels[1].includes('usecase'));

  // Level 2: Adapters
  assert.ok(levels[2].includes('controller'));

  // Level 3: Infrastructure
  assert.ok(levels[3].includes('infrastructure'));
  assert.ok(levels[3].includes('config'));

  // Order contains all subpackages in sequence
  assert.equal(order.length, 5);
  assert.equal(order[0], 'domain');
});

test('generatePolicy creates complete policy matching Archlens schema', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-policy-test-'));

  try {
    const src = path.join(tmpDir, 'src');
    const domainPkg = path.join(src, 'domain');
    const appPkg = path.join(src, 'service');
    const adapterPkg = path.join(src, 'api');
    fs.mkdirSync(domainPkg, { recursive: true });
    fs.mkdirSync(appPkg, { recursive: true });
    fs.mkdirSync(adapterPkg, { recursive: true });

    const policy = generatePolicy(tmpDir, {
      title: 'Sample Service',
      prefix: 'com.example'
    });

    assert.equal(policy.title, 'Sample Service');
    assert.equal(policy.src, 'src');
    assert.equal(policy.prefix, 'com.example');
    assert.equal(policy.hierarchical, true);
    assert.ok(Array.isArray(policy.order));
    assert.ok(Array.isArray(policy.levels));
    assert.ok(policy.order.includes('domain'));
    assert.ok(policy.order.includes('service'));
    assert.ok(policy.order.includes('api'));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('formatProjectTitle formats hyphenated and underscored names nicely', () => {
  assert.equal(formatProjectTitle('my-cool_service'), 'My Cool Service');
  assert.equal(formatProjectTitle('archlens'), 'Archlens');
});
