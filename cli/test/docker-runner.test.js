import test from 'node:test';
import assert from 'node:assert/strict';

import {
  checkServerHealth,
  isDockerAvailable,
  isContainerRunning,
  isContainerExisting,
  startContainer,
  isJavaAvailable,
  getPlatformBinarySuffix,
  findNativeBinary,
  startNativeBinary,
  findNativeJar,
  startNativeJar,
  ensureServerRunning,
  openInBrowser,
  executeStart,
  dockerPublishSpec,
  serverLaunchEnv
} from '../src/docker-runner.js';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

test('docker-runner uses spawnSync argument arrays only and contains no execSync shell branches', () => {
  const runnerPath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src/docker-runner.js');
  const sourceWithoutComments = fs
    .readFileSync(runnerPath, 'utf8')
    .replace(/\/\*[\s\S]*?\*\/|\/\/.*$/gm, '');
  assert.equal(
    /\bexecSync\b/.test(sourceWithoutComments),
    false,
    'docker-runner.js must not import or invoke execSync'
  );
});

test('checkServerHealth reports offline when port is unreachable', async () => {
  const result = await checkServerHealth('http://127.0.0.1:59999', 200);
  assert.equal(result.ok, false);
});

test('isJavaAvailable detects availability via spawnSync argument array', () => {
  const calls = [];
  const mockSpawnSuccess = (cmd, args) => {
    calls.push({ cmd, args });
    return { status: 0, stderr: 'openjdk version "25.0.4"' };
  };
  assert.equal(isJavaAvailable(mockSpawnSuccess), true);
  assert.deepEqual(calls[0], { cmd: 'java', args: ['-version'] });

  const mockSpawnNonZero = () => ({ status: 1, stderr: 'not found' });
  assert.equal(isJavaAvailable(mockSpawnNonZero), false);

  const mockSpawnError = () => ({ status: null, error: new Error('ENOENT') });
  assert.equal(isJavaAvailable(mockSpawnError), false);
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
    return { ok: true, version: '0.1.0-Beta-04' };
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
    return { ok: true, version: '0.1.0-Beta-04' };
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

test('isDockerAvailable detects availability via spawnSync argument array', () => {
  const calls = [];
  const mockSpawnSuccess = (cmd, args) => {
    calls.push({ cmd, args });
    return { status: 0, stdout: 'Server Version: 29.0' };
  };
  assert.equal(isDockerAvailable(mockSpawnSuccess), true);
  assert.deepEqual(calls[0], { cmd: 'docker', args: ['info'] });

  const mockSpawnFail = () => ({ status: 1, error: new Error('command not found: docker') });
  assert.equal(isDockerAvailable(mockSpawnFail), false);
});

test('isContainerRunning checks container name match via spawnSync argument array', () => {
  const calls = [];
  const mockSpawnRunning = (cmd, args) => {
    calls.push({ cmd, args });
    return { status: 0, stdout: 'archlens-server\n' };
  };
  assert.equal(isContainerRunning('archlens-server', mockSpawnRunning), true);
  assert.deepEqual(calls[0], {
    cmd: 'docker',
    args: ['ps', '--filter', 'name=^/archlens-server$', '--format', '{{.Names}}']
  });

  const mockSpawnStopped = () => ({ status: 0, stdout: '' });
  assert.equal(isContainerRunning('archlens-server', mockSpawnStopped), false);
});

test('isContainerExisting checks container existence in any state via spawnSync argument array', () => {
  const calls = [];
  const mockSpawnExists = (cmd, args) => {
    calls.push({ cmd, args });
    return { status: 0, stdout: 'archlens-server\n' };
  };
  assert.equal(isContainerExisting('archlens-server', mockSpawnExists), true);
  assert.deepEqual(calls[0], {
    cmd: 'docker',
    args: ['ps', '-a', '--filter', 'name=^/archlens-server$', '--format', '{{.Names}}']
  });

  const mockSpawnMissing = () => ({ status: 0, stdout: '' });
  assert.equal(isContainerExisting('archlens-server', mockSpawnMissing), false);
});

test('startContainer invokes docker start with argument array when container exists', () => {
  const calls = [];
  const mockSpawn = (cmd, args) => {
    calls.push({ cmd, args });
    return { status: 0, stdout: '' };
  };

  const result = startContainer(
    { containerName: 'archlens-server' },
    {
      spawnSync: mockSpawn,
      isContainerExisting: () => true
    }
  );

  assert.equal(result.action, 'started');
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], { cmd: 'docker', args: ['start', 'archlens-server'] });
});

test('startContainer invokes docker run with argument array safely for paths with spaces and throws on non-zero status', () => {
  const calls = [];
  const mockSpawn = (cmd, args) => {
    calls.push({ cmd, args });
    return { status: 0, stdout: '' };
  };

  const workspaceWithSpaces = path.resolve('/test/my workspace "quoted"');
  const result = startContainer(
    { containerName: 'archlens-server', workspace: workspaceWithSpaces },
    {
      spawnSync: mockSpawn,
      isContainerExisting: () => false
    }
  );

  assert.equal(result.action, 'created');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].cmd, 'docker');
  assert.ok(calls[0].args.includes('archlens-server'));
  assert.ok(calls[0].args.includes(`${workspaceWithSpaces}:/workspace`));
  assert.ok(calls[0].args.includes('127.0.0.1:8088:8088'), 'publishes on loopback by default');

  assert.throws(
    () =>
      startContainer(
        { containerName: 'archlens-server', workspace: '/test/ws' },
        {
          spawnSync: () => ({ status: 125, stderr: 'port is already allocated' }),
          isContainerExisting: () => false
        }
      ),
    /port is already allocated/
  );

  assert.throws(
    () =>
      startContainer(
        { containerName: 'archlens-server', workspace: '/test/ws' },
        {
          spawnSync: () => ({ status: 1, stderr: '' }),
          isContainerExisting: () => true
        }
      ),
    /docker start exited with status 1/
  );

  assert.throws(
    () =>
      startContainer(
        { containerName: 'archlens-server', workspace: '/test/ws' },
        {
          spawnSync: () => ({ status: null, error: new Error('spawnSync docker ENOENT') }),
          isContainerExisting: () => true
        }
      ),
    /ENOENT/
  );
});

test('startContainer publishes on loopback, passes the token by name only, and honours --host', () => {
  const calls = [];
  const mockSpawn = (cmd, args, opts) => {
    calls.push({ cmd, args, opts });
    return { status: 0 };
  };
  startContainer(
    { workspace: '/ws', port: '9000', token: 'secret-token' },
    { spawnSync: mockSpawn, isContainerExisting: () => false }
  );
  const { args, opts } = calls[0];
  assert.ok(args.includes('127.0.0.1:9000:8088'));
  assert.ok(args.includes('ARCHLENS_TOKEN'));
  assert.ok(!args.some((a) => a.includes('secret-token')), 'token must not appear in argv');
  assert.equal(opts.env.ARCHLENS_TOKEN, 'secret-token');

  startContainer(
    { workspace: '/ws', port: '9000', host: '0.0.0.0' },
    { spawnSync: mockSpawn, isContainerExisting: () => false }
  );
  assert.ok(calls[1].args.includes('0.0.0.0:9000:8088'));
  assert.equal(dockerPublishSpec('8088'), '127.0.0.1:8088:8088');
  assert.equal(dockerPublishSpec('8088', '  '), '127.0.0.1:8088:8088');
});

test('native launches receive the session token and only an explicit bind host', () => {
  let spawned;
  const mockSpawn = (cmd, args, opts) => {
    spawned = { cmd, args, opts };
    return { pid: 1, unref() {} };
  };
  startNativeJar('/x/quarkus-run.jar', { token: 'tok-1' }, { spawn: mockSpawn });
  assert.equal(spawned.opts.env.ARCHLENS_TOKEN, 'tok-1');
  assert.equal(spawned.opts.env.ARCHLENS_HOST, process.env.ARCHLENS_HOST);
  startNativeBinary('/x/archlens', { token: 'tok-2', host: '0.0.0.0' }, { spawn: mockSpawn });
  assert.equal(spawned.opts.env.ARCHLENS_TOKEN, 'tok-2');
  assert.equal(spawned.opts.env.ARCHLENS_HOST, '0.0.0.0');
  const env = serverLaunchEnv({}, { A: '1' });
  assert.deepEqual(env, { A: '1' });
});

test('ensureServerRunning generates a token for servers it launches and reuses published tokens', async () => {
  let launchedWith;
  let healthy = false;
  const status = await ensureServerRunning(
    { serverUrl: 'http://127.0.0.1:8088', maxWaitMs: 2000 },
    {
      env: {},
      checkServerHealth: async () => ({ ok: healthy, version: '1' }),
      findNativeBinary: () => '/bin/archlens',
      startNativeBinary: (bin, opts) => {
        launchedWith = opts;
        healthy = true;
      },
      generateSessionToken: () => 'fresh-token'
    }
  );
  assert.equal(status.status, 'running');
  assert.equal(launchedWith.token, 'fresh-token');
  assert.equal(status.token, 'fresh-token');

  const pinned = await ensureServerRunning(
    { serverUrl: 'http://127.0.0.1:8088', maxWaitMs: 2000 },
    {
      env: { ARCHLENS_TOKEN: 'pinned' },
      checkServerHealth: async () => ({ ok: launchedWith && launchedWith.token === 'pinned' }),
      findNativeBinary: () => '/bin/archlens',
      startNativeBinary: (bin, opts) => {
        launchedWith = opts;
      }
    }
  );
  assert.equal(pinned.token, 'pinned');

  const already = await ensureServerRunning(
    { serverUrl: 'http://127.0.0.1:8088' },
    {
      checkServerHealth: async () => ({ ok: true, version: '1' }),
      resolveSessionToken: () => 'published-token'
    }
  );
  assert.equal(already.spawned, false);
  assert.equal(already.token, 'published-token');
});

test('ensureServerRunning keeps the original token when restarting an existing container', async () => {
  let healthy = false;
  const status = await ensureServerRunning(
    { serverUrl: 'http://127.0.0.1:8088', preferDocker: true, maxWaitMs: 2000 },
    {
      env: {},
      checkServerHealth: async () => ({ ok: healthy }),
      isDockerAvailable: () => true,
      startContainer: () => {
        healthy = true;
        return { action: 'started' };
      },
      resolveSessionToken: () => 'original-token',
      generateSessionToken: () => 'new-token'
    }
  );
  assert.equal(status.token, 'original-token');
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
    serverUrl: 'http://127.0.0.1:8088',
    version: '0.0.1-Alpha-11',
    spawned: true,
    token: 'sess-tok'
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
  assert.equal(res.serverUrl, 'http://127.0.0.1:8088');
  assert.equal(openedUrl, 'http://127.0.0.1:8088/?token=sess-tok');
  assert.equal(res.workbenchUrl, 'http://127.0.0.1:8088/?token=sess-tok');
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
  assert.equal(res.serverUrl, 'http://127.0.0.1:9099');
});

test('findNativeBinary, startNativeBinary, and ensureServerRunning support GraalVM native binaries', async () => {
  assert.equal(getPlatformBinarySuffix('darwin', 'arm64'), 'macos-arm64');
  assert.equal(getPlatformBinarySuffix('linux', 'x64'), 'linux-amd64');
  assert.equal(getPlatformBinarySuffix('win32', 'x64'), 'windows-amd64.exe');

  const binCandidate = path.resolve('/my/workspace/backend/target/archlens-macos-arm64');
  const foundBin = findNativeBinary(
    { workspace: '/my/workspace' },
    { existsSync: (p) => p === binCandidate, platform: 'darwin', arch: 'arm64' }
  );
  assert.equal(foundBin, binCandidate);

  const spawned = [];
  const started = startNativeBinary(
    binCandidate,
    { port: '8091' },
    {
      spawn: (cmd, args, opts) => {
        spawned.push({ cmd, args, opts });
        return { pid: 7777, unref: () => {} };
      }
    }
  );
  assert.equal(started.mode, 'binary');
  assert.equal(started.pid, 7777);
  assert.equal(spawned[0].cmd, binCandidate);
  assert.deepEqual(spawned[0].args, ['-Dquarkus.http.port=8091']);

  let healthChecks = 0;
  const status = await ensureServerRunning(
    { serverUrl: 'http://localhost:8088', maxWaitMs: 2000 },
    {
      checkServerHealth: async () => {
        healthChecks += 1;
        return healthChecks === 1 ? { ok: false } : { ok: true, version: '0.2.0' };
      },
      findNativeBinary: () => binCandidate,
      startNativeBinary: () => ({ action: 'started_binary', mode: 'binary', binaryPath: binCandidate }),
      isJavaAvailable: () => false,
      isDockerAvailable: () => false
    }
  );
  assert.equal(status.status, 'running');
  assert.equal(status.mode, 'binary');
  assert.equal(status.binaryPath, binCandidate);
});


