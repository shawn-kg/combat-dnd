import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In development, Spring runs on :8080 and Vite forwards API calls (including SSE streams) to it.
// In production, Spring serves the built files itself, so there is one origin and no CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
