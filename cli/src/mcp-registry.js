/**
 * Archlens Multi-Client MCP Registry
 * Discovers and configures Model Context Protocol (MCP) clients:
 * Claude Desktop, Claude Code, Google Antigravity, Gemini CLI, Cursor, and VS Code.
 * Zero external dependencies.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';
import { DEFAULT_DOCKER_IMAGE } from './docker-runner.js';

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
  exportArchitectureDiagram: {
    name: 'exportArchitectureDiagram',
    description:
      'Export a deterministic Mermaid architecture diagram (COMPONENT_RINGS, CLASS_UML, or DIP_REFACTOR) for the target project.',
    parameters: {
      type: 'object',
      properties: {
        projectRoot: {
          type: 'string',
          description:
            'Absolute or relative path to the target project directory. Defaults to current directory.',
          default: '.'
        },
        diagramType: {
          type: 'string',
          description:
            'Diagram export type: COMPONENT_RINGS, CLASS_UML, or DIP_REFACTOR. Defaults to COMPONENT_RINGS.',
          default: 'COMPONENT_RINGS'
        },
        proposalId: {
          type: 'string',
          description:
            'Optional structural proposal ID. Defaults to empty for active architecture.',
          default: ''
        },
        violationsOnly: {
          type: 'boolean',
          description:
            'When true, restrict exported nodes and edges to outward Clean Architecture violations.',
          default: false
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
  },
  synthesizeDipInversion: {
    name: 'synthesizeDipInversion',
    description:
      'Generate a Dependency Inversion Principle (DIP) interface port and outward adapter blueprint to resolve a concentric ring violation.',
    parameters: {
      type: 'object',
      properties: {
        fromClass: {
          type: 'string',
          description: 'Fully qualified or simple name of the inner-layer class with the outward dependency (alias: sourceClass)'
        },
        toClass: {
          type: 'string',
          description: 'Fully qualified or simple name of the outer-layer class being depended upon (alias: targetClass)'
        },
        projectRoot: {
          type: 'string',
          description:
            'Absolute or relative path to the target project directory. Defaults to current directory.',
          default: '.'
        }
      },
      required: ['fromClass', 'toClass']
    }
  },
  evaluateFitness: {
    name: 'evaluateFitness',
    description:
      'Evaluate the Architecture Fitness Index (AFI), Main Sequence Distance, Screaming Architecture Score, and concentric ring compliance.',
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
  getFitnessHistory: {
    name: 'getFitnessHistory',
    description:
      'Retrieve historical Architecture Fitness Index (AFI) snapshots and regression trend (IMPROVING, STABLE, DEGRADING).',
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
  proposeScreamingMigration: {
    name: 'proposeScreamingMigration',
    description:
      'Generate a Screaming Architecture package-by-feature verticalization migration proposal.',
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
  getPolicy: {
    name: 'getPolicy',
    description:
      'Retrieve the active .archlens/policy.json concentric ring governance policy for the target project.',
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
  syncDipAdr: {
    name: 'syncDipAdr',
    description:
      'Generate and synchronize an Architecture Decision Record (ADR) documenting a Dependency Inversion Principle (DIP) port extraction.',
    parameters: {
      type: 'object',
      properties: {
        sourceClass: {
          type: 'string',
          description: 'Fully qualified or simple name of the inner-layer class'
        },
        targetClass: {
          type: 'string',
          description: 'Fully qualified or simple name of the outer-layer class'
        },
        interfacePortName: {
          type: 'string',
          description: 'Synthesized interface port name (e.g. OrderRepositoryPort)'
        },
        reason: {
          type: 'string',
          description: 'Architectural rationale for the DIP inversion',
          default: 'Invert outward Clean Architecture dependency'
        },
        projectRoot: {
          type: 'string',
          description:
            'Absolute or relative path to the target project directory. Defaults to current directory.',
          default: '.'
        }
      },
      required: ['sourceClass', 'targetClass', 'interfacePortName']
    }
  }
};

export function getAntigravityInstructions() {
  return `# Archlens Clean Architecture MCP Server Instructions

## Overview
Archlens provides automated Clean Architecture governance, static structural analysis, and autonomous refactoring protocols.
It strictly enforces Uncle Bob's Dependency Rule: source code dependencies must point inward toward higher-level policies.

### Concentric Layer Hierarchy:
1. **Domain Core (L0)**: Pure business entities, primitives, and domain logic. Zero outward imports.
2. **Application (L1)**: Use cases, interaction orchestrators, and interface ports. Depends only on Domain Core.
3. **Adapters (L2)**: Gateways, presenters, REST resources, controllers, and repository implementations.
4. **Infrastructure (L3)**: Web frameworks, persistence engines, database drivers, third-party libraries, CLI entrypoints.

## Governed Invariants & Drifts:
- **Concentric Dependency Rule**: Zero outward dependencies ($L_i \nrightarrow L_j$ where $i < j$).
- **Acyclic Dependencies Principle (ADP)**: Component dependencies must form a Directed Acyclic Graph (zero package cycles).
- **Screaming Architecture Invariant (SAS)**: Packaging must scream business use cases rather than framework technology (score >= 0.70).
- **Main Sequence Balance Invariant**: Distance from Main Sequence $D = |A + I - 1| \le 0.35$ avoiding Zones of Pain and Uselessness.
- **Architectural Fitness Index (AFI)**: Composite fitness score (0-100%) and letter grade (A-F) tracking regression trends.

## Available MCP Tools (11):
- \`exportLlmDossier\`: Primary tool to diagnose architectural drift and generate copy-ready LLM refactoring instructions with Dependency Inversion Principle (DIP) interface port prescriptions. Accepts \`{ "projectRoot": "." }\`.
- \`exportArchitectureDiagram\`: Exports deterministic Mermaid architecture diagrams (\`COMPONENT_RINGS\`, \`CLASS_UML\`, or \`DIP_REFACTOR\`) with optional \`violationsOnly\` filtering.
- \`inspectArchitecture\`: Returns full structural JSON graph model: concentric rings, dependency edges, cycle paths, and screaming metrics.
- \`synthesizeDipInversion\`: Synthesizes a polyglot Dependency Inversion port (Java/Python/TS/Go/Rust) with caller and adapter diff previews. Accepts \`{ "fromClass": "...", "toClass": "...", "projectRoot": "." }\`.
- \`evaluateFitness\`: Evaluates the Architecture Fitness Index (\`AFI\` 0-100%, Grade \`A\`-\`F\`), Main Sequence metrics ($I, A, D$), and Screaming Architecture Score (\`SAS\`).
- \`getFitnessHistory\`: Retrieves historical Architecture Fitness Index snapshots and regression trajectory (\`IMPROVING\`, \`STABLE\`, \`DEGRADING\`).
- \`proposeScreamingMigration\`: Generates a Screaming Architecture package-by-feature verticalization migration proposal.
- \`listSnapshots\`: Lists historical architecture release snapshots in \`.archlens/snapshots/\` and Git release tags.
- \`getSnapshot\`: Retrieves architectural graph models for a specific historical release tag or snapshot ID.
- \`getPolicy\`: Retrieves the active \`.archlens/policy.json\` concentric ring governance policy.
- \`syncDipAdr\`: Mints and synchronizes a living Architecture Decision Record (\`docs/adr/ADR-*.md\`) for a DIP port extraction.

## Available MCP Resources (3) & Prompts (2):
- \`archlens://llms.txt\`: Canonical reference guide, architectural rules, CLI commands, and refactoring protocols.
- \`archlens://policy\`: Active \`.archlens/policy.json\` concentric ring policy and refactoring proposals.
- \`archlens://fitness\`: Live Architecture Fitness Index (\`AFI\`) evaluation JSON report.
- \`architectural-review\` (Prompt): Structured prompt template for conducting a concentric ring & Main Sequence architectural review.
- \`dip-refactoring-guide\` (Prompt): Guided prompt template for executing a surgical Dependency Inversion refactoring.

## Refactoring Guidelines (Remediating Architectural Drift):
Whenever outward breaches are detected from inner layers (Domain Core or Application) to outer layers (Adapters or Infrastructure):
1. **Declare Port**: Create an interface port inside the inner layer (\`domain/ports/\` or \`application/ports/\`) declaring required operations.
2. **Implement Port**: Implement the interface inside the outer layer (\`infrastructure/\` or \`adapters/\`).
3. **Invert Injection**: Inject the outer implementation into the inner layer using constructor or dependency injection.
4. **Verify**: Run \`npx @fmatar/archlens-skill check --detect-cycles\` to verify zero violations.
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
      name: 'Google Antigravity & Gemini CLI (Schema Directory)',
      type: 'tool-directory',
      scope: 'global',
      filePath: path.join(homedir, '.gemini', 'antigravity', 'mcp', 'archlens'),
      parentDir: path.join(homedir, '.gemini', 'antigravity', 'mcp')
    },
    {
      id: 'antigravity-config',
      name: 'Google Antigravity (mcp_config.json)',
      type: 'json-manifest',
      scope: 'global',
      filePath: path.join(homedir, '.gemini', 'antigravity', 'mcp_config.json'),
      parentDir: path.join(homedir, '.gemini', 'antigravity')
    },
    {
      id: 'gemini-cli',
      name: 'Gemini CLI',
      type: 'json-manifest',
      scope: 'global',
      filePath: path.join(homedir, '.gemini', 'settings.json'),
      parentDir: path.join(homedir, '.gemini')
    },
    {
      id: 'grok',
      name: 'Grok CLI (xAI)',
      type: 'json-manifest',
      scope: 'local',
      filePath: path.join(root, '.grok', 'mcp.json'),
      parentDir: path.join(root, '.grok')
    },
    {
      id: 'windsurf',
      name: 'Windsurf',
      type: 'json-manifest',
      scope: 'global',
      filePath: path.join(homedir, '.codeium', 'windsurf', 'mcp_config.json'),
      parentDir: path.join(homedir, '.codeium', 'windsurf')
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

export function resolveDockerHostWorkspace(options = {}) {
  if (options.hostWorkspace) {
    return path.resolve(options.hostWorkspace);
  }
  const exists = options.existsSync || fs.existsSync;
  const projectRoot = path.resolve(options.path || '.');
  if (options.scope === 'global' && options.homedir) {
    const sharedWorkspace = path.resolve(options.homedir, 'workspace');
    if (
      exists(sharedWorkspace) &&
      (projectRoot === sharedWorkspace || projectRoot.startsWith(sharedWorkspace + path.sep))
    ) {
      return sharedWorkspace;
    }
  }
  return projectRoot;
}

export function getMcpManifestEntry(serverUrl = 'http://localhost:8088', options = {}) {
  if (options.preferDocker || options.docker) {
    const hostWorkspace = resolveDockerHostWorkspace(options);
    const image = options.image || DEFAULT_DOCKER_IMAGE;
    return {
      command: 'docker',
      args: [
        'run',
        '-i',
        '--rm',
        '-e',
        'QUARKUS_PROFILE',
        '-e',
        'ARCHLENS_HOST_WORKSPACE',
        '-v',
        `${hostWorkspace}:/workspace`,
        image
      ],
      env: {
        QUARKUS_PROFILE: 'mcp-stdio',
        ARCHLENS_HOST_WORKSPACE: hostWorkspace
      }
    };
  }

  return {
    command: 'npx',
    args: ['-y', '@fmatar/archlens-skill', 'mcp'],
    env: {
      ARCHLENS_SERVER_URL: serverUrl
    }
  };
}

/**
 * Parses JSON or JSONC (// and /* *\/ comments, trailing commas), as used by VS Code and other
 * MCP clients. Throws on anything else. Returns { value, hadComments }.
 */
export function parseJsonc(text) {
  const src = String(text).replace(/^\uFEFF/, '');
  let out = '';
  let hadComments = false;
  let inString = false;
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    const next = src[i + 1];
    if (inString) {
      out += ch;
      if (ch === '\\') {
        out += next ?? '';
        i++;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }
    if (ch === '"') {
      inString = true;
      out += ch;
    } else if (ch === '/' && next === '/') {
      hadComments = true;
      while (i < src.length && src[i] !== '\n') i++;
      out += '\n';
    } else if (ch === '/' && next === '*') {
      hadComments = true;
      const end = src.indexOf('*/', i + 2);
      if (end === -1) throw new Error('Unterminated block comment');
      i = end + 1;
      out += ' ';
    } else {
      out += ch;
    }
  }
  // Remove trailing commas before } or ] (strings are intact, comments are gone).
  let cleaned = '';
  inString = false;
  for (let i = 0; i < out.length; i++) {
    const ch = out[i];
    if (inString) {
      cleaned += ch;
      if (ch === '\\') {
        cleaned += out[i + 1] ?? '';
        i++;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }
    if (ch === '"') {
      inString = true;
      cleaned += ch;
      continue;
    }
    if (ch === ',') {
      let j = i + 1;
      while (j < out.length && /\s/.test(out[j])) j++;
      if (out[j] === '}' || out[j] === ']') {
        continue;
      }
    }
    cleaned += ch;
  }
  if (cleaned.trim() === '') {
    return { value: {}, hadComments };
  }
  return { value: JSON.parse(cleaned), hadComments };
}

/** Copies an existing config next to itself with a timestamp before it is modified. */
export function backupFile(filePath, now = new Date()) {
  const stamp = (now instanceof Date ? now : new Date(now)).toISOString().replace(/[-:]/g, '').replace(/\..*$/, '');
  const backupPath = `${filePath}.archlens-${stamp}.bak`;
  fs.copyFileSync(filePath, backupPath);
  return backupPath;
}

export function installMcpConfigs(options = {}) {
  const projectRoot = path.resolve(options.path || '.');
  const homedir = options.homedir || os.homedir();
  const dryRun = Boolean(options.dryRun);
  const force = Boolean(options.force);
  const scopeOnly = options.scopeOnly || null;
  const serverUrl = options.serverUrl || 'http://localhost:8088';

  const targets = getClientConfigLocations(projectRoot, homedir);
  const results = [];

  for (const target of targets) {
    if (scopeOnly && target.scope !== scopeOnly) {
      continue;
    }
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
      let hadComments = false;
      if (targetExists) {
        // Never overwrite a config we cannot parse: that would erase the user's other MCP
        // servers and settings (#229). JSONC (comments, trailing commas) is accepted.
        let parsed;
        try {
          parsed = parseJsonc(fs.readFileSync(target.filePath, 'utf8'));
        } catch (err) {
          results.push({
            id: target.id,
            name: target.name,
            path: target.filePath,
            type: target.type,
            action: 'skipped',
            error: `Could not parse ${target.filePath}: ${err.message}. File left unchanged; fix it or add the archlens entry manually.`
          });
          continue;
        }
        if (!parsed.value || typeof parsed.value !== 'object' || Array.isArray(parsed.value)) {
          results.push({
            id: target.id,
            name: target.name,
            path: target.filePath,
            type: target.type,
            action: 'skipped',
            error: `${target.filePath} does not contain a JSON object. File left unchanged.`
          });
          continue;
        }
        manifest = parsed.value;
        hadComments = parsed.hadComments;
      }

      const entryOptions = {
        ...options,
        path: projectRoot,
        homedir,
        scope: target.scope
      };

      if (target.id === 'vscode') {
        if (!manifest.servers || typeof manifest.servers !== 'object') {
          manifest.servers = {};
        }
        manifest.servers.archlens = {
          type: 'stdio',
          ...getMcpManifestEntry(serverUrl, entryOptions)
        };
      } else {
        if (!manifest.mcpServers || typeof manifest.mcpServers !== 'object') {
          manifest.mcpServers = {};
        }
        manifest.mcpServers.archlens = getMcpManifestEntry(serverUrl, entryOptions);
      }

      let backupPath = null;
      if (!dryRun) {
        fs.mkdirSync(path.dirname(target.filePath), { recursive: true });
        if (targetExists) {
          backupPath = backupFile(target.filePath, options.now);
        }
        fs.writeFileSync(target.filePath, JSON.stringify(manifest, null, 2) + '\n', 'utf8');
      }

      results.push({
        id: target.id,
        name: target.name,
        path: target.filePath,
        type: target.type,
        action: targetExists ? 'updated' : 'created',
        ...(backupPath ? { backupPath } : {}),
        ...(hadComments
          ? { warning: `Comments in ${target.filePath} were not preserved${backupPath ? `; original saved to ${backupPath}` : ''}.` }
          : {})
      });
    }
  }

  return {
    serverUrl,
    configuredClients: results,
    dryRun
  };
}
