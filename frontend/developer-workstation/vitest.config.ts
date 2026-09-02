import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.{test,spec}.{js,ts}'],
    // 覆盖率只出报告、不设阈值（thresholds）。目的不是提高数字，而是让覆盖率
    // **下降**变得可见 —— 此前没有任何测量，一个 PR 加 500 行零测试代码看不出来。
    // 用 `pnpm test:coverage` 生成；CI 归档产物，不作为门禁。
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,vue}'],
      exclude: [
        'src/**/*.{test,spec}.{ts,js}',
        'src/**/__tests__/**',
        'src/i18n/locales/**',
        'src/main.ts',
        'src/**/*.d.ts',
      ],
    },
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
