/**
 * MI / multi-instance sub-table regression suite (unit phase).
 * Full gate: cd frontend && npm run regression:mi  (unit + Playwright screenshots)
 * Mapping: frontend/scripts/mi-regression-scenarios.mjs, user-portal/MI_REGRESSION.md
 * See .cursor/rules/performance-change-safety.mdc
 */
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

const MI_REGRESSION_FILES = [
  'src/composables/tasks/__tests__/linkFormMiIsolation.test.ts',
  'src/composables/tasks/__tests__/miSubProcessScope.test.ts',
  'src/composables/tasks/__tests__/subTableRowMetaFields.predicates.test.ts',
  'src/composables/tasks/__tests__/subTableRowMetaFields.merge.test.ts',
  'src/composables/tasks/__tests__/mergeSubTableRowsMiMerge.test.ts',
  'src/composables/tasks/__tests__/mcyInitiatorMyRequest.test.ts',
  'src/composables/tasks/__tests__/subForm2CarryForward.test.ts',
  'src/composables/tasks/__tests__/miCollectionIdIdwScrub.test.ts',
  'src/composables/tasks/__tests__/dropSubsumedSubTableRows.test.ts',
  'src/utils/__tests__/inlineFormBelowTableRuntime.test.ts',
  'src/utils/__tests__/subTableRowRuntime.test.ts',
]

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    include: MI_REGRESSION_FILES,
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      // Keep in sync with vite.config.ts — cross-app shared TS sources.
      '@platform-shared': resolve(__dirname, '../shared/src'),
    },
  },
})
