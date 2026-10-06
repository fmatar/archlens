import test from 'node:test';
import assert from 'node:assert/strict';

import {
  checkServerHealth,
  isDockerAvailable,
  isContainerRunning,
  isContainerExisting,
  startContainer,
  isJavaAvailable,
  findNativeJar,
  startNativeJar,
  ensureServerRunning,
  openInBrowser,
  executeStart
} from '../src/docker-runner.js';
import * as path from 'node:path';

test('checkServerHealth reports offline when port is unreachable', async () => {
  const result = await checkServerHealth('http://127.0.0.1:59999', 200);
  assert.equal(result.ok, false);
});

test('isJavaAvailable detects availability from command execution', () => {
  const mockExecSuccess = () => 'openjdk version "25.0.4"';
  assert.equal(isJavaAvailable(mockExecSuccess), true);

  const mockExecFail = () => {
    throw new Error('command not found: java');
  };
  assert.equal(isJavaAvailable(mockExecFail), false);
});

test('findNativeJar discovers jar from options, environment, and candidate paths', () => {
  const existingFiles = new Set(['/custom/path/archlens.jar']);
  const mockExists = (p) => existingFiles.has(p);

  // 1. Explicit options.jarPath
  assert.equal(
    findNativeJar({ jarPath: '/custom/path/archlens.jar' }, { existsSync: mockExists }),
    path.resolve('/custom/path/archlens.jar')
  );

  // 2. Candidate in project target
  const targetJar = path.resolve('/my/workspace/target/runner-release/quarkus-app/quarkus-run.jar');
  existingFiles.add(targetJar);
  assert.equal(
    findNativeJar({ workspace: '/my/workspace' }, { existsSync: mockExists }),
    targetJar
  );

  // 3. None exists
  assert.equal(
    findNativeJar({ workspace: '/empty/workspace' }, { existsSync: () => false }),
    null
  );
});

test('startNativeJar invokes spawn with appropriate jvm args and detached mode', () => {
  const spawnCalls = [];
  const mockSpawn = (cmd, args, opts) => {
    spawnCalls.push({ cmd, args, opts });
    return { pid: 4242, unref: () => {} };
  };

  const res = startNativeJar('/path/to/quarkus-run.jar', { port: '8090' }, { spawn: mockSpawn });

  assert.equal(res.action, 'started_jar');
  assert.equal(res.mode, 'jar');
  assert.equal(res.pid, 4242);
  assert.equal(spawnCalls.length, 1);
  assert.equal(spawnCalls[0].cmd, 'java');
  assert.deepEqual(spawnCalls[0].args, ['-Dquarkus.http.port=8090', '-jar', '/path/to/quarkus-run.jar']);
  assert.equal(spawnCalls[0].opts.detached, true);
});

test('ensureServerRunning prefers native JAR when java and jar are available', async () => {
  let callCount = 0;
  const mockHealth = async () => {
    callCount++;
    if (callCount === 1) return { ok: false };
    return { ok: true, version: '0.1.0-Beta-03' };
  };

  let jarStarted = false;
  let dockerStarted = false;

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088', maxWaitMs: 3000 },
    {
      checkServerHealth: mockHealth,
      isJavaAvailable: () => true,
      findNativeJar: () => '/path/to/quarkus-run.jar',
      startNativeJar: () => {
        jarStarted = true;
        return { action: 'started_jar', mode: 'jar' };
      },
      isDockerAvailable: () => true,
      startContainer: () => {
        dockerStarted = true;
      }
    }
  );

  assert.equal(jarStarted, true);
  assert.equal(dockerStarted, false);
  assert.equal(status.status, 'running');
  assert.equal(status.mode, 'jar');
  assert.equal(status.jarPath, '/path/to/quarkus-run.jar');
  assert.equal(status.spawned, true);
});

test('ensureServerRunning respects preferDocker option and skips native JAR', async () => {
  let callCount = 0;
  const mockHealth = async () => {
    callCount++;
    if (callCount === 1) return { ok: false };
    return { ok: true, version: '0.1.0-Beta-03' };
  };

  let jarStarted = false;
  let dockerStarted = false;

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088', preferDocker: true, maxWaitMs: 3000 },
    {
      checkServerHealth: mockHealth,
      isJavaAvailable: () => true,
      findNativeJar: () => '/path/to/quarkus-run.jar',
      startNativeJar: () => {
        jarStarted = true;
      },
      isDockerAvailable: () => true,
      startContainer: () => {
        dockerStarted = true;
      }
    }
  );

  assert.equal(jarStarted, false);
  assert.equal(dockerStarted, true);
  assert.equal(status.status, 'running');
  assert.equal(status.mode, 'docker');
});

test('isDockerAvailable detects availability from command execution', () => {
  const mockExecSuccess = () => 'Server Version: 29.0';
  assert.equal(isDockerAvailable(mockExecSuccess), true);

  const mockExecFail = () => {
    throw new Error('command not found: docker');
  };
  assert.equal(isDockerAvailable(mockExecFail), false);
});

test('isContainerRunning checks container name match', () => {
  const mockExecRunning = () => 'archlens-server';
  assert.equal(isContainerRunning('archlens-server', mockExecRunning), true);

  const mockExecStopped = () => '';
  assert.equal(isContainerRunning('archlens-server', mockExecStopped), false);
});

test('isContainerExisting checks container existence in any state', () => {
  const mockExecExists = () => 'archlens-server';
  assert.equal(isContainerExisting('archlens-server', mockExecExists), true);

  const mockExecMissing = () => '';
  assert.equal(isContainerExisting('archlens-server', mockExecMissing), false);
});

test('startContainer invokes docker start when container exists', () => {
  const commands = [];
  const mockExec = (cmd) => {
    commands.push(cmd);
    return '';
  };

  const result = startContainer(
    { containerName: 'archlens-server' },
    {
      execSync: mockExec,
      isContainerExisting: () => true
    }
  );

  assert.equal(result.action, 'started');
  assert.equal(commands.length, 1);
  assert.ok(commands[0].includes('docker start archlens-server'));
});

test('startContainer invokes docker run when container does not exist', () => {
  const commands = [];
  const mockExec = (cmd) => {
    commands.push(cmd);
    return '';
  };

  const result = startContainer(
    { containerName: 'archlens-server', workspace: '/test/workspace' },
    {
      execSync: mockExec,
      isContainerExisting: () => false
    }
  );

  assert.equal(result.action, 'created');
  assert.equal(commands.length, 1);
  assert.ok(commands[0].includes('docker run -d --name archlens-server'));
  assert.ok(commands[0].includes('/test/workspace:/workspace'));
});

test('ensureServerRunning returns running immediately if server is already healthy', async () => {
  const mockHealth = async () => ({ ok: true, version: '0.0.1-Alpha-11' });

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088' },
    { checkServerHealth: mockHealth }
  );

  assert.equal(status.status, 'running');
  assert.equal(status.version, '0.0.1-Alpha-11');
  assert.equal(status.spawned, false);
});

test('ensureServerRunning reports offline when server is down and docker is unavailable', async () => {
  const mockHealth = async () => ({ ok: false });
  const mockDockerAvail = () => false;

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088' },
    {
      checkServerHealth: mockHealth,
      isDockerAvailable: mockDockerAvail,
      isJavaAvailable: () => false
    }
  );

  assert.equal(status.status, 'offline');
  assert.equal(status.spawned, false);
  assert.ok(status.message.includes('Docker daemon is unavailable') || status.message.includes('server is unreachable'));
});

test('ensureServerRunning launches container and polls readiness when server is down and native runner is absent', async () => {
  let callCount = 0;
  const mockHealth = async () => {
    callCount++;
    if (callCount === 1) return { ok: false };
    return { ok: true, version: '0.0.1-Alpha-11' };
  };

  let containerStarted = false;
  const mockStart = () => {
    containerStarted = true;
  };

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088', maxWaitMs: 3000 },
    {
      checkServerHealth: mockHealth,
      isJavaAvailable: () => false,
      isDockerAvailable: () => true,
      startContainer: mockStart
    }
  );

  assert.equal(containerStarted, true);
  assert.equal(status.status, 'running');
  assert.equal(status.spawned, true);
  assert.equal(status.version, '0.0.1-Alpha-11');
});

test('openInBrowser delegates to platform opener command', () => {
  const spawnedCommands = [];
  const mockSpawn = (cmd, args) => {
    spawnedCommands.push({ cmd, args });
    return {};
  };

  const ok = openInBrowser('http://localhost:8088', mockSpawn);
  assert.equal(ok, true);
  assert.equal(spawnedCommands.length, 1);
  if (process.platform === 'darwin') {
    assert.equal(spawnedCommands[0].cmd, 'open');
  } else if (process.platform === 'win32') {
    assert.equal(spawnedCommands[0].cmd, 'cmd');
  } else {
    assert.equal(spawnedCommands[0].cmd, 'xdg-open');
  }
});

test('executeStart runs container and launches browser when open option is set', async () => {
  let openedUrl = null;
  const mockOpener = (url) => {
    openedUrl = url;
    return true;
  };

  const mockRunner = async () => ({
    status: 'running',
    serverUrl: 'http://localhost:8088',
    version: '0.0.1-Alpha-11',
    spawned: true
  });

  const res = await executeStart(
    { open: true, port: '8088' },
    {
      ensureServerRunning: mockRunner,
      openInBrowser: mockOpener
    }
  );

  assert.equal(res.status, 'running');
  assert.equal(res.port, '8088');
  assert.equal(res.serverUrl, 'http://localhost:8088');
  assert.equal(openedUrl, 'http://localhost:8088');
});

test('executeStart handles custom port and offline states', async () => {
  const mockRunner = async ({ serverUrl }) => ({
    status: 'offline',
    serverUrl,
    message: 'Docker is unavailable'
  });

  const res = await executeStart(
    { port: '9099' },
    { ensureServerRunning: mockRunner }
  );

  assert.equal(res.status, 'offline');
  assert.equal(res.port, '9099');
  assert.equal(res.serverUrl, 'http://localhost:9099');
});

