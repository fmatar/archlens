/**
 * Archlens Policy & Multi-Agent Skill Updater
 * Re-scans codebases to detect newly added packages, updates policy layers
 * while preserving user custom configurations, and refreshes global agent skills.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import {
  detectProjectLanguage,
  detectSourceRoot,
  findCommonPackagePrefix,
  discoverSubpackages,
  classifyLayers
} from './analyzer.js';
import {
  COMPANION_PROTOCOL_HEADER,
  getCompanionProtocolBlock,
  installGlobalSkills,
  getGlobalSkillDirectories
} from './installer.js';

export function updateLocalPolicy(projectRoot, options = {}) {
  const root = path.resolve(projectRoot);
  const dryRun = Boolean(options.dryRun);
  const umlDir = path.join(root, '.uml-viewer');
  const policyFile = path.join(umlDir, 'policy.json');
  const configFile = path.join(umlDir, 'workbench.config.json');

  if (!fs.existsSync(root)) {
    throw new Error(`Target directory does not exist: ${root}`);
  }

  if (!fs.existsSync(policyFile)) {
    throw new Error(`Archlens policy not found at ${policyFile}. Run init first.`);
  }

  const existingPolicy = JSON.parse(fs.readFileSync(policyFile, 'utf8'));

  const lang = existingPolicy.lang || detectProjectLanguage(root);
  const srcRoot = existingPolicy.src || detectSourceRoot(root, lang);
  const fullSrc = path.join(root, srcRoot);
  const prefix = existingPolicy.prefix !== undefined
    ? existingPolicy.prefix
    : findCommonPackagePrefix(fullSrc);

  const discoveredPackages = discoverSubpackages(fullSrc, prefix);

  // Determine which packages in codebase are not currently listed in existing policy
  const existingOrder = Array.isArray(existingPolicy.order) ? existingPolicy.order : [];
  const existingLevels = Array.isArray(existingPolicy.levels) ? existingPolicy.levels : [];

  const existingPackageSet = new Set(existingOrder);
  const newPackages = discoveredPackages.filter((p) => !existingPackageSet.has(p));

  let updatedLevels = existingLevels.map((lvl) => [...lvl]);
  if (updatedLevels.length < 3) {
    while (updatedLevels.length < 3) {
      updatedLevels.push([]);
    }
  }

  if (newPackages.length > 0) {
    const { levels: classifiedNewLevels } = classifyLayers(newPackages);

    for (let i = 0; i < classifiedNewLevels.length; i++) {
      if (!updatedLevels[i]) {
        updatedLevels[i] = [];
      }
      for (const p of classifiedNewLevels[i]) {
        // Exclude default placeholder names if they were synthetic
        if (newPackages.includes(p) && !updatedLevels[i].includes(p)) {
          updatedLevels[i].push(p);
        }
      }
    }
  }

  // Rebuild order ensuring all levels are represented sequentially
  const updatedOrder = [];
  for (const group of updatedLevels) {
    for (const p of group) {
      if (!updatedOrder.includes(p)) {
        updatedOrder.push(p);
      }
    }
  }

  const mergedPolicy = {
    ...existingPolicy,
    title: options.title || existingPolicy.title,
    src: srcRoot,
    prefix,
    order: updatedOrder,
    levels: updatedLevels,
    lang
  };

  if (!dryRun) {
    fs.writeFileSync(policyFile, JSON.stringify(mergedPolicy, null, 2) + '\n', 'utf8');
  }

  const serverUrl = options.serverUrl || 'http://localhost:8088';
  if (!dryRun) {
    const workbenchConfig = {
      serverUrl,
      protocolVersion: '1.0',
      description: 'Visual Workbench communication endpoint and mailbox IPC configuration'
    };
    fs.writeFileSync(configFile, JSON.stringify(workbenchConfig, null, 2) + '\n', 'utf8');
  }

  // Refresh companion documentation
  const refreshedCompanions = [];
  for (const filename of ['CLAUDE.md', 'AGENTS.md']) {
    const filePath = path.join(root, filename);
    const companionBlock = getCompanionProtocolBlock(serverUrl);

    if (fs.existsSync(filePath)) {
      const existing = fs.readFileSync(filePath, 'utf8');
      if (!existing.includes(COMPANION_PROTOCOL_HEADER)) {
        if (!dryRun) {
          fs.writeFileSync(filePath, existing.trimEnd() + '\n\n' + companionBlock + '\n', 'utf8');
        }
        refreshedCompanions.push(filename);
      }
    }
  }

  return {
    targetDir: root,
    policyFile,
    newPackages,
    totalPackages: updatedOrder.length,
    refreshedCompanions,
    dryRun
  };
}

export function updateGlobalSkills(options = {}) {
  const destinations = getGlobalSkillDirectories();
  const existingDestinations = destinations.filter((dest) => fs.existsSync(dest.targetDir));

  if (existingDestinations.length === 0) {
    // If none installed yet, install to all detected agent environments
    return installGlobalSkills(options);
  }

  const installResult = installGlobalSkills({
    ...options,
    force: true
  });

  return {
    updatedLocations: installResult.installed,
    dryRun: installResult.dryRun
  };
}
