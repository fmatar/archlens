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
import { ARCHLENS_TOOL_SCHEMAS } from './mcp-registry.js';
import { ensureServerRunning, DEFAULT_SERVER_URL } from './docker-runner.js';
import { generateLlmPrompt, generatePolicy } from './analyzer.js';

export function getMcpToolsList() {
  return Object.values(ARCHLENS_TOOL_SCHEMAS).map((tool) => ({
    name: tool.name,
    description: tool.description,
    inputSchema: tool.parameters
  }));
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
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/llm-dossier?${query}`);
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
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/diagram?${query}`);
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
        const query = new URLSearchParams({ snapshotId, projectRoot });
        const res = await (deps.fetch || fetch)(`${serverUrl}/api/snapshot?${query}`);
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
          tools: {}
        },
        serverInfo: {
          name: 'archlens-mcp-server',
          version: options.version || '0.0.1-Alpha-07'
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

  if (method === 'tools/list') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        tools: getMcpToolsList()
      }
    };
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
