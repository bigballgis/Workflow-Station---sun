import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import federation from '@originjs/vite-plugin-federation'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    federation({
      name: 'notification_mfe',
      filename: 'remoteEntry.js',
      exposes: {
        './App': './src/App.vue'
      },
      shared: []  // Self-contained bundle — no shared deps with host
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    target: 'esnext',
    minify: true,
    cssCodeSplit: false
  }
})
