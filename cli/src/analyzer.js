/**
 * Archlens Codebase Analyzer
 * Inspects any project repository to detect language, source roots, package prefixes,
 * and classifies architectural packages into concentric Clean Architecture layers.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

export function detectProjectLanguage(projectRoot) {
  const root = path.resolve(projectRoot);

  const exists = (rel) => fs.existsSync(path.join(root, rel));

  if (exists('pom.xml') || exists('build.gradle') || exists('build.gradle.kts')) {
    if (findMatchingFiles(root, (f) => f.endsWith('.kt'), 5).length > 0) {
      return 'kotlin';
    }
    return 'java';
  }

  if (exists('package.json') || exists('tsconfig.json')) {
    return 'typescript';
  }

  if (exists('pyproject.toml') || exists('setup.py') || exists('requirements.txt')) {
    return 'python';
  }

  if (exists('Cargo.toml')) {
    return 'rust';
  }

  if (exists('go.mod')) {
    return 'go';
  }

  if (exists('project.clj') || exists('deps.edn')) {
    return 'clojure';
  }

  if (exists('CMakeLists.txt') || exists('Makefile')) {
    return 'cpp';
  }

  return 'java';
}

export function detectSourceRoot(projectRoot, lang) {
  const root = path.resolve(projectRoot);

  const candidateMap = {
    java: ['src/main/java', 'backend/src/main/java', 'core/src/main/java', 'src'],
    kotlin: ['src/main/kotlin', 'src/main/java', 'backend/src/main/kotlin', 'src'],
    typescript: ['src', 'frontend/src', 'lib', 'app', '.'],
    python: ['src', 'app', 'lib', '.'],
    rust: ['src'],
    go: ['pkg', 'internal', 'cmd', '.'],
    clojure: ['src', 'src/clj'],
    cpp: ['src', 'include', '.']
  };

  const candidates = candidateMap[lang] || ['src', '.'];

  for (const cand of candidates) {
    const p = path.join(root, cand);
    if (fs.existsSync(p) && fs.statSync(p).isDirectory()) {
      return cand;
    }
  }

  return 'src';
}

export function findCommonPackagePrefix(sourceDir) {
  const resolved = path.resolve(sourceDir);
  if (!fs.existsSync(resolved)) {
    return '';
  }

  const javaFiles = findMatchingFiles(resolved, (f) => f.endsWith('.java') || f.endsWith('.kt'), 100);
  if (javaFiles.length === 0) {
    return '';
  }

  const packages = [];
  const packageRegex = /^\s*package\s+([a-zA-Z0-9_.]+)\s*;/m;

  for (const file of javaFiles) {
    try {
      const content = fs.readFileSync(file, 'utf8');
      const match = content.match(packageRegex);
      if (match && match[1]) {
        packages.push(match[1].trim());
      }
    } catch {
      // Continue inspecting next file
    }
  }

  if (packages.length === 0) {
    return '';
  }

  const splitPackages = packages.map((p) => p.split('.'));
  const common = [];
  const minLength = Math.min(...splitPackages.map((p) => p.length));

  for (let i = 0; i < minLength; i++) {
    const candidate = splitPackages[0][i];
    const allMatch = splitPackages.every((p) => p[i] === candidate);
    if (allMatch) {
      common.push(candidate);
    } else {
      break;
    }
  }

  const architecturalKeywords = new Set([
    'domain', 'model', 'models', 'entity', 'entities', 'core',
    'application', 'app', 'usecase', 'usecases', 'service', 'services', 'port', 'ports',
    'adapter', 'adapters', 'controller', 'controllers', 'gateway', 'gateways',
    'presenter', 'presenters', 'repository', 'repositories', 'resource', 'resources', 'api', 'dto',
    'infrastructure', 'infra', 'persistence', 'db', 'database', 'config', 'configuration', 'web', 'server'
  ]);

  while (common.length > 0 && architecturalKeywords.has(common[common.length - 1].toLowerCase())) {
    common.pop();
  }

  return common.join('.');
}

export function discoverSubpackages(sourceDir, prefix) {
  const resolved = path.resolve(sourceDir);
  if (!fs.existsSync(resolved)) {
    return [];
  }

  let searchPath = resolved;
  if (prefix) {
    const prefixSegments = prefix.split('.');
    for (const seg of prefixSegments) {
      const cand = path.join(searchPath, seg);
      if (fs.existsSync(cand) && fs.statSync(cand).isDirectory()) {
        searchPath = cand;
      } else {
        break;
      }
    }
  }

  if (!fs.existsSync(searchPath)) {
    return [];
  }

  const entries = fs.readdirSync(searchPath, { withFileTypes: true });
  return entries
    .filter((e) => e.isDirectory() && !e.name.startsWith('.'))
    .map((e) => e.name)
    .sort();
}

export function classifyLayers(subpackages) {
  const domainKeywords = ['domain', 'model', 'models', 'entity', 'entities', 'core', 'types', 'kernel'];
  const appKeywords = ['application', 'app', 'usecase', 'usecases', 'service', 'services', 'port', 'ports', 'interactor'];
  const adapterKeywords = [
    'adapter', 'adapters', 'controller', 'controllers', 'gateway', 'gateways',
    'presenter', 'presenters', 'repository', 'repositories', 'resource', 'resources',
    'api', 'dto', 'endpoint', 'endpoints'
  ];
  const infraKeywords = [
    'infrastructure', 'infra', 'persistence', 'db', 'database', 'config',
    'configuration', 'web', 'server', 'driver', 'client'
  ];

  const level0 = [];
  const level1 = [];
  const level2 = [];
  const level3 = [];

  for (const pkg of subpackages) {
    const lower = pkg.toLowerCase();
    if (domainKeywords.some((k) => lower.includes(k))) {
      level0.push(pkg);
    } else if (appKeywords.some((k) => lower.includes(k))) {
      level1.push(pkg);
    } else if (adapterKeywords.some((k) => lower.includes(k))) {
      level2.push(pkg);
    } else if (infraKeywords.some((k) => lower.includes(k))) {
      level3.push(pkg);
    } else {
      level1.push(pkg);
    }
  }

  if (level0.length === 0) level0.push('domain');
  if (level1.length === 0) level1.push('application');
  if (level2.length === 0) level2.push('adapters');

  const levels = [level0, level1, level2];
  if (level3.length > 0) {
    levels.push(level3);
  }

  const order = [];
  for (const group of levels) {
    for (const p of group) {
      if (!order.includes(p)) {
        order.push(p);
      }
    }
  }

  return { order, levels };
}

export function generatePolicy(projectRoot, options = {}) {
  const root = path.resolve(projectRoot);
  const lang = options.lang || detectProjectLanguage(root);
  const srcRoot = options.src || detectSourceRoot(root, lang);
  const fullSrc = path.join(root, srcRoot);

  const prefix = options.prefix !== undefined ? options.prefix : findCommonPackagePrefix(fullSrc);
  const title = options.title || formatProjectTitle(path.basename(root));

  const subpackages = discoverSubpackages(fullSrc, prefix);
  const { order, levels } = classifyLayers(subpackages);

  const foreignMap = {
    java: ['java.', 'jakarta.', 'org.springframework.', 'io.quarkus.'],
    kotlin: ['kotlin.', 'kotlinx.'],
    typescript: ['node:', '@types/'],
    python: ['sys', 'os', 'pathlib'],
    rust: ['std::', 'core::'],
    go: ['fmt', 'os', 'net/http'],
    clojure: ['clojure.']
  };

  return {
    title,
    src: srcRoot,
    prefix,
    hierarchical: true,
    order,
    levels,
    foreign: foreignMap[lang] || ['java.', 'jakarta.'],
    omit: ['target', 'build', '.git', 'node_modules', '.gradle', '.idea', '.vscode'],
    lang
  };
}

export function formatProjectTitle(dirname) {
  return dirname
    .replace(/[-_]+/g, ' ')
    .trim()
    .split(' ')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

function findMatchingFiles(dir, predicate, limit = 50, collected = []) {
  if (!fs.existsSync(dir) || collected.length >= limit) {
    return collected;
  }

  const skipDirs = new Set(['.git', 'node_modules', 'target', 'build', '.gradle', '.idea', '.vscode']);

  try {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      if (collected.length >= limit) break;
      const fullPath = path.join(dir, entry.name);

      if (entry.isDirectory()) {
        if (!skipDirs.has(entry.name)) {
          findMatchingFiles(fullPath, predicate, limit, collected);
        }
      } else if (entry.isFile() && predicate(entry.name)) {
        collected.push(fullPath);
      }
    }
  } catch {
    // Gracefully handle unreadable directories
  }

  return collected;
}
