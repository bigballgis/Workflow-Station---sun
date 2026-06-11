/**
 * MI regression: unit test file ↔ Playwright screenshot scenario mapping.
 * See .cursor/rules/performance-change-safety.mdc
 */
export const MI_REGRESSION_SCENARIOS = [
  {
    id: '1441-myrequest-details',
    issue: '#1441',
    unitTests: ['mcyInitiatorMyRequest.test.ts', 'mergeSubTableRowsMiMerge.test.ts', 'dropSubsumedSubTableRows.test.ts'],
    script: 'verify-myrequest-details-modal.mjs',
    screenshots: ['app-*-details-060-unprocessed.png', 'app-*-details-061-filled.png'],
  },
  {
    id: '1440-sex-toggle-isolation',
    issue: '#1440',
    unitTests: ['linkFormMiIsolation.test.ts', 'inlineFormBelowTableRuntime.test.ts'],
    script: 'verify-sex-toggle-isolation.mjs',
    screenshots: ['task-6c6c-sex-before.png', 'task-6c6c-sex-after.png'],
  },
  {
    id: '1438-attachment-rows',
    issue: '#1438',
    unitTests: ['subTableRowMetaFields.test.ts'],
    script: 'verify-mi-attachment-rows.mjs',
    screenshots: ['task-093962-attachment-table.png', 'task-093962-subtask-grid.png'],
  },
  {
    id: '1439-subform2-carry-forward',
    issue: '#1439',
    unitTests: ['subForm2CarryForward.test.ts', 'inlineFormBelowTableRuntime.test.ts'],
    script: 'verify-subform2-people-carry-forward.mjs',
    screenshots: ['task-75d662-subform2-people.png'],
  },
  {
    id: '1435-people-inline-uuid',
    issue: '#1435',
    unitTests: ['subTableRowRuntime.test.ts', 'linkFormMiIsolation.test.ts'],
    script: 'verify-mi-people-inline-uuid.mjs',
    screenshots: ['task-09367-people-inline-uuid.png'],
  },
  {
    id: 'mi-assignee-subtask-slice',
    issue: 'miSubProcessScope',
    unitTests: ['miSubProcessScope.test.ts'],
    script: 'verify-mi-assignee-subtask-slice.mjs',
    screenshots: ['task-6c6c-assignee-subtask-slice.png'],
  },
]

export const MI_REGRESSION_SCRIPT_ORDER = MI_REGRESSION_SCENARIOS.map(s => s.script)
