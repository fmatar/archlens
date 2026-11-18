#!/usr/bin/env node

import { pathToFileURL } from 'node:url';

const DEFAULT_API_BASE = 'https://api.github.com';
const DEFAULT_TIMEOUT_MS = 15 * 60 * 1000;
const DEFAULT_POLL_INTERVAL_MS = 15 * 1000;
const PASSING_CONCLUSIONS = new Set(['success', 'neutral']);

/**
 * Pick the most recent check run matching each required check name and classify overall gate state.
 */
export function evaluateCheckRuns(checkRuns, requiredChecks) {
  if (!Array.isArray(requiredChecks) || requiredChecks.length === 0) {
    throw new Error('At least one required check name must be specified.');
  }
  const runs = Array.isArray(checkRuns) ? checkRuns : [];
  const missing = [];
  const pending = [];
  const failed = [];
  const passed = [];

  for (const requiredName of requiredChecks) {
    const matching = runs.filter((run) => run && run.name === requiredName);
    if (matching.length === 0) {
      missing.push(requiredName);
      continue;
    }
    const latest = matching.reduce((best, candidate) => {
      const bestId = typeof best.id === 'number' ? best.id : -1;
      const candId = typeof candidate.id === 'number' ? candidate.id : -1;
      return candId >= bestId ? candidate : best;
    });

    if (latest.status !== 'completed') {
      pending.push({ name: requiredName, status: latest.status || 'unknown', url: latest.html_url || '' });
    } else if (PASSING_CONCLUSIONS.has(latest.conclusion)) {
      passed.push({ name: requiredName, conclusion: latest.conclusion, url: latest.html_url || '' });
    } else {
      failed.push({
        name: requiredName,
        conclusion: latest.conclusion || 'unknown',
        url: latest.html_url || ''
      });
    }
  }

  let state = 'passed';
  if (failed.length > 0) {
    state = 'failed';
  } else if (missing.length > 0 || pending.length > 0) {
    state = 'pending';
  }

  return { state, missing, pending, failed, passed };
}

/**
 * Poll GitHub Check Runs for `repo` / `sha` until every check in `requiredChecks` completes with success.
 */
export async function waitForCiChecks(options, deps = {}) {
  const {
    repo,
    sha,
    token,
    requiredChecks = ['Verify & Quality Gates'],
    timeoutMs = DEFAULT_TIMEOUT_MS,
    pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
    apiBaseUrl = DEFAULT_API_BASE
  } = options || {};

  if (!repo || !sha) {
    throw new Error('Both repo (owner/name) and sha are required.');
  }
  if (!token) {
    throw new Error('GitHub token is required to query check-runs.');
  }

  const fetchFn = deps.fetch || globalThis.fetch;
  const nowFn = deps.now || Date.now;
  const sleepFn = deps.sleep || ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  const logFn = deps.log || console.log;

  const url = `${apiBaseUrl.replace(/\/$/, '')}/repos/${repo}/commits/${sha}/check-runs?per_page=100`;
  const headers = {
    Accept: 'application/vnd.github+json',
    Authorization: `Bearer ${token}`,
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'archlens-ci-gate'
  };

  const deadline = nowFn() + timeoutMs;

  while (true) {
    let evaluation = null;
    try {
      const response = await fetchFn(url, { headers });
      if (!response.ok) {
        if (response.status >= 500 || response.status === 429) {
          logFn(`[ci-gate] Transient GitHub API HTTP ${response.status}; retrying...`);
        } else {
          throw new Error(`GitHub Check Runs API returned HTTP ${response.status} for ${repo}@${sha}`);
        }
      } else {
        const data = await response.json();
        evaluation = evaluateCheckRuns(data?.check_runs, requiredChecks);
      }
    } catch (err) {
      if (err.message && err.message.startsWith('GitHub Check Runs API returned HTTP')) {
        throw err;
      }
      logFn(`[ci-gate] Error polling check runs (${err.message}); retrying...`);
    }

    if (evaluation) {
      if (evaluation.state === 'passed') {
        logFn(
          `[ci-gate] All required CI checks passed for ${sha}: ${evaluation.passed.map((p) => p.name).join(', ')}`
        );
        return evaluation;
      }
      if (evaluation.state === 'failed') {
        const summary = evaluation.failed
          .map((f) => `${f.name}=${f.conclusion}${f.url ? ` (${f.url})` : ''}`)
          .join(', ');
        throw new Error(`Required CI check(s) failed for ${sha}: ${summary}`);
      }
      logFn(
        `[ci-gate] Waiting for ${sha} (missing=[${evaluation.missing.join(', ')}], pending=[${evaluation.pending.map((p) => `${p.name}:${p.status}`).join(', ')}])`
      );
    }

    const remaining = deadline - nowFn();
    if (remaining <= 0) {
      const missingText = evaluation ? evaluation.missing.join(', ') : 'unknown';
      const pendingText = evaluation
        ? evaluation.pending.map((p) => `${p.name}:${p.status}`).join(', ')
        : 'unknown';
      throw new Error(
        `Timed out after ${timeoutMs}ms waiting for CI checks on ${sha} (missing=[${missingText}], pending=[${pendingText}]).`
      );
    }

    await sleepFn(Math.min(pollIntervalMs, remaining));
  }
}

export function parseArgs(argv) {
  const checks = [];
  let repo = process.env.GITHUB_REPOSITORY || '';
  let sha = process.env.GITHUB_SHA || '';
  let timeoutMs = DEFAULT_TIMEOUT_MS;
  let pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--repo' && argv[i + 1]) {
      repo = argv[++i];
    } else if (arg === '--sha' && argv[i + 1]) {
      sha = argv[++i];
    } else if (arg === '--check' && argv[i + 1]) {
      checks.push(argv[++i]);
    } else if (arg === '--timeout-ms' && argv[i + 1]) {
      timeoutMs = Number(argv[++i]);
    } else if (arg === '--poll-ms' && argv[i + 1]) {
      pollIntervalMs = Number(argv[++i]);
    }
  }

  return {
    repo,
    sha,
    token: process.env.GITHUB_TOKEN || process.env.GH_TOKEN || '',
    requiredChecks: checks.length > 0 ? checks : ['Verify & Quality Gates'],
    timeoutMs,
    pollIntervalMs
  };
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  await waitForCiChecks(options);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((err) => {
    console.error(`❌ CI Gate failed: ${err.message}`);
    process.exit(1);
  });
}
