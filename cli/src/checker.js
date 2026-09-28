/**
 * Archlens Architecture Conformance Checker
 * Audits repository against .archlens/policy.json Clean Architecture rules.
 * Enforces zero outward dependency violations and configurable thresholds for CI/CD gates.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';

function findMatchingFiles(dir, predicate, maxFiles = 500, results = []) {
  if (!fs.existsSync(dir) || results.length >= maxFiles) return results;

  try {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      if (results.length >= maxFiles) break;
      const fullPath = path.join(dir, entry.name);

      if (entry.isDirectory()) {
        if (
          entry.name.startsWith('.') ||
          entry.name === 'node_modules' ||
          entry.name === 'target' ||
          entry.name === 'dist' ||
          entry.name === 'build'
        ) {
          continue;
        }
        findMatchingFiles(fullPath, predicate, maxFiles, results);
      } else if (predicate(entry.name)) {
        results.push(fullPath);
      }
    }
  } catch {
    // Ignore unreadable directories
  }

  return results;
}

export function loadProjectPolicy(projectRoot) {
  const root = path.resolve(projectRoot);
  const candidates = [
    path.join(root, '.archlens', 'policy.json'),
    path.join(root, '.uml-viewer', 'policy.json'),
    path.join(root, 'policy.json')
  ];

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      try {
        return JSON.parse(fs.readFileSync(candidate, 'utf8'));
      } catch (err) {
        throw new Error(`Failed to parse policy at ${candidate}: ${err.message}`);
      }
    }
  }

  return null;
}

export async function checkArchitecture(projectRoot, options = {}) {
  const root = path.resolve(projectRoot || '.');
  const policy = loadProjectPolicy(root);

  if (!policy) {
    return {
      passed: false,
      violationsCount: 0,
      violations: [],
      error: `No Archlens policy found in ${root}. Run 'npx @fmatar/archlens-skill init' first.`
    };
  }

  const maxViolations = options.maxViolations !== undefined ? Number(options.maxViolations) : 0;
  const levels = policy.levels || [];
  const tierNames = ['Domain Core (L0)', 'Application (L1)', 'Adapters (L2)', 'Infrastructure (L3)'];

  const pkgToLevel = new Map();
  levels.forEach((tier, lvl) => {
    tier.forEach((p) => pkgToLevel.set(p.toLowerCase(), lvl));
  });

  const fullSrc = path.join(root, policy.src || 'src');
  const sourceFiles = findMatchingFiles(
    fullSrc,
    (f) => f.endsWith('.java') || f.endsWith('.kt') || f.endsWith('.ts') || f.endsWith('.js'),
    500
  );

  const violations = [];
  const importRegex = /import\s+(?:static\s+)?([a-zA-Z0-9_.]+)/g;

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
      // Ignore unreadable files
    }
  }

  const passed = violations.length <= maxViolations;

  return {
    passed,
    projectTitle: policy.title || path.basename(root),
    sourceFilesCount: sourceFiles.length,
    packagesCount: (policy.order || []).length,
    violationsCount: violations.length,
    maxViolations,
    violations
  };
}
