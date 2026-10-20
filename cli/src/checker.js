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
          entry.name === 'build' ||
          entry.name === 'venv' ||
          entry.name === '__pycache__' ||
          entry.name === 'site-packages' ||
          entry.name === 'tests' ||
          entry.name === 'test' ||
          entry.name.endsWith('.egg-info')
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

export const TECHNICAL_SUFFIXES = [
  'Controllers',
  'Controller',
  'Resources',
  'Resource',
  'Endpoints',
  'Endpoint',
  'Services',
  'ServiceImpl',
  'Service',
  'Repositories',
  'Repository',
  'Repos',
  'Repo',
  'Daos',
  'Dao',
  'Dtos',
  'Dto',
  'Entities',
  'Entity',
  'Models',
  'Model',
  'Views',
  'View',
  'Handlers',
  'Handler',
  'Validators',
  'Validator',
  'Adapters',
  'Adapter',
  'Helpers',
  'Helper',
  'Utils',
  'Util'
];

export const TECHNICAL_PREFIXES = [
  'Default',
  'Abstract',
  'Base',
  'Postgres',
  'Jpa',
  'Mongo',
  'Sql',
  'Http'
];

export function extractFeatureToken(className) {
  if (!className || typeof className !== 'string') return null;
  let candidate = className;
  for (const prefix of TECHNICAL_PREFIXES) {
    if (candidate.startsWith(prefix) && candidate.length > prefix.length + 2) {
      candidate = candidate.substring(prefix.length);
      break;
    }
  }
  for (const suffix of TECHNICAL_SUFFIXES) {
    if (candidate.endsWith(suffix) && candidate.length > suffix.length) {
      candidate = candidate.substring(0, candidate.length - suffix.length);
      break;
    }
  }
  candidate = candidate.replace(/[0-9_]+$/, '');
  if (
    TECHNICAL_PREFIXES.includes(candidate) ||
    TECHNICAL_SUFFIXES.includes(candidate) ||
    TECHNICAL_MARKERS.has(candidate.toLowerCase())
  ) {
    return null;
  }
  return candidate.length >= 3 ? candidate : null;
}

export function suggestFeatureClusters(packages, sourceFiles = []) {
  const currentMetric = calculateScreamingMetric(packages);
  const techPkgSet = new Set(currentMetric.technicalPackages.map((p) => p.toLowerCase()));

  if (techPkgSet.size === 0) {
    return {
      currentScore: currentMetric.score,
      projectedScore: currentMetric.score,
      currentClassification: currentMetric.classification,
      projectedClassification: currentMetric.classification,
      clusters: [],
      stagedClassMoves: {},
      unclusteredClasses: []
    };
  }

  const technicalClasses = [];
  for (const file of sourceFiles) {
    const ext = path.extname(file);
    const className = path.basename(file, ext);
    const lowerFile = file.toLowerCase();

    let filePkg = null;
    for (const pkg of currentMetric.technicalPackages) {
      const pkgPath = pkg.toLowerCase().replace(/\./g, path.sep);
      if (lowerFile.includes(pkgPath) || lowerFile.includes(pkg.toLowerCase())) {
        filePkg = pkg;
        break;
      }
    }

    if (filePkg) {
      technicalClasses.push({
        className,
        packageName: filePkg,
        filePath: file
      });
    }
  }

  const tokenToClasses = new Map();
  for (const cls of technicalClasses) {
    const token = extractFeatureToken(cls.className);
    if (token) {
      if (!tokenToClasses.has(token)) {
        tokenToClasses.set(token, []);
      }
      tokenToClasses.get(token).push(cls);
    }
  }

  const clusters = [];
  const stagedClassMoves = {};
  const clusteredClassNames = new Set();

  for (const [token, classes] of tokenToClasses.entries()) {
    const sourcePkgs = new Set(classes.map((c) => c.packageName));
    if (classes.length >= 2 || sourcePkgs.size >= 2) {
      const featureName = token.charAt(0).toUpperCase() + token.slice(1);
      const firstPkg = Array.from(sourcePkgs)[0];
      const parts = firstPkg.split('.');
      let basePrefix = '';
      if (parts.length > 1) {
        basePrefix = parts.slice(0, -1).join('.') + '.';
      }
      const proposedPackage = `${basePrefix}${token.toLowerCase()}`;

      clusters.push({
        featureName,
        proposedPackageName: proposedPackage,
        classNames: classes.map((c) => c.className),
        sourcePackages: Array.from(sourcePkgs).sort(),
        classCount: classes.length
      });

      for (const cls of classes) {
        stagedClassMoves[cls.className] = proposedPackage;
        clusteredClassNames.add(cls.className);
      }
    }
  }

  clusters.sort((a, b) => a.featureName.localeCompare(b.featureName));

  const unclustered = technicalClasses
    .filter((c) => !clusteredClassNames.has(c.className))
    .map((c) => c.className)
    .sort();

  const projectedPackages = new Set();
  for (const pkg of packages) {
    if (!techPkgSet.has(pkg.toLowerCase())) {
      projectedPackages.add(pkg);
    }
  }
  for (const cluster of clusters) {
    projectedPackages.add(cluster.proposedPackageName);
  }

  const projectedMetric = calculateScreamingMetric(Array.from(projectedPackages));

  return {
    currentScore: currentMetric.score,
    projectedScore: projectedMetric.score,
    currentClassification: currentMetric.classification,
    projectedClassification: projectedMetric.classification,
    clusters,
    stagedClassMoves,
    unclusteredClasses: unclustered
  };
}

function resolvePythonRelativeImport(packageName, imported) {
  if (!imported || !imported.startsWith('.')) return imported;
  let dotCount = 0;
  while (dotCount < imported.length && imported[dotCount] === '.') dotCount++;
  const rest = imported.slice(dotCount);
  const pkgParts = packageName ? packageName.split('.').filter(Boolean) : [];
  const targetLen = Math.max(0, pkgParts.length - (dotCount - 1));
  const base = pkgParts.slice(0, targetLen).join('.');
  if (!base) return rest;
  return rest ? `${base}.${rest}` : base;
}

function extractPythonStatements(content) {
  const rawLines = content.split('\n');
  const statements = [];
  let currentStmt = '';
  let stmtLine = 1;
  let inTripleDouble = false;
  let inTripleSingle = false;
  let parenDepth = 0;

  for (let lineIdx = 0; lineIdx < rawLines.length; lineIdx++) {
    const line = rawLines[lineIdx];
    const lineNo = lineIdx + 1;
    let lineBuf = '';
    let i = 0;

    while (i < line.length) {
      if (inTripleDouble) {
        const close = line.indexOf('"""', i);
        if (close !== -1) {
          inTripleDouble = false;
          i = close + 3;
        } else break;
        continue;
      }
      if (inTripleSingle) {
        const close = line.indexOf("'''", i);
        if (close !== -1) {
          inTripleSingle = false;
          i = close + 3;
        } else break;
        continue;
      }
      if (line.startsWith('"""', i)) {
        inTripleDouble = true;
        i += 3;
        continue;
      }
      if (line.startsWith("'''", i)) {
        inTripleSingle = true;
        i += 3;
        continue;
      }
      const c = line[i];
      if (c === '#') break;
      if (c === '"' || c === "'") {
        const quote = c;
        lineBuf += c;
        i++;
        while (i < line.length) {
          const sc = line[i];
          lineBuf += sc;
          if (sc === '\\' && i + 1 < line.length) {
            i++;
            lineBuf += line[i];
          } else if (sc === quote) {
            i++;
            break;
          }
          i++;
        }
        continue;
      }
      if (c === '(' || c === '[' || c === '{') parenDepth++;
      else if (c === ')' || c === ']' || c === '}') parenDepth = Math.max(0, parenDepth - 1);
      else if (c === ';' && parenDepth === 0) {
        const emitted = (currentStmt ? `${currentStmt} ${lineBuf}` : lineBuf).trim();
        if (emitted) statements.push({ text: emitted, line: currentStmt ? stmtLine : lineNo });
        currentStmt = '';
        lineBuf = '';
        i++;
        continue;
      }
      lineBuf += c;
      i++;
    }

    if (inTripleDouble || inTripleSingle) continue;

    const trimmedRight = lineBuf.trimEnd();
    if (trimmedRight.endsWith('\\')) {
      if (!currentStmt) stmtLine = lineNo;
      currentStmt += (currentStmt ? ' ' : '') + trimmedRight.slice(0, -1).trim();
      continue;
    }
    if (parenDepth > 0) {
      if (trimmedRight.trim()) {
        if (!currentStmt) stmtLine = lineNo;
        currentStmt += (currentStmt ? ' ' : '') + trimmedRight.trim();
      }
      continue;
    }
    if (currentStmt) {
      const emitted = `${currentStmt} ${trimmedRight.trim()}`.trim();
      if (emitted) statements.push({ text: emitted, line: stmtLine });
      currentStmt = '';
    } else if (trimmedRight.trim()) {
      statements.push({ text: trimmedRight.trim(), line: lineNo });
    }
  }
  if (currentStmt.trim()) {
    statements.push({ text: currentStmt.trim(), line: stmtLine });
  }
  return statements;
}

export function extractFileImports(content, filePath, fullSrc) {
  if (!filePath.endsWith('.py')) {
    const results = [];
    const importRegex = /import\s+(?:static\s+)?([a-zA-Z0-9_.]+)/;
    const lines = content.split('\n');
    for (let i = 0; i < lines.length; i++) {
      const m = importRegex.exec(lines[i]);
      if (m) results.push({ imported: m[1], line: i + 1 });
    }
    return results;
  }

  let rel = fullSrc ? path.relative(fullSrc, filePath).replace(/\\/g, '/') : path.basename(filePath);
  if (rel.startsWith('src/')) rel = rel.slice(4);
  const isInit = path.basename(filePath) === '__init__.py';
  const modId = rel.replace(/\.py$/, '').replace(/\/__init__$/, '').replace(/\//g, '.');
  const packageName = isInit ? modId : (modId.includes('.') ? modId.slice(0, modId.lastIndexOf('.')) : modId);

  const results = [];
  const fromRegex = /^from\s+([a-zA-Z0-9_.]+)(?:\s+|(?<=\.))import\s+(.+)$/;
  const directRegex = /^import\s+([a-zA-Z0-9_., ]+)$/;
  const dynRegex = /(?:importlib\.import_module|__import__)\s*\(\s*['"]([a-zA-Z0-9_.]+)['"]/g;

  for (const { text, line } of extractPythonStatements(content)) {
    const fromMatch = fromRegex.exec(text);
    if (fromMatch) {
      const resolvedMod = resolvePythonRelativeImport(packageName, fromMatch[1].trim());
      const itemsClause = fromMatch[2].replace(/[()]/g, ' ').trim();
      let emittedSpecific = false;
      for (const rawPart of itemsClause.split(',')) {
        const token = rawPart.trim();
        if (!token) continue;
        const orig = token.split(/\s+/)[0].trim();
        if (orig && orig !== '*' && /^[a-zA-Z_]/.test(orig)) {
          results.push({ imported: resolvedMod ? `${resolvedMod}.${orig}` : orig, line });
          emittedSpecific = true;
        }
      }
      if (!emittedSpecific && resolvedMod) {
        results.push({ imported: resolvedMod, line });
      }
      continue;
    }

    const directMatch = directRegex.exec(text);
    if (directMatch) {
      for (const rawPart of directMatch[1].replace(/[()]/g, ' ').split(',')) {
        const orig = rawPart.trim().split(/\s+/)[0].trim();
        if (orig && /^[a-zA-Z_]/.test(orig)) {
          results.push({ imported: orig, line });
        }
      }
      continue;
    }

    let dynMatch;
    dynRegex.lastIndex = 0;
    while ((dynMatch = dynRegex.exec(text)) !== null) {
      const resolvedDyn = resolvePythonRelativeImport(packageName, dynMatch[1].trim());
      if (resolvedDyn) results.push({ imported: resolvedDyn, line });
    }
  }
  return results;
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
    (f) =>
      (f.endsWith('.java') ||
        f.endsWith('.kt') ||
        f.endsWith('.ts') ||
        f.endsWith('.js') ||
        f.endsWith('.py')) &&
      !f.startsWith('test_') &&
      !f.endsWith('_test.py'),
    500
  );

  const violations = [];
  const packageEdges = new Map();

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

      const fileImports = extractFileImports(content, file, fullSrc);
      const seenLinePkgViolations = new Set();
      for (const { imported, line } of fileImports) {
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
              const dedupKey = `${line}:${targetPkg}`;
              if (!seenLinePkgViolations.has(dedupKey)) {
                seenLinePkgViolations.add(dedupKey);
                const baseName = path.basename(file, path.extname(file));
                violations.push({
                  fromFile: relPath,
                  fromTier: tierNames[fileLevel] || `Level ${fileLevel}`,
                  toImport: imported,
                  toTier: tierNames[targetLvl] || `Level ${targetLvl}`,
                  portName: `${baseName}Port`,
                  line
                });
              }
            }
            break;
          }
        }
      }
    } catch {
      // Ignore unreadable files
    }
  }

  const allKnownPackages = Array.from(pkgToLevel.keys());
  const screaming = calculateScreamingMetric(allKnownPackages);
  const screamingThreshold = options.screamingThreshold !== undefined ? Number(options.screamingThreshold) : null;
  const failOnScreaming = screamingThreshold !== null && screaming.score < screamingThreshold;

  const cycles = detectPackageCycles(packageEdges);
  const failOnCycles = Boolean(options.detectCycles);

  const fitness = calculateArchitectureFitness(
    {
      violationsCount: violations.length,
      cyclesCount: cycles.length,
      screamingScore: screaming.score,
      maxDistance: 0.12
    },
    policy.fitness || {}
  );

  const fitnessThreshold =
    options.fitnessThreshold !== undefined && options.fitnessThreshold !== null
      ? parseFloat(options.fitnessThreshold)
      : (typeof policy.fitness?.threshold === 'number'
          ? policy.fitness.threshold
          : null);
  const failOnFitness =
    fitnessThreshold !== null &&
    !isNaN(fitnessThreshold) &&
    fitness.fitnessScore < fitnessThreshold;

  const passed =
    violations.length <= maxViolations &&
    (!failOnCycles || cycles.length === 0) &&
    !failOnScreaming &&
    !failOnFitness;

  const featureClusters = options.suggestFeatures
    ? suggestFeatureClusters(allKnownPackages, sourceFiles)
    : null;

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
    fitnessThreshold,
    fitness,
    violations,
    cycles,
    featureClusters
  };
}

/**
 * Calculate composite Architectural Fitness Index (AFI), grade, and rule compliance.
 */
export function calculateArchitectureFitness(metrics, thresholds = {}) {
  const maxViolations = thresholds.maxViolations ?? 0;
  const maxCycles = thresholds.maxCycles ?? 0;
  const minScreamingScore = thresholds.minScreamingScore ?? 0.70;
  const maxMainSequenceDistance = thresholds.maxMainSequenceDistance ?? 0.35;

  const violations = metrics.violationsCount ?? 0;
  const cycles = metrics.cyclesCount ?? 0;
  const screamingScore = metrics.screamingScore ?? 1.0;
  const maxDistance = metrics.maxDistance ?? 0.0;

  const concentricPassed = violations <= maxViolations;
  const concentricScore = concentricPassed ? 1.0 : Math.max(0.0, 1.0 - violations * 0.25);

  const adpPassed = cycles <= maxCycles;
  const adpScore = adpPassed ? 1.0 : Math.max(0.0, 1.0 - cycles * 0.35);

  const screamingPassed = screamingScore >= minScreamingScore;
  const screamingFitnessScore =
    minScreamingScore > 0 ? Math.min(1.0, screamingScore / minScreamingScore) : 1.0;

  const distancePassed = maxDistance <= maxMainSequenceDistance;
  const distanceScore =
    maxMainSequenceDistance > 0
      ? Math.max(0.0, 1.0 - maxDistance / (2.0 * maxMainSequenceDistance))
      : 1.0;

  const weightedScore =
    concentricScore * 0.35 +
    adpScore * 0.25 +
    screamingFitnessScore * 0.20 +
    distanceScore * 0.20;
  const fitnessScore = Math.round(weightedScore * 100) / 100;

  let grade = 'F';
  if (fitnessScore >= 0.90) grade = 'A';
  else if (fitnessScore >= 0.80) grade = 'B';
  else if (fitnessScore >= 0.70) grade = 'C';
  else if (fitnessScore >= 0.60) grade = 'D';

  const rules = [
    {
      id: 'CONCENTRIC_DEPENDENCY_RULE',
      name: 'Concentric Dependency Rule',
      threshold: maxViolations,
      actualValue: violations,
      passed: concentricPassed
    },
    {
      id: 'ACYCLIC_DEPENDENCIES_RULE',
      name: 'Acyclic Dependencies Principle (ADP)',
      threshold: maxCycles,
      actualValue: cycles,
      passed: adpPassed
    },
    {
      id: 'SCREAMING_ARCHITECTURE_RULE',
      name: 'Screaming Architecture Invariant',
      threshold: minScreamingScore,
      actualValue: screamingScore,
      passed: screamingPassed
    },
    {
      id: 'MAIN_SEQUENCE_DISTANCE_RULE',
      name: 'Main Sequence Balance Invariant',
      threshold: maxMainSequenceDistance,
      actualValue: maxDistance,
      passed: distancePassed
    }
  ];

  const overallPassed = rules.every((r) => r.passed);

  return {
    fitnessScore,
    grade,
    overallPassed,
    passedRuleCount: rules.filter((r) => r.passed).length,
    totalRuleCount: rules.length,
    rules
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
