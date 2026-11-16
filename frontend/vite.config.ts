import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Node's process, without adding @types/node for one line.
declare const process: { env: Record<string, string | undefined> }

// BACKEND_URL lets a second backend run beside the usual one, as the screenshot checks do.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8081'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Same paths as nginx, so the frontend behaves the same in dev and in containers.
    proxy: {
      '/api': backend,
      '/actuator/health': backend,
      // Google sign-in: the start link and Google's return address (DECISIONS.md, D-028).
      '/oauth2/authorization': backend,
      '/login/oauth2': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    // Playwright owns e2e/. Vitest runs only the component tests.
    include: ['src/**/*.test.{ts,tsx}', 'edge/**/*.test.ts'],
  },
})
