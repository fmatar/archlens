import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { evaluateCheckRuns, parseArgs, waitForCiChecks } from '../wait-for-ci-checks.js';

describe('CI Gate Check-Runs Waiter (scripts/wait-for-ci-checks.js)', () => {
  it('evaluateCheckRuns passes when every required check completes with success or neutral', () => {
    const result = evaluateCheckRuns(
      [
        { id: 10, name: 'Verify & Quality Gates', status: 'completed', conclusion: 'success' },
        { id: 11, name: 'Other Job', status: 'in_progress', conclusion: null }
      ],
      ['Verify & Quality Gates']
    );
    assert.equal(result.state, 'passed');
    assert.equal(result.passed.length, 1);
    assert.equal(result.failed.length, 0);
    assert.equal(result.pending.length, 0);
  });

  it('evaluateCheckRuns uses the highest id when a check run is retried', () => {
    const result = evaluateCheckRuns(
      [
        { id: 100, name: 'Verify & Quality Gates', status: 'completed', conclusion: 'failure' },
        { id: 205, name: 'Verify & Quality Gates', status: 'completed', conclusion: 'success' }
      ],
      ['Verify & Quality Gates']
    );
    assert.equal(result.state, 'passed');
  });

  it('evaluateCheckRuns fails immediately on failure, cancelled, or skipped required checks', () => {
    for (const badConclusion of ['failure', 'cancelled', 'timed_out', 'action_required', 'skipped']) {
      const result = evaluateCheckRuns(
        [{ id: 1, name: 'Verify & Quality Gates', status: 'completed', conclusion: badConclusion }],
        ['Verify & Quality Gates']
      );
      assert.equal(result.state, 'failed', `conclusion=${badConclusion} must fail gate`);
    }
  });

  it('evaluateCheckRuns stays pending when a required check is missing or in_progress', () => {
    const missingRes = evaluateCheckRuns([], ['Verify & Quality Gates']);
    assert.equal(missingRes.state, 'pending');
    assert.deepEqual(missingRes.missing, ['Verify & Quality Gates']);

    const inProgressRes = evaluateCheckRuns(
      [{ id: 1, name: 'Verify & Quality Gates', status: 'in_progress', conclusion: null }],
      ['Verify & Quality Gates']
    );
    assert.equal(inProgressRes.state, 'pending');
    assert.equal(inProgressRes.pending.length, 1);
  });

  it('waitForCiChecks polls through missing -> in_progress -> success', async () => {
    let clock = 1000;
    const responses = [
      { ok: true, status: 200, json: async () => ({ check_runs: [] }) },
      {
        ok: true,
        status: 200,
        json: async () => ({
          check_runs: [{ id: 1, name: 'Verify & Quality Gates', status: 'in_progress', conclusion: null }]
        })
      },
      {
        ok: true,
        status: 200,
        json: async () => ({
          check_runs: [{ id: 1, name: 'Verify & Quality Gates', status: 'completed', conclusion: 'success' }]
        })
      }
    ];
    let calls = 0;
    const logs = [];
    const result = await waitForCiChecks(
      {
        repo: 'fmatar/archlens',
        sha: 'abc123',
        token: 'ghs_test',
        timeoutMs: 10000,
        pollIntervalMs: 500
      },
      {
        fetch: async () => responses[calls++],
        now: () => clock,
        sleep: async (ms) => {
          clock += ms;
        },
        log: (msg) => logs.push(msg)
      }
    );

    assert.equal(result.state, 'passed');
    assert.equal(calls, 3);
    assert.ok(logs.some((m) => m.includes('All required CI checks passed')));
  });

  it('waitForCiChecks fails fast when a required check fails without waiting for timeout', async () => {
    let slept = false;
    await assert.rejects(
      () =>
        waitForCiChecks(
          {
            repo: 'fmatar/archlens',
            sha: 'deadbeef',
            token: 'ghs_test',
            timeoutMs: 60000,
            pollIntervalMs: 5000
          },
          {
            fetch: async () => ({
              ok: true,
              status: 200,
              json: async () => ({
                check_runs: [
                  {
                    id: 9,
                    name: 'Verify & Quality Gates',
                    status: 'completed',
                    conclusion: 'failure',
                    html_url: 'https://github.com/fmatar/archlens/runs/9'
                  }
                ]
              })
            }),
            sleep: async () => {
              slept = true;
            },
            log: () => {}
          }
        ),
      /Required CI check\(s\) failed for deadbeef: Verify & Quality Gates=failure/
    );
    assert.equal(slept, false);
  });

  it('waitForCiChecks retries transient 5xx responses and rejects on timeout', async () => {
    let clock = 0;
    let calls = 0;
    await assert.rejects(
      () =>
        waitForCiChecks(
          {
            repo: 'fmatar/archlens',
            sha: 'slow123',
            token: 'ghs_test',
            timeoutMs: 1000,
            pollIntervalMs: 400
          },
          {
            fetch: async () => {
              calls++;
              return calls === 1
                ? { ok: false, status: 503 }
                : {
                    ok: true,
                    status: 200,
                    json: async () => ({
                      check_runs: [{ id: 1, name: 'Verify & Quality Gates', status: 'queued' }]
                    })
                  };
            },
            now: () => clock,
            sleep: async (ms) => {
              clock += ms;
            },
            log: () => {}
          }
        ),
      /Timed out after 1000ms waiting for CI checks on slow123/
    );
    assert.ok(calls >= 3);
  });

  it('waitForCiChecks rejects non-retryable HTTP errors and missing credentials', async () => {
    await assert.rejects(
      () => waitForCiChecks({ repo: 'a/b', sha: '1', token: '' }),
      /GitHub token is required/
    );
    await assert.rejects(
      () =>
        waitForCiChecks(
          { repo: 'a/b', sha: '1', token: 'tok' },
          { fetch: async () => ({ ok: false, status: 404 }), log: () => {} }
        ),
      /GitHub Check Runs API returned HTTP 404/
    );
  });

  it('parseArgs parses multiple --check flags and numeric options', () => {
    const parsed = parseArgs([
      '--repo',
      'fmatar/archlens',
      '--sha',
      'abc',
      '--check',
      'Verify & Quality Gates',
      '--check',
      'GitHub Action Self-Test (uses ./)',
      '--timeout-ms',
      '30000',
      '--poll-ms',
      '1000'
    ]);
    assert.equal(parsed.repo, 'fmatar/archlens');
    assert.equal(parsed.sha, 'abc');
    assert.deepEqual(parsed.requiredChecks, [
      'Verify & Quality Gates',
      'GitHub Action Self-Test (uses ./)'
    ]);
    assert.equal(parsed.timeoutMs, 30000);
    assert.equal(parsed.pollIntervalMs, 1000);
  });
});
