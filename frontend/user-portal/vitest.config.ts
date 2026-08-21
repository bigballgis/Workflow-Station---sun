import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.{test,spec}.{js,ts}'],
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      // Keep in sync with vite.config.ts — cross-app shared TS sources.
      '@platform-shared': resolve(__dirname, '../shared/src'),
    },
    // Keep in sync with vite.config.ts — shared SFCs need bare imports pinned to this app.
    dedupe: ['vue', 'vue-i18n', 'element-plus', '@element-plus/icons-vue'],
  },
})
