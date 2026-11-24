import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

const srcDir = fileURLToPath(new URL('./src', import.meta.url))
const prodDemoStub = fileURLToPath(new URL('./src/demo/prodStub.ts', import.meta.url))

// envDir points at the repository root so the single local .env supplies both
// Spring Boot and Vite variables. Secrets are never read by the frontend.
export default defineConfig(({ command, mode }) => ({
  plugins: [react(), tailwindcss()],
  envDir: fileURLToPath(new URL('..', import.meta.url)),
  resolve: {
    alias: [
      // Production builds redirect every app-level demo import to a no-op stub.
      // This removes the demo adapter, seeded credentials and demo UI entirely;
      // development and Vitest keep resolving the real demo modules.
      ...(command === 'build' && mode === 'production'
        ? [
            {
              find: /^(?:\.\.?\/)+demo\/(?:adapter|feature|DemoControls)$/,
              replacement: prodDemoStub,
            },
          ]
        : []),
      { find: '@', replacement: srcDir },
    ],
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
  },
}))
