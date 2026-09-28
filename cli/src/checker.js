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
  const packageEdges = new Map();
  const importRegex = /import\s+(?:static\s+)?([a-zA-Z0-9_.]+)/;

  for (const file of sourceFiles) {
    try {
      const content = fs.readFileSync(file, 'utf8');
      const relPath = path.relative(root, file);
      const lowerRel = relPath.toLowerCase();

      let fileLevel = null;
      let filePackage = null;
      for (const [pkg, lvl] of pkgToLevel.entries()) {
        if (lowerRel.includes(pkg)) {
          fileLevel = lvl;
          filePackage = pkg;
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
              if (filePackage && filePackage !== targetPkg) {
                if (!packageEdges.has(filePackage)) {
                  packageEdges.set(filePackage, new Set());
                }
                packageEdges.get(filePackage).add(targetPkg);
              }
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

export const TECHNICAL_MARKERS = new Set([
  'controller',
  'controllers',
  'service',
  'services',
  'dao',
  'daos',
  'repository',
  'repositories',
  'dto',
  'dtos',
  'model',
  'models',
  'entity',
  'entities',
  'util',
  'utils',
  'helper',
  'helpers',
  'common',
  'infra',
  'infrastructure',
  'adapter',
  'adapters',
  'resource',
  'resources',
  'api',
  'endpoint',
  'endpoints',
  'view',
  'views',
  'handler',
  'handlers'
]);

export function calculateScreamingMetric(packages) {
  if (!packages || packages.length === 0) {
    return {
      score: 1.0,
      domainPackageCount: 0,
      technicalPackageCount: 0,
      totalPackageCount: 0,
      classification: 'PACKAGE_BY_FEATURE',
      domainPackages: [],
      technicalPackages: []
    };
  }

  const domainPackages = [];
  const technicalPackages = [];

  for (const pkg of packages) {
    const lower = pkg.toLowerCase();
    const leaf = lower.includes('.') ? lower.substring(lower.lastIndexOf('.') + 1) : lower;
    if (TECHNICAL_MARKERS.has(leaf) || TECHNICAL_MARKERS.has(lower)) {
      technicalPackages.push(pkg);
    } else {
      let isTech = false;
      for (const segment of lower.split('.')) {
        if (TECHNICAL_MARKERS.has(segment)) {
          isTech = true;
          break;
        }
      }
      if (isTech) {
        technicalPackages.push(pkg);
      } else {
        domainPackages.push(pkg);
      }
    }
  }

  const total = domainPackages.length + technicalPackages.length;
  const score = total > 0 ? Math.round((domainPackages.length / total) * 100) / 100 : 1.0;
  let classification = 'PACKAGE_BY_LAYER';
  if (score >= 0.75) classification = 'PACKAGE_BY_FEATURE';
  else if (score >= 0.40) classification = 'HYBRID';

  return {
    score,
    domainPackageCount: domainPackages.length,
    technicalPackageCount: technicalPackages.length,
    totalPackageCount: total,
    classification,
    domainPackages,
    technicalPackages
  };
}

  const allKnownPackages = Array.from(pkgToLevel.keys());
  const screaming = calculateScreamingMetric(allKnownPackages);
  const screamingThreshold = options.screamingThreshold !== undefined ? Number(options.screamingThreshold) : null;
  const failOnScreaming = screamingThreshold !== null && screaming.score < screamingThreshold;

  const cycles = detectPackageCycles(packageEdges);
  const failOnCycles = Boolean(options.detectCycles);
  const passed =
    violations.length <= maxViolations &&
    (!failOnCycles || cycles.length === 0) &&
    !failOnScreaming;

  return {
    passed,
    projectTitle: policy.title || path.basename(root),
    sourceFilesCount: sourceFiles.length,
    packagesCount: (policy.order || []).length,
    violationsCount: violations.length,
    cyclesCount: cycles.length,
    maxViolations,
    screamingThreshold,
    screaming,
    violations,
    cycles
  };
}

/**
 * Detect elementary directed cycles in package dependency graph using DFS
 */
export function detectPackageCycles(packageEdges) {
  const detectedCycles = [];
  const sortedNodes = Array.from(packageEdges.keys()).sort();

  function dfs(startNode, currentNode, currentPath, visitedOnPath) {
    const neighbors = packageEdges.get(currentNode);
    if (!neighbors) return;

    for (const neighbor of Array.from(neighbors).sort()) {
      if (neighbor === startNode && currentPath.length > 1) {
        detectedCycles.push([...currentPath, startNode]);
      } else if (neighbor > startNode && !visitedOnPath.has(neighbor)) {
        visitedOnPath.add(neighbor);
        currentPath.push(neighbor);
        dfs(startNode, neighbor, currentPath, visitedOnPath);
        currentPath.pop();
        visitedOnPath.delete(neighbor);
      }
    }
  }

  for (const startNode of sortedNodes) {
    const visited = new Set([startNode]);
    dfs(startNode, startNode, [startNode], visited);
  }

  return detectedCycles.map((c) => ({
    packages: c,
    formatted: c.join(' -> ')
  }));
}

/**
 * Generate standard SARIF 2.1.0 JSON report representing architectural violations
 * for GitHub Code Scanning and PR annotations.
 */
export function generateSarifReport(report, options = {}) {
  const version = options.version || '0.0.1';
  const violations = report.violations || [];
  const cycles = report.cycles || [];

  const rules = [
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
  ];

  if (cycles.length > 0) {
    rules.push({
      id: 'ARCH002',
      name: 'AcyclicDependenciesPrincipleRule',
      shortDescription: {
        text: 'Package dependency cycle detected (Robert C. Martin ADP violation)'
      },
      fullDescription: {
        text: 'The dependency structure between packages must be a Directed Acyclic Graph (DAG). There must be no cycles in the dependency structure.'
      },
      defaultConfiguration: {
        level: 'error'
      },
      helpUri: 'https://github.com/fmatar/archlens#clean-architecture-rules'
    });
  }

  const screaming = report.screaming;
  const failOnScreaming =
    report.screamingThreshold !== null &&
    report.screamingThreshold !== undefined &&
    screaming &&
    screaming.score < report.screamingThreshold;

  if (failOnScreaming) {
    rules.push({
      id: 'ARCH003',
      name: 'ScreamingArchitectureRule',
      shortDescription: {
        text: 'Screaming Architecture score below threshold (Uncle Bob Chapter 21 violation)'
      },
      fullDescription: {
        text: 'The architecture should scream its business intent and domain use cases rather than framework delivery mechanisms or technical layers. Package-by-feature should predominate over package-by-layer.'
      },
      defaultConfiguration: {
        level: 'error'
      },
      helpUri: 'https://github.com/fmatar/archlens#clean-architecture-rules'
    });
  }

  const sarifResults = violations.map((v) => ({
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
  }));

  for (const c of cycles) {
    sarifResults.push({
      ruleId: 'ARCH002',
      level: 'error',
      message: {
        text: `Package Dependency Cycle (ADP Violation): Cyclic loop detected: ${c.formatted}. Invert dependencies to break the cycle.`
      }
    });
  }

  if (failOnScreaming) {
    sarifResults.push({
      ruleId: 'ARCH003',
      level: 'error',
      message: {
        text: `Screaming Architecture Violation: Score ${screaming.score.toFixed(2)} is below required threshold ${report.screamingThreshold.toFixed(2)} (${screaming.classification}: ${screaming.technicalPackageCount} technical / ${screaming.totalPackageCount} total packages). Organize packages by domain feature.`
      }
    });
  }

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
            rules
          }
        },
        results: sarifResults
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
