/**
 * Archlens workbench session token helpers.
 *
 * The workbench server protects /api/* and /mcp with a per-launch session token. The CLI either
 * generates the token and hands it to the server it launches (ARCHLENS_TOKEN), or reads the
 * token the running server published to ~/.archlens/session-<port>.json.
 * Zero external dependencies.
 */

import * as crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

export const TOKEN_HEADER = 'X-Archlens-Token';

export function generateSessionToken() {
  return crypto.randomBytes(32).toString('base64url');
}

export function portFromUrl(serverUrl) {
  try {
    const url = new URL(serverUrl);
    if (url.port) return url.port;
    return url.protocol === 'https:' ? '443' : '80';
  } catch {
    return null;
  }
}

function homeDir(deps = {}) {
  if (deps.homedir) return deps.homedir;
  return os.homedir ? os.homedir() : process.env.HOME || '';
}

export function sessionFilePath(port, deps = {}) {
  return path.join(homeDir(deps), '.archlens', `session-${port}.json`);
}

/** Reads the token a server on {@code port} published, or null. */
export function readSessionToken(port, deps = {}) {
  if (!port) return null;
  const read = deps.readFileSync || fs.readFileSync;
  try {
    const data = JSON.parse(read(sessionFilePath(port, deps), 'utf8'));
    return typeof data.token === 'string' && data.token.length > 0 ? data.token : null;
  } catch {
    return null;
  }
}

/** Publishes a session file (owner-only permissions) for a server the CLI launched in Docker. */
export function writeSessionFile(port, token, deps = {}) {
  const file = sessionFilePath(port, deps);
  const mkdir = deps.mkdirSync || fs.mkdirSync;
  const write = deps.writeFileSync || fs.writeFileSync;
  try {
    mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
    write(file, JSON.stringify({ port: Number(port), token }), { mode: 0o600 });
    return file;
  } catch {
    return null;
  }
}

/** Token for talking to {@code serverUrl}: ARCHLENS_TOKEN first, then the published loopback session file. */
export function resolveSessionToken(serverUrl, deps = {}) {
  const env = deps.env || process.env;
  if (env.ARCHLENS_TOKEN && env.ARCHLENS_TOKEN.trim()) {
    if (!serverUrl || isSafeLoopbackServerUrl(serverUrl)) {
      return env.ARCHLENS_TOKEN.trim();
    }
  }
  if (!isSafeLoopbackServerUrl(serverUrl)) {
    return null;
  }
  return readSessionToken(portFromUrl(serverUrl), deps);
}

export function isSafeLoopbackServerUrl(serverUrl) {
  if (!serverUrl) return false;
  try {
    const url = new URL(serverUrl);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return false;
    if (url.username || url.password) return false;
    return isLoopbackHost(url.hostname);
  } catch {
    return false;
  }
}

/** Returns fetch init with the session token header added (unchanged when there is no token). */
export function withAuth(init, token) {
  if (!token) return init;
  const base = init || {};
  return { ...base, headers: { ...(base.headers || {}), [TOKEN_HEADER]: token } };
}

/** Browser URL that bootstraps the workbench session cookie. */
export function withTokenQuery(serverUrl, token) {
  if (!token) return serverUrl;
  try {
    const url = new URL(serverUrl);
    url.searchParams.set('token', token);
    return url.toString();
  } catch {
    return serverUrl;
  }
}

export function isLoopbackHost(host) {
  if (!host) return false;
  const h = String(host).trim().toLowerCase().replace(/^\[|\]$/g, '');
  return h === 'localhost' || h === '::1' || h.startsWith('127.');
}
