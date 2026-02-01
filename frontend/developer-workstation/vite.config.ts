import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
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
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3002,
    proxy: {
      // Auth API routes to developer-workstation
      '/api/v1/auth': {
        target: 'http://localhost:8094',
        changeOrigin: true,
        rewrite: (path) => '/auth' + path.substring('/api/v1/auth'.length)
      },
      // Admin Center API (departments, virtual-groups)
      '/api/admin-center': {
        target: 'http://localhost:8092',
        changeOrigin: true,
        rewrite: (path) => path.replace('/api/admin-center', '/api/v1/admin')
      },
      // Function units, icons, versions, export/import - developer-workstation
      '/api/v1/function-units': {
        target: 'http://localhost:8094',
        changeOrigin: true
      },
      '/api/v1/icons': {
        target: 'http://localhost:8094',
        changeOrigin: true
      },
      '/api/v1/versions': {
        target: 'http://localhost:8094',
        changeOrigin: true
      },
      '/api/v1/export': {
        target: 'http://localhost:8094',
        changeOrigin: true
      },
      '/api/v1/import': {
        target: 'http://localhost:8094',
        changeOrigin: true
      },
      // Default: all other /api requests go to developer-workstation
      '/api': {
        target: 'http://localhost:8094',
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
