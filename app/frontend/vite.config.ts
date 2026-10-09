import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The production build goes straight into the Spring Boot classpath (target/classes/static), so the
// packaged application serves the front end itself. In development `npm run dev` proxies the API.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'node',
  },
})
