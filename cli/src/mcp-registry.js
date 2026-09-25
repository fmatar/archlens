/**
 * Archlens Multi-Client MCP Registry
 * Discovers and configures Model Context Protocol (MCP) clients:
 * Claude Desktop, Claude Code, Google Antigravity, Gemini CLI, Cursor, and VS Code.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

export const ARCHLENS_TOOL_SCHEMAS = {
  inspectArchitecture: {
    name: 'inspectArchitecture',
    description:
      'Inspect codebase Clean Architecture concentric rings, inward dependency breaches, complexity hotspots, and instability metrics.',
    parameters: {
      type: 'object',
      properties: {
        projectRoot: {
          type: 'string',
          description:
            'Absolute or relative path to the target project directory. Defaults to current directory.',
          default: '.'
        }
      },
      required: []
    }
  },
  exportLlmDossier: {
    name: 'exportLlmDossier',
    description:
      'Export an actionable Clean Architecture LLM prompt dossier diagnosing outward dependency breaches and prescribing concrete Dependency Inversion Principle (DIP) interface ports.',
    parameters: {
      type: 'object',
      properties: {
        projectRoot: {
          type: 'string',
          description:
            'Absolute or relative path to the target project directory. Defaults to current directory.',
          default: '.'
        },
        proposalId: {
          type: 'string',
          description:
            'Optional structural proposal ID. Defaults to empty for active architecture.',
          default: ''
        }
      },
      required: []
    }
  },
  listSnapshots: {
    name: 'listSnapshots',
    description:
      'List available architecture release snapshots in .archlens/snapshots and historical Git release tags for architectural comparison.',
    parameters: {
      type: 'object',
      properties: {
        projectRoot: {
          type: 'string',
          description:
            'Absolute or relative path to the target project directory. Defaults to current directory.',
          default: '.'
        }
      },
      required: []
    }
  },
  getSnapshot: {
    name: 'getSnapshot',
    description:
      'Retrieve the Clean Architecture model for a specific historical Git release tag or pre-compiled snapshot ID.',
    parameters: {
      type: 'object',
      properties: {
        snapshotId: {
          type: 'string',
          description: 'Snapshot identifier or Git release tag name (e.g. v1.0.0)'
        },
        projectRoot: {
          type: 'string',
          description:
            'Absolute or relative path to the target project directory. Defaults to current directory.',
          default: '.'
        }
      },
      required: ['snapshotId']
    }
  }
};

export function getAntigravityInstructions() {
  return `# Archlens Clean Architecture MCP Server Instructions

## Overview
Archlens provides automated Clean Architecture governance and static structural analysis.
It enforces the Dependency Rule: source code dependencies must point inward toward higher-level policies.

### Concentric Layer Hierarchy:
1. **Domain Core (L0)**: Pure business entities and primitives. Zero outward imports.
2. **Application (L1)**: Use cases and business orchestrations. Depends only on Domain Core.
3. **Adapters (L2)**: Gateways, presenters, and controllers converting between UI/database formats and application models.
4. **Infrastructure (L3)**: Web frameworks, databases, network libraries, UI rendering, CLI entrypoints.

## Available Tools:
- \`inspectArchitecture\`: Analyzes codebase rings, computes instability metrics, and reports illegal outward dependency breaches.
- \`exportLlmDossier\`: Returns a structured refactoring dossier with concrete Dependency Inversion Principle (DIP) interface port specifications.
- \`listSnapshots\`: Lists historical Git release tags and pre-compiled snapshot files.
- \`getSnapshot\`: Retrieves architectural graph models for a specific historical release tag.

## Refactoring Guidelines:
Whenever outward breaches are detected from inner layers (Domain Core or Application) to outer layers (Adapters or Infrastructure):
1. Create an interface port inside the inner layer declaring required contract operations.
2. Implement the interface inside the outer layer.
3. Inject the outer implementation into the inner layer using constructor or dependency injection.
`.trim();
}

export function getClientConfigLocations(projectRoot = '.', homedir = os.homedir()) {
  const root = path.resolve(projectRoot);

  let claudeDesktopPath = '';
  if (process.platform === 'darwin') {
    claudeDesktopPath = path.join(
      homedir,
      'Library',
      'Application Support',
      'Claude',
      'claude_desktop_config.json'
    );
  } else if (process.platform === 'win32') {
    const appData = process.env.APPDATA || path.join(homedir, 'AppData', 'Roaming');
    claudeDesktopPath = path.join(appData, 'Claude', 'claude_desktop_config.json');
  } else {
    claudeDesktopPath = path.join(homedir, '.config', 'Claude', 'claude_desktop_config.json');
  }

  return [
    {
      id: 'claude-desktop',
      name: 'Claude Desktop',
      type: 'json-manifest',
      scope: 'global',
      filePath: claudeDesktopPath,
      parentDir: path.dirname(claudeDesktopPath)
    },
    {
      id: 'claude-code',
      name: 'Claude Code',
      type: 'json-manifest',
      scope: 'local',
      filePath: path.join(root, '.mcp.json'),
      parentDir: root
    },
    {
      id: 'antigravity-global',
      name: 'Google Antigravity & Gemini CLI',
      type: 'tool-directory',
      scope: 'global',
      filePath: path.join(homedir, '.gemini', 'antigravity', 'mcp', 'archlens'),
      parentDir: path.join(homedir, '.gemini', 'antigravity', 'mcp')
    },
    {
      id: 'cursor',
      name: 'Cursor',
      type: 'json-manifest',
      scope: 'local',
      filePath: path.join(root, '.cursor', 'mcp.json'),
      parentDir: path.join(root, '.cursor')
    },
    {
      id: 'vscode',
      name: 'VS Code',
      type: 'json-manifest',
      scope: 'local',
      filePath: path.join(root, '.vscode', 'mcp.json'),
      parentDir: path.join(root, '.vscode')
    }
  ];
}

export function getMcpManifestEntry(serverUrl = 'http://localhost:8088') {
  return {
    command: 'npx',
    args: ['-y', '@fmatar/archlens-skill', 'mcp'],
    env: {
      ARCHLENS_SERVER_URL: serverUrl
    }
  };
}

export function installMcpConfigs(options = {}) {
  const projectRoot = path.resolve(options.path || '.');
  const homedir = options.homedir || os.homedir();
  const dryRun = Boolean(options.dryRun);
  const force = Boolean(options.force);
  const serverUrl = options.serverUrl || 'http://localhost:8088';

  const targets = getClientConfigLocations(projectRoot, homedir);
  const results = [];

  for (const target of targets) {
    const parentExists = fs.existsSync(target.parentDir);
    const targetExists = fs.existsSync(target.filePath);

    // Install if parent directory exists or if force flag is requested
    const shouldInstall = force || parentExists || targetExists;
    if (!shouldInstall) {
      continue;
    }

    if (target.type === 'tool-directory') {
      // Antigravity schema directory
      if (!dryRun) {
        fs.mkdirSync(target.filePath, { recursive: true });
        for (const [toolName, schema] of Object.entries(ARCHLENS_TOOL_SCHEMAS)) {
          const toolFilePath = path.join(target.filePath, `${toolName}.json`);
          fs.writeFileSync(toolFilePath, JSON.stringify(schema, null, 2) + '\n', 'utf8');
        }
        const instructionsPath = path.join(target.filePath, 'instructions.md');
        fs.writeFileSync(instructionsPath, getAntigravityInstructions() + '\n', 'utf8');
      }

      results.push({
        id: target.id,
        name: target.name,
        path: target.filePath,
        type: target.type,
        action: targetExists ? 'updated' : 'created'
      });
    } else if (target.type === 'json-manifest') {
      let manifest = {};
      if (fs.existsSync(target.filePath)) {
        try {
          manifest = JSON.parse(fs.readFileSync(target.filePath, 'utf8'));
        } catch {
          manifest = {};
        }
      }

      if (!manifest.mcpServers || typeof manifest.mcpServers !== 'object') {
        manifest.mcpServers = {};
      }

      manifest.mcpServers.archlens = getMcpManifestEntry(serverUrl);

      if (!dryRun) {
        fs.mkdirSync(path.dirname(target.filePath), { recursive: true });
        fs.writeFileSync(target.filePath, JSON.stringify(manifest, null, 2) + '\n', 'utf8');
      }

      results.push({
        id: target.id,
        name: target.name,
        path: target.filePath,
        type: target.type,
        action: targetExists ? 'updated' : 'created'
      });
    }
  }

  return {
    serverUrl,
    configuredClients: results,
    dryRun
  };
}
