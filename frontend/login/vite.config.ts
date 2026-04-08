import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: '/login/',
  resolve: {
    alias: { '@': resolve(__dirname, 'src') }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  },
  server: {
    port: 3010,
    proxy: {
      '/api/v1/admin': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
})
