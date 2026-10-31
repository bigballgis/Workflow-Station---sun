import { it, expect } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailMiIsolation } from '../useTaskDetailMiIsolation'
import type { TaskDetailCtx } from '../context'

it('retains the configured main key outside the canvas for case-level binding projection', async () => {
  const formData = ref<Record<string, unknown>>({ case_key: 'Case-1', title: 'Synthetic case' })
  const ctx = {
    subTableBindings: ref([]), previousForms: ref([]),
    primaryTableBinding: ref({ primaryKeyFields: ['case_key'] }),
    taskForm: { formData, getCurrentFormFieldKeys: () => ['title'] },
    resolveCurrentMiParticipantRowIdFromTaskVars: () => 'Transaction-A',
    warnMiCollectionPrimaryKeyIfNeeded: () => {},
    seedMiParticipantScopedBindingForeignKeys: async () => {},
    miCollectionPrimaryKeyFields: () => ['transaction_key'],
    getPrimaryTableFieldNames: () => new Set(['case_key', 'title']),
    rebuildIsolatedSubTablesPayload: () => ({}),
  } as unknown as TaskDetailCtx
  await createTaskDetailMiIsolation(ctx).isolateMiSubTaskData({ variables: formData.value })
  expect(formData.value.case_key).toBe('Case-1')
})
