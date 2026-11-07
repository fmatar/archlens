import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import {
  findFreePort,
  waitForServerReady,
  runHttpSmokeChecks,
  runMcpStdioSmokeChecks,
  runSmokeSuite
} from '../smoke-test.js';

function createMockFetch() {
  return async (rawUrl) => {
    const url = new URL(rawUrl);
    if (url.pathname === '/') {
      return {
        ok: true,
        status: 200,
        text: async () => '<!doctype html><html><body><div id="app"></div></body></html>'
      };
    }
    if (url.pathname === '/api/version') {
      return {
        ok: true,
        status: 200,
        json: async () => ({ version: '0.1.1' })
      };
    }
    if (url.pathname === '/api/graph') {
      return {
        ok: true,
        status: 200,
        json: async () => ({ packages: [{ name: 'domain' }], classes: [], edges: [] })
      };
    }
    if (url.pathname === '/api/fitness') {
      return {
        ok: true,
        status: 200,
        json: async () => ({ overallScore: 92, grade: 'A', passed: true })
      };
    }
    if (url.pathname === '/api/fitness/history') {
      return {
        ok: true,
        status: 200,
        json: async () => ({ trend: 'STABLE', snapshots: [] })
      };
    }
    if (url.pathname === '/api/violations/invert-plan') {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          sourceClass: url.searchParams.get('from'),
          targetClass: url.searchParams.get('to'),
          portInterfaceName: 'StripeGatewayPort',
          interfaceCode: 'public interface StripeGatewayPort {}'
        })
      };
    }
    if (url.pathname === '/api/policy') {
      return {
        ok: true,
        status: 200,
        json: async () => ({ title: 'Archlens', levels: [{ name: 'Domain Core' }] })
      };
    }
    if (url.pathname === '/mcp/sse') {
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'text/event-stream']])
      };
    }
    return { ok: false, status: 404 };
  };
}

describe('End-to-End Smoke Test Engine (scripts/smoke-test.js)', () => {
  it('waitForServerReady resolves when /api/version responds 200 OK', async () => {
    let attempts = 0;
    const mockFetch = async () => {
      attempts += 1;
      if (attempts < 2) throw new Error('ECONNREFUSED');
      return { ok: true, status: 200 };
    };
    const ready = await waitForServerReady('http://127.0.0.1:8088', 2000, 10, { fetch: mockFetch });
    assert.equal(ready, true);
    assert.equal(attempts, 2);
  });

  it('runHttpSmokeChecks verifies UI bundle, REST APIs, and MCP SSE endpoint', async () => {
    const results = await runHttpSmokeChecks('http://127.0.0.1:8088', '0.1.1', {
      fetch: createMockFetch()
    });
    assert.equal(results.passed, true);
    assert.equal(results.checks.length, 8);
    assert.ok(results.checks.every((c) => c.passed));
  });

  it('runHttpSmokeChecks detects version mismatch or endpoint failure', async () => {
    const results = await runHttpSmokeChecks('http://127.0.0.1:8088', '9.9.9', {
      fetch: createMockFetch()
    });
    assert.equal(results.passed, false);
    const versionCheck = results.checks.find((c) => c.name.includes('/api/version'));
    assert.equal(versionCheck.passed, false);
  });

  it('runMcpStdioSmokeChecks verifies JSON-RPC 2.0 initialize, 10 tools/list, resources/list, and tools/call', async () => {
    const mcpResult = await runMcpStdioSmokeChecks('http://127.0.0.1:8088', process.cwd(), {
      fetch: createMockFetch()
    });
    assert.equal(mcpResult.passed, true);
    assert.equal(mcpResult.toolsCount, 10);
    assert.ok(mcpResult.resourcesCount >= 1);
    assert.equal(mcpResult.dipPort, 'StripeGatewayPort');
    assert.equal(mcpResult.policyTitle, 'Archlens');
  });

  it('runSmokeSuite orchestrates server lifecycle, HTTP/MCP checks, and clean SIGTERM teardown', async () => {
    const port = await findFreePort();
    assert.ok(port > 1024);

    let killedWith = null;
    const mockChild = new EventEmitter();
    mockChild.stderr = new EventEmitter();
    mockChild.kill = (sig) => {
      killedWith = sig;
      setImmediate(() => mockChild.emit('exit', 0));
    };

    const suiteResult = await runSmokeSuite(
      { port: 18088 },
      {
        existsSync: () => true,
        readFileSync: () => JSON.stringify({ version: '0.1.1' }),
        spawn: () => mockChild,
        fetch: createMockFetch()
      }
    );

    assert.equal(suiteResult.passed, true);
    assert.equal(killedWith, 'SIGTERM');

    await assert.rejects(
      () => runSmokeSuite({ buildIfMissing: false }, { existsSync: () => false }),
      /Packaged Quarkus JAR not found/
    );
  });

  it('runSmokeSuite fails fast immediately if child process exits prematurely during startup', async () => {
    const crashingChild = new EventEmitter();
    crashingChild.stdout = new EventEmitter();
    crashingChild.stderr = new EventEmitter();
    crashingChild.kill = () => {};

    const startTime = Date.now();
    setImmediate(() => {
      crashingChild.stderr.emit('data', Buffer.from('BindException: Address already in use'));
      crashingChild.emit('exit', 1);
    });

    await assert.rejects(
      () =>
        runSmokeSuite(
          { port: 18089 },
          {
            existsSync: () => true,
            readFileSync: () => JSON.stringify({ version: '0.1.1' }),
            spawn: () => crashingChild,
            fetch: async () => {
              throw new Error('ECONNREFUSED');
            }
          }
        ),
      /BindException: Address already in use/
    );
    assert.ok(Date.now() - startTime < 2000, 'Should fail fast without waiting 30s timeout');
  });
});
