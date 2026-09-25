import test from 'node:test';
import assert from 'node:assert/strict';

import {
  checkServerHealth,
  isDockerAvailable,
  isContainerRunning,
  isContainerExisting,
  startContainer,
  ensureServerRunning,
  openInBrowser,
  executeStart
} from '../src/docker-runner.js';

test('checkServerHealth reports offline when port is unreachable', async () => {
  const result = await checkServerHealth('http://127.0.0.1:59999', 200);
  assert.equal(result.ok, false);
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
  const mockHealth = async () => ({ ok: true, version: '0.0.1-Alpha-08' });

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088' },
    { checkServerHealth: mockHealth }
  );

  assert.equal(status.status, 'running');
  assert.equal(status.version, '0.0.1-Alpha-08');
  assert.equal(status.spawned, false);
});

test('ensureServerRunning reports offline when server is down and docker is unavailable', async () => {
  const mockHealth = async () => ({ ok: false });
  const mockDockerAvail = () => false;

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088' },
    {
      checkServerHealth: mockHealth,
      isDockerAvailable: mockDockerAvail
    }
  );

  assert.equal(status.status, 'offline');
  assert.equal(status.spawned, false);
  assert.ok(status.message.includes('Docker daemon is unavailable'));
});

test('ensureServerRunning launches container and polls readiness when server is down', async () => {
  let callCount = 0;
  const mockHealth = async () => {
    callCount++;
    if (callCount === 1) return { ok: false };
    return { ok: true, version: '0.0.1-Alpha-08' };
  };

  let containerStarted = false;
  const mockStart = () => {
    containerStarted = true;
  };

  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088', maxWaitMs: 3000 },
    {
      checkServerHealth: mockHealth,
      isDockerAvailable: () => true,
      startContainer: mockStart
    }
  );

  assert.equal(containerStarted, true);
  assert.equal(status.status, 'running');
  assert.equal(status.spawned, true);
  assert.equal(status.version, '0.0.1-Alpha-08');
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
    version: '0.0.1-Alpha-08',
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

