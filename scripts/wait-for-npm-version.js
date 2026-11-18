#!/usr/bin/env node
/**
 * Waits until an npm registry serves `<name>@<version>` (#233).
 *
 * `npm publish` returns before the registry's read replicas see the new version, so a single
 * `npm view` right after publishing fails with E404. This polls with exponential backoff until the
 * version is visible or the timeout expires.
 *
 * Usage: node scripts/wait-for-npm-version.js <name> <version> [--registry URL]
 *          [--timeout-seconds 300] [--initial-delay-seconds 5] [--max-delay-seconds 30]
 * Exit codes: 0 visible, 1 timed out, 2 usage error.
 */

import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export function parseArgs(argv) {
  const opts = {
    name: null,
    version: null,
    registry: 'https://registry.npmjs.org/',
    timeoutSeconds: 300,
    initialDelaySeconds: 5,
    maxDelaySeconds: 30
  };
  const positional = [];
  const numeric = {
    '--timeout-seconds': 'timeoutSeconds',
    '--initial-delay-seconds': 'initialDelaySeconds',
    '--max-delay-seconds': 'maxDelaySeconds'
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--registry') {
      opts.registry = argv[++i];
    } else if (numeric[arg]) {
      const value = Number(argv[++i]);
      if (!Number.isFinite(value) || value < 0) {
        throw new Error(`${arg} must be a non-negative number`);
      }
      opts[numeric[arg]] = value;
    } else if (arg.startsWith('--')) {
      throw new Error(`Unknown option ${arg}`);
    } else {
      positional.push(arg);
    }
  }
  [opts.name, opts.version] = positional;
  if (!opts.name || !opts.version || !opts.registry) {
    throw new Error('Usage: wait-for-npm-version.js <name> <version> [--registry URL]');
  }
  return opts;
}

/** Returns the version the registry reports for name@version, or null if not (yet) visible. */
export function viewVersion(name, version, registry, deps = {}) {
  const exec = deps.execFileSync || execFileSync;
  const npmBin = deps.npmBin || process.env.NPM_BIN || 'npm';
  try {
    const out = exec(npmBin, ['view', `${name}@${version}`, 'version', `--registry=${registry}`], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe']
    });
    const lines = String(out).trim().split(/\r?\n/).filter(Boolean);
    return lines.length ? lines[lines.length - 1].replace(/^.*\s/, '').replace(/'/g, '') : null;
  } catch {
    return null;
  }
}

export async function waitForNpmVersion(opts, deps = {}) {
  const now = deps.now || (() => Date.now());
  const sleep = deps.sleep || ((ms) => new Promise((r) => setTimeout(r, ms)));
  const log = deps.log || ((msg) => console.log(msg));
  const deadline = now() + opts.timeoutSeconds * 1000;
  let delay = Math.max(opts.initialDelaySeconds, 0) * 1000;
  let attempt = 0;
  for (;;) {
    attempt += 1;
    const seen = viewVersion(opts.name, opts.version, opts.registry, deps);
    if (seen === opts.version) {
      log(`${opts.name}@${opts.version} is visible on ${opts.registry} (attempt ${attempt}).`);
      return { ok: true, attempts: attempt };
    }
    const remaining = deadline - now();
    if (remaining <= 0) {
      log(
        `::error::${opts.name}@${opts.version} not visible on ${opts.registry} after ` +
          `${opts.timeoutSeconds}s (${attempt} attempts).`
      );
      return { ok: false, attempts: attempt };
    }
    const wait = Math.min(delay, remaining);
    log(
      `${opts.name}@${opts.version} not visible yet (attempt ${attempt}); retrying in ` +
        `${Math.round(wait / 1000)}s...`
    );
    await sleep(wait);
    delay = Math.min(Math.max(delay * 2, 1000), opts.maxDelaySeconds * 1000);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  let opts;
  try {
    opts = parseArgs(process.argv.slice(2));
  } catch (err) {
    console.error(err.message);
    process.exit(2);
  }
  waitForNpmVersion(opts).then((res) => process.exit(res.ok ? 0 : 1));
}
