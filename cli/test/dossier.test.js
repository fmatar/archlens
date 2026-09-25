import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import { generateLlmPrompt } from '../src/analyzer.js';

test('generateLlmPrompt generates conforming dossier offline for compliant codebase', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-prompt-test-'));

  try {
    const domainDir = path.join(tmpDir, 'src', 'main', 'java', 'com', 'example', 'domain');
    const appDir = path.join(tmpDir, 'src', 'main', 'java', 'com', 'example', 'application');
    fs.mkdirSync(domainDir, { recursive: true });
    fs.mkdirSync(appDir, { recursive: true });

    fs.writeFileSync(
      path.join(domainDir, 'Order.java'),
      'package com.example.domain;\npublic class Order {}\n'
    );
    fs.writeFileSync(
      path.join(appDir, 'OrderService.java'),
      'package com.example.application;\nimport com.example.domain.Order;\npublic class OrderService {}\n'
    );

    const dossier = await generateLlmPrompt(tmpDir, { serverUrl: 'http://127.0.0.1:59999' });

    assert.ok(dossier.includes('# Clean Architecture Optimization Dossier'));
    assert.ok(dossier.includes('Concentric Rings'));
    assert.ok(dossier.includes('Clean Architecture Status'));
    assert.ok(dossier.includes('Conforming'));
    assert.ok(dossier.includes('Actionable LLM Refactoring Instructions'));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('generateLlmPrompt detects outward violations and prescribes DIP interface ports offline', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-prompt-violation-'));

  try {
    const domainDir = path.join(tmpDir, 'src', 'main', 'java', 'com', 'example', 'domain');
    const infraDir = path.join(tmpDir, 'src', 'main', 'java', 'com', 'example', 'infrastructure');
    fs.mkdirSync(domainDir, { recursive: true });
    fs.mkdirSync(infraDir, { recursive: true });

    // Illegal outward import: domain importing infrastructure
    fs.writeFileSync(
      path.join(domainDir, 'User.java'),
      'package com.example.domain;\nimport com.example.infrastructure.DatabaseClient;\npublic class User {}\n'
    );
    fs.writeFileSync(
      path.join(infraDir, 'DatabaseClient.java'),
      'package com.example.infrastructure;\npublic class DatabaseClient {}\n'
    );

    const dossier = await generateLlmPrompt(tmpDir, { serverUrl: 'http://127.0.0.1:59999' });

    assert.ok(dossier.includes('Dependency Rule Violation'));
    assert.ok(dossier.includes('Dependency Inversion Principle (DIP)'));
    assert.ok(dossier.includes('UserPort'));
    assert.ok(dossier.includes('Define an interface port'));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
