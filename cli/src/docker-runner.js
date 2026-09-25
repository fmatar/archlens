/**
 * Archlens On-Demand Docker Runner & Health Inspector
 * Manages container lifecycle and health checks for the Archlens Quarkus server.
 * Zero external dependencies.
 */

import { spawnSync, execSync } from 'node:child_process';
import * as path from 'node:path';

export const DEFAULT_SERVER_URL = 'http://localhost:8088';
export const DEFAULT_DOCKER_IMAGE = 'ghcr.io/fmatar/archlens:latest';
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

export function startContainer(options = {}, deps = {}) {
  const containerName = options.containerName || DEFAULT_CONTAINER_NAME;
  const image = options.image || DEFAULT_DOCKER_IMAGE;
  const workspace = path.resolve(options.workspace || '.');
  const port = options.port || '8088';
  const customExec = deps.execSync || execSync;

  const exists = deps.isContainerExisting
    ? deps.isContainerExisting(containerName)
    : isContainerExisting(containerName, customExec);

  if (exists) {
    customExec(`docker start ${containerName}`, { stdio: 'pipe', timeout: 10000 });
    return { action: 'started', containerName };
  }

  const runCmd = `docker run -d --name ${containerName} -p ${port}:8088 -v "${workspace}:/workspace" ${image}`;
  customExec(runCmd, { stdio: 'pipe', timeout: 15000 });
  return { action: 'created', containerName };
}

export async function ensureServerRunning(options = {}, deps = {}) {
  const serverUrl = options.serverUrl || DEFAULT_SERVER_URL;
  const maxWaitMs = options.maxWaitMs || 5000;
  const spawnDocker = options.spawnDocker !== false;

  const healthChecker = deps.checkServerHealth || checkServerHealth;
  const dockerChecker = deps.isDockerAvailable || isDockerAvailable;
  const starter = deps.startContainer || startContainer;

  // 1. Initial health inspection
  const initialCheck = await healthChecker(serverUrl, 1000);
  if (initialCheck.ok) {
    return {
      status: 'running',
      serverUrl,
      version: initialCheck.version,
      spawned: false
    };
  }

  // 2. Fallback check when Docker is disabled or unavailable
  if (!spawnDocker || !dockerChecker(deps.execSync || execSync)) {
    return {
      status: 'offline',
      serverUrl,
      spawned: false,
      message: 'Archlens Quarkus server is unreachable and Docker daemon is unavailable.'
    };
  }

  // 3. Launch Docker container
  try {
    starter(options, deps);
  } catch (err) {
    return {
      status: 'error',
      serverUrl,
      spawned: false,
      message: `Failed to launch Docker container: ${err.message}`
    };
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
        spawned: true
      };
    }
  }

  return {
    status: 'starting',
    serverUrl,
    spawned: true,
    message: 'Archlens container launched; service is warming up.'
  };
}
