/**
 * Shared Backend-vs-Offline Engine Parity Harness (#244)
 *
 * Runs identical Java (including nested subpackages), Python, and TypeScript Clean Architecture
 * fixtures through both `checkArchitectureViaBackend` (Quarkus scanners) and `checkArchitecture`
 * (offline JS lexical fallback), asserting 100% parity on pass/fail status, violation counts,
 * `(fromPkg, toPkg)` pairs, `fromFile`, `portName`, `fromTier`, `toTier`, `sourceFilesCount`,
 * `screaming.score`, `screamingThreshold`, and ADP cycle counts — plus live Quarkus backend
 * enforcement verification on Rust (`.rs`) and Go (`.go`) violating fixtures.
 */

import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { checkArchitecture } from '../cli/src/checker.js';
import { checkArchitectureViaBackend } from '../cli/src/backend-checker.js';

function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, JSON.stringify(value, null, 2), 'utf8');
}

function writeText(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, content, 'utf8');
}

export function createSharedParityFixtures(baseDir) {
  const fixtures = [];

  // 1. Java — conforming
  const javaOk = path.join(baseDir, 'java-conforming');
  writeJson(path.join(javaOk, '.archlens', 'policy.json'), {
    title: 'Java Conforming',
    src: 'src/main/java',
    lang: 'java',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeText(
    path.join(javaOk, 'src/main/java/domain/Order.java'),
    'package domain;\npublic class Order {}\n'
  );
  writeText(
    path.join(javaOk, 'src/main/java/adapters/OrderController.java'),
    'package adapters;\nimport domain.Order;\npublic class OrderController { Order load() { return new Order(); } }\n'
  );
  fixtures.push({
    name: 'java-conforming',
    dir: javaOk,
    expectedPassed: true,
    expectedViolations: [],
    expectedCyclesCount: 0,
    expectedSourceFiles: 2
  });

  // 2. Java — outward violation + ADP package cycle
  const javaBad = path.join(baseDir, 'java-violating');
  writeJson(path.join(javaBad, '.archlens', 'policy.json'), {
    title: 'Java Violating',
    src: 'src/main/java',
    lang: 'java',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeText(
    path.join(javaBad, 'src/main/java/domain/OrderService.java'),
    'package domain;\nimport adapters.PostgresRepo;\npublic class OrderService {}\n'
  );
  writeText(
    path.join(javaBad, 'src/main/java/adapters/PostgresRepo.java'),
    'package adapters;\nimport domain.OrderService;\npublic class PostgresRepo {}\n'
  );
  fixtures.push({
    name: 'java-violating',
    dir: javaBad,
    expectedPassed: false,
    expectedViolations: ['domain->adapters'],
    expectedFromFile: path.join('src', 'main', 'java', 'domain', 'OrderService.java'),
    expectedPortName: 'OrderServicePort',
    expectedCyclesCount: 1,
    expectedSourceFiles: 2
  });

  // 3. Java — nested subpackage precedence (`core.orders` -> `service.billing`)
  const javaNested = path.join(baseDir, 'java-nested');
  writeJson(path.join(javaNested, '.archlens', 'policy.json'), {
    title: 'Java Nested',
    src: 'src/main/java',
    lang: 'java',
    order: ['core', 'service'],
    levels: [['core'], ['service']]
  });
  writeText(
    path.join(javaNested, 'src/main/java/core/orders/OrderProcessor.java'),
    'package core.orders;\nimport service.billing.BillingAdapter;\npublic class OrderProcessor {}\n'
  );
  writeText(
    path.join(javaNested, 'src/main/java/service/billing/BillingAdapter.java'),
    'package service.billing;\npublic class BillingAdapter {}\n'
  );
  fixtures.push({
    name: 'java-nested',
    dir: javaNested,
    expectedPassed: false,
    expectedViolations: ['core->service'],
    expectedFromFile: path.join('src', 'main', 'java', 'core', 'orders', 'OrderProcessor.java'),
    expectedPortName: 'OrderProcessorPort',
    expectedCyclesCount: 0,
    expectedSourceFiles: 2
  });

  // 4. Python — conforming
  const pyOk = path.join(baseDir, 'python-conforming');
  writeJson(path.join(pyOk, '.archlens', 'policy.json'), {
    title: 'Python Conforming',
    src: 'src',
    lang: 'python',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeText(path.join(pyOk, 'pyproject.toml'), '[project]\nname = "py-ok"\n');
  writeText(path.join(pyOk, 'src/domain/order.py'), 'class Order:\n    pass\n');
  writeText(
    path.join(pyOk, 'src/adapters/controller.py'),
    'from domain.order import Order\n\nclass OrderController:\n    pass\n'
  );
  fixtures.push({
    name: 'python-conforming',
    dir: pyOk,
    expectedPassed: true,
    expectedViolations: [],
    expectedCyclesCount: 0,
    expectedSourceFiles: 2
  });

  // 5. Python — multi-line outward violation
  const pyBad = path.join(baseDir, 'python-violating');
  writeJson(path.join(pyBad, '.archlens', 'policy.json'), {
    title: 'Python Violating',
    src: 'src',
    lang: 'python',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeText(path.join(pyBad, 'pyproject.toml'), '[project]\nname = "py-bad"\n');
  writeText(
    path.join(pyBad, 'src/adapters/postgres_repo.py'),
    'class PostgresRepo:\n    pass\n'
  );
  writeText(
    path.join(pyBad, 'src/domain/order_service.py'),
    'from adapters.postgres_repo import (\n    PostgresRepo,\n)\n\nclass OrderService:\n    pass\n'
  );
  fixtures.push({
    name: 'python-violating',
    dir: pyBad,
    expectedPassed: false,
    expectedViolations: ['domain->adapters'],
    expectedFromFile: path.join('src', 'domain', 'order_service.py'),
    expectedPortName: 'order_servicePort',
    expectedCyclesCount: 0,
    expectedSourceFiles: 2
  });

  // 6. TypeScript — conforming
  const tsOk = path.join(baseDir, 'ts-conforming');
  writeJson(path.join(tsOk, '.archlens', 'policy.json'), {
    title: 'TS Conforming',
    src: 'src',
    lang: 'typescript',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeJson(path.join(tsOk, 'tsconfig.json'), { compilerOptions: { strict: true } });
  writeText(path.join(tsOk, 'src/domain/Order.ts'), 'export class Order {}\n');
  writeText(
    path.join(tsOk, 'src/adapters/OrderController.ts'),
    "import { Order } from '../domain/Order';\nexport class OrderController { item?: Order; }\n"
  );
  fixtures.push({
    name: 'ts-conforming',
    dir: tsOk,
    expectedPassed: true,
    expectedViolations: [],
    expectedCyclesCount: 0,
    expectedSourceFiles: 2
  });

  // 7. TypeScript — multi-line ESM outward violation
  const tsBad = path.join(baseDir, 'ts-violating');
  writeJson(path.join(tsBad, '.archlens', 'policy.json'), {
    title: 'TS Violating',
    src: 'src',
    lang: 'typescript',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeJson(path.join(tsBad, 'tsconfig.json'), { compilerOptions: { strict: true } });
  writeText(path.join(tsBad, 'src/adapters/PostgresRepo.ts'), 'export class PostgresRepo {}\n');
  writeText(
    path.join(tsBad, 'src/domain/OrderService.ts'),
    "import {\n  PostgresRepo\n} from '../adapters/PostgresRepo';\nexport class OrderService { repo?: PostgresRepo; }\n"
  );
  fixtures.push({
    name: 'ts-violating',
    dir: tsBad,
    expectedPassed: false,
    expectedViolations: ['domain->adapters'],
    expectedFromFile: path.join('src', 'domain', 'OrderService.ts'),
    expectedPortName: 'OrderServicePort',
    expectedCyclesCount: 0,
    expectedSourceFiles: 2
  });

  return fixtures;
}

export function createBackendRustAndGoFixtures(baseDir) {
  const rsBad = path.join(baseDir, 'rust-violating');
  writeJson(path.join(rsBad, '.archlens', 'policy.json'), {
    title: 'Rust Violating',
    src: 'src',
    lang: 'rust',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeText(path.join(rsBad, 'Cargo.toml'), '[package]\nname = "rs-bad"\nversion = "0.1.0"\n');
  writeText(path.join(rsBad, 'src/adapters/postgres.rs'), 'pub struct PostgresRepo;\n');
  writeText(
    path.join(rsBad, 'src/domain/order.rs'),
    '// header\nuse crate::adapters::postgres::PostgresRepo;\npub struct OrderService;\n'
  );

  const goBad = path.join(baseDir, 'go-violating');
  writeJson(path.join(goBad, '.archlens', 'policy.json'), {
    title: 'Go Violating',
    src: '.',
    lang: 'go',
    order: ['domain', 'adapters'],
    levels: [['domain'], ['adapters']]
  });
  writeText(path.join(goBad, 'go.mod'), 'module example.com/gobad\n\ngo 1.23\n');
  writeText(path.join(goBad, 'internal/adapters/repo.go'), 'package adapters\ntype Repo struct{}\n');
  writeText(
    path.join(goBad, 'internal/domain/helper.go'),
    'package domain\ntype Helper struct{}\n'
  );
  writeText(
    path.join(goBad, 'internal/domain/service.go'),
    'package domain\n\nimport (\n\t"example.com/gobad/internal/adapters"\n)\n\ntype Service struct{}\n'
  );

  return [
    {
      name: 'rust-violating',
      dir: rsBad,
      expectedFromFile: path.join('src', 'domain', 'order.rs'),
      expectedLine: 2
    },
    {
      name: 'go-violating',
      dir: goBad,
      expectedFromFile: path.join('internal', 'domain', 'service.go'),
      expectedLine: 4
    }
  ];
}

export async function runEngineParityChecks(serverUrl, token, deps = {}) {
  const tmpBase = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-engine-parity-'));
  try {
    const fixtures = createSharedParityFixtures(tmpBase);
    for (const fx of fixtures) {
      const gateOpts = {
        serverUrl,
        maxViolations: 0,
        detectCycles: true,
        screamingThreshold: null
      };
      const backendAttempt = await checkArchitectureViaBackend(fx.dir, gateOpts, {
        checkServerHealth: async () => ({ ok: true, version: '1.2.0' }),
        resolveSessionToken: () => token,
        ...(deps.fetch ? { fetch: deps.fetch } : {})
      });
      assert.equal(backendAttempt.delegated, true, `Backend delegation failed for ${fx.name}`);

      const backendReport = backendAttempt.report;
      const offlineReport = await checkArchitecture(fx.dir, gateOpts);

      const backendPairs = backendReport.violations
        .map((v) => `${v.fromPkg}->${v.toPkg}`)
        .sort();
      const offlinePairs = offlineReport.violations
        .map((v) => `${v.fromPkg}->${v.toPkg}`)
        .sort();

      assert.equal(backendReport.passed, fx.expectedPassed, `${fx.name} backend passed mismatch`);
      assert.equal(offlineReport.passed, fx.expectedPassed, `${fx.name} offline passed mismatch`);
      assert.equal(backendReport.screamingThreshold, offlineReport.screamingThreshold);
      assert.equal(backendReport.sourceFilesCount, fx.expectedSourceFiles);
      assert.equal(offlineReport.sourceFilesCount, fx.expectedSourceFiles);
      assert.equal(backendReport.screaming.score, offlineReport.screaming.score);
      assert.equal(
        backendReport.violationsCount,
        offlineReport.violationsCount,
        `${fx.name} violationsCount mismatch`
      );
      assert.deepEqual(backendPairs, offlinePairs, `${fx.name} violation package pairs mismatch`);
      assert.deepEqual(backendPairs, fx.expectedViolations, `${fx.name} expected pairs mismatch`);
      assert.equal(backendReport.cyclesCount, offlineReport.cyclesCount);
      assert.equal(backendReport.cyclesCount, fx.expectedCyclesCount);

      if (fx.expectedFromFile) {
        assert.equal(backendReport.violations[0].fromFile, fx.expectedFromFile);
        assert.equal(offlineReport.violations[0].fromFile, fx.expectedFromFile);
        assert.equal(backendReport.violations[0].portName, fx.expectedPortName);
        assert.equal(offlineReport.violations[0].portName, fx.expectedPortName);
        assert.equal(backendReport.violations[0].fromTier, offlineReport.violations[0].fromTier);
        assert.equal(backendReport.violations[0].toTier, offlineReport.violations[0].toTier);
      }
    }

    const rsGoFixtures = createBackendRustAndGoFixtures(tmpBase);
    for (const fx of rsGoFixtures) {
      const backendAttempt = await checkArchitectureViaBackend(
        fx.dir,
        { serverUrl, maxViolations: 0, detectCycles: true },
        {
          checkServerHealth: async () => ({ ok: true, version: '1.2.0' }),
          resolveSessionToken: () => token,
          ...(deps.fetch ? { fetch: deps.fetch } : {})
        }
      );
      assert.equal(backendAttempt.delegated, true, `Backend delegation failed for ${fx.name}`);
      assert.equal(backendAttempt.report.passed, false);
      assert.equal(backendAttempt.report.violationsCount, 1);
      assert.equal(backendAttempt.report.violations[0].fromPkg, 'domain');
      assert.equal(backendAttempt.report.violations[0].toPkg, 'adapters');
      assert.equal(backendAttempt.report.violations[0].fromFile, fx.expectedFromFile);
      assert.equal(backendAttempt.report.violations[0].line, fx.expectedLine);
    }

    return { fixturesVerified: fixtures.length + rsGoFixtures.length };
  } finally {
    fs.rmSync(tmpBase, { recursive: true, force: true });
  }
}
