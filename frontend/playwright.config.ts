import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, devices } from '@playwright/test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const port = Number(process.env.ARCHLENS_E2E_PORT || 58088);
const token = process.env.ARCHLENS_TOKEN || crypto.randomBytes(24).toString('hex');
process.env.ARCHLENS_TOKEN = token;
const jarPath = path.join(repoRoot, 'backend/target/quarkus-app/quarkus-run.jar');

export default defineConfig({
  testDir: './e2e',
  testMatch: /.*\.spec\.ts/,
  timeout: 20000,
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    extraHTTPHeaders: {
      'X-Archlens-Token': token
    },
    headless: true
  },
  webServer: {
    command: `java -Dquarkus.http.host=127.0.0.1 -Dquarkus.http.port=${port} -jar "${jarPath}"`,
    cwd: repoRoot,
    url: `http://127.0.0.1:${port}/api/version`,
    reuseExistingServer: !process.env.CI,
    timeout: 30000,
    env: {
      ...process.env,
      ARCHLENS_TOKEN: token
    }
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
