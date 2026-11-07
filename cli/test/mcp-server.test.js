import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

import { EventEmitter } from 'node:events';

import {
  getMcpToolsList,
  getMcpResourcesList,
  readMcpResource,
  handleMcpMessage,
  executeToolCall,
  startMcpStdioServer
} from '../src/mcp-server.js';

test('getMcpToolsList formats tools conforming to MCP specification', () => {
  const tools = getMcpToolsList();
  assert.equal(tools.length, 10);

  const names = tools.map((t) => t.name);
  assert.ok(names.includes('inspectArchitecture'));
  assert.ok(names.includes('exportLlmDossier'));
  assert.ok(names.includes('listSnapshots'));
  assert.ok(names.includes('getSnapshot'));
  assert.ok(names.includes('synthesizeDipInversion'));
  assert.ok(names.includes('evaluateFitness'));
  assert.ok(names.includes('getFitnessHistory'));
  assert.ok(names.includes('proposeScreamingMigration'));
  assert.ok(names.includes('getPolicy'));
  assert.ok(names.includes('syncDipAdr'));

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
  assert.equal(resp.result.tools.length, 10);
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

test('executeToolCall synthesizeDipInversion calls GET /api/violations/invert-plan when online and supports fromClass/toClass & sourceClass/targetClass', async () => {
  let calledUrl = '';
  const mockPlan = {
    sourceClass: 'OrderUseCase',
    targetClass: 'JpaOrderRepository',
    portInterfaceName: 'JpaOrderRepositoryPort',
    interfaceCode: 'public interface JpaOrderRepositoryPort {}'
  };
  const deps = {
    ensureServerRunning: async () => ({ status: 'running' }),
    fetch: async (url) => {
      calledUrl = url;
      return {
        ok: true,
        json: async () => mockPlan
      };
    }
  };

  const result = await executeToolCall(
    'synthesizeDipInversion',
    { fromClass: 'OrderUseCase', toClass: 'JpaOrderRepository', projectRoot: '/tmp/proj' },
    { serverUrl: 'http://localhost:8088' },
    deps
  );

  assert.ok(calledUrl.startsWith('http://localhost:8088/api/violations/invert-plan?'));
  assert.ok(calledUrl.includes('from=OrderUseCase'));
  assert.ok(calledUrl.includes('to=JpaOrderRepository'));
  const parsed = JSON.parse(result.content[0].text);
  assert.equal(parsed.portInterfaceName, 'JpaOrderRepositoryPort');

  // Offline fallback with sourceClass/targetClass alias
  const offlineResult = await executeToolCall(
    'synthesizeDipInversion',
    { sourceClass: 'com.app.OrderUseCase', targetClass: 'com.app.JpaOrderRepository' },
    {},
    { ensureServerRunning: async () => ({ status: 'offline' }) }
  );
  const offlineParsed = JSON.parse(offlineResult.content[0].text);
  assert.equal(offlineParsed.portInterfaceName, 'JpaOrderRepositoryPort');
  assert.equal(offlineParsed.offline, true);

  await assert.rejects(
    () => executeToolCall('synthesizeDipInversion', {}, {}, { ensureServerRunning: async () => ({ status: 'offline' }) }),
    /Missing required arguments/
  );
});

test('executeToolCall syncDipAdr sends POST /api/mailbox/sync-adr?projectRoot=... with sourceClass, targetClass, interfacePortName', async () => {
  let calledUrl = '';
  let calledBody = null;
  const deps = {
    ensureServerRunning: async () => ({ status: 'running' }),
    fetch: async (url, init) => {
      calledUrl = url;
      calledBody = JSON.parse(init.body);
      return {
        ok: true,
        json: async () => ({ status: 'created', adrPath: 'docs/adr/ADR-0001-dip.md' })
      };
    }
  };

  const result = await executeToolCall(
    'syncDipAdr',
    {
      sourceClass: 'OrderUseCase',
      targetClass: 'JpaOrderRepository',
      interfacePortName: 'OrderRepositoryPort',
      reason: 'Decouple Ring 1 from Ring 3',
      projectRoot: '/tmp/proj'
    },
    { serverUrl: 'http://localhost:8088' },
    deps
  );

  assert.ok(calledUrl.startsWith('http://localhost:8088/api/mailbox/sync-adr?'));
  assert.ok(calledUrl.includes('projectRoot=%2Ftmp%2Fproj'));
  assert.equal(calledBody.sourceClass, 'OrderUseCase');
  assert.equal(calledBody.targetClass, 'JpaOrderRepository');
  assert.equal(calledBody.interfacePortName, 'OrderRepositoryPort');
  assert.equal(calledBody.reason, 'Decouple Ring 1 from Ring 3');
  const parsed = JSON.parse(result.content[0].text);
  assert.equal(parsed.status, 'created');

  // Offline fallback
  const offlineRes = await executeToolCall(
    'syncDipAdr',
    {
      fromClass: 'OrderUseCase',
      toClass: 'JpaOrderRepository',
      portName: 'OrderRepositoryPort'
    },
    {},
    { ensureServerRunning: async () => ({ status: 'offline' }) }
  );
  const offlineParsed = JSON.parse(offlineRes.content[0].text);
  assert.equal(offlineParsed.status, 'recorded-offline');
  assert.equal(offlineParsed.offline, true);

  await assert.rejects(
    () => executeToolCall('syncDipAdr', { sourceClass: 'OnlyOne' }, {}, { ensureServerRunning: async () => ({ status: 'offline' }) }),
    /Missing required arguments/
  );
});

test('executeToolCall covers listSnapshots, getSnapshot, evaluateFitness, getFitnessHistory, proposeScreamingMigration, and getPolicy online and offline', async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-mcp-all-'));
  try {
    const archlensDir = path.join(tmpDir, '.archlens');
    const snapsDir = path.join(archlensDir, 'snapshots');
    fs.mkdirSync(snapsDir, { recursive: true });
    fs.writeFileSync(path.join(snapsDir, 'snap1.json'), JSON.stringify({ id: 'snap1' }));
    fs.writeFileSync(path.join(archlensDir, 'policy.json'), JSON.stringify({ title: 'TestPolicy', levels: [] }));

    const onlineDeps = {
      ensureServerRunning: async () => ({ status: 'running' }),
      fetch: async (url) => ({
        ok: true,
        text: async () => '# Online Dossier',
        json: async () => ({ url, online: true })
      })
    };

    const offlineDeps = {
      ensureServerRunning: async () => ({ status: 'offline' }),
      checkArchitecture: async () => ({
        passed: true,
        violationsCount: 0,
        cyclesCount: 0,
        screaming: { score: 1.0 },
        fitness: { overallScore: 95, grade: 'A' }
      })
    };

    // exportLlmDossier online with proposalId
    const dossierOnline = await executeToolCall(
      'exportLlmDossier',
      { projectRoot: tmpDir, proposalId: 'p1' },
      { serverUrl: 'http://localhost:8088' },
      onlineDeps
    );
    assert.equal(dossierOnline.content[0].text, '# Online Dossier');

    // inspectArchitecture offline
    const inspectOffline = await executeToolCall('inspectArchitecture', { projectRoot: tmpDir }, {}, offlineDeps);
    assert.equal(JSON.parse(inspectOffline.content[0].text).offline, true);

    // listSnapshots online & offline
    const listOnline = await executeToolCall('listSnapshots', { projectRoot: tmpDir }, {}, onlineDeps);
    assert.equal(JSON.parse(listOnline.content[0].text).online, true);
    const listOffline = await executeToolCall('listSnapshots', { projectRoot: tmpDir }, {}, offlineDeps);
    assert.deepEqual(JSON.parse(listOffline.content[0].text).snapshots, ['snap1.json']);

    // getSnapshot online, missing file offline, and missing arg error
    const snapOnline = await executeToolCall('getSnapshot', { snapshotId: 'snap1', projectRoot: tmpDir }, {}, onlineDeps);
    assert.equal(JSON.parse(snapOnline.content[0].text).online, true);
    const snapMissing = await executeToolCall('getSnapshot', { snapshotId: 'nonexistent', projectRoot: tmpDir }, {}, offlineDeps);
    assert.ok(JSON.parse(snapMissing.content[0].text).error.includes('Snapshot not found'));
    await assert.rejects(() => executeToolCall('getSnapshot', {}, {}, offlineDeps), /Missing required argument: 'snapshotId'/);

    // evaluateFitness online & offline
    const fitOnline = await executeToolCall('evaluateFitness', { projectRoot: tmpDir }, {}, onlineDeps);
    assert.equal(JSON.parse(fitOnline.content[0].text).online, true);
    const fitOffline = await executeToolCall('evaluateFitness', { projectRoot: tmpDir }, {}, offlineDeps);
    assert.equal(JSON.parse(fitOffline.content[0].text).passed, true);

    // getFitnessHistory online & offline
    const histOnline = await executeToolCall('getFitnessHistory', { projectRoot: tmpDir }, {}, onlineDeps);
    assert.equal(JSON.parse(histOnline.content[0].text).online, true);
    const histOffline = await executeToolCall('getFitnessHistory', { projectRoot: tmpDir }, {}, offlineDeps);
    assert.equal(JSON.parse(histOffline.content[0].text).trend, 'STABLE');

    // proposeScreamingMigration online & offline
    const screamOnline = await executeToolCall('proposeScreamingMigration', { projectRoot: tmpDir }, {}, onlineDeps);
    assert.equal(JSON.parse(screamOnline.content[0].text).online, true);
    const screamOffline = await executeToolCall('proposeScreamingMigration', { projectRoot: tmpDir }, {}, offlineDeps);
    assert.equal(JSON.parse(screamOffline.content[0].text).projectedSas, 1.0);

    // getPolicy online, local policy.json, and generated policy fallback
    const polOnline = await executeToolCall('getPolicy', { projectRoot: tmpDir }, {}, onlineDeps);
    assert.equal(JSON.parse(polOnline.content[0].text).online, true);
    const polOfflineFile = await executeToolCall('getPolicy', { projectRoot: tmpDir }, {}, offlineDeps);
    assert.equal(JSON.parse(polOfflineFile.content[0].text).title, 'TestPolicy');
    fs.rmSync(path.join(archlensDir, 'policy.json'));
    const polOfflineGen = await executeToolCall('getPolicy', { projectRoot: tmpDir }, {}, offlineDeps);
    assert.ok(JSON.parse(polOfflineGen.content[0].text).levels);

    // Unknown tool
    await assert.rejects(() => executeToolCall('nonExistentTool', {}, {}, offlineDeps), /Unknown tool: nonExistentTool/);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
});

test('handleMcpMessage handles tools/call success, tools/call error, and invalid request parse error', async () => {
  const parseErr = await handleMcpMessage(null);
  assert.equal(parseErr.error.code, -32700);

  const okResp = await handleMcpMessage(
    {
      jsonrpc: '2.0',
      id: 10,
      method: 'tools/call',
      params: { name: 'getFitnessHistory', arguments: {} }
    },
    {},
    { ensureServerRunning: async () => ({ status: 'offline' }) }
  );
  assert.equal(okResp.id, 10);
  assert.ok(okResp.result.content[0].text.includes('STABLE'));

  const errResp = await handleMcpMessage(
    {
      jsonrpc: '2.0',
      id: 11,
      method: 'tools/call',
      params: { name: 'getSnapshot', arguments: {} }
    },
    {},
    { ensureServerRunning: async () => ({ status: 'offline' }) }
  );
  assert.equal(errResp.id, 11);
  assert.equal(errResp.result.isError, true);
  assert.ok(errResp.result.content[0].text.includes("Missing required argument: 'snapshotId'"));
});

test('startMcpStdioServer processes JSON-RPC lines and handles parse errors', async () => {
  const lineEmitter = new EventEmitter();
  const outputs = [];
  const deps = {
    createInterface: () => lineEmitter,
    stdout: {
      write: (chunk) => outputs.push(chunk)
    }
  };

  startMcpStdioServer({ version: '0.1.1' }, deps);

  lineEmitter.emit('line', '   ');
  lineEmitter.emit('line', JSON.stringify({ jsonrpc: '2.0', id: 99, method: 'ping' }));
  lineEmitter.emit('line', '{invalid-json');

  await new Promise((r) => setTimeout(r, 20));

  assert.equal(outputs.length, 2);
  const parsedOutputs = outputs.map((o) => JSON.parse(o));
  assert.ok(parsedOutputs.some((r) => r.id === 99));
  assert.ok(parsedOutputs.some((r) => r.error && r.error.code === -32700));
});
