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

export async function generateLlmPrompt(projectRoot, options = {}) {
  const root = path.resolve(projectRoot || '.');
  const serverUrl = options.serverUrl || 'http://localhost:8088';

  // 1. Attempt querying active Archlens backend server
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 1200);
    const apiUrl = `${serverUrl.replace(/\/$/, '')}/api/diagram/llm-dossier?projectRoot=${encodeURIComponent(root)}`;
    const res = await fetch(apiUrl, { signal: controller.signal });
    clearTimeout(timeoutId);
    if (res.ok) {
      const text = await res.text();
      if (text && text.trim().length > 0) {
        return text;
      }
    }
  } catch {
    // Offline or server unreachable: fallback to local analysis
  }

  // 2. Offline analysis using local policy and source scan
  let policy;
  const policyFile = path.join(root, '.archlens', 'policy.json');
  if (fs.existsSync(policyFile)) {
    try {
      policy = JSON.parse(fs.readFileSync(policyFile, 'utf8'));
    } catch {
      policy = generatePolicy(root, options);
    }
  } else {
    policy = generatePolicy(root, options);
  }

  const title = policy.title || formatProjectTitle(path.basename(root));
  const levels = policy.levels || [];
  const tierNames = ['Domain Core (L0)', 'Application (L1)', 'Adapters (L2)', 'Infrastructure (L3)'];

  let sb = '';
  sb += `# Clean Architecture Optimization Dossier — ${title}\n\n`;
  sb += `Generated by **Archlens Dynamic Clean Architecture Workbench** (CLI offline mode).\n`;
  sb += `This dossier identifies architectural ring boundaries, illegal outward dependency rule violations,\n`;
  sb += `and concrete Dependency Inversion Principle (DIP) refactoring guidance for Large Language Models.\n\n`;
  sb += `---\n\n`;

  // 1. Concentric Tiers
  sb += `## 1. Clean Architecture Concentric Rings\n\n`;
  sb += `| Tier | Level | Packages |\n`;
  sb += `| :--- | :---: | :--- |\n`;

  const maxTiers = Math.max(levels.length, 4);
  for (let i = 0; i < maxTiers; i++) {
    const tierName = i < tierNames.length ? tierNames[i] : `Ring Level ${i}`;
    const pkgs = i < levels.length ? levels[i] : [];
    const pkgList = pkgs.length === 0 ? '*(none declared)*' : `\`${pkgs.join('`, `')}\``;
    sb += `| **${tierName}** | Level ${i} | ${pkgList} |\n`;
  }
  sb += `\n---\n\n`;

  // 2. Scan source files for cross-ring violations
  const fullSrc = path.join(root, policy.src || 'src');
  const sourceFiles = findMatchingFiles(
    fullSrc,
    (f) => f.endsWith('.java') || f.endsWith('.kt') || f.endsWith('.ts') || f.endsWith('.js'),
    300
  );

  const pkgToLevel = new Map();
  levels.forEach((tier, lvl) => {
    tier.forEach((p) => pkgToLevel.set(p.toLowerCase(), lvl));
  });

  const violations = [];
  const importRegex = /import\s+([a-zA-Z0-9_.]+)/g;

  for (const file of sourceFiles) {
    try {
      const content = fs.readFileSync(file, 'utf8');
      const relPath = path.relative(root, file);
      const lowerRel = relPath.toLowerCase();

      let fileLevel = null;
      for (const [pkg, lvl] of pkgToLevel.entries()) {
        if (lowerRel.includes(pkg)) {
          fileLevel = lvl;
          break;
        }
      }

      if (fileLevel === null) continue;

      let match;
      while ((match = importRegex.exec(content)) !== null) {
        const imported = match[1];
        const lowerImport = imported.toLowerCase();
        for (const [targetPkg, targetLvl] of pkgToLevel.entries()) {
          if (lowerImport.includes(targetPkg)) {
            if (fileLevel < targetLvl) {
              const baseName = path.basename(file, path.extname(file));
              violations.push({
                fromFile: relPath,
                fromTier: tierNames[fileLevel] || `Level ${fileLevel}`,
                toImport: imported,
                toTier: tierNames[targetLvl] || `Level ${targetLvl}`,
                portName: `${baseName}Port`
              });
            }
            break;
          }
        }
      }
    } catch {
      // Proceed to next file
    }
  }

  sb += `## 2. Architectural Diagnostics Summary\n\n`;
  sb += `- **Analyzed Source Files**: ${sourceFiles.length}\n`;
  sb += `- **Declared Packages**: ${(policy.order || []).length}\n`;

  if (violations.length === 0) {
    sb += `- **Clean Architecture Status**: ✅ **Conforming** (Zero outward dependency rule violations detected)\n\n`;
  } else {
    sb += `- **Clean Architecture Status**: 🚨 **${violations.length} Dependency Rule ${violations.length === 1 ? 'Violation' : 'Violations'} Detected**\n\n`;
  }
  sb += `---\n\n`;

  // 3. Violations Breakdown & DIP Guidance
  sb += `## 3. Prioritized Architectural Violations & DIP Refactoring Prescriptions\n\n`;
  if (violations.length === 0) {
    sb += `🎉 **No dependency violations found.** All source code dependencies adhere to Uncle Bob's Dependency Rule,\n`;
    sb += `pointing strictly inward toward higher-level domain policies.\n\n`;
  } else {
    violations.forEach((v, idx) => {
      sb += `### Violation ${idx + 1}: \`${v.fromFile}\` ➔ \`${v.toImport}\`\n\n`;
      sb += `- **Direction**: \`${v.fromTier}\` depends on \`${v.toTier}\`\n`;
      sb += `- **Uncle Bob's Law**: Source code dependencies must point ONLY inward, toward higher-level policies.\n`;
      sb += `- **Source File**: \`${v.fromFile}\`\n`;
      sb += `- **Target Dependency**: \`${v.toImport}\`\n`;
      sb += `- **Prescribed Dependency Inversion Principle (DIP) Fix**:\n`;
      sb += `  1. Define an interface port \`${v.portName}\` in the inner layer (\`${v.fromTier}\`).\n`;
      sb += `  2. Change source component to declare dependencies exclusively against \`${v.portName}\`.\n`;
      sb += `  3. Implement \`${v.portName}\` in the outer layer (\`${v.toTier}\`).\n`;
      sb += `  4. Wire the concrete implementation at the infrastructure boundary using dependency injection.\n\n`;
    });
  }
  sb += `---\n\n`;

  // 4. Actionable LLM Instructions
  sb += `## 4. Actionable LLM Refactoring Instructions\n\n`;
  sb += `\`\`\`markdown\n`;
  sb += `You are an expert Clean Architecture software engineer and refactoring specialist.\n\n`;
  sb += `TASK:\n`;
  if (violations.length === 0) {
    sb += `The current codebase cleanly conforms to Clean Architecture boundaries.\n`;
    sb += `Ensure any future enhancements maintain strict inward dependency flow.\n`;
  } else {
    sb += `Refactor the codebase to eliminate the ${violations.length} architectural violation${violations.length === 1 ? '' : 's'} detailed above.\n\n`;
    sb += `EXECUTION PRINCIPLES:\n`;
    sb += `1. Invert outward dependencies by creating interface ports in the inner layers and implementing them in the outer layers.\n`;
    sb += `2. Maintain strict separation of concerns across concentric rings (Domain -> Application -> Adapters -> Infrastructure).\n`;
    sb += `3. Avoid mock-heavy tests or test smells. Verify the refactoring with clean unit tests adhering to Robert C. Martin craftsmanship.\n`;
    sb += `4. Keep public contracts stable and regression-free.\n`;
  }
  sb += `\`\`\`\n`;

  return sb;
}
