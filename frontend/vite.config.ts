import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import tailwindcss from '@tailwindcss/vite';

// The workbench API requires a per-session token (#225). For `npm run dev` on :5173, start the
// backend with ARCHLENS_TOKEN=<value> and export the same value here; the proxy adds the header.
const archlensToken = process.env.ARCHLENS_TOKEN;

export default defineConfig({
  plugins: [tailwindcss(), svelte()],
  resolve: {
    conditions: ['browser']
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true,
        ...(archlensToken ? { headers: { 'X-Archlens-Token': archlensToken } } : {})
      }
    }
  },
  // @ts-ignore
  test: {
    environment: 'jsdom',
    exclude: ['**/node_modules/**', '**/e2e/**', '**/dist/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      thresholds: {
        lines: 45,
        functions: 50,
        branches: 30,
        statements: 45
      }
    }
  }
});
