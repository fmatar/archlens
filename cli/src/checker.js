/**
 * Archlens Architecture Conformance Checker
 * Audits repository against .archlens/policy.json Clean Architecture rules.
 * Enforces zero outward dependency violations and configurable thresholds for CI/CD gates.
 * Generates human-readable, JSON, and SARIF 2.1.0 reports for GitHub Code Scanning.
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
  const importRegex = /import\s+(?:static\s+)?([a-zA-Z0-9_.]+)/;

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

      const lines = content.split('\n');
      for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
        const line = lines[lineIndex];
        const match = importRegex.exec(line);
        if (match) {
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
                  portName: `${baseName}Port`,
                  line: lineIndex + 1
                });
              }
              break;
            }
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

/**
 * Generate standard SARIF 2.1.0 JSON report representing architectural violations
 * for GitHub Code Scanning and PR annotations.
 */
export function generateSarifReport(report, options = {}) {
  const version = options.version || '0.0.1';
  const violations = report.violations || [];

  const sarif = {
    $schema: 'https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json',
    version: '2.1.0',
    runs: [
      {
        tool: {
          driver: {
            name: 'archlens',
            version,
            informationUri: 'https://github.com/fmatar/archlens',
            rules: [
              {
                id: 'ARCH001',
                name: 'CleanArchitectureOutwardDependencyRule',
                shortDescription: {
                  text: 'Clean Architecture concentric dependency rule violation'
                },
                fullDescription: {
                  text: 'Inner layers (Domain, Application) must not depend outwardly on outer layers (Adapters, Infrastructure). Apply the Dependency Inversion Principle (DIP) to invert the dependency.'
                },
                defaultConfiguration: {
                  level: 'error'
                },
                helpUri: 'https://github.com/fmatar/archlens#clean-architecture-rules'
              }
            ]
          }
        },
        results: violations.map((v) => ({
          ruleId: 'ARCH001',
          level: 'error',
          message: {
            text: `Clean Architecture Violation: Inner tier '${v.fromTier}' (${v.fromFile}) depends outwardly on outer tier '${v.toTier}' via import '${v.toImport}'. Invert with interface '${v.portName}'.`
          },
          locations: [
            {
              physicalLocation: {
                artifactLocation: {
                  uri: v.fromFile
                },
                region: {
                  startLine: v.line || 1
                }
              }
            }
          ]
        }))
      }
    ]
  };

  return JSON.stringify(sarif, null, 2);
}

/**
 * Generate structured JSON report
 */
export function generateJsonReport(report) {
  return JSON.stringify(report, null, 2);
}
