import { defineConfig, devices } from '@playwright/test'

/**
 * Browser-level verification runs against local Vite dev servers. The
 * `stubbed-api` project keeps the original network-stub conventions; the
 * `local-demo` project exercises the development-only local adapter. Neither
 * project exercises hosted Supabase/PostGIS, real email, object storage or AI
 * providers.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 7_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    channel: 'msedge',
    headless: true,
    viewport: devices['Desktop Chrome'].viewport,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'stubbed-api',
      testMatch: /phase10-workflows\.spec\.ts/,
    },
    {
      name: 'local-demo',
      testMatch: /demo-mode\.spec\.ts/,
      use: { baseURL: 'http://127.0.0.1:5174' },
    },
  ],
  webServer: [
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        VITE_API_BASE_URL: 'http://127.0.0.1:5173/api/v1',
        VITE_DEMO_MODE: 'false',
      },
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5174 --strictPort',
      url: 'http://127.0.0.1:5174',
      reuseExistingServer: false,
      timeout: 120_000,
      env: {
        VITE_API_BASE_URL: 'http://127.0.0.1:5173/api/v1',
        VITE_DEMO_MODE: 'true',
      },
    },
  ],
})
