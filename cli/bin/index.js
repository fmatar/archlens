#!/usr/bin/env node

/**
 * Archlens Skill & Policy CLI
 * Interactive and headless runner for Clean Architecture governance and AI assistant integration.
 * Zero external dependencies.
 */

import * as path from 'node:path';
import * as fs from 'node:fs';
import { fileURLToPath } from 'node:url';

import * as ui from '../src/ui.js';
import {
  detectProjectLanguage,
  detectSourceRoot,
  findCommonPackagePrefix,
  generateLlmPrompt
} from '../src/analyzer.js';
import { installLocalPolicy, installGlobalSkills } from '../src/installer.js';
import { updateLocalPolicy, updateGlobalSkills } from '../src/updater.js';
import { upgradeProject } from '../src/upgrader.js';
import { spawnSync } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function getPackageVersion() {
  try {
    const pkgPath = path.resolve(__dirname, '..', 'package.json');
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
    return pkg.version || '0.0.1';
  } catch {
    return '0.0.1';
  }
}

function printHelp() {
  const version = getPackageVersion();
  console.log(`
${ui.colors.brightCyan}${ui.colors.bold}Archlens Skill & Policy Installer${ui.colors.reset} ${ui.colors.dim}v${version}${ui.colors.reset}

${ui.colors.bold}USAGE:${ui.colors.reset}
  npx @fmatar/archlens-skill [command] [options]
  archlens-skill [command] [options]
  archlens-init [command] [options]

${ui.colors.bold}COMMANDS:${ui.colors.reset}
  init                     Initialize Clean Architecture policy in target project (default)
  update                   Update existing policy with newly discovered packages and refresh skills
  upgrade                  Upgrade legacy .uml-viewer configuration to .archlens
  global                   Install Archlens skills globally for AI coding assistants
  prompt                   Export Clean Architecture LLM Refactoring Prompt Dossier

${ui.colors.bold}OPTIONS:${ui.colors.reset}
  -p, --path <DIR>         Target repository path (default: current directory)
  -t, --title <NAME>       Project title displayed in Archlens workbench
  --prefix <PKG>           Common package prefix (e.g. com.example.app)
  --server-url <URL>       Archlens visual workbench URL (default: http://localhost:8088)
  -c, --copy               Copy LLM prompt output directly to system clipboard
  -g, --global             Install or update skill globally in ~/.claude and ~/.gemini
  -u, --update             Update mode: sync new packages into existing policy
  --upgrade                Upgrade mode: migrate .uml-viewer to .archlens
  -f, --force              Overwrite existing policy files
  --dry-run                Simulate actions without writing files to disk
  -y, --yes                Accept defaults automatically (non-interactive mode)
  -v, --version            Display version information
  -h, --help               Display this help guide

${ui.colors.bold}EXAMPLES:${ui.colors.reset}
  ${ui.colors.dim}# Export Clean Architecture LLM Refactoring Prompt to stdout:${ui.colors.reset}
  npx @fmatar/archlens-skill prompt

  ${ui.colors.dim}# Copy prompt directly to system clipboard:${ui.colors.reset}
  npx @fmatar/archlens-skill prompt --copy

  ${ui.colors.dim}# Interactive setup wizard:${ui.colors.reset}
  npx @fmatar/archlens-skill

  ${ui.colors.dim}# Initialize policy in current directory with default settings:${ui.colors.reset}
  npx @fmatar/archlens-skill --yes

  ${ui.colors.dim}# Upgrade legacy .uml-viewer configuration to .archlens:${ui.colors.reset}
  npx @fmatar/archlens-skill upgrade

  ${ui.colors.dim}# Install Archlens skill globally for Claude Code and Gemini CLI:${ui.colors.reset}
  npx @fmatar/archlens-skill global

  ${ui.colors.dim}# Update existing policy after adding new modules:${ui.colors.reset}
  npx @fmatar/archlens-skill update
`);
}

function parseArgs(args) {
  const parsed = {
    command: null,
    path: '.',
    title: null,
    prefix: null,
    serverUrl: 'http://localhost:8088',
    copy: false,
    prompt: false,
    global: false,
    update: false,
    upgrade: false,
    force: false,
    dryRun: false,
    yes: false,
    version: false,
    help: false
  };

  const positional = [];

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];

    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else if (arg === '--version' || arg === '-v') {
      parsed.version = true;
    } else if (arg === '--global' || arg === '-g') {
      parsed.global = true;
    } else if (arg === '--update' || arg === '-u') {
      parsed.update = true;
    } else if (arg === '--upgrade') {
      parsed.upgrade = true;
    } else if (arg === '--prompt') {
      parsed.prompt = true;
    } else if (arg === '--copy' || arg === '-c') {
      parsed.copy = true;
    } else if (arg === '--force' || arg === '-f') {
      parsed.force = true;
    } else if (arg === '--dry-run') {
      parsed.dryRun = true;
    } else if (arg === '--yes' || arg === '-y') {
      parsed.yes = true;
    } else if (arg === '--path' || arg === '-p') {
      parsed.path = args[++i] || '.';
    } else if (arg === '--title' || arg === '-t') {
      parsed.title = args[++i];
    } else if (arg === '--prefix') {
      parsed.prefix = args[++i];
    } else if (arg === '--server-url') {
      parsed.serverUrl = args[++i] || 'http://localhost:8088';
    } else if (!arg.startsWith('-')) {
      positional.push(arg);
    }
  }

  if (positional.length > 0) {
    const cmd = positional[0].toLowerCase();
    if (['init', 'update', 'upgrade', 'global', 'prompt', 'help', 'version'].includes(cmd)) {
      parsed.command = cmd;
    } else if (!parsed.title && !parsed.path) {
      parsed.path = positional[0];
    }
  }

  if (parsed.command === 'help') parsed.help = true;
  if (parsed.command === 'version') parsed.version = true;
  if (parsed.command === 'update') parsed.update = true;
  if (parsed.command === 'upgrade') parsed.upgrade = true;
  if (parsed.command === 'global') parsed.global = true;
  if (parsed.command === 'prompt') parsed.prompt = true;

  return parsed;
}

async function runInteractive(options) {
  ui.banner();

  const targetRoot = path.resolve(options.path || '.');
  const lang = detectProjectLanguage(targetRoot);
  const src = detectSourceRoot(targetRoot, lang);
  const prefix = options.prefix || findCommonPackagePrefix(path.join(targetRoot, src));

  console.log(`${ui.colors.bold}Target Workspace Diagnostics:${ui.colors.reset}`);
  ui.table([
    ['Directory', targetRoot],
    ['Language', lang.toUpperCase()],
    ['Source Root', src],
    ['Package Prefix', prefix || '(root)']
  ]);

  const choices = [
    {
      value: 'init_local',
      label: `${ui.colors.bold}Initialize Archlens Policy locally${ui.colors.reset}`,
      description: 'Scaffolds .archlens/policy.json and AI companion protocols'
    },
    {
      value: 'install_global',
      label: `${ui.colors.bold}Deploy AI Agent Skills globally${ui.colors.reset}`,
      description: 'Equips Claude Code, Gemini CLI, and Antigravity with Archlens skills'
    },
    {
      value: 'both',
      label: `${ui.colors.bold}Full Suite (Local Policy + Global Skills)${ui.colors.reset}`,
      description: 'Configures current repository and installs global AI assistant skills'
    },
    {
      value: 'update',
      label: `${ui.colors.bold}Update Existing Policy & Skills${ui.colors.reset}`,
      description: 'Re-scans code for new packages and updates installed agent skills'
    },
    {
      value: 'upgrade',
      label: `${ui.colors.bold}Upgrade Legacy Config (.uml-viewer -> .archlens)${ui.colors.reset}`,
      description: 'Migrate configuration and mailbox directories to modern .archlens layout'
    }
  ];

  const selectedAction = await ui.askChoice('Select installation action:', choices);

  let customTitle = options.title;
  if (['init_local', 'both'].includes(selectedAction) && !customTitle) {
    const defaultTitle = ui.colors.dim + path.basename(targetRoot) + ui.colors.reset;
    customTitle = await ui.askQuestion('Project display title', path.basename(targetRoot));
  }

  const execOptions = {
    ...options,
    title: customTitle,
    prefix
  };

  ui.divider();

  if (selectedAction === 'init_local' || selectedAction === 'both') {
    executeLocalInit(targetRoot, execOptions);
  }

  if (selectedAction === 'install_global' || selectedAction === 'both') {
    executeGlobalInstall(execOptions);
  }

  if (selectedAction === 'update') {
    executeUpdate(targetRoot, execOptions);
  }

  if (selectedAction === 'upgrade') {
    executeUpgrade(targetRoot, execOptions);
  }

  printCompletionMessage(targetRoot, execOptions.serverUrl);
}

function executeLocalInit(targetRoot, options) {
  ui.step(1, 2, 'Scaffolding Clean Architecture Policy');
  const result = installLocalPolicy(targetRoot, options);

  if (result.policyCreated) {
    ui.success(`Created policy file: ${path.relative(targetRoot, result.policyFile)}`);
  } else {
    ui.info(`Policy file already exists: ${path.relative(targetRoot, result.policyFile)} (use --force to overwrite)`);
  }

  ui.success(`Created workbench config: ${path.relative(targetRoot, result.configFile)}`);

  if (result.companionFiles.length > 0) {
    ui.success(`Configured agent guidelines in: ${result.companionFiles.join(', ')}`);
  }

  if (result.policyData) {
    console.log(`\n  ${ui.colors.bold}Architectural Rings Defined:${ui.colors.reset}`);
    const tiers = ['Domain Core (L0)', 'Application (L1)', 'Adapters (L2)', 'Infrastructure (L3)'];
    result.policyData.levels.forEach((lvl, idx) => {
      const tierName = tiers[idx] || `Level ${idx}`;
      console.log(`    ${ui.colors.cyan}${tierName}:${ui.colors.reset} [${lvl.join(', ')}]`);
    });
  }
}

function executeGlobalInstall(options) {
  const modeSuffix = options.dryRun ? ' (dry run)' : '';
  ui.step(2, 2, `Deploying AI Assistant Skills Globally${modeSuffix}`);
  const result = installGlobalSkills(options);

  if (result.installed.length === 0) {
    ui.warn('No active AI agent directories found (~/.claude, ~/.gemini). Use --force to deploy anyway.');
  } else {
    for (const inst of result.installed) {
      ui.success(`Installed skill for ${inst.agent}: ${inst.path}${modeSuffix}`);
    }
  }
}

function executeUpgrade(targetRoot, options) {
  const modeSuffix = options.dryRun ? ' (dry run)' : '';
  ui.step(1, 1, `Migrating Configuration to .archlens${modeSuffix}`);
  const result = upgradeProject(targetRoot, options);

  if (result.migrated) {
    ui.success(
      `Migrated legacy directory: ${path.relative(targetRoot, result.legacyDir)} -> ${path.relative(targetRoot, result.primaryDir)}`
    );
    if (result.migratedFiles.length > 0) {
      ui.info(`Files moved: ${result.migratedFiles.join(', ')}`);
    }
  } else {
    ui.info('Project is already aligned with .archlens configuration standard.');
  }

  if (result.updatedCompanions.length > 0) {
    ui.success(`Updated companion references in: ${result.updatedCompanions.join(', ')}`);
  }
}

function executeUpdate(targetRoot, options) {
  const modeSuffix = options.dryRun ? ' (dry run)' : '';
  ui.step(1, 2, `Synchronizing Clean Architecture Policy${modeSuffix}`);
  const result = updateLocalPolicy(targetRoot, options);

  if (result.newPackages.length > 0) {
    ui.success(`Detected and incorporated ${result.newPackages.length} new package(s): ${result.newPackages.join(', ')}`);
  } else {
    ui.info('All discovered packages are already declared in the architectural policy.');
  }
  ui.info(`Total governed packages: ${result.totalPackages}`);

  ui.step(2, 2, `Refreshing Global AI Assistant Skills${modeSuffix}`);
  const globalResult = updateGlobalSkills(options);
  if (globalResult.updatedLocations && globalResult.updatedLocations.length > 0) {
    for (const loc of globalResult.updatedLocations) {
      ui.success(`Updated skill for ${loc.agent}: ${loc.path}${modeSuffix}`);
    }
  }
}

function printCompletionMessage(targetRoot, serverUrl) {
  ui.divider();
  console.log(`\n${ui.colors.brightGreen}${ui.colors.bold}✦ Archlens Configuration Complete! ✦${ui.colors.reset}\n`);
  console.log(`To visualize and govern Clean Architecture boundaries:`);
  console.log(`  1. Launch Archlens workbench`);
  console.log(`  2. Visit: ${ui.colors.brightCyan}${serverUrl}?projectRoot=${targetRoot}${ui.colors.reset}`);
  console.log(`  3. Or press ${ui.colors.bold}⌘O / Ctrl+O${ui.colors.reset} inside Archlens to select this workspace.\n`);
}

function copyToSystemClipboard(text) {
  try {
    if (process.platform === 'darwin') {
      spawnSync('pbcopy', { input: text, encoding: 'utf8' });
      return true;
    } else if (process.platform === 'win32') {
      spawnSync('clip', { input: text, encoding: 'utf8' });
      return true;
    } else {
      const res = spawnSync('xclip', ['-selection', 'clipboard'], { input: text, encoding: 'utf8' });
      if (res.error) {
        spawnSync('wl-copy', { input: text, encoding: 'utf8' });
      }
      return true;
    }
  } catch {
    return false;
  }
}

async function main() {
  const options = parseArgs(process.argv.slice(2));

  if (options.help) {
    printHelp();
    process.exit(0);
  }

  if (options.version) {
    console.log(getPackageVersion());
    process.exit(0);
  }

  if (options.command === 'prompt' || options.prompt) {
    const targetRoot = path.resolve(options.path || '.');
    try {
      const dossier = await generateLlmPrompt(targetRoot, options);
      if (options.copy) {
        const ok = copyToSystemClipboard(dossier);
        if (ok) {
          ui.success('Clean Architecture Refactoring Dossier copied to system clipboard!');
        } else {
          process.stdout.write(dossier);
        }
      } else {
        process.stdout.write(dossier);
      }
      process.exit(0);
    } catch (err) {
      ui.error(err.message || String(err));
      process.exit(1);
    }
  }

  const isInteractive = Boolean(
    process.stdin.isTTY &&
    process.stdout.isTTY &&
    !options.yes &&
    !options.dryRun &&
    !options.command &&
    !options.global &&
    !options.update &&
    !options.upgrade &&
    !options.prompt
  );

  if (isInteractive) {
    try {
      await runInteractive(options);
    } catch (err) {
      ui.error(err.message || String(err));
      process.exit(1);
    }
    return;
  }

  // Non-interactive or flag-driven execution
  const targetRoot = path.resolve(options.path || '.');

  try {
    if (options.upgrade) {
      executeUpgrade(targetRoot, options);
    } else if (options.update) {
      executeUpdate(targetRoot, options);
    } else if (options.global && !options.command) {
      executeGlobalInstall(options);
      executeLocalInit(targetRoot, options);
    } else if (options.global || options.command === 'global') {
      executeGlobalInstall(options);
    } else {
      executeLocalInit(targetRoot, options);
    }

    if (!options.dryRun) {
      printCompletionMessage(targetRoot, options.serverUrl);
    }
  } catch (err) {
    ui.error(err.message || String(err));
    process.exit(1);
  }
}

main();
