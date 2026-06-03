import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import federation from '@originjs/vite-plugin-federation'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    federation({
      name: 'workflow_mfe',
      filename: 'remoteEntry.js',
      exposes: { './App': './src/App.vue' },
      shared: []
    })
  ],
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
  build: { target: 'esnext', minify: true, cssCodeSplit: false }
})
