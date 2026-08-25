import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  base: '/admin/',
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia', 'vue-i18n'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      // Cross-app shared TS sources (frontend/shared/src) — single source for logic that
      // must stay identical across portal/DW/admin (tableFkRuntime, pkGenerationConfig, ...).
      '@platform-shared': resolve(__dirname, '../shared/src')
    },
    // frontend/shared is not a package: bare imports inside its SFCs (vue-i18n, element-plus
    // icons, ElementPlusResolver's `element-plus/es`) cannot node-resolve upward from there.
    // Dedupe pins them to this app's copy (same as user-portal vite.config.ts).
    dedupe: ['vue', 'vue-i18n', 'element-plus', '@element-plus/icons-vue']
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
  server: {
    port: 3000,
    // Allow the dev server to serve ../shared sources (outside the app root).
    fs: { allow: [resolve(__dirname, '..')] },
    proxy: {
      '/api/v1/auth': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => '/api/v1/admin/auth' + path.substring('/api/v1/auth'.length)
      },
      '/api/v1/admin': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      '/api/v1': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => '/api/v1/admin' + path.substring('/api/v1'.length)
      }
    }
  }
})
