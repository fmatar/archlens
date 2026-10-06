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
  detectPackageCycles,
  calculateScreamingMetric,
  extractFeatureToken,
  suggestFeatureClusters,
  calculateArchitectureFitness
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
    const sarifString = generateSarifReport(strictResult, { version: '0.1.0-Beta-04' });
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
    const sarif = JSON.parse(generateSarifReport(adpResult, { version: '0.1.0-Beta-04' }));
    assert.equal(sarif.runs[0].results.length, 1);
    assert.equal(sarif.runs[0].results[0].ruleId, 'ARCH002');
    assert.ok(sarif.runs[0].results[0].message.text.includes('pkga -> pkgb -> pkga'));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('calculateScreamingMetric correctly calculates SAS score and classification', () => {
  // Empty
  const empty = calculateScreamingMetric([]);
  assert.equal(empty.score, 1.0);
  assert.equal(empty.classification, 'PACKAGE_BY_FEATURE');

  // Package by feature (domain packages)
  const features = calculateScreamingMetric(['com.app.billing', 'com.app.shipping', 'com.app.inventory', 'com.app.auth']);
  assert.equal(features.score, 1.0);
  assert.equal(features.classification, 'PACKAGE_BY_FEATURE');
  assert.equal(features.domainPackageCount, 4);
  assert.equal(features.technicalPackageCount, 0);

  // Package by layer (technical packages)
  const layers = calculateScreamingMetric(['com.app.controllers', 'com.app.services', 'com.app.repositories', 'com.app.dtos']);
  assert.equal(layers.score, 0.0);
  assert.equal(layers.classification, 'PACKAGE_BY_LAYER');
  assert.equal(layers.domainPackageCount, 0);
  assert.equal(layers.technicalPackageCount, 4);

  // Hybrid
  const hybrid = calculateScreamingMetric(['com.app.orders', 'com.app.billing', 'com.app.controllers', 'com.app.repositories']);
  assert.equal(hybrid.score, 0.5);
  assert.equal(hybrid.classification, 'HYBRID');
  assert.equal(hybrid.domainPackageCount, 2);
  assert.equal(hybrid.technicalPackageCount, 2);
});

test('checkArchitecture enforces screamingThreshold and produces ARCH003 in SARIF', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-screaming-test-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    const srcDir = path.join(tmpDir, 'src');
    const controllersDir = path.join(srcDir, 'controllers');
    const servicesDir = path.join(srcDir, 'services');

    fs.mkdirSync(archlensDir, { recursive: true });
    fs.mkdirSync(controllersDir, { recursive: true });
    fs.mkdirSync(servicesDir, { recursive: true });

    // Technical layered architecture: score = 0.0
    const policy = {
      title: 'Layered App',
      src: 'src',
      order: ['services', 'controllers'],
      levels: [['services'], ['controllers']]
    };
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify(policy), 'utf8');

    fs.writeFileSync(
      path.join(servicesDir, 'OrderService.java'),
      'package services;\npublic class OrderService {}\n',
      'utf8'
    );
    fs.writeFileSync(
      path.join(controllersDir, 'OrderController.java'),
      'package controllers;\nimport services.OrderService;\npublic class OrderController {}\n',
      'utf8'
    );

    // Without threshold: passes (0 outward violations)
    const passResult = await checkArchitecture(tmpDir);
    assert.equal(passResult.passed, true);
    assert.equal(passResult.screaming.score, 0.0);
    assert.equal(passResult.screaming.classification, 'PACKAGE_BY_LAYER');

    // With threshold 0.7: fails due to screaming threshold
    const failResult = await checkArchitecture(tmpDir, { screamingThreshold: 0.7 });
    assert.equal(failResult.passed, false);
    assert.equal(failResult.screamingThreshold, 0.7);

    // SARIF contains ARCH003
    const sarif = JSON.parse(generateSarifReport(failResult, { version: '0.0.1-Alpha-14' }));
    const arch003 = sarif.runs[0].results.find((r) => r.ruleId === 'ARCH003');
    assert.ok(arch003, 'Should contain ARCH003 rule in results');
    assert.ok(arch003.message.text.includes('Screaming Architecture Violation'));
    assert.ok(arch003.message.text.includes('PACKAGE_BY_LAYER'));

    const ruleDef = sarif.runs[0].tool.driver.rules.find((r) => r.id === 'ARCH003');
    assert.ok(ruleDef, 'Should define ARCH003 rule in driver');
    assert.equal(ruleDef.name, 'ScreamingArchitectureRule');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('extractFeatureToken correctly extracts domain feature names from technical class names', () => {
  assert.equal(extractFeatureToken('OrderController'), 'Order');
  assert.equal(extractFeatureToken('OrderService'), 'Order');
  assert.equal(extractFeatureToken('OrderRepository'), 'Order');
  assert.equal(extractFeatureToken('PostgresOrderRepository'), 'Order');
  assert.equal(extractFeatureToken('InvoiceResource'), 'Invoice');
  assert.equal(extractFeatureToken('GlobalDateHelper'), 'GlobalDate');
  assert.equal(extractFeatureToken('Base'), null);
});

test('suggestFeatureClusters proposes cohesive domain packages and calculates SAS gain', () => {
  const packages = ['com.app.controllers', 'com.app.services', 'com.app.repositories'];
  const sourceFiles = [
    '/root/src/com/app/controllers/OrderController.java',
    '/root/src/com/app/services/OrderService.java',
    '/root/src/com/app/repositories/OrderRepository.java',
    '/root/src/com/app/controllers/InvoiceController.java',
    '/root/src/com/app/services/InvoiceService.java'
  ];

  const proposal = suggestFeatureClusters(packages, sourceFiles);
  assert.equal(proposal.currentScore, 0.0);
  assert.equal(proposal.currentClassification, 'PACKAGE_BY_LAYER');
  assert.equal(proposal.projectedScore, 1.0);
  assert.equal(proposal.projectedClassification, 'PACKAGE_BY_FEATURE');
  assert.equal(proposal.clusters.length, 2);

  const orderCluster = proposal.clusters.find((c) => c.featureName === 'Order');
  assert.ok(orderCluster);
  assert.equal(orderCluster.proposedPackageName, 'com.app.order');
  assert.equal(orderCluster.classCount, 3);
  assert.deepEqual(orderCluster.classNames.sort(), ['OrderController', 'OrderRepository', 'OrderService']);

  const invoiceCluster = proposal.clusters.find((c) => c.featureName === 'Invoice');
  assert.ok(invoiceCluster);
  assert.equal(invoiceCluster.proposedPackageName, 'com.app.invoice');
  assert.equal(invoiceCluster.classCount, 2);

  assert.equal(proposal.stagedClassMoves['OrderController'], 'com.app.order');
  assert.equal(proposal.stagedClassMoves['InvoiceService'], 'com.app.invoice');
});

test('checkArchitecture with suggestFeatures flag returns refactoring proposal', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-check-suggest-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    const srcDir = path.join(tmpDir, 'src');
    const controllersDir = path.join(srcDir, 'controllers');
    const servicesDir = path.join(srcDir, 'services');

    fs.mkdirSync(archlensDir, { recursive: true });
    fs.mkdirSync(controllersDir, { recursive: true });
    fs.mkdirSync(servicesDir, { recursive: true });

    const policy = {
      title: 'Layered App',
      src: 'src',
      order: ['controllers', 'services'],
      levels: [['controllers'], ['services']]
    };
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify(policy), 'utf8');

    fs.writeFileSync(
      path.join(controllersDir, 'OrderController.java'),
      'package controllers;\npublic class OrderController {}\n',
      'utf8'
    );
    fs.writeFileSync(
      path.join(servicesDir, 'OrderService.java'),
      'package services;\npublic class OrderService {}\n',
      'utf8'
    );

    const result = await checkArchitecture(tmpDir, { suggestFeatures: true });
    assert.ok(result.featureClusters);
    assert.equal(result.featureClusters.clusters.length, 1);
    assert.equal(result.featureClusters.clusters[0].featureName, 'Order');
    assert.equal(result.featureClusters.clusters[0].proposedPackageName, 'order');
    assert.equal(result.featureClusters.projectedScore, 1.0);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('calculateArchitectureFitness calculates composite fitness score and grade', () => {
  const perfect = calculateArchitectureFitness({
    violationsCount: 0,
    cyclesCount: 0,
    screamingScore: 0.90,
    maxDistance: 0.10
  });

  assert.equal(perfect.overallPassed, true);
  assert.equal(perfect.grade, 'A');
  assert.ok(perfect.fitnessScore >= 0.90);
  assert.equal(perfect.totalRuleCount, 4);
  assert.equal(perfect.passedRuleCount, 4);

  const failingViolations = calculateArchitectureFitness({
    violationsCount: 3,
    cyclesCount: 0,
    screamingScore: 0.85,
    maxDistance: 0.12
  });

  assert.equal(failingViolations.overallPassed, false);
  const concentric = failingViolations.rules.find((r) => r.id === 'CONCENTRIC_DEPENDENCY_RULE');
  assert.equal(concentric.passed, false);
  assert.equal(concentric.actualValue, 3);
});

test('checkArchitecture enforces fitnessThreshold in options', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-check-fitness-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    const srcDir = path.join(tmpDir, 'src');
    const domainDir = path.join(srcDir, 'domain');

    fs.mkdirSync(archlensDir, { recursive: true });
    fs.mkdirSync(domainDir, { recursive: true });

    const policy = {
      title: 'Clean App',
      src: 'src',
      order: ['domain'],
      levels: [['domain']]
    };
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify(policy), 'utf8');
    fs.writeFileSync(
      path.join(domainDir, 'Order.java'),
      'package domain;\npublic class Order {}\n',
      'utf8'
    );

    // Pass with 0.80 threshold
    const passResult = await checkArchitecture(tmpDir, { fitnessThreshold: 0.80 });
    assert.ok(passResult.fitness);
    assert.equal(passResult.passed, true);
    assert.ok(passResult.fitness.fitnessScore >= 0.80);

    // Fail with impossible 1.05 threshold
    const failResult = await checkArchitecture(tmpDir, { fitnessThreshold: 1.05 });
    assert.equal(failResult.passed, false);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});


