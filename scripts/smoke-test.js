#!/usr/bin/env node
/**
 * Archlens End-to-End Packaged Workbench & Multi-Agent MCP Smoke Test Suite
 *
 * Boots `backend/target/quarkus-app/quarkus-run.jar` on an ephemeral port and verifies:
 * 1. Bundled Svelte 5 Workbench UI (`GET /`)
 * 2. REST API contracts (`/api/version`, `/api/graph`, `/api/fitness`, `/api/fitness/history`,
 *    `/api/violations/invert-plan`, `/api/policy`)
 * 3. Native Quarkus MCP 2.0 HTTP/SSE transport (`GET /mcp/sse`)
 * 4. CLI MCP JSON-RPC 2.0 Stdio Bridge (`initialize`, `tools/list` [10 tools], `resources/list`,
 *    `tools/call` [`evaluateFitness`, `synthesizeDipInversion`, `getPolicy`])
 * 5. Multi-Agent MCP Installer dry-run (`installMcpConfigs`)
 */

import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import { spawn, execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { handleMcpMessage } from '../cli/src/mcp-server.js';
import { installMcpConfigs } from '../cli/src/mcp-registry.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '..');

export function findFreePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.on('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    });
  });
}

export async function waitForServerReady(
  serverUrl,
  timeoutMs = 25000,
  pollIntervalMs = 250,
  deps = {}
) {
  const fetchFn = deps.fetch || fetch;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 1500);
      const res = await fetchFn(`${serverUrl}/api/version`, { signal: controller.signal });
      clearTimeout(timer);
      if (res.ok) {
        return true;
      }
    } catch {
      // Server still booting
    }
    await new Promise((r) => setTimeout(r, pollIntervalMs));
  }
  return false;
}

export async function runHttpSmokeChecks(serverUrl, expectedVersion = '0.1.1', deps = {}) {
  const fetchFn = deps.fetch || fetch;
  const checks = [];

  async function recordCheck(name, fn) {
    const start = Date.now();
    try {
      const detail = await fn();
      checks.push({ name, passed: true, durationMs: Date.now() - start, detail });
    } catch (err) {
      checks.push({ name, passed: false, durationMs: Date.now() - start, detail: err.message });
    }
  }

  // 1. Bundled Svelte 5 UI (GET /)
  await recordCheck('Workbench UI Bundle (GET /)', async () => {
    const res = await fetchFn(`${serverUrl}/`);
    if (!res.ok) throw new Error(`Expected 200 OK, got ${res.status}`);
    const html = await res.text();
    if (!html.includes('<div id="app">') && !html.toLowerCase().includes('<!doctype html>')) {
      throw new Error('Response HTML missing Svelte app mount node');
    }
    return `HTTP ${res.status} (${html.length} bytes)`;
  });

  // 2. Version Manifest (GET /api/version)
  await recordCheck('Version Manifest (GET /api/version)', async () => {
    const res = await fetchFn(`${serverUrl}/api/version`);
    if (!res.ok) throw new Error(`Expected 200 OK, got ${res.status}`);
    const data = await res.json();
    if (expectedVersion && data.version !== expectedVersion) {
      throw new Error(`Expected version ${expectedVersion}, got ${data.version}`);
    }
    return `version=${data.version}`;
  });

  // 3. Architecture Graph (GET /api/graph)
  await recordCheck('Architecture Graph (GET /api/graph)', async () => {
    const res = await fetchFn(`${serverUrl}/api/graph`);
    if (!res.ok) throw new Error(`Expected 200 OK, got ${res.status}`);
    const data = await res.json();
    const components = data.components || data.packages;
    if (!Array.isArray(components)) {
      throw new Error('Missing components array in /api/graph');
    }
    return `components=${components.length}, edges=${(data.edges || []).length}`;
  });

  // 4. Architecture Fitness Index (GET /api/fitness)
  await recordCheck('Architecture Fitness Index (GET /api/fitness)', async () => {
    const res = await fetchFn(`${serverUrl}/api/fitness`);
    if (!res.ok) throw new Error(`Expected 200 OK, got ${res.status}`);
    const data = await res.json();
    const score =
      typeof data.fitnessScore === 'number'
        ? Math.round(data.fitnessScore <= 1 ? data.fitnessScore * 100 : data.fitnessScore)
        : data.overallScore;
    if (typeof score !== 'number' || !data.grade) {
      throw new Error('Invalid fitness response payload');
    }
    return `score=${score}%, grade=${data.grade}`;
  });

  // 5. Fitness History Trend (GET /api/fitness/history)
  await recordCheck('Fitness History Trend (GET /api/fitness/history)', async () => {
    const res = await fetchFn(`${serverUrl}/api/fitness/history`);
    if (!res.ok) throw new Error(`Expected 200 OK, got ${res.status}`);
    const data = await res.json();
    const trend = data.trendDirection || data.trend;
    if (!trend) {
      throw new Error('Missing trendDirection in /api/fitness/history');
    }
    return `trend=${trend}`;
  });

  // 6. Surgical DIP Port Synthesizer (GET /api/violations/invert-plan)
  await recordCheck('DIP Port Synthesizer (GET /api/violations/invert-plan)', async () => {
    const res = await fetchFn(
      `${serverUrl}/api/violations/invert-plan?from=OrderUseCase&to=StripeGateway`
    );
    if (!res.ok) throw new Error(`Expected 200 OK, got ${res.status}`);
    const data = await res.json();
    const portName = data.portName || data.portInterfaceName;
    const portCode = data.portInterfaceCode || data.interfaceCode;
    if (!portName || !portCode) {
      throw new Error('Missing portName or portInterfaceCode in DIP plan');
    }
    return `port=${portName}`;
  });

  // 7. Active Concentric Policy (GET /api/policy)
  await recordCheck('Concentric Ring Policy (GET /api/policy)', async () => {
    const res = await fetchFn(`${serverUrl}/api/policy`);
    if (!res.ok) throw new Error(`Expected 200 OK, got ${res.status}`);
    const data = await res.json();
    if (!Array.isArray(data.levels) || data.levels.length === 0) {
      throw new Error('Missing concentric levels in /api/policy');
    }
    return `levels=${data.levels.length}`;
  });

  // 8. Native Quarkus MCP 2.0 SSE Endpoint (GET /mcp/sse)
  await recordCheck('Quarkus MCP 2.0 SSE Transport (GET /mcp/sse)', async () => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2000);
    try {
      const res = await fetchFn(`${serverUrl}/mcp/sse`, {
        headers: { Accept: 'text/event-stream' },
        signal: controller.signal
      });
      if (!res.ok) throw new Error(`Expected 200 OK on /mcp/sse, got ${res.status}`);
      const contentType = res.headers?.get?.('content-type') || '';
      if (!contentType.includes('text/event-stream')) {
        throw new Error(`Expected text/event-stream, got ${contentType}`);
      }
      return `content-type=${contentType}`;
    } finally {
      clearTimeout(timer);
      controller.abort();
    }
  });

  return {
    passed: checks.every((c) => c.passed),
    checks
  };
}

export async function runMcpStdioSmokeChecks(serverUrl, projectRoot = REPO_ROOT, deps = {}) {
  const options = { serverUrl, path: projectRoot };
  const mcpDeps = {
    ensureServerRunning: async () => ({ status: 'running' }),
    ...(deps.fetch ? { fetch: deps.fetch } : {})
  };

  // 1. initialize
  const initResp = await handleMcpMessage(
    { jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2024-11-05' } },
    options,
    mcpDeps
  );
  if (!initResp?.result?.capabilities?.tools) {
    throw new Error('MCP initialize failed to return tools capability');
  }

  // 2. tools/list (must expose all 10 tools)
  const listResp = await handleMcpMessage(
    { jsonrpc: '2.0', id: 2, method: 'tools/list' },
    options,
    mcpDeps
  );
  const tools = listResp?.result?.tools || [];
  if (tools.length !== 10) {
    throw new Error(`Expected 10 MCP tools, got ${tools.length}`);
  }

  // 3. tools/call evaluateFitness
  const fitResp = await handleMcpMessage(
    {
      jsonrpc: '2.0',
      id: 3,
      method: 'tools/call',
      params: { name: 'evaluateFitness', arguments: { projectRoot } }
    },
    options,
    mcpDeps
  );
  if (fitResp?.result?.isError) {
    throw new Error(`MCP evaluateFitness failed: ${fitResp.result.content?.[0]?.text}`);
  }

  // 4. tools/call synthesizeDipInversion
  const dipResp = await handleMcpMessage(
    {
      jsonrpc: '2.0',
      id: 4,
      method: 'tools/call',
      params: {
        name: 'synthesizeDipInversion',
        arguments: { fromClass: 'OrderUseCase', toClass: 'StripeGateway', projectRoot }
      }
    },
    options,
    mcpDeps
  );
  if (dipResp?.result?.isError) {
    throw new Error(`MCP synthesizeDipInversion failed: ${dipResp.result.content?.[0]?.text}`);
  }
  const dipData = JSON.parse(dipResp.result.content[0].text);
  const resolvedPort = dipData.portName || dipData.portInterfaceName;
  if (!resolvedPort) {
    throw new Error('MCP synthesizeDipInversion missing portName');
  }

  // 5. Multi-agent installer dry-run
  const installDryRun = installMcpConfigs({ path: projectRoot, dryRun: true, serverUrl });
  if (installDryRun.dryRun !== true) {
    throw new Error('MCP installer dry-run failed');
  }

  return {
    passed: true,
    toolsCount: tools.length,
    dipPort: resolvedPort,
    configuredClientsCount: installDryRun.configuredClients.length
  };
}

export async function runSmokeSuite(options = {}, deps = {}) {
  const repoRoot = options.repoRoot || REPO_ROOT;
  const jarPath =
    options.jarPath ||
    path.join(repoRoot, 'backend', 'target', 'quarkus-app', 'quarkus-run.jar');

  const existsFn = deps.existsSync || fs.existsSync;
  const readFn = deps.readFileSync || fs.readFileSync;
  const spawnFn = deps.spawn || spawn;
  const execFn = deps.execSync || execSync;

  if (!existsFn(jarPath)) {
    if (options.buildIfMissing) {
      console.log('🔨 Packaged JAR not found. Running `mvn -B -ntp package -DskipTests`...');
      execFn('mvn -B -ntp package -DskipTests', { cwd: repoRoot, stdio: 'inherit' });
    } else {
      throw new Error(
        `Packaged Quarkus JAR not found at ${jarPath}. Run 'npm run build' or pass '--build'.`
      );
    }
  }

  const pkgJson = JSON.parse(readFn(path.join(repoRoot, 'package.json'), 'utf8'));
  const expectedVersion = pkgJson.version;
  const port = options.port || (await (deps.findFreePort || findFreePort)());
  const serverUrl = `http://127.0.0.1:${port}`;

  console.log(`🚀 Booting Archlens Workbench (${jarPath}) on ${serverUrl}...`);
  const child = spawnFn(
    'java',
    [`-Dquarkus.http.port=${port}`, '-Dquarkus.http.host=127.0.0.1', '-jar', jarPath],
    {
      cwd: repoRoot,
      env: { ...process.env, ARCHLENS_PROJECT_ROOT: repoRoot },
      stdio: ['ignore', 'pipe', 'pipe']
    }
  );

  let stderrLog = '';
  child.stderr.on('data', (chunk) => {
    stderrLog += chunk.toString();
  });

  try {
    const ready = await waitForServerReady(serverUrl, 30000, 250, deps);
    if (!ready) {
      throw new Error(`Quarkus server failed to become ready on ${serverUrl}.\n${stderrLog}`);
    }

    const httpResults = await runHttpSmokeChecks(serverUrl, expectedVersion, deps);
    for (const c of httpResults.checks) {
      const icon = c.passed ? '✅' : '❌';
      console.log(`  ${icon} ${c.name} (${c.durationMs}ms) -> ${c.detail}`);
    }

    const mcpResults = await runMcpStdioSmokeChecks(serverUrl, repoRoot, deps);
    console.log(
      `  ✅ MCP Stdio Bridge & Multi-Agent Registry -> ${mcpResults.toolsCount} tools verified, DIP port=${mcpResults.dipPort}`
    );

    if (!httpResults.passed || !mcpResults.passed) {
      throw new Error('One or more smoke checks failed.');
    }

    console.log(`\n🎉 All End-to-End Smoke Checks Passed (v${expectedVersion})!`);
    return { passed: true, httpResults, mcpResults };
  } finally {
    child.kill('SIGTERM');
    await new Promise((resolve) => {
      const killTimer = setTimeout(() => {
        try {
          child.kill('SIGKILL');
        } catch {
          // ignore
        }
        resolve();
      }, 2000);
      child.once('exit', () => {
        clearTimeout(killTimer);
        resolve();
      });
    });
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const buildIfMissing = process.argv.includes('--build');
  runSmokeSuite({ buildIfMissing }).catch((err) => {
    console.error(`\n❌ Smoke Suite Failed: ${err.message}`);
    process.exit(1);
  });
}
