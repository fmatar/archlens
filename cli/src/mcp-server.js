/**
 * Archlens MCP Stdio Bridge Server
 * Implements the Model Context Protocol (MCP) JSON-RPC 2.0 stdio transport.
 * Allows AI assistants (Claude Desktop, Claude Code, Cursor, Antigravity)
 * to interact with Archlens tools with automatic Docker orchestration and offline fallback.
 * Zero external dependencies.
 */

import * as readline from 'node:readline';
import * as path from 'node:path';
import * as fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { ARCHLENS_TOOL_SCHEMAS } from './mcp-registry.js';
import { ensureServerRunning, DEFAULT_SERVER_URL } from './docker-runner.js';
import { generateLlmPrompt, generatePolicy } from './analyzer.js';
import { checkArchitecture } from './checker.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function getPackageVersion() {
  try {
    const pkgPath = path.resolve(__dirname, '..', 'package.json');
    if (fs.existsSync(pkgPath)) {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
      if (pkg.version) return pkg.version;
    }
  } catch {
    // ignore
  }
  return '1.1.2';
}

export function getMcpToolsList() {
  return Object.values(ARCHLENS_TOOL_SCHEMAS).map((tool) => ({
    name: tool.name,
    description: tool.description,
    inputSchema: tool.parameters
  }));
}

export function getMcpResourcesList() {
  return [
    {
      uri: 'archlens://llms.txt',
      name: 'Archlens Architecture Guide & Best Practices',
      description:
        'Comprehensive Clean Architecture guide, concentric ring hierarchy, CLI commands, fitness invariants, and refactoring protocols for LLMs.',
      mimeType: 'text/markdown'
    },
    {
      uri: 'archlens://policy',
      name: 'Archlens Concentric Ring Policy (.archlens/policy.json)',
      description: 'Active Clean Architecture concentric ring policy and fitness thresholds.',
      mimeType: 'application/json'
    },
    {
      uri: 'archlens://fitness',
      name: 'Archlens Architectural Fitness Evaluation',
      description:
        'Current Architectural Fitness Index (AFI), Screaming Architecture Score (SAS), and Main Sequence Distance evaluation.',
      mimeType: 'application/json'
    }
  ];
}

export function getMcpPromptsList() {
  return [
    {
      name: 'architectural-review',
      description:
        'Generate a comprehensive Clean Architecture & Robert C. Martin Main Sequence review prompt for a target workspace.',
      arguments: [
        {
          name: 'projectRoot',
          description: 'Absolute or relative path to the target project directory. Defaults to current directory.',
          required: false
        }
      ]
    },
    {
      name: 'dip-refactoring-guide',
      description:
        'Synthesize a surgical Dependency Inversion Principle (DIP) prompt to decouple an inner-ring caller from an outer-ring concrete class.',
      arguments: [
        { name: 'fromClass', description: 'Fully qualified caller class name', required: true },
        { name: 'toClass', description: 'Fully qualified target concrete class name', required: true },
        {
          name: 'projectRoot',
          description: 'Absolute or relative path to the target project directory. Defaults to current directory.',
          required: false
        }
      ]
    }
  ];
}

export function readMcpResource(uri, options = {}, deps = {}) {
  const projectRoot = path.resolve(options.path || '.');
  const fileReader = deps.readFileSync || fs.readFileSync;
  const fileExists = deps.existsSync || fs.existsSync;

  if (uri === 'archlens://llms.txt') {
    const localLlmsPath = path.join(projectRoot, 'llms.txt');
    const cliLlmsPath = path.resolve(__dirname, '..', 'llms.txt');
    const rootLlmsPath = path.resolve(__dirname, '..', '..', 'llms.txt');

    let content = '';
    if (fileExists(localLlmsPath)) {
      content = fileReader(localLlmsPath, 'utf8');
    } else if (fileExists(cliLlmsPath)) {
      content = fileReader(cliLlmsPath, 'utf8');
    } else if (fileExists(rootLlmsPath)) {
      content = fileReader(rootLlmsPath, 'utf8');
    } else {
      content = '# Archlens Clean Architecture Guide\n\nRefer to https://github.com/fmatar/archlens for documentation.';
    }

    return {
      contents: [
        {
          uri,
          mimeType: 'text/markdown',
          text: content
        }
      ]
    };
  }

  if (uri === 'archlens://policy') {
    const policyFile = path.join(projectRoot, '.archlens', 'policy.json');
    const policyText = fileExists(policyFile)
      ? fileReader(policyFile, 'utf8')
      : JSON.stringify(generatePolicy(projectRoot, options), null, 2);
    return {
      contents: [
        {
          uri,
          mimeType: 'application/json',
          text: policyText
        }
      ]
    };
  }

  if (uri === 'archlens://fitness') {
    const checker = deps.checkArchitecture || checkArchitecture;
    const report = checker(projectRoot, { detectCycles: true });
    return {
      contents: [
        {
          uri,
          mimeType: 'application/json',
          text: JSON.stringify(report.fitness || report, null, 2)
        }
      ]
    };
  }

  throw new Error(`Resource not found: ${uri}`);
}

export async function executeToolCall(toolName, args = {}, options = {}, deps = {}) {
  const serverUrl = options.serverUrl || process.env.ARCHLENS_SERVER_URL || DEFAULT_SERVER_URL;
  const projectRoot = path.resolve(args.projectRoot || options.path || '.');
  const runner = deps.ensureServerRunning || ensureServerRunning;

  // Attempt server readiness inspection
  const serverStatus = await runner({ serverUrl, workspace: projectRoot }, deps);
  const isOnline = serverStatus.status === 'running';

  if (toolName === 'exportLlmDossier') {
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        if (args.proposalId) {
          query.set('proposalId', args.proposalId);
        }
        let res = await (deps.fetch || fetch)(`${serverUrl}/api/diagram/llm-dossier?${query}`);
        if (!res.ok) {
          res = await (deps.fetch || fetch)(`${serverUrl}/api/llm-dossier?${query}`);
        }
        if (res.ok) {
          const text = await res.text();
          return { content: [{ type: 'text', text }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online dossier fetch error: ${err.message}. Using offline engine.\n`);
      }
    }

    // Offline fallback
    const offlineGenerator = deps.generateLlmPrompt || generateLlmPrompt;
    const dossier = await offlineGenerator(projectRoot, {
      proposalId: args.proposalId || null,
      serverUrl
    });
    return { content: [{ type: 'text', text: dossier }] };
  }

  if (toolName === 'inspectArchitecture') {
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        let res = await (deps.fetch || fetch)(`${serverUrl}/api/graph?${query}`);
        if (!res.ok) {
          res = await (deps.fetch || fetch)(`${serverUrl}/api/diagram?${query}`);
        }
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online graph fetch error: ${err.message}. Using offline engine.\n`);
      }
    }

    // Offline fallback
    const offlinePolicyGen = deps.generatePolicy || generatePolicy;
    const policy = offlinePolicyGen(projectRoot, options);
    const summary = {
      title: policy.title,
      offline: true,
      levels: policy.levels,
      message: 'Generated via Archlens offline static AST engine'
    };
    return { content: [{ type: 'text', text: JSON.stringify(summary, null, 2) }] };
  }

  if (toolName === 'listSnapshots') {
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/snapshots?${query}`);
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online snapshots fetch error: ${err.message}.\n`);
      }
    }

    const snapshotsDir = path.join(projectRoot, '.archlens', 'snapshots');
    const files = fs.existsSync(snapshotsDir)
      ? fs.readdirSync(snapshotsDir).filter((f) => f.endsWith('.json'))
      : [];
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({ snapshots: files, offline: true }, null, 2)
        }
      ]
    };
  }

  if (toolName === 'getSnapshot') {
    const snapshotId = args.snapshotId;
    if (!snapshotId) {
      throw new Error("Missing required argument: 'snapshotId'");
    }

    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        const res = await (deps.fetch || fetch)(
          `${serverUrl}/api/snapshots/${encodeURIComponent(snapshotId)}?${query}`
        );
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online snapshot fetch error: ${err.message}.\n`);
      }
    }

    const safeSnapshotId = path.basename(snapshotId);
    const snapshotFile = path.join(projectRoot, '.archlens', 'snapshots', `${safeSnapshotId}.json`);
    if (fs.existsSync(snapshotFile)) {
      const data = JSON.parse(fs.readFileSync(snapshotFile, 'utf8'));
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    }

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({ error: `Snapshot not found: ${safeSnapshotId}`, offline: true })
        }
      ]
    };
  }

  if (toolName === 'synthesizeDipInversion') {
    const fromClass = args.fromClass || args.sourceClass;
    const toClass = args.toClass || args.targetClass;
    if (!fromClass || !toClass) {
      throw new Error("Missing required arguments: 'fromClass' and 'toClass'");
    }

    if (isOnline) {
      try {
        const query = new URLSearchParams({ from: fromClass, to: toClass, projectRoot });
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/violations/invert-plan?${query}`);
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online DIP synthesis error: ${err.message}.\n`);
      }
    }

    const simpleTarget = toClass.split('.').pop();
    const portName = `${simpleTarget}Port`;
    const blueprint = {
      fromClass,
      toClass,
      sourceClass: fromClass,
      targetClass: toClass,
      portName,
      portInterfaceName: portName,
      portInterfaceCode: `public interface ${portName} {}`,
      prescription: `Extract interface ${portName} in the inner layer of ${fromClass} and implement it in ${toClass}.`,
      offline: true
    };
    return { content: [{ type: 'text', text: JSON.stringify(blueprint, null, 2) }] };
  }

  if (toolName === 'evaluateFitness') {
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/fitness?${query}`);
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online fitness fetch error: ${err.message}.\n`);
      }
    }

    const checker = deps.checkArchitecture || checkArchitecture;
    const checkResult = await checker(projectRoot, { detectCycles: true });
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              passed: checkResult.passed,
              violationsCount: checkResult.violationsCount,
              cyclesCount: checkResult.cyclesCount,
              screaming: checkResult.screaming,
              fitness: checkResult.fitness,
              offline: true
            },
            null,
            2
          )
        }
      ]
    };
  }

  if (toolName === 'getFitnessHistory') {
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/fitness/history?${query}`);
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online fitness history error: ${err.message}.\n`);
      }
    }
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            { trendDirection: 'STABLE', trend: 'STABLE', history: [], snapshots: [], offline: true },
            null,
            2
          )
        }
      ]
    };
  }

  if (toolName === 'proposeScreamingMigration') {
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        const res = await (deps.fetch || fetch)(
          `${serverUrl}/api/screaming/migration-proposal?${query}`
        );
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online screaming migration error: ${err.message}.\n`);
      }
    }
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify({ clusters: [], projectedSas: 1.0, offline: true }, null, 2)
        }
      ]
    };
  }

  if (toolName === 'getPolicy') {
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/policy?${query}`);
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online policy fetch error: ${err.message}.\n`);
      }
    }
    const policyFile = path.join(projectRoot, '.archlens', 'policy.json');
    if (fs.existsSync(policyFile)) {
      return { content: [{ type: 'text', text: fs.readFileSync(policyFile, 'utf8') }] };
    }
    const policy = generatePolicy(projectRoot, {});
    return { content: [{ type: 'text', text: JSON.stringify(policy, null, 2) }] };
  }

  if (toolName === 'syncDipAdr') {
    const sourceClass = args.sourceClass || args.fromClass;
    const targetClass = args.targetClass || args.toClass;
    const interfacePortName = args.interfacePortName || args.portName;
    const reason = args.reason || 'Invert outward Clean Architecture dependency via interface port';
    if (!sourceClass || !targetClass || !interfacePortName) {
      throw new Error(
        "Missing required arguments: 'sourceClass', 'targetClass', and 'interfacePortName'"
      );
    }
    if (isOnline) {
      try {
        const query = new URLSearchParams({ projectRoot });
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/mailbox/sync-adr?${query}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            sourceClass,
            targetClass,
            interfacePortName,
            reason
          })
        });
        if (res.ok) {
          const json = await res.json();
          return { content: [{ type: 'text', text: JSON.stringify(json, null, 2) }] };
        }
      } catch (err) {
        process.stderr.write(`[archlens-mcp] Online syncDipAdr error: ${err.message}.\n`);
      }
    }
    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(
            {
              sourceClass,
              targetClass,
              interfacePortName,
              reason,
              status: 'recorded-offline',
              offline: true
            },
            null,
            2
          )
        }
      ]
    };
  }

  throw new Error(`Unknown tool: ${toolName}`);
}

export async function handleMcpMessage(request, options = {}, deps = {}) {
  if (!request || typeof request !== 'object') {
    return {
      jsonrpc: '2.0',
      id: null,
      error: { code: -32700, message: 'Parse error' }
    };
  }

  const { id, method, params } = request;

  // Notifications (requests without id) do not expect a response
  if (id === undefined || id === null) {
    if (method === 'notifications/initialized') {
      process.stderr.write('[archlens-mcp] Host AI client connection initialized.\n');
    }
    return null;
  }

  if (method === 'initialize') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: {
          tools: {},
          resources: {},
          prompts: {}
        },
        serverInfo: {
          name: 'archlens-mcp-server',
          version: options.version || getPackageVersion()
        }
      }
    };
  }

  if (method === 'ping') {
    return {
      jsonrpc: '2.0',
      id,
      result: {}
    };
  }

  if (method === 'prompts/list') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        prompts: getMcpPromptsList()
      }
    };
  }

  if (method === 'prompts/get') {
    const promptName = params?.name;
    const args = params?.arguments || {};
    if (promptName === 'architectural-review') {
      const dossierRes = await executeToolCall('exportLlmDossier', args, options, deps);
      return {
        jsonrpc: '2.0',
        id,
        result: {
          description: 'Clean Architecture & Main Sequence review prompt',
          messages: [{ role: 'user', content: dossierRes.content[0] }]
        }
      };
    }
    if (promptName === 'dip-refactoring-guide') {
      const dipRes = await executeToolCall('synthesizeDipInversion', args, options, deps);
      return {
        jsonrpc: '2.0',
        id,
        result: {
          description: 'Surgical Dependency Inversion Principle (DIP) refactoring prompt',
          messages: [{ role: 'user', content: dipRes.content[0] }]
        }
      };
    }
    return {
      jsonrpc: '2.0',
      id,
      error: { code: -32602, message: `Unknown prompt: ${promptName}` }
    };
  }

  if (method === 'tools/list') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        tools: getMcpToolsList()
      }
    };
  }

  if (method === 'resources/list') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        resources: getMcpResourcesList()
      }
    };
  }

  if (method === 'resources/read') {
    const uri = params?.uri;
    try {
      const readResult = readMcpResource(uri, options, deps);
      return {
        jsonrpc: '2.0',
        id,
        result: readResult
      };
    } catch (err) {
      return {
        jsonrpc: '2.0',
        id,
        error: {
          code: -32002,
          message: err.message
        }
      };
    }
  }

  if (method === 'tools/call') {
    const toolName = params?.name;
    const args = params?.arguments || {};

    try {
      const callResult = await executeToolCall(toolName, args, options, deps);
      return {
        jsonrpc: '2.0',
        id,
        result: callResult
      };
    } catch (err) {
      return {
        jsonrpc: '2.0',
        id,
        result: {
          content: [
            {
              type: 'text',
              text: `Error executing ${toolName}: ${err.message}`
            }
          ],
          isError: true
        }
      };
    }
  }

  return {
    jsonrpc: '2.0',
    id,
    error: {
      code: -32601,
      message: `Method not found: ${method}`
    }
  };
}

export function startMcpStdioServer(options = {}, deps = {}) {
  const rl = (deps.createInterface || readline.createInterface)({
    input: deps.stdin || process.stdin,
    output: deps.stdout || process.stdout,
    terminal: false
  });

  process.stderr.write('[archlens-mcp] Archlens Clean Architecture MCP Server active on stdio transport.\n');

  rl.on('line', async (line) => {
    const trimmed = line.trim();
    if (!trimmed) return;

    try {
      const req = JSON.parse(trimmed);
      const resp = await handleMcpMessage(req, options, deps);
      if (resp) {
        (deps.stdout || process.stdout).write(JSON.stringify(resp) + '\n');
      }
    } catch (err) {
      const errorResp = {
        jsonrpc: '2.0',
        id: null,
        error: { code: -32700, message: `Parse error: ${err.message}` }
      };
      (deps.stdout || process.stdout).write(JSON.stringify(errorResp) + '\n');
    }
  });
}
