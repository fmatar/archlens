import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

import {
  generateSessionToken,
  portFromUrl,
  sessionFilePath,
  readSessionToken,
  writeSessionFile,
  resolveSessionToken,
  withAuth,
  withTokenQuery,
  isLoopbackHost,
  TOKEN_HEADER
} from '../src/session.js';

test('generateSessionToken returns distinct url-safe 256-bit tokens', () => {
  const a = generateSessionToken();
  const b = generateSessionToken();
  assert.notEqual(a, b);
  assert.match(a, /^[A-Za-z0-9_-]{43}$/);
});

test('portFromUrl extracts explicit and default ports', () => {
  assert.equal(portFromUrl('http://127.0.0.1:8088'), '8088');
  assert.equal(portFromUrl('http://example.com'), '80');
  assert.equal(portFromUrl('https://example.com/x'), '443');
  assert.equal(portFromUrl('not a url'), null);
});

test('session file round-trips with owner-only permissions', () => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-session-'));
  const file = writeSessionFile('9123', 'tok-abc', { homedir: home });
  assert.equal(file, sessionFilePath('9123', { homedir: home }));
  if (process.platform !== 'win32') {
    assert.equal(fs.statSync(file).mode & 0o777, 0o600);
  }
  assert.equal(readSessionToken('9123', { homedir: home }), 'tok-abc');
  assert.equal(readSessionToken('9124', { homedir: home }), null);
  assert.equal(readSessionToken(null, { homedir: home }), null);
  fs.writeFileSync(sessionFilePath('9125', { homedir: home }), '{not json');
  assert.equal(readSessionToken('9125', { homedir: home }), null);
});

test('resolveSessionToken prefers ARCHLENS_TOKEN, then the session file for the URL port', () => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'archlens-session-'));
  writeSessionFile('9200', 'from-file', { homedir: home });
  assert.equal(resolveSessionToken('http://127.0.0.1:9200', { env: { ARCHLENS_TOKEN: ' env-tok ' }, homedir: home }), 'env-tok');
  assert.equal(resolveSessionToken('http://127.0.0.1:9200', { env: {}, homedir: home }), 'from-file');
  assert.equal(resolveSessionToken('http://127.0.0.1:9201', { env: {}, homedir: home }), null);
});

test('withAuth adds the token header without dropping existing headers', () => {
  assert.equal(withAuth(undefined, null), undefined);
  const init = { method: 'POST', headers: { 'Content-Type': 'application/json' } };
  assert.equal(withAuth(init, ''), init);
  const authed = withAuth(init, 't');
  assert.equal(authed.method, 'POST');
  assert.equal(authed.headers['Content-Type'], 'application/json');
  assert.equal(authed.headers[TOKEN_HEADER], 't');
  assert.equal(init.headers[TOKEN_HEADER], undefined, 'input is not mutated');
  assert.deepEqual(withAuth(undefined, 't'), { headers: { [TOKEN_HEADER]: 't' } });
});

test('withTokenQuery builds the browser bootstrap URL', () => {
  assert.equal(withTokenQuery('http://127.0.0.1:8088', 'a b'), 'http://127.0.0.1:8088/?token=a+b');
  assert.equal(withTokenQuery('http://127.0.0.1:8088', null), 'http://127.0.0.1:8088');
  assert.equal(withTokenQuery('::bad', 't'), '::bad');
});

test('isLoopbackHost', () => {
  for (const h of ['127.0.0.1', 'localhost', '::1', '[::1]', '127.1.2.3']) assert.equal(isLoopbackHost(h), true, h);
  for (const h of ['0.0.0.0', '::', '192.168.0.2', '', undefined]) assert.equal(isLoopbackHost(h), false, String(h));
});
