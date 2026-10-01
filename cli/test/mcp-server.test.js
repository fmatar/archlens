import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import {
  getMcpToolsList,
  getMcpResourcesList,
  readMcpResource,
  handleMcpMessage,
  executeToolCall
} from '../src/mcp-server.js';

test('getMcpToolsList formats tools conforming to MCP specification', () => {
  const tools = getMcpToolsList();
  assert.equal(tools.length, 4);

  const names = tools.map((t) => t.name);
  assert.ok(names.includes('inspectArchitecture'));
  assert.ok(names.includes('exportLlmDossier'));
  assert.ok(names.includes('listSnapshots'));
  assert.ok(names.includes('getSnapshot'));

  for (const t of tools) {
    assert.equal(typeof t.name, 'string');
    assert.equal(typeof t.description, 'string');
    assert.equal(typeof t.inputSchema, 'object');
  }
});

test('getMcpResourcesList defines canonical llms.txt resource', () => {
  const resources = getMcpResourcesList();
  assert.equal(resources.length, 1);
  assert.equal(resources[0].uri, 'archlens://llms.txt');
  assert.equal(resources[0].mimeType, 'text/markdown');
  assert.ok(resources[0].description.includes('Clean Architecture'));
});

test('readMcpResource returns markdown content of llms.txt', () => {
  const res = readMcpResource('archlens://llms.txt');
  assert.ok(res.contents);
  assert.equal(res.contents.length, 1);
  assert.equal(res.contents[0].uri, 'archlens://llms.txt');
  assert.equal(res.contents[0].mimeType, 'text/markdown');
  assert.ok(res.contents[0].text.includes('# Archlens'));
  assert.ok(res.contents[0].text.includes('Clean Architecture'));
});

test('handleMcpMessage responds to initialize with tools and resources capabilities', async () => {
  const req = {
    jsonrpc: '2.0',
    id: 1,
    method: 'initialize',
    params: { protocolVersion: '2024-11-05' }
  };

  const resp = await handleMcpMessage(req, { version: '1.2.3' });
  assert.equal(resp.jsonrpc, '2.0');
  assert.equal(resp.id, 1);
  assert.equal(resp.result.protocolVersion, '2024-11-05');
  assert.equal(resp.result.serverInfo.name, 'archlens-mcp-server');
  assert.equal(resp.result.serverInfo.version, '1.2.3');
  assert.ok(resp.result.capabilities.tools);
  assert.ok(resp.result.capabilities.resources);
});

test('handleMcpMessage responds to ping', async () => {
  const req = { jsonrpc: '2.0', id: 2, method: 'ping' };
  const resp = await handleMcpMessage(req);
  assert.equal(resp.jsonrpc, '2.0');
  assert.equal(resp.id, 2);
  assert.deepEqual(resp.result, {});
});

test('handleMcpMessage responds to tools/list', async () => {
  const req = { jsonrpc: '2.0', id: 3, method: 'tools/list' };
  const resp = await handleMcpMessage(req);
  assert.equal(resp.id, 3);
  assert.equal(resp.result.tools.length, 4);
});

test('handleMcpMessage responds to resources/list', async () => {
  const req = { jsonrpc: '2.0', id: 4, method: 'resources/list' };
  const resp = await handleMcpMessage(req);
  assert.equal(resp.id, 4);
  assert.equal(resp.result.resources.length, 1);
  assert.equal(resp.result.resources[0].uri, 'archlens://llms.txt');
});

test('handleMcpMessage responds to resources/read', async () => {
  const req = {
    jsonrpc: '2.0',
    id: 5,
    method: 'resources/read',
    params: { uri: 'archlens://llms.txt' }
  };
  const resp = await handleMcpMessage(req);
  assert.equal(resp.id, 5);
  assert.ok(resp.result.contents);
  assert.equal(resp.result.contents[0].uri, 'archlens://llms.txt');
  assert.ok(resp.result.contents[0].text.includes('# Archlens'));
});

test('handleMcpMessage returns error for unknown resource uri', async () => {
  const req = {
    jsonrpc: '2.0',
    id: 6,
    method: 'resources/read',
    params: { uri: 'archlens://unknown' }
  };
  const resp = await handleMcpMessage(req);
  assert.equal(resp.id, 6);
  assert.equal(resp.error.code, -32002);
});

test('handleMcpMessage ignores notifications without error', async () => {
  const req = { jsonrpc: '2.0', method: 'notifications/initialized' };
  const resp = await handleMcpMessage(req);
  assert.equal(resp, null);
});

test('handleMcpMessage returns method not found for unknown methods', async () => {
  const req = { jsonrpc: '2.0', id: 4, method: 'unknown/action' };
  const resp = await handleMcpMessage(req);
  assert.equal(resp.id, 4);
  assert.equal(resp.error.code, -32601);
});

test('executeToolCall exportLlmDossier uses offline fallback when server is offline', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-call-'));
  try {
    fs.mkdirSync(path.join(tmpDir, 'src', 'domain'), { recursive: true });
    fs.writeFileSync(path.join(tmpDir, 'package.json'), '{}');

    const deps = {
      ensureServerRunning: async () => ({ status: 'offline' })
    };

    const result = await executeToolCall(
      'exportLlmDossier',
      { projectRoot: tmpDir },
      {},
      deps
    );

    assert.ok(result.content);
    assert.equal(result.content[0].type, 'text');
    assert.ok(result.content[0].text.includes('# Clean Architecture Optimization Dossier'));
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('executeToolCall inspectArchitecture uses online backend when available', async () => {
  const mockGraph = { title: 'OnlineGraph', nodes: [], links: [] };
  const deps = {
    ensureServerRunning: async () => ({ status: 'running' }),
    fetch: async () => ({
      ok: true,
      json: async () => mockGraph
    })
  };

  const result = await executeToolCall(
    'inspectArchitecture',
    { projectRoot: '.' },
    { serverUrl: 'http://localhost:8088' },
    deps
  );

  assert.ok(result.content);
  assert.equal(result.content[0].type, 'text');
  const parsed = JSON.parse(result.content[0].text);
  assert.equal(parsed.title, 'OnlineGraph');
});

test('executeToolCall getSnapshot sanitizes snapshotId against directory traversal', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-snap-'));
  try {
    const snapshotsDir = path.join(tmpDir, '.archlens', 'snapshots');
    fs.mkdirSync(snapshotsDir, { recursive: true });
    fs.writeFileSync(path.join(snapshotsDir, 'v1.0.0.json'), JSON.stringify({ version: 'v1.0.0' }));

    const deps = {
      ensureServerRunning: async () => ({ status: 'offline' })
    };

    const result = await executeToolCall(
      'getSnapshot',
      { snapshotId: '../../v1.0.0', projectRoot: tmpDir },
      {},
      deps
    );

    assert.ok(result.content);
    assert.equal(result.content[0].type, 'text');
    const parsed = JSON.parse(result.content[0].text);
    assert.equal(parsed.version, 'v1.0.0');
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});
