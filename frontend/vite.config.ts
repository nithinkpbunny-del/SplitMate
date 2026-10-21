import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  // Reads the repository-root .env so BACKEND_URL can point the dev proxy at another port.
  const env = loadEnv(mode, path.resolve(import.meta.dirname, '..'), '')
  const backendUrl = env.BACKEND_URL || 'http://localhost:8090'

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': { target: backendUrl },
      },
    },
  }
})
