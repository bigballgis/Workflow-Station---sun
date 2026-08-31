import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    include: [
      'src/**/*.{test,spec}.{js,ts}',
      '../shared/src/**/*.{test,spec}.{js,ts}',
    ],
    // vitest 默认 5000ms 对本套件里的组件测试太紧：relationTablesQueryState /
    // delegationsSharedList / completedTasksHeader / applicationsQueryState 这类
    // mount() + 多次 flushPromises() 的用例，单独跑稳过，并入全量套件（167 文件并行）
    // 就会偶发超时，且每次失败的用例还不一样 —— 典型的负载相关抖动，不是测试有缺陷。
    // 开覆盖率后 v8 插桩进一步拖慢执行，抖动更明显。
    //
    // 这些用例不依赖计时行为（无 fake timer、不对耗时做断言），放宽超时不掩盖真实缺陷；
    // 真正挂死的用例仍会失败。取 60s 是为了让「跑覆盖率」这条最慢的路径也稳定。
    // 注意：delegationsSharedList.test.ts 与 PermissionRequestSharedList.test.ts 里
    // 各有一处硬编码的 `}, 15000)`，会覆盖此处配置 —— 那两条仍可能在覆盖率模式下抖动，
    // 属于用例自身要处理的事，不在本次改动范围内。
    testTimeout: 60000,
    hookTimeout: 60000,
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
  server: {
    fs: { allow: [resolve(__dirname, '..')] },
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
