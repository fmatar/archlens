import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import {
  checkArchitecture,
  loadProjectPolicy,
  generateSarifReport,
  generateJsonReport,
  detectPackageCycles
} from '../src/checker.js';

test('loadProjectPolicy detects .archlens/policy.json', (t) => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-policy-test-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    fs.mkdirSync(archlensDir, { recursive: true });
    const policy = { title: 'Test App', src: 'src', levels: [['domain'], ['engine']] };
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify(policy), 'utf8');

    const loaded = loadProjectPolicy(tmpDir);
    assert.ok(loaded);
    assert.equal(loaded.title, 'Test App');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('checkArchitecture returns passed: true on conforming codebase', async (t) => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-check-conforming-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    fs.mkdirSync(archlensDir, { recursive: true });
    const policy = {
      title: 'Conforming App',
      src: 'src',
      order: ['domain', 'adapters'],
      levels: [['domain'], ['adapters']]
    };
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify(policy), 'utf8');

    const domainDir = path.join(tmpDir, 'src', 'domain');
    const adapterDir = path.join(tmpDir, 'src', 'adapters');
    fs.mkdirSync(domainDir, { recursive: true });
    fs.mkdirSync(adapterDir, { recursive: true });

    fs.writeFileSync(path.join(domainDir, 'Order.java'), 'package domain;\npublic class Order {}\n', 'utf8');
    fs.writeFileSync(
      path.join(adapterDir, 'OrderController.java'),
      'package adapters;\nimport domain.Order;\npublic class OrderController {}\n',
      'utf8'
    );

    const result = await checkArchitecture(tmpDir);
    assert.equal(result.passed, true);
    assert.equal(result.violationsCount, 0);

    const sarif = JSON.parse(generateSarifReport(result, { version: '1.0.0' }));
    assert.equal(sarif.version, '2.1.0');
    assert.equal(sarif.runs[0].results.length, 0);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('checkArchitecture detects outward violations and fails when exceeding maxViolations', async (t) => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-check-violation-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    fs.mkdirSync(archlensDir, { recursive: true });
    const policy = {
      title: 'Violating App',
      src: 'src',
      order: ['domain', 'adapters'],
      levels: [['domain'], ['adapters']]
    };
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify(policy), 'utf8');

    const domainDir = path.join(tmpDir, 'src', 'domain');
    const adapterDir = path.join(tmpDir, 'src', 'adapters');
    fs.mkdirSync(domainDir, { recursive: true });
    fs.mkdirSync(adapterDir, { recursive: true });

    // Illegal outward import: domain imports adapters at line 3
    fs.writeFileSync(
      path.join(domainDir, 'OrderService.java'),
      'package domain;\n\nimport adapters.PostgresRepo;\npublic class OrderService {}\n',
      'utf8'
    );
    fs.writeFileSync(path.join(adapterDir, 'PostgresRepo.java'), 'package adapters;\npublic class PostgresRepo {}\n', 'utf8');

    const strictResult = await checkArchitecture(tmpDir, { maxViolations: 0 });
    assert.equal(strictResult.passed, false);
    assert.equal(strictResult.violationsCount, 1);
    assert.equal(strictResult.violations[0].portName, 'OrderServicePort');
    assert.equal(strictResult.violations[0].line, 3);

    const lenientResult = await checkArchitecture(tmpDir, { maxViolations: 1 });
    assert.equal(lenientResult.passed, true);

    // SARIF Report generation test
    const sarifString = generateSarifReport(strictResult, { version: '0.0.1-Alpha-14' });
    const sarif = JSON.parse(sarifString);
    assert.equal(sarif.version, '2.1.0');
    assert.equal(sarif.runs[0].tool.driver.name, 'archlens');
    assert.equal(sarif.runs[0].results.length, 1);
    assert.equal(sarif.runs[0].results[0].ruleId, 'ARCH001');
    assert.equal(sarif.runs[0].results[0].locations[0].physicalLocation.region.startLine, 3);
    assert.ok(sarif.runs[0].results[0].message.text.includes('OrderServicePort'));

    // JSON Report generation test
    const jsonString = generateJsonReport(strictResult);
    const parsedJson = JSON.parse(jsonString);
    assert.equal(parsedJson.passed, false);
    assert.equal(parsedJson.violationsCount, 1);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('detectPackageCycles finds elementary cycles accurately', () => {
  // Acyclic: A -> B -> C
  const acyclic = new Map([
    ['pkgA', new Set(['pkgB'])],
    ['pkgB', new Set(['pkgC'])],
    ['pkgC', new Set()]
  ]);
  assert.equal(detectPackageCycles(acyclic).length, 0);

  // Direct Cycle: A -> B -> A
  const direct = new Map([
    ['pkgA', new Set(['pkgB'])],
    ['pkgB', new Set(['pkgA'])]
  ]);
  const directCycles = detectPackageCycles(direct);
  assert.equal(directCycles.length, 1);
  assert.equal(directCycles[0].formatted, 'pkgA -> pkgB -> pkgA');

  // Transitive Cycle: A -> B -> C -> A
  const transitive = new Map([
    ['pkgA', new Set(['pkgB'])],
    ['pkgB', new Set(['pkgC'])],
    ['pkgC', new Set(['pkgA'])]
  ]);
  const transitiveCycles = detectPackageCycles(transitive);
  assert.equal(transitiveCycles.length, 1);
  assert.equal(transitiveCycles[0].formatted, 'pkgA -> pkgB -> pkgC -> pkgA');
});

test('checkArchitecture enforces ADP when detectCycles is true and generates ARCH002 in SARIF', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-adp-test-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    const srcDir = path.join(tmpDir, 'src');
    const pkgADir = path.join(srcDir, 'pkga');
    const pkgBDir = path.join(srcDir, 'pkgb');

    fs.mkdirSync(archlensDir, { recursive: true });
    fs.mkdirSync(pkgADir, { recursive: true });
    fs.mkdirSync(pkgBDir, { recursive: true });

    // Same tier (Level 1), so no concentric violation, but forms a package cycle
    const policy = {
      title: 'ADP Test',
      src: 'src',
      order: ['pkga', 'pkgb'],
      levels: [['pkga', 'pkgb']]
    };
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify(policy), 'utf8');

    // pkga imports pkgb
    fs.writeFileSync(
      path.join(pkgADir, 'ClassA.java'),
      'package pkga;\nimport pkgb.ClassB;\npublic class ClassA {}\n',
      'utf8'
    );
    // pkgb imports pkga (forming cycle pkga <-> pkgb)
    fs.writeFileSync(
      path.join(pkgBDir, 'ClassB.java'),
      'package pkgb;\nimport pkga.ClassA;\npublic class ClassB {}\n',
      'utf8'
    );

    // Without detectCycles: 0 outward violations -> passes
    const normalResult = await checkArchitecture(tmpDir, { detectCycles: false });
    assert.equal(normalResult.violationsCount, 0);
    assert.equal(normalResult.cyclesCount, 1);
    assert.equal(normalResult.passed, true);

    // With detectCycles: 1 cycle -> fails ADP gate
    const adpResult = await checkArchitecture(tmpDir, { detectCycles: true });
    assert.equal(adpResult.passed, false);
    assert.equal(adpResult.cyclesCount, 1);
    assert.equal(adpResult.cycles[0].formatted, 'pkga -> pkgb -> pkga');

    // SARIF report includes ARCH002
    const sarif = JSON.parse(generateSarifReport(adpResult, { version: '0.0.1-Alpha-14' }));
    assert.equal(sarif.runs[0].results.length, 1);
    assert.equal(sarif.runs[0].results[0].ruleId, 'ARCH002');
    assert.ok(sarif.runs[0].results[0].message.text.includes('pkga -> pkgb -> pkga'));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
