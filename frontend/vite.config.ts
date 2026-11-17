/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The backend of OpsFlow. In development the browser talks only to this dev server, which passes
// every request under /api on to the backend, so the browser sees one origin and the backend needs
// no cross-origin rule. Set OPSFLOW_API_TARGET when the backend runs somewhere else.
const apiTarget = process.env.OPSFLOW_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        configure(proxy) {
          proxy.on('error', (_error, _request, response) => {
            if ('writeHead' in response && !response.headersSent && !response.writableEnded) {
              response.writeHead(502).end()
            }
          })
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    // Many tests drive whole pages of Ant Design in jsdom and run in parallel; 30 seconds leaves room on a busy machine.
    testTimeout: 30_000,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
  },
})
