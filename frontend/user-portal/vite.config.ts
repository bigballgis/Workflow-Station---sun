import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const isTest = process.env.VITEST === 'true'

export default defineConfig({
  base: '/portal/',
  plugins: [
    vue(),
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
      '@': resolve(__dirname, 'src'),
      // Cross-app shared TS sources (frontend/shared/src) — single source for logic that
      // must stay identical across portal/DW/admin (tableFkRuntime, pkGenerationConfig, ...).
      '@platform-shared': resolve(__dirname, '../shared/src')
    },
    // frontend/shared is not a package: bare imports inside its SFCs (vue-i18n, element-plus
    // icons) cannot node-resolve upward from there. Dedupe pins them to this app's copy
    // (plugin-vue already does this for 'vue' itself).
    dedupe: ['vue', 'vue-i18n', 'element-plus', '@element-plus/icons-vue']
  },
  // sockjs-client (pulled in by the STOMP/WebSocket client) is CommonJS written for a Node/webpack
  // world and dereferences the bare identifier `global` at module load. `vite build` shims it, the
  // dev server does not — so `npm run dev` died on "ReferenceError: global is not defined" before
  // the router could even start (blank page, no portal dev server at all).
  // The define must sit on optimizeDeps.esbuildOptions: sockjs-client is PRE-BUNDLED, and the
  // top-level `define` is only applied to app sources, never to deps in node_modules/.vite/deps.
  optimizeDeps: {
    include: ['pdfjs-dist', 'xlsx', 'utif', 'docx-preview', 'pptx-preview'],
    esbuildOptions: {
      define: { global: 'globalThis' }
    }
  },
  server: {
    port: 3001,
    // Allow the dev server to serve ../shared sources (outside the app root).
    fs: { allow: [resolve(__dirname, '..')] },
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
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          'echarts': ['echarts', 'vue-echarts'],
          'bpmn': ['bpmn-js']
        }
      }
    }
  }
})
