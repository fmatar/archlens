/**
 * Archlens Policy & Multi-Agent Skill Installer
 * Scaffolds Clean Architecture policies locally and deploys skills globally.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { generatePolicy } from './analyzer.js';
import { installMcpConfigs } from './mcp-registry.js';

export { installMcpConfigs };

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const COMPANION_PROTOCOL_HEADER = '## Archlens Clean Architecture Workbench Companion Protocol';

export function getCompanionProtocolBlock(serverUrl = 'http://localhost:8088') {
  return `
${COMPANION_PROTOCOL_HEADER}

This project is governed by the **Archlens Dynamic Clean Architecture Workbench**.
- **Workbench UI & API**: \`${serverUrl}\`
- **Architectural Policy**: \`.archlens/policy.json\`
- **Mailbox IPC**:
  - Inbound queue: \`.archlens/to-agent.json\`
  - Outbound response: \`.archlens/to-viewer.json\`

### Handling Mailbox Commands:
1. **REGEN**: Re-index AST, evaluate package dependency rules, and acknowledge.
2. **APPLY_PROPOSAL**: Reorganize packages to conform to concentric rings (Adapters -> Application -> Domain Core). Where an inner layer relies on an outer layer, apply the **Dependency Inversion Principle** by creating an interface port in the inner layer and implementing it in the outer layer.
3. **REFRESH_CRAP**: Execute test suite and report code coverage metrics.
`.trim();
}

export function installLocalPolicy(projectRoot, options = {}) {
  const root = path.resolve(projectRoot);
  const dryRun = Boolean(options.dryRun);
  const force = Boolean(options.force);
  const serverUrl = options.serverUrl || 'http://localhost:8088';

  if (!fs.existsSync(root)) {
    throw new Error(`Target directory does not exist: ${root}`);
  }

  const primaryDir = path.join(root, '.archlens');
  const legacyDir = path.join(root, '.uml-viewer');

  const primaryPolicyFile = path.join(primaryDir, 'policy.json');
  const legacyPolicyFile = path.join(legacyDir, 'policy.json');

  let targetDir = primaryDir;
  let policyFile = primaryPolicyFile;

  // Honor legacy directory if present and primary does not exist yet
  if (!fs.existsSync(primaryPolicyFile) && fs.existsSync(legacyPolicyFile)) {
    targetDir = legacyDir;
    policyFile = legacyPolicyFile;
  }

  const configFile = path.join(targetDir, 'workbench.config.json');

  let policyCreated = false;
  let policyData = null;

  if (!dryRun && !fs.existsSync(targetDir)) {
    fs.mkdirSync(targetDir, { recursive: true });
  }

  if (fs.existsSync(policyFile) && !force) {
    policyCreated = false;
    policyData = JSON.parse(fs.readFileSync(policyFile, 'utf8'));
  } else {
    policyData = generatePolicy(root, options);
    if (!dryRun) {
      fs.writeFileSync(policyFile, JSON.stringify(policyData, null, 2) + '\n', 'utf8');
    }
    policyCreated = true;
  }

  const workbenchConfig = {
    serverUrl,
    protocolVersion: '1.0',
    description: 'Visual Workbench communication endpoint and mailbox IPC configuration'
  };

  if (!dryRun) {
    fs.writeFileSync(configFile, JSON.stringify(workbenchConfig, null, 2) + '\n', 'utf8');
  }

  const companionFiles = ['CLAUDE.md', 'AGENTS.md'];
  const updatedCompanions = [];

  for (const filename of companionFiles) {
    const filePath = path.join(root, filename);
    const companionBlock = getCompanionProtocolBlock(serverUrl);

    if (fs.existsSync(filePath)) {
      const existing = fs.readFileSync(filePath, 'utf8');
      if (!existing.includes(COMPANION_PROTOCOL_HEADER)) {
        if (!dryRun) {
          fs.writeFileSync(filePath, existing.trimEnd() + '\n\n' + companionBlock + '\n', 'utf8');
        }
        updatedCompanions.push(filename);
      }
    } else {
      if (!dryRun) {
        fs.writeFileSync(filePath, `# Assistant Guidelines\n\n${companionBlock}\n`, 'utf8');
      }
      updatedCompanions.push(filename);
    }
  }

  let mcpResult = null;
  if (options.mcp) {
    mcpResult = installMcpConfigs({ path: root, ...options });
  }

  let skillsResult = null;
  if (options.skills) {
    skillsResult = installLocalSkills(root, options);
  }

  return {
    targetDir: root,
    archlensDir: targetDir,
    umlDir: targetDir,
    policyFile,
    configFile,
    policyCreated,
    policyData,
    companionFiles: updatedCompanions,
    mcpResult,
    skillsResult,
    dryRun
  };
}

function parseListOption(val) {
  if (!val) return [];
  if (Array.isArray(val)) return val.map((v) => String(v).trim()).filter(Boolean);
  if (typeof val === 'string') {
    return val
      .split(',')
      .map((v) => v.trim())
      .filter(Boolean);
  }
  return [];
}

export function buildSkillsCliArgs(options = {}) {
  const packageRoot = options.sourceDir || path.resolve(__dirname, '..');
  const args = ['--yes', 'skills', 'add', packageRoot];

  const selectedSkills = parseListOption(options.skill || options.skillsList);
  if (selectedSkills.length > 0) {
    for (const s of selectedSkills) {
      args.push('--skill', s);
    }
  } else {
    args.push('--all');
  }

  if (options.global) {
    args.push('-g');
  }

  const agents = parseListOption(options.agent || options.agents);
  for (const a of agents) {
    args.push('--agent', a);
  }

  if (!options.symlink) {
    args.push('--copy');
  }
  args.push('-y');
  return args;
}

function runSkillsCliInstall(options = {}) {
  const packageRoot = options.skillsSource || path.resolve(__dirname, '..');
  const spawnFn = options.spawnSync || spawnSync;
  const isWin = process.platform === 'win32';
  const npxCmd = isWin ? 'npx.cmd' : 'npx';
  const args = buildSkillsCliArgs({ ...options, sourceDir: packageRoot });

  const proc = spawnFn(npxCmd, args, {
    cwd: options.cwd || process.cwd(),
    encoding: 'utf8',
    timeout: options.timeoutMs || 60000,
    stdio: ['ignore', 'pipe', 'pipe']
  });

  if (proc && proc.status === 0) {
    return {
      ok: true,
      sourceDir: packageRoot,
      output: proc.stdout || ''
    };
  }
  return { ok: false, error: proc?.stderr || '' };
}

function listBundledSkillNames(bundledSkillsDir) {
  if (!fs.existsSync(bundledSkillsDir)) return ['archlens', 'archlens-install-policy', 'uml-workbench-companion'];
  try {
    return fs
      .readdirSync(bundledSkillsDir, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name);
  } catch {
    return ['archlens-install-policy'];
  }
}

export function getGlobalSkillDirectories(home = os.homedir()) {
  const bundledSkillsDir = path.resolve(__dirname, '..', 'skills');
  const skillNames = listBundledSkillNames(bundledSkillsDir);

  const agents = [
    {
      agent: 'Claude Code',
      baseDir: path.join(home, '.claude', 'skills')
    },
    {
      agent: 'Gemini CLI & Google Antigravity',
      baseDir: path.join(home, '.gemini', 'config', 'skills')
    },
    {
      agent: 'Generic Agents Workspace',
      baseDir: path.join(home, '.agents', 'skills')
    }
  ];

  const results = [];
  for (const a of agents) {
    for (const skillName of skillNames) {
      results.push({
        agent: a.agent,
        skill: skillName,
        baseDir: a.baseDir,
        targetDir: path.join(a.baseDir, skillName)
      });
    }
  }
  return results;
}

export function installLocalSkills(projectRoot, options = {}) {
  const root = path.resolve(projectRoot);
  const dryRun = Boolean(options.dryRun);
  const bundledSkillsRoot = path.resolve(__dirname, '..', 'skills');

  if (options.preferVercelSkills !== false && !dryRun) {
    try {
      const cliRes = runSkillsCliInstall({
        ...options,
        global: false,
        cwd: root
      });
      if (cliRes.ok) {
        return {
          sourceDir: cliRes.sourceDir,
          method: 'skills-cli',
          output: cliRes.output,
          installed: [
            {
              agent: options.agent || 'Detected Project Agents (via npx skills)',
              skill: 'archlens-suite',
              path: root
            }
          ],
          dryRun: false
        };
      }
    } catch {
      // Offline fallback to standard .agents/skills directory
    }
  }

  const installed = [];
  for (const skillName of listBundledSkillNames(bundledSkillsRoot)) {
    const sourceSkillDir = path.join(bundledSkillsRoot, skillName);
    const targetSkillDir = path.join(root, '.agents', 'skills', skillName);
    if (fs.existsSync(sourceSkillDir)) {
      if (!dryRun) {
        fs.mkdirSync(targetSkillDir, { recursive: true });
        copyDirectorySync(sourceSkillDir, targetSkillDir);
      }
      installed.push({
        agent: 'Project Agents (.agents/skills)',
        skill: skillName,
        path: targetSkillDir
      });
    }
  }

  return {
    sourceDir: bundledSkillsRoot,
    method: 'direct-copy',
    installed,
    dryRun
  };
}

export function installGlobalSkills(options = {}) {
  const dryRun = Boolean(options.dryRun);
  const force = Boolean(options.force);
  const bundledSkillsRoot = path.resolve(__dirname, '..', 'skills');

  // Primary path: delegate to `npx skills add <cliPackageRoot> --all -g --copy -y`
  if ((options.preferVercelSkills ?? !options.homedir) && !dryRun) {
    try {
      const cliRes = runSkillsCliInstall({
        ...options,
        global: true
      });
      if (cliRes.ok) {
        return {
          sourceDir: cliRes.sourceDir,
          method: 'skills-cli',
          output: cliRes.output,
          installed: [
            {
              agent: options.agent || 'All Supported Agents (via npx skills)',
              skill: 'archlens-suite',
              path: 'global'
            }
          ],
          dryRun: false
        };
      }
    } catch {
      // Fallback seamlessly when offline / air-gapped
    }
  }

  const destinations = getGlobalSkillDirectories(options.homedir);
  const installed = [];

  for (const dest of destinations) {
    const agentHomeDir = path.dirname(dest.baseDir);
    const shouldInstall = force || fs.existsSync(agentHomeDir);
    const sourceSkillDir = path.join(bundledSkillsRoot, dest.skill);

    if (shouldInstall && fs.existsSync(sourceSkillDir)) {
      if (!dryRun) {
        fs.mkdirSync(dest.targetDir, { recursive: true });
        copyDirectorySync(sourceSkillDir, dest.targetDir);
      }
      installed.push({
        agent: dest.agent,
        skill: dest.skill,
        path: dest.targetDir
      });
    }
  }

  return {
    sourceDir: bundledSkillsRoot,
    method: 'direct-copy',
    installed,
    dryRun
  };
}

function copyDirectorySync(srcDir, destDir) {
  if (!fs.existsSync(destDir)) {
    fs.mkdirSync(destDir, { recursive: true });
  }

  const entries = fs.readdirSync(srcDir, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(srcDir, entry.name);
    const destPath = path.join(destDir, entry.name);

    if (entry.isDirectory()) {
      copyDirectorySync(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}
