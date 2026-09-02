import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const HERMES_EVENT_CONFIG = resolve(__dirname, 'src/components/designer/HermesEventConfig.vue')
const HERMES_FN_CONFIG = resolve(__dirname, 'src/components/designer/HermesFnConfig.vue')

/** Redirect fc-designer stock panels to Hermes overrides (Vite string alias misses relative imports). */
function hermesDesignerOverridePlugin(): Plugin {
  return {
    name: 'hermes-designer-overrides',
    enforce: 'pre',
    resolveId(source, importer) {
      if (!importer) return null
      const fromDesigner = importer.replace(/\\/g, '/').includes('@form-create/designer')
      if (!fromDesigner) return null
      const isEventConfig =
        source === './EventConfig.vue'
        || source.endsWith('/components/EventConfig.vue')
        || source === '@form-create/designer/src/components/EventConfig.vue'
      if (isEventConfig) return HERMES_EVENT_CONFIG
      const isFnConfig =
        source === './FnConfig.vue'
        || source.endsWith('/components/FnConfig.vue')
        || source === '@form-create/designer/src/components/FnConfig.vue'
      if (isFnConfig) return HERMES_FN_CONFIG
      return null
    },
  }
}

export default defineConfig({
  base: '/dev/',
  /** Prebundle would bake stock EventConfig/FnConfig and ignore hermesDesignerOverridePlugin. */
  optimizeDeps: {
    exclude: ['@form-create/designer'],
  },
  plugins: [
    hermesDesignerOverridePlugin(),
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: [
      { find: '@', replacement: resolve(__dirname, 'src') },
      // Cross-app shared TS sources (frontend/shared/src) — single source for logic that
      // must stay identical across portal/DW/admin (tableFkRuntime, pkGenerationConfig, ...).
      { find: '@platform-shared', replacement: resolve(__dirname, '../shared/src') },
      /**
       * Package `main` points at dist/index.es.js (stock EventConfig/FnConfig baked in).
       * Use source entry so hermesDesignerOverridePlugin can replace panel components.
       */
      {
        find: /^@form-create\/designer$/,
        replacement: resolve(__dirname, 'node_modules/@form-create/designer/src/index.js'),
      },
    ],
    // frontend/shared SFCs import vue-i18n / element-plus icons; pin them to this app's copy.
    dedupe: ['vue', 'vue-i18n', 'element-plus', '@element-plus/icons-vue'],
  },
  server: {
    port: 3002,
    // Allow the dev server to serve ../shared sources (outside the app root).
    fs: { allow: [resolve(__dirname, '..')] },
    proxy: {
      // Auth：保留完整路径 /api/v1/auth/*（与 Spring context-path 一致）
      '/api/v1/auth': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      // Admin Center API (departments, virtual-groups)
      '/api/admin-center': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (path) => path.replace('/api/admin-center', '/api/v1/admin')
      },
      // Function units, icons, versions, export/import - developer-workstation
      '/api/v1/function-units': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      '/api/v1/icons': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      '/api/v1/versions': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      '/api/v1/export': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      '/api/v1/import': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      // Default: all other /api requests go to developer-workstation
      '/api': {
        target: 'http://localhost:8083',
        changeOrigin: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/variables.scss" as *;`
      }
    }
  }
})
