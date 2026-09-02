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
  'src/composables/subTableField/__tests__/subTableLinkFormSelfBound.test.ts',
  'src/composables/tasks/__tests__/miSubProcessScope.test.ts',
  'src/composables/tasks/__tests__/subTableRowMetaFields.predicates.test.ts',
  'src/composables/tasks/__tests__/subTableRowMetaFields.merge.test.ts',
  'src/composables/tasks/__tests__/mergeSubTableRowsMiMerge.test.ts',
  'src/composables/tasks/__tests__/mergeMiCollectionSubTableRows.test.ts',
  'src/composables/tasks/__tests__/mcyInitiatorMyRequest.test.ts',
  'src/composables/tasks/__tests__/miDetailsFieldMapping.test.ts',
  'src/composables/tasks/__tests__/subForm2CarryForward.test.ts',
  'src/composables/tasks/__tests__/miCollectionIdIdwScrub.test.ts',
  'src/composables/tasks/__tests__/dropSubsumedSubTableRows.test.ts',
  'src/utils/__tests__/inlineFormBelowTableRuntime.test.ts',
  'src/utils/__tests__/subTableRowRuntime.test.ts',
  'src/composables/taskDetail/__tests__/miLinkChildNoPhantomRow.test.ts',
  'src/composables/taskDetail/__tests__/miNestedChildRowsSurviveSave.test.ts',
  // 参与者归属语义（跨子任务串行的第一道闸）——曾因不在门禁清单里而漏掉一次跨参与者行泄漏
  'src/composables/tasks/__tests__/miExpansionIdDesignerPk.test.ts',
  'src/composables/formRenderer/__tests__/saveWithRelationTableBindings.test.ts',
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
