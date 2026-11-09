import { defineConfig } from '@playwright/test'

declare const process: { env: Record<string, string | undefined> }

/**
 * Screenshot checks for the redesign (docs/07; brought forward by DECISIONS.md D-023). They need
 * the backend and `npm run dev` running, and write images to e2e/screenshots/, which Git ignores.
 * They use synthetic data only, created through the API by each run.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.APP_URL ?? 'http://localhost:5173',
    colorScheme: 'light',
  },
  projects: [
    { name: 'phone', use: { browserName: 'chromium', viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } },
    { name: 'tablet', use: { browserName: 'chromium', viewport: { width: 768, height: 1024 } } },
    { name: 'desktop', use: { browserName: 'chromium', viewport: { width: 1280, height: 800 } } },
  ],
})
