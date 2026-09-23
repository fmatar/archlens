/**
 * Archlens Policy & Multi-Agent Skill Installer
 * Scaffolds Clean Architecture policies locally and deploys skills globally.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { fileURLToPath } from 'node:url';
import { generatePolicy } from './analyzer.js';

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

  return {
    targetDir: root,
    archlensDir: targetDir,
    umlDir: targetDir,
    policyFile,
    configFile,
    policyCreated,
    policyData,
    companionFiles: updatedCompanions,
    dryRun
  };
}

export function getGlobalSkillDirectories() {
  const home = os.homedir();
  return [
    {
      agent: 'Claude Code',
      baseDir: path.join(home, '.claude', 'skills'),
      targetDir: path.join(home, '.claude', 'skills', 'archlens-install-policy')
    },
    {
      agent: 'Gemini CLI & Google Antigravity',
      baseDir: path.join(home, '.gemini', 'config', 'skills'),
      targetDir: path.join(home, '.gemini', 'config', 'skills', 'archlens-install-policy')
    },
    {
      agent: 'Generic Agents Workspace',
      baseDir: path.join(home, '.agents', 'skills'),
      targetDir: path.join(home, '.agents', 'skills', 'archlens-install-policy')
    }
  ];
}

export function installGlobalSkills(options = {}) {
  const dryRun = Boolean(options.dryRun);
  const force = Boolean(options.force);
  const bundledSkillDir = path.resolve(__dirname, '..', 'skills', 'archlens-install-policy');

  if (!fs.existsSync(bundledSkillDir)) {
    throw new Error(`Bundled skill template directory not found at: ${bundledSkillDir}`);
  }

  const destinations = getGlobalSkillDirectories();
  const installed = [];

  for (const dest of destinations) {
    // Install if parent agent directory exists or if force flag is enabled
    const agentHomeDir = path.dirname(dest.baseDir);
    const shouldInstall = force || fs.existsSync(agentHomeDir);

    if (shouldInstall) {
      if (!dryRun) {
        fs.mkdirSync(dest.targetDir, { recursive: true });
        copyDirectorySync(bundledSkillDir, dest.targetDir);
      }
      installed.push({
        agent: dest.agent,
        path: dest.targetDir
      });
    }
  }

  return {
    sourceDir: bundledSkillDir,
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
