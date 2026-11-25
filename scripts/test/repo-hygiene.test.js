import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function listCandidateFiles() {
  const out = execFileSync(
    'git',
    ['ls-files', '--cached', '--others', '--exclude-standard'],
    { cwd: REPO_ROOT, encoding: 'utf8' }
  );
  return Array.from(
    new Set(
      out
        .split('\n')
        .map((f) => f.trim())
        .filter(Boolean)
    )
  ).filter((f) => fs.existsSync(path.join(REPO_ROOT, f)));
}

test('Phase 2 repo hygiene (#237, #238, #239): no personal paths, legacy aliases, Clojure scanner, or .uml-viewer artifacts', () => {
  const exemptHistorical = new Set([
    'CHANGELOG.md',
    'docs/DECISION_MAP.md',
    'docs/AUDIT_CHAIN.jsonl',
    'scripts/test/repo-hygiene.test.js'
  ]);

  const forbidden = [
    { label: 'hardcoded personal path (/Users/fady)', pattern: /\/Users\/fady\b/ },
    { label: 'legacy alias (agentlens)', pattern: /\bagentlens\b/ },
    { label: 'legacy alias (unclebob-design)', pattern: /\bunclebob-design\b/ },
    { label: 'legacy directory (.uml-viewer)', pattern: /\.uml-viewer\b/ },
    { label: 'removed Clojure scanner (ClojureAstScanner)', pattern: /\bClojureAstScanner\b/ },
    {
      label: 'renamed non-Java *AstScanner (#243)',
      pattern: /\b(?:Python|TypeScript|Go|Rust)AstScanner\b/
    }
  ];

  const clojureExtPattern = /\.(?:clj|cljs|cljc)\b/;

  const files = listCandidateFiles();
  assert.equal(
    files.some(
      (f) => f.startsWith('.metrics/') || f.startsWith('.grok/') || f.includes('.uml-viewer/')
    ),
    false,
    '.metrics/, .grok/, and .uml-viewer/ prototype artifacts must not exist in repo'
  );

  const violations = [];
  for (const rel of files) {
    if (exemptHistorical.has(rel) || rel.startsWith('docs/adr/')) {
      continue;
    }
    for (const { label, pattern } of forbidden) {
      if (pattern.test(rel)) {
        violations.push(`${rel} (path): matched ${label}`);
      }
    }
    const full = path.join(REPO_ROOT, rel);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) {
      continue;
    }
    if (rel.endsWith('.md') && stat.size === 0) {
      violations.push(`${rel}: markdown file must not be truncated to 0 bytes`);
      continue;
    }
    if (/\.(png|jpg|jpeg|gif|mp4|webm|ico|woff2?|ttf|pdf)$/i.test(rel) || rel.includes('/scripts/bin/')) {
      continue;
    }
    const content = fs.readFileSync(full, 'utf8');
    for (const { label, pattern } of forbidden) {
      if (pattern.test(content)) {
        violations.push(`${rel}: matched ${label}`);
      }
    }
    if ((rel.startsWith('docs/') || rel.startsWith('wiki/') || rel === 'README.md') && clojureExtPattern.test(content)) {
      violations.push(`${rel}: stale Clojure extension reference (.clj/.cljs/.cljc)`);
    }
  }

  assert.deepEqual(violations, []);
});

test('Phase 2 setup & Node engine consistency (#237): engines.node >=22.0.0 and no broken ./mvnw doc commands', () => {
  for (const manifestRel of ['package.json', 'cli/package.json', 'frontend/package.json']) {
    const pkg = JSON.parse(fs.readFileSync(path.join(REPO_ROOT, manifestRel), 'utf8'));
    assert.equal(pkg.engines?.node, '>=22.0.0', `${manifestRel} must declare engines.node >=22.0.0`);
  }

  for (const docRel of ['README.md', 'CONTRIBUTING.md', 'docs/guide/DEVELOPER_GUIDE.md']) {
    const doc = fs.readFileSync(path.join(REPO_ROOT, docRel), 'utf8');
    assert.equal(doc.includes('./mvnw'), false, `${docRel} must not reference non-existent ./mvnw`);
  }

  const notice = fs.readFileSync(path.join(REPO_ROOT, 'NOTICE'), 'utf8');
  assert.match(notice, /Copyright 2026 Fady Matar/);
  assert.match(notice, /e05c9bf/);
});

test('Phase 3 repo hygiene (#242): one lockfile per package, single TypeScript compiler, no vendored binaries, and no non-LFS files > 5 MB', () => {
  const MAX_BLOB_BYTES = 5 * 1024 * 1024;
  const files = listCandidateFiles();

  // 1. One lockfile per package
  for (const pkgDir of ['', 'cli/', 'frontend/']) {
    const lockNames = ['package-lock.json', 'pnpm-lock.yaml', 'yarn.lock', 'bun.lockb', 'bun.lock'];
    const present = lockNames.filter((name) => files.includes(`${pkgDir}${name}`));
    assert.ok(
      present.length <= 1,
      `Expected at most 1 lockfile in '${pkgDir || '.'}', found: ${present.join(', ')}`
    );
  }
  assert.ok(files.includes('frontend/pnpm-lock.yaml'), 'frontend/pnpm-lock.yaml must exist');

  // 2. Single TypeScript compiler dependency in frontend/package.json
  const frontendPkg = JSON.parse(
    fs.readFileSync(path.join(REPO_ROOT, 'frontend/package.json'), 'utf8')
  );
  assert.ok(frontendPkg.devDependencies?.typescript, 'frontend must declare typescript');
  assert.equal(
    '@typescript/native' in (frontendPkg.devDependencies || {}),
    false,
    'frontend/package.json must not declare duplicate @typescript/native compiler'
  );

  // 3. No vendored skill binaries or non-LFS blobs > 5 MB
  const oversized = [];
  const vendoredBins = [];
  for (const rel of files) {
    if (/\/skills\/[^/]+\/scripts\/bin\//.test(`/${rel}`)) {
      vendoredBins.push(rel);
    }
    const full = path.join(REPO_ROOT, rel);
    const stat = fs.statSync(full);
    if (stat.isFile() && stat.size > MAX_BLOB_BYTES) {
      oversized.push(`${rel} (${(stat.size / (1024 * 1024)).toFixed(2)} MB)`);
    }
  }
  assert.deepEqual(vendoredBins, [], 'Vendored skill binaries must not be tracked in git');
  assert.deepEqual(oversized, [], 'Non-LFS tracked files must not exceed 5 MB');

  // 4. Only the single unified 'archlens' skill may be tracked in git (zero leaked dev skills in .agents/, .github/skills/, etc.)
  const skillMdFiles = files.filter((rel) => /(?:^|\/)SKILL\.md$/.test(rel)).sort();
  assert.deepEqual(
    skillMdFiles,
    ['cli/skills/archlens/SKILL.md', 'skills/archlens/SKILL.md'],
    'Only the unified archlens skill (skills/archlens & cli/skills/archlens) may be tracked in git so npx skills add fmatar/archlens never leaks dev or legacy skills'
  );
});
