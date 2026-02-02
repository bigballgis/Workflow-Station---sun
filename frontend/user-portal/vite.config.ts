import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import fs from 'fs'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    {
      name: 'favicon-fallback',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url === '/favicon.ico') {
            const svgPath = resolve(__dirname, 'public/logo.svg')
            if (fs.existsSync(svgPath)) {
              res.setHeader('Content-Type', 'image/svg+xml')
              res.end(fs.readFileSync(svgPath))
              return
            }
          }
          next()
        })
      }
    },
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
    port: 3001,
    proxy: {
      '/api/v1/auth': {
        target: 'http://localhost:8093',
        changeOrigin: true,
        rewrite: (path) => '/api/portal/auth' + path.substring('/api/v1/auth'.length)
      },
      '/api/portal': {
        target: 'http://localhost:8093',
        changeOrigin: true
      },
      '/api/v1/admin': {
        target: 'http://localhost:8092',
        changeOrigin: true
      },
      '/api/admin-center': {
        target: 'http://localhost:8092',
        changeOrigin: true,
        rewrite: (path) => '/api/v1/admin' + path.substring('/api/admin-center'.length)
      }
    }
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
