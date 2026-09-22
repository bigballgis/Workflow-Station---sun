import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

const submitTaskForm = vi.fn()
const getTaskFormData = vi.fn()

vi.mock('@/api/processForm', () => ({
  submitTaskForm: (...args: unknown[]) => submitTaskForm(...args),
  getTaskFormData: (...args: unknown[]) => getTaskFormData(...args),
}))

import { createSubTableAutosave } from '../subTableAutosave'

describe('syncSubTableRowVersionsBeforeSubmit', () => {
  beforeEach(() => {
    submitTaskForm.mockReset()
    getTaskFormData.mockReset()
  })

  it('waits for a failing autosave, then copies the server version onto the open row', async () => {
    let rejectSave: (error: Error) => void = () => {}
    submitTaskForm.mockReturnValue(new Promise((_resolve, reject) => {
      rejectSave = reject
    }))
    getTaskFormData.mockResolvedValue({
      fieldValues: {
        __subTables__: {
          'dw:p0_dual_file': [{ id: 'F1', title: 'server', _wsRowVersion: 4 }],
        },
      },
    })
    const row = { id: 'F1', title: 'edited', _wsRowVersion: 1 }
    const autosave = createSubTableAutosave({
      formReadOnly: ref(false),
      isCompletedTask: ref(false),
      isMiSubTaskMode: ref(false),
      effectiveTaskId: ref('task-1'),
      subTableBindings: ref([{
        bindingId: 50705,
        tableName: 'p0_dual_file',
        designerTableName: 'p0_dual_file',
        primaryKeyFields: ['id'],
        data: [row],
      }]),
      formData: ref({ __subTables__: { 'dw:p0_dual_file': [{ id: 'F1', title: 'edited', _wsRowVersion: 1 }] } }),
      taskFormDTO: ref(null),
      loadedSubTableBaseline: ref({ 'dw:p0_dual_file': [{ id: 'F1', _wsRowVersion: 1 }] }),
      buildSubTableSubmitPayload: () => ({
        formData: { __subTables__: {} },
        subTableBindingScopes: [{ bindingId: '50705' }],
      }),
      delayMs: 0,
    })

    autosave.scheduleSubTableAutosave()
    await vi.waitFor(() => expect(submitTaskForm).toHaveBeenCalledTimes(1))
    const syncing = autosave.syncSubTableRowVersionsBeforeSubmit()
    rejectSave(new Error('409'))
    await syncing

    expect(row).toEqual({ id: 'F1', title: 'edited', _wsRowVersion: 4 })
    expect(submitTaskForm).toHaveBeenCalledTimes(1)
  })

  it('drops a not-yet-fired autosave and still adopts the server version', async () => {
    getTaskFormData.mockResolvedValue({
      fieldValues: {
        __subTables__: {
          'dw:p0_dual_file': [{ id: 'F1', _wsRowVersion: 4 }],
        },
      },
    })
    const row = { id: 'F1', title: 'edited', _wsRowVersion: 1 }
    const autosave = createSubTableAutosave({
      formReadOnly: ref(false),
      isCompletedTask: ref(false),
      isMiSubTaskMode: ref(false),
      effectiveTaskId: ref('task-1'),
      subTableBindings: ref([{
        bindingId: 50705,
        tableName: 'p0_dual_file',
        designerTableName: 'p0_dual_file',
        primaryKeyFields: ['id'],
        data: [row],
      }]),
      formData: ref({}),
      taskFormDTO: ref(null),
      loadedSubTableBaseline: ref({}),
      buildSubTableSubmitPayload: () => ({
        formData: {},
        subTableBindingScopes: [{ bindingId: '50705' }],
      }),
      delayMs: 10_000,
    })

    autosave.scheduleSubTableAutosave()
    await autosave.syncSubTableRowVersionsBeforeSubmit()

    expect(submitTaskForm).not.toHaveBeenCalled()
    expect(row._wsRowVersion).toBe(4)
    expect(row.title).toBe('edited')
  })

  it('refresh copies the server version and does not replace page rows with the store', async () => {
    const pageRow = { id: 'F1', title: 'page', case_id: 'C1', _wsRowVersion: 1 }
    const bindings = ref([{
      bindingId: 50705,
      tableName: 'p0_dual_file',
      designerTableName: 'p0_dual_file',
      primaryKeyFields: ['id'],
      data: [pageRow],
    }])
    getTaskFormData.mockResolvedValue({
      fieldValues: {
        __subTables__: {
          'dw:p0_dual_file': [
            { id: 'F1', title: 'server', case_id: 'C1', _wsRowVersion: 4 },
            { id: 'F2', title: 'sibling', case_id: 'C2', _wsRowVersion: 2 },
          ],
        },
      },
    })
    const autosave = createSubTableAutosave({
      formReadOnly: ref(false),
      isCompletedTask: ref(false),
      isMiSubTaskMode: ref(false),
      effectiveTaskId: ref('task-1'),
      subTableBindings: bindings,
      formData: ref({ __subTables__: { 'dw:p0_dual_file': [pageRow] } }),
      taskFormDTO: ref(null),
      loadedSubTableBaseline: ref({}),
      buildSubTableSubmitPayload: () => ({ formData: {}, subTableBindingScopes: [] }),
    })

    await autosave.refreshSavedSubTableVersions()

    expect(bindings.value[0].data).toEqual([
      { id: 'F1', title: 'page', case_id: 'C1', _wsRowVersion: 4 },
    ])
    expect(pageRow.title).toBe('page')
  })
})
