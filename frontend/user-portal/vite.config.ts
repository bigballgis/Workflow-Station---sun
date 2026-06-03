import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import federation from '@originjs/vite-plugin-federation'

const isTest = process.env.VITEST === 'true'

export default defineConfig({
  base: '/portal/',
  plugins: [
    vue(),
    federation({
      name: 'user_portal',
      shared: ['vue', 'pinia', 'vue-router']
    }),
    AutoImport({
      resolvers: [ElementPlusResolver({ importStyle: isTest ? false : 'css' })],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: isTest ? false : 'css' })],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3001,
    proxy: {
      // Unified SSO UI (`frontend/login`, base `/login/`, default dev port 3010).
      // Without this, opening portal on :3001 redirects to /login/ on the same origin and Vite
      // serves the portal SPA → router bounce /login → redirect loop or blank login.
      '/login': {
        target: 'http://localhost:3010',
        changeOrigin: true
      },
      '/api/v1/auth': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (path) => '/api/portal/auth' + path.substring('/api/v1/auth'.length)
      },
      '/api/portal': {
        target: 'http://localhost:8082',
        changeOrigin: true
      },
      '/api/v1/admin': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/api/admin-center': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => '/api/v1/admin' + path.substring('/api/admin-center'.length)
      }
    }
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    css: true
  },
  build: {
    target: 'esnext'
  }
})
