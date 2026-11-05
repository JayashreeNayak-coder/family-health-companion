import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const backend = 'http://localhost:8081'

export default defineConfig({
  plugins: [react()],
  server: {
    // Same paths as nginx, so the frontend behaves the same in dev and in containers.
    proxy: {
      '/api': backend,
      '/actuator/health': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})
