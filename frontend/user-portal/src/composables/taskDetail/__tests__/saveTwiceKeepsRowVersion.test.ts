import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

const submitTaskForm = vi.fn()
const getTaskFormData = vi.fn()

vi.mock('@/api/processForm', () => ({
  submitTaskForm: (...args: unknown[]) => submitTaskForm(...args),
  getTaskFormData: (...args: unknown[]) => getTaskFormData(...args),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

import { useTaskForm } from '@/composables/tasks/useTaskForm'
import { createTaskDetailMiPersist } from '../useTaskDetailMiPersist'

function rowVersion(payload: { formData?: { __subTables__?: Record<string, unknown> } }): unknown {
  const rows = payload.formData?.__subTables__?.['dw:file_table']
  const row = Array.isArray(rows) ? rows[0] as { _wsRowVersion?: unknown } : null
  return row?._wsRowVersion
}

describe('non-MI Save clicked twice', () => {
  const sentVersions: unknown[] = []

  beforeEach(() => {
    sentVersions.length = 0
    submitTaskForm.mockReset()
    getTaskFormData.mockReset()
    submitTaskForm.mockImplementation(async (_taskId: unknown, payload: { formData?: { __subTables__?: Record<string, unknown> } }) => {
      sentVersions.push(rowVersion(payload))
    })
    getTaskFormData.mockResolvedValue({
      fieldValues: { __request_id: 'R1' },
      processFormRef: {
        fieldValues: {
          __subTables__: {
            'dw:file_table': [{ id: 'F1', title: 'server', _wsRowVersion: 5 }],
          },
        },
      },
    })
  })

  it('sends the server row version on the second save', async () => {
    const row = { id: 'F1', title: 'page', _wsRowVersion: 1 }
    const bindings = ref([
      {
        bindingId: 1,
        tableId: 10,
        tableName: 'file_table',
        designerTableName: 'file_table',
        bindingType: 'SUB',
        primaryKeyFields: ['id'],
        data: [row],
      },
      {
        bindingId: 2,
        tableId: 10,
        tableName: 'file_table',
        designerTableName: 'file_table',
        bindingType: 'SUB',
        filterFkFieldName: 'other_fk',
        filterFkRefTableId: 9,
        primaryKeyFields: ['id'],
        data: [row],
      },
    ])
    const taskFormDTO = ref(null)
    const taskForm = useTaskForm({
      subTableBindings: bindings,
      isMiSubTaskMode: ref(false),
      isCompletedTask: ref(false),
      effectiveTaskId: ref('task-1'),
      taskFormDTO,
      primaryTableBinding: ref({ tableId: 1, primaryKeyFields: ['id'] }),
    })
    const persist = createTaskDetailMiPersist({
      t: (key: string) => key,
      taskInfo: ref({}),
      effectiveTaskId: ref('task-1'),
      submitting: ref(false),
      subTableBindings: bindings,
      isMiSubTaskMode: ref(false),
      miSubProcessScope: ref(null),
      miFullSubTablesSnapshotRef: ref(null),
      miFillDialogVisible: ref(false),
      miFillDialogData: ref({}),
      miFillSubTableBindings: ref([]),
      miFilled: ref(false),
      miFillDialogReadOnly: ref(false),
      primaryTableFieldNames: ref(new Set<string>()),
      taskFormDTO,
      taskForm,
    } as never)

    await persist.saveCurrentTaskFormWithMiPersist()
    await persist.saveCurrentTaskFormWithMiPersist()

    expect(sentVersions).toEqual([1, 5])
  })
})
