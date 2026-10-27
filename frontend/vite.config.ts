import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The SPA and API share one origin: in development Vite proxies /api and
// /actuator to Spring Boot; in production a reverse proxy does the same.
// This keeps session cookies first-party and avoids CORS entirely.
const backend = process.env.WATCHMARK_BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/actuator': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    // Interaction tests type character by character; leave headroom on slow runners.
    testTimeout: 15_000,
  },
})
