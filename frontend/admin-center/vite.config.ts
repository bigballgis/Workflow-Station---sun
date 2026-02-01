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
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api/v1/auth': {
        target: 'http://localhost:8092',
        changeOrigin: true,
        rewrite: (path) => '/api/v1/admin/auth' + path.substring('/api/v1/auth'.length)
      },
      '/api/v1/admin': {
        target: 'http://localhost:8092',
        changeOrigin: true
      },
      '/api/v1': {
        target: 'http://localhost:8092',
        changeOrigin: true,
        rewrite: (path) => '/api/v1/admin' + path.substring('/api/v1'.length)
      }
    }
  }
})
