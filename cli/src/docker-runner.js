/**
 * Archlens On-Demand Docker Runner & Health Inspector
 * Manages container lifecycle and health checks for the Archlens Quarkus server.
 * Zero external dependencies.
 */

import { spawn, spawnSync, execSync } from 'node:child_process';
import * as path from 'node:path';
import * as fs from 'node:fs';
import * as os from 'node:os';
import {
  generateSessionToken,
  isLoopbackHost,
  resolveSessionToken,
  withTokenQuery,
  writeSessionFile
} from './session.js';

export const DEFAULT_SERVER_URL = 'http://127.0.0.1:8088';
export const DEFAULT_DOCKER_IMAGE = 'ghcr.io/fmatar/archlens:latest';
export const DEFAULT_DOCKERHUB_IMAGE = 'docker.io/fmatar/archlens:latest';
export const DEFAULT_CONTAINER_NAME = 'archlens-server';

export async function checkServerHealth(serverUrl = DEFAULT_SERVER_URL, timeoutMs = 1500) {
  try {
    const url = new URL('/api/version', serverUrl).toString();
    const res = await fetch(url, {
      signal: AbortSignal.timeout(timeoutMs)
    });
    if (res.ok) {
      const data = await res.json().catch(() => ({}));
      return { ok: true, version: data.version || 'unknown' };
    }
    return { ok: false, status: res.status };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

export function isJavaAvailable(customExec = execSync) {
  try {
    customExec('java -version', { stdio: 'pipe', timeout: 3000 });
    return true;
  } catch {
    return false;
  }
}

export function getPlatformBinarySuffix(platform = process.platform, arch = process.arch) {
  if (platform === 'darwin') {
    return arch === 'arm64' ? 'macos-arm64' : 'macos-amd64';
  }
  if (platform === 'win32') {
    return 'windows-amd64.exe';
  }
  return arch === 'arm64' ? 'linux-arm64' : 'linux-amd64';
}

export function findNativeBinary(options = {}, deps = {}) {
  const exists = deps.existsSync || fs.existsSync;
  const homedir = (deps.os && deps.os.homedir)
    ? deps.os.homedir()
    : os.homedir
      ? os.homedir()
      : process.env.HOME || '';
  const workspace = path.resolve(options.workspace || options.path || '.');
  const cwd = (deps.process && deps.process.cwd) ? deps.process.cwd() : process.cwd();
  const suffix = getPlatformBinarySuffix(deps.platform || process.platform, deps.arch || process.arch);
  const isWin = (deps.platform || process.platform) === 'win32';
  const runnerName = isWin ? 'archlens-runner.exe' : 'archlens-runner';

  const candidates = [];
  if (options.binaryPath) candidates.push(path.resolve(options.binaryPath));
  if (process.env.ARCHLENS_BINARY) candidates.push(path.resolve(process.env.ARCHLENS_BINARY));

  if (homedir) {
    candidates.push(path.join(homedir, '.archlens', 'bin', `archlens-${suffix}`));
    candidates.push(path.join(homedir, '.archlens', 'bin', isWin ? 'archlens.exe' : 'archlens'));
    candidates.push(path.join(homedir, '.archlens', `archlens-${suffix}`));
  }

  const projectRoots = [workspace];
  if (cwd && cwd !== workspace) {
    projectRoots.push(cwd);
  }

  for (const root of projectRoots) {
    candidates.push(path.resolve(root, 'backend', 'target', `archlens-${suffix}`));
    candidates.push(path.resolve(root, 'backend', 'target', runnerName));
    candidates.push(path.resolve(root, 'target', `archlens-${suffix}`));
    candidates.push(path.resolve(root, 'target', runnerName));
  }

  for (const candidate of candidates) {
    try {
      if (candidate && exists(candidate)) {
        return candidate;
      }
    } catch {
      // ignore
    }
  }
  return null;
}

/**
 * Environment for a server process the CLI launches: hands over the session token and, only when
 * explicitly requested, a non-loopback bind address.
 */
export function serverLaunchEnv(options = {}, baseEnv = process.env) {
  const env = { ...baseEnv };
  if (options.token) env.ARCHLENS_TOKEN = options.token;
  if (options.host) env.ARCHLENS_HOST = options.host;
  return env;
}

export function startNativeBinary(binaryPath, options = {}, deps = {}) {
  const port = options.port || '8088';
  const customSpawn = deps.spawn || spawn;
  const args = [`-Dquarkus.http.port=${port}`];

  if (options.jvmArgs && Array.isArray(options.jvmArgs)) {
    args.unshift(...options.jvmArgs);
  }

  const child = customSpawn(binaryPath, args, {
    detached: true,
    stdio: 'ignore',
    env: serverLaunchEnv(options)
  });

  if (child && typeof child.unref === 'function') {
    child.unref();
  }

  return {
    action: 'started_binary',
    mode: 'binary',
    binaryPath,
    pid: child ? child.pid : undefined
  };
}

export function findNativeJar(options = {}, deps = {}) {
  const exists = deps.existsSync || fs.existsSync;
  const homedir = (deps.os && deps.os.homedir) ? deps.os.homedir() : (os.homedir ? os.homedir() : process.env.HOME || '');
  const workspace = path.resolve(options.workspace || options.path || '.');
  const cwd = (deps.process && deps.process.cwd) ? deps.process.cwd() : process.cwd();

  const candidates = [];
  if (options.jarPath) candidates.push(path.resolve(options.jarPath));
  if (process.env.ARCHLENS_JAR) candidates.push(path.resolve(process.env.ARCHLENS_JAR));

  if (homedir) {
    candidates.push(path.join(homedir, '.archlens', 'runner', 'quarkus-app', 'quarkus-run.jar'));
    candidates.push(path.join(homedir, '.archlens', 'quarkus-app', 'quarkus-run.jar'));
    candidates.push(path.join(homedir, '.archlens', 'runner', 'archlens-runner.jar'));
    candidates.push(path.join(homedir, '.archlens', 'archlens-runner.jar'));
  }

  const projectRoots = [workspace];
  if (cwd && cwd !== workspace) {
    projectRoots.push(cwd);
  }

  for (const root of projectRoots) {
    candidates.push(path.resolve(root, 'target', 'runner-release', 'quarkus-app', 'quarkus-run.jar'));
    candidates.push(path.resolve(root, 'target', 'quarkus-app', 'quarkus-run.jar'));
    candidates.push(path.resolve(root, 'backend', 'target', 'quarkus-app', 'quarkus-run.jar'));
  }

  for (const candidate of candidates) {
    try {
      if (candidate && exists(candidate)) {
        return candidate;
      }
    } catch {
      // ignore
    }
  }
  return null;
}

export function startNativeJar(jarPath, options = {}, deps = {}) {
  const port = options.port || '8088';
  const customSpawn = deps.spawn || spawn;

  const jvmArgs = [
    `-Dquarkus.http.port=${port}`,
    '-jar',
    jarPath
  ];

  if (options.jvmArgs && Array.isArray(options.jvmArgs)) {
    jvmArgs.unshift(...options.jvmArgs);
  }

  const child = customSpawn('java', jvmArgs, {
    detached: true,
    stdio: 'ignore',
    env: serverLaunchEnv(options)
  });

  if (child && typeof child.unref === 'function') {
    child.unref();
  }

  return {
    action: 'started_jar',
    mode: 'jar',
    jarPath,
    pid: child ? child.pid : undefined
  };
}

export function isDockerAvailable(customExec = execSync) {
  try {
    customExec('docker info', { stdio: 'pipe', timeout: 3000 });
    return true;
  } catch {
    return false;
  }
}

export function isContainerRunning(containerName = DEFAULT_CONTAINER_NAME, customExec = execSync) {
  try {
    const out = customExec(
      `docker ps --filter name=^/${containerName}$ --format "{{.Names}}"`,
      { encoding: 'utf8', stdio: 'pipe', timeout: 3000 }
    ).trim();
    return out === containerName;
  } catch {
    return false;
  }
}

export function isContainerExisting(containerName = DEFAULT_CONTAINER_NAME, customExec = execSync) {
  try {
    const out = customExec(
      `docker ps -a --filter name=^/${containerName}$ --format "{{.Names}}"`,
      { encoding: 'utf8', stdio: 'pipe', timeout: 3000 }
    ).trim();
    return out === containerName;
  } catch {
    return false;
  }
}

/** Host-side port publish spec: loopback only unless a wider bind host was explicitly requested. */
export function dockerPublishSpec(port = '8088', host) {
  const bindHost = host && String(host).trim() ? String(host).trim() : '127.0.0.1';
  return `${bindHost}:${port}:8088`;
}

export function startContainer(options = {}, deps = {}) {
  const containerName = options.containerName || DEFAULT_CONTAINER_NAME;
  const image = options.image || DEFAULT_DOCKER_IMAGE;
  const workspace = path.resolve(options.workspace || '.');
  const port = options.port || '8088';
  const customExec = deps.execSync || execSync;
  const customSpawn = deps.spawnSync || spawnSync;

  const exists = deps.isContainerExisting
    ? deps.isContainerExisting(containerName)
    : isContainerExisting(containerName, customExec);

  if (exists) {
    if (deps.execSync && !deps.spawnSync) {
      customExec(`docker start ${containerName}`, { stdio: 'pipe', timeout: 10000 });
    } else {
      customSpawn('docker', ['start', containerName], { stdio: 'pipe', timeout: 10000 });
    }
    return { action: 'started', containerName };
  }

  const publish = dockerPublishSpec(port, options.host);
  // The token is passed by name only (-e ARCHLENS_TOKEN) so it never appears on the command line.
  const tokenArgs = options.token ? ['-e', 'ARCHLENS_TOKEN'] : [];
  const runArgs = [
    'run',
    '-d',
    '--name',
    containerName,
    '-p',
    publish,
    ...tokenArgs,
    '-v',
    `${workspace}:/workspace`,
    image
  ];
  const env = serverLaunchEnv({ token: options.token });

  if (deps.execSync && !deps.spawnSync) {
    customExec(
      `docker run -d --name ${containerName} -p ${publish}${options.token ? ' -e ARCHLENS_TOKEN' : ''} -v "${workspace}:/workspace" ${image}`,
      { stdio: 'pipe', timeout: 15000, env }
    );
  } else {
    customSpawn('docker', runArgs, { stdio: 'pipe', timeout: 15000, env });
  }

  return { action: 'created', containerName, mode: 'docker' };
}

export async function ensureServerRunning(options = {}, deps = {}) {
  const serverUrl = options.serverUrl || DEFAULT_SERVER_URL;
  const maxWaitMs = options.maxWaitMs || 5000;
  const preferDocker = Boolean(options.preferDocker || options.dockerOnly);
  const preferNative = options.preferNative !== false && !preferDocker;
  const spawnDocker = options.spawnDocker !== false;

  const healthChecker = deps.checkServerHealth || checkServerHealth;
  const binaryFinder = deps.findNativeBinary || (deps.findNativeJar ? () => null : findNativeBinary);
  const binaryStarter = deps.startNativeBinary || startNativeBinary;
  const javaChecker = deps.isJavaAvailable || isJavaAvailable;
  const jarFinder = deps.findNativeJar || findNativeJar;
  const jarStarter = deps.startNativeJar || startNativeJar;
  const dockerChecker = deps.isDockerAvailable || isDockerAvailable;
  const starter = deps.startContainer || startContainer;

  const tokenResolver = deps.resolveSessionToken || resolveSessionToken;

  // 1. Initial health inspection
  const initialCheck = await healthChecker(serverUrl, 1000);
  if (initialCheck.ok) {
    return {
      status: 'running',
      serverUrl,
      version: initialCheck.version,
      spawned: false,
      token: tokenResolver(serverUrl, deps) || undefined
    };
  }

  // The token for a server we launch: pinned via ARCHLENS_TOKEN, or freshly generated.
  const env = deps.env || process.env;
  let token =
    options.token ||
    (env.ARCHLENS_TOKEN && env.ARCHLENS_TOKEN.trim()) ||
    (deps.generateSessionToken || generateSessionToken)();
  options = { ...options, token };

  let launchedMode = null;
  let launchedJarPath = null;
  let launchedBinaryPath = null;

  // 2a. Try GraalVM native binary first if preferNative is true
  if (preferNative) {
    const binPath = binaryFinder(options, deps);
    if (binPath) {
      try {
        binaryStarter(binPath, options, deps);
        launchedMode = 'binary';
        launchedBinaryPath = binPath;
      } catch {
        // Fall back to JAR or Docker if native binary launch throws
      }
    }
  }

  // 2b. Try native JAR if preferNative is true and no native binary launched
  if (!launchedMode && preferNative && javaChecker(deps.execSync || execSync)) {
    const jarPath = jarFinder(options, deps);
    if (jarPath) {
      try {
        jarStarter(jarPath, options, deps);
        launchedMode = 'jar';
        launchedJarPath = jarPath;
      } catch {
        // Fall back to Docker if native JAR launch throws
      }
    }
  }

  // 3. Fallback to Docker if native binary/JAR was not launched
  if (!launchedMode) {
    const dockerAvailable = spawnDocker && dockerChecker(deps.execSync || execSync);
    if (dockerAvailable) {
      try {
        const started = starter(options, deps);
        launchedMode = 'docker';
        if (started && started.action === 'created') {
          // New container: it received our token, publish it for other local clients.
          writeSessionFile(options.port || '8088', token, deps.session || {});
        } else if (started && started.action === 'started') {
          // Restarted container keeps the token it was created with.
          token = tokenResolver(serverUrl, deps) || undefined;
        }
      } catch (err) {
        return {
          status: 'error',
          serverUrl,
          spawned: false,
          message: `Failed to launch Docker container: ${err.message}`
        };
      }
    } else {
      return {
        status: 'offline',
        serverUrl,
        spawned: false,
        message: 'Archlens Quarkus server is unreachable. Neither a native runner JAR (with Java 21+) nor Docker daemon is available.'
      };
    }
  }

  // 4. Poll readiness until maxWaitMs
  const startTime = Date.now();
  while (Date.now() - startTime < maxWaitMs) {
    await new Promise((r) => setTimeout(r, 400));
    const probe = await healthChecker(serverUrl, 800);
    if (probe.ok) {
      return {
        status: 'running',
        serverUrl,
        version: probe.version,
        spawned: true,
        mode: launchedMode,
        jarPath: launchedJarPath,
        binaryPath: launchedBinaryPath,
        token
      };
    }
  }

  return {
    status: 'starting',
    serverUrl,
    spawned: true,
    mode: launchedMode,
    jarPath: launchedJarPath,
    binaryPath: launchedBinaryPath,
    token,
    message: `Archlens ${launchedMode === 'jar' || launchedMode === 'binary' ? 'native server' : 'container'} launched; service is warming up.`
  };
}

export function openInBrowser(url, customSpawn = spawnSync) {
  try {
    if (process.platform === 'darwin') {
      customSpawn('open', [url], { stdio: 'ignore' });
      return true;
    }
    if (process.platform === 'win32') {
      customSpawn('cmd', ['/c', 'start', '""', url], { stdio: 'ignore' });
      return true;
    }
    customSpawn('xdg-open', [url], { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

export async function executeStart(options = {}, deps = {}) {
  const runner = deps.ensureServerRunning || ensureServerRunning;
  const opener = deps.openInBrowser || openInBrowser;
  const port = options.port || '8088';
  const serverUrl = options.serverUrl || (port === '8088' ? DEFAULT_SERVER_URL : `http://127.0.0.1:${port}`);
  const workspace = path.resolve(options.path || options.workspace || '.');

  const status = await runner({
    serverUrl,
    workspace,
    port,
    ...options
  }, deps);

  const workbenchUrl = withTokenQuery(serverUrl, status.token);
  if (status.status === 'running' && options.open) {
    opener(workbenchUrl, deps.spawnSync || spawnSync);
  }

  return {
    ...status,
    port,
    serverUrl,
    workbenchUrl,
    exposed: Boolean(options.host && !isLoopbackHost(options.host))
  };
}


