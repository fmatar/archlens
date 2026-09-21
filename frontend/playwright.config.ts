import { defineConfig, devices } from '@playwright/test';

// ponytail: minimal robust e2e test configuration using system Chrome
export default defineConfig({
  testDir: './e2e',
  testMatch: /.*\.spec\.ts/,
  timeout: 15000,
  use: {
    baseURL: 'http://localhost:8088',
    channel: 'chrome', // uses host Mac Google Chrome without extra binary downloads
    headless: true,
  },
  projects: [
    {
      name: 'chrome',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
