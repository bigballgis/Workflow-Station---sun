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
  it('rejects another writer snapshot after Save instead of stamping its newer version onto old values', async () => {
    const row = { key: 'A', amount: 7, _wsRowVersion: 1 }
    const baseline = ref({ 'dw:entries': [{ ...row }] })
    const autosave = createSubTableAutosave({ formReadOnly: ref(false), isCompletedTask: ref(false),
      isMiSubTaskMode: ref(false), effectiveTaskId: ref('task-1'), subTableBindings: ref([
        { bindingId: 1, tableName: 'entries', primaryKeyFields: ['key'], data: [row] }]),
      formData: ref({ __subTables__: { 'dw:entries': [row] } }), taskFormDTO: ref(null),
      loadedSubTableBaseline: baseline, buildSubTableSubmitPayload: () => ({ formData: {}, subTableBindingScopes: [] }) })
    getTaskFormData.mockResolvedValue({ fieldValues: { __subTables__: {
      'dw:entries': [{ key: 'A', amount: 8, _wsRowVersion: 3 }],
    } } })
    const saved = { formData: { __subTables__: { 'dw:entries': [{ ...row }] } }, subTableBindingScopes: [
      { bindingId: '1', storeKey: 'dw:entries', rowKeys: [{ key: 'A' }], emptied: false },
    ] }
    await expect(autosave.refreshSavedSubTableVersions(saved)).rejects.toThrow('changed after this save')
    expect(row).toEqual({ key: 'A', amount: 7, _wsRowVersion: 1 })
    expect(baseline.value['dw:entries'][0]._wsRowVersion).toBe(1)
    getTaskFormData.mockResolvedValue({ fieldValues: { __subTables__: {
      'dw:entries': [{ key: 'A', amount: 7, _wsRowVersion: 2 }],
    } } })
    await autosave.refreshSavedSubTableVersions(saved)
    expect(row._wsRowVersion).toBe(2)
  })
  it('retains an unsaved draft, blocks submission and clears the failure only after a successful retry', async () => {
    const error = new Error('403 write rejected')
    submitTaskForm.mockRejectedValueOnce(error).mockResolvedValue(undefined)
    const formData = ref({ __subTables__: { 'dw:entries': [{ key: 'A', amount: 2 }] } })
    const autosave = createSubTableAutosave({ formReadOnly: ref(false), isCompletedTask: ref(false),
      isMiSubTaskMode: ref(false), effectiveTaskId: ref('task-1'), subTableBindings: ref([{}]),
      formData, taskFormDTO: ref(null), loadedSubTableBaseline: ref({}),
      buildSubTableSubmitPayload: () => ({ formData: formData.value, subTableBindingScopes: [] }), delayMs: 0 })
    autosave.scheduleSubTableAutosave()
    await vi.waitFor(() => expect(submitTaskForm).toHaveBeenCalledTimes(1))
    await autosave.pauseSubTableAutosave()
    expect(autosave.saveError.value).toBe(error)
    await expect(autosave.syncSubTableRowVersionsBeforeSubmit()).rejects.toBe(error)
    expect(getTaskFormData).not.toHaveBeenCalled()
    expect(formData.value.__subTables__['dw:entries'][0].amount).toBe(2)
    autosave.retrySubTableAutosave()
    await vi.waitFor(() => expect(submitTaskForm).toHaveBeenCalledTimes(2))
    await autosave.pauseSubTableAutosave()
    expect(autosave.saveError.value).toBe(null)
  })
  it('serializes a second save behind the first request and its version refresh', async () => {
    let finishFirst: () => void = () => {}
    submitTaskForm.mockImplementationOnce(() => new Promise<void>(resolve => { finishFirst = resolve }))
      .mockResolvedValue(undefined)
    getTaskFormData.mockResolvedValue({ fieldValues: { __subTables__: {} } })
    const build = vi.fn(() => ({ formData: {}, subTableBindingScopes: [] }))
    const autosave = createSubTableAutosave({ formReadOnly: ref(false), isCompletedTask: ref(false),
      isMiSubTaskMode: ref(false), effectiveTaskId: ref('task-1'), subTableBindings: ref([]),
      formData: ref({}), taskFormDTO: ref(null), loadedSubTableBaseline: ref({}),
      buildSubTableSubmitPayload: build, delayMs: 0 })
    autosave.scheduleSubTableAutosave()
    await vi.waitFor(() => expect(submitTaskForm).toHaveBeenCalledTimes(1))
    autosave.scheduleSubTableAutosave()
    await vi.waitFor(() => expect(build).toHaveBeenCalledTimes(1))
    finishFirst()
    await vi.waitFor(() => expect(submitTaskForm).toHaveBeenCalledTimes(2))
    await autosave.pauseSubTableAutosave()
  })
  beforeEach(() => {
    submitTaskForm.mockReset()
    getTaskFormData.mockReset()
  })

  it('waits for a failing autosave and refuses to submit the unsaved row', async () => {
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
        subTableBindingScopes: [{ bindingId: '50705', storeKey: 'dw:p0_dual_file', rowKeys: [], emptied: false }],
      }),
      delayMs: 0,
    })

    autosave.scheduleSubTableAutosave()
    await vi.waitFor(() => expect(submitTaskForm).toHaveBeenCalledTimes(1))
    const syncing = autosave.syncSubTableRowVersionsBeforeSubmit()
    const blocked = expect(syncing).rejects.toThrow('409')
    rejectSave(new Error('409'))
    await blocked

    expect(row).toEqual({ id: 'F1', title: 'edited', _wsRowVersion: 1 })
    expect(getTaskFormData).not.toHaveBeenCalled()
    expect(submitTaskForm).toHaveBeenCalledTimes(1)
  })

  it('keeps the read version when a pending edit goes directly to Complete', async () => {
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
        subTableBindingScopes: [{ bindingId: '50705', storeKey: 'dw:p0_dual_file', rowKeys: [], emptied: false }],
      }),
      delayMs: 10_000,
    })

    autosave.scheduleSubTableAutosave()
    await autosave.syncSubTableRowVersionsBeforeSubmit()

    expect(submitTaskForm).not.toHaveBeenCalled()
    expect(getTaskFormData).not.toHaveBeenCalled()
    expect(row._wsRowVersion).toBe(1)
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
