import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useTaskForm } from '../useTaskForm'
import { getTaskFormData, submitTaskForm } from '@/api/processForm'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/api/processForm', () => ({ submitTaskForm: vi.fn(), getTaskFormData: vi.fn() }))

describe('batch membership submit payload', () => {
  it('protected assignment without batch runtime does not submit or erase lookup caches', async () => {
    const rows = [{ entry_key: 'A', amount: 1, _wsRowVersion: 1 }]
    const lookup = [{ directory_key: 'U1', display_name: 'Display only' }]
    const form = useTaskForm({ subTableBindings: ref([
      { bindingId: 2, tableId: 20, tableName: 'renamed_lines', bindingMode: 'EDITABLE', primaryKeyFields: ['entry_key'], data: rows },
    ]), assignmentTracked: ref(true), isMiSubTaskMode: ref(false), isCompletedTask: ref(false), effectiveTaskId: ref('task-1'),
    taskFormDTO: ref({ subTableBatchRuntimeEnabled: false, fieldValues: { __subTables__: { 'dw:renamed_lines': rows } } }) })
    form.formData.value = { __subTables__: { 'dw:renamed_lines': rows, 'rt:renamed_directory': lookup } }
    expect(form.buildCurrentTaskFormSubmitPayload().formData.__subTables__).toEqual({ 'dw:renamed_lines': rows })
    expect(form.formData.value.__subTables__['rt:renamed_directory']).toEqual(lookup)
    vi.mocked(submitTaskForm).mockResolvedValueOnce(undefined as any)
    vi.mocked(getTaskFormData).mockResolvedValueOnce({ fieldValues: { __subTables__: {
      'dw:renamed_lines': [{ ...rows[0], _wsRowVersion: 2 }],
    } } } as any)
    await form.saveCurrentTaskForm()
    expect(form.subTableSaveError.value).toBeNull()
    expect(form.formData.value.__subTables__['rt:renamed_directory']).toEqual(lookup)
  })
  it.each([true, false])('ordinary single-binding writes retain version claims with batch runtime %s', enabled => {
    const saved = { entry_key: 'A', amount: 1, _wsRowVersion: 4 }
    const form = useTaskForm({ subTableBindings: ref([{ bindingId: 2, tableId: 20, tableName: 'lines',
      bindingMode: 'EDITABLE', primaryKeyFields: ['entry_key'], data: [{ ...saved, amount: 2 }] }]),
      isMiSubTaskMode: ref(false), isCompletedTask: ref(false), effectiveTaskId: ref('task-1'),
      taskFormDTO: ref({ subTableBatchRuntimeEnabled: enabled, fieldValues: { __subTables__: { 'dw:lines': [saved] } } }) })
    form.formData.value = { __subTables__: { 'dw:lines': [{ ...saved, amount: 2 }] } }
    expect(form.buildCurrentTaskFormSubmitPayload().subTableBindingScopes).toEqual([
      { bindingId: '2', storeKey: 'dw:lines', rowKeys: [{ entry_key: 'A' }], emptied: false },
    ])
  })
  it('omits read-only display copies only for enhanced instances, without erasing page data', async () => {
    const parent = [{ parent_key: 'P1', assignee_display_name: 'Synthetic display', __subTables__: {} }]
    const child = [{ line_key: 'L1', description: 'edited', _wsRowVersion: 1 }]
    const dto = ref({ subTableBatchRuntimeEnabled: true, fieldValues: { __subTables__: {
      'dw:parents': [{ parent_key: 'P1' }], 'dw:lines': child,
    } } })
    const form = useTaskForm({ subTableBindings: ref([
      { bindingId: 1, tableId: 10, tableName: 'parents', bindingMode: 'READONLY', primaryKeyFields: ['parent_key'], data: parent },
      { bindingId: 2, tableId: 20, tableName: 'lines', bindingMode: 'EDITABLE', primaryKeyFields: ['line_key'], data: child },
    ]), isMiSubTaskMode: ref(false), isCompletedTask: ref(false), effectiveTaskId: ref('task-1'), taskFormDTO: dto })
    form.formData.value = { __subTables__: { 'dw:parents': parent, 'dw:lines': child } }
    const payload = form.buildCurrentTaskFormSubmitPayload()
    expect(Object.keys(payload.formData.__subTables__)).toEqual(['dw:lines'])
    expect(Object.keys(payload.subTableData)).toEqual(['dw:lines'])
    expect(payload.subTableBindingScopes.every(scope => scope.bindingId === '2')).toBe(true)
    expect(form.formData.value.__subTables__['dw:parents']).toEqual(parent)
    await form.saveCurrentTaskForm()
    expect(form.formData.value.__subTables__['dw:parents']).toEqual(parent)
    dto.value.subTableBatchRuntimeEnabled = false
    expect(form.buildCurrentTaskFormSubmitPayload().formData.__subTables__).toHaveProperty('dw:parents')
  })
  it('declares deletion-to-empty with frozen identities and the saved row version', () => {
    const capabilities = { grid: { batchControlId: 'grid', bindingId: '101', originalCrud: {},
      capabilities: { allowBatchDelete: true }, batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description' }] },
      rowDesign: { binding: { tableId: 20, tableName: 'lines', bindingType: 'SUB', bindingMode: 'EDITABLE',
        bindingLinkMode: 'structuralFk', primaryKeyFields: ['line_key'], foreignKeyField: 'case_ref',
        fieldDefinitions: [{ fieldName: 'case_ref', isForeignKey: true, refTableId: 10, refPrimaryKeyFields: ['case_key'] }] },
      fields: [], parentTablesById: {} },
    } }
    const form = useTaskForm({ subTableBindings: ref([{ bindingId: 101, tableId: 20, tableName: 'lines',
      data: [], primaryKeyFields: ['unpublished_key'], bindingLinkMode: 'structuralFk' }]),
      isMiSubTaskMode: ref(false), isCompletedTask: ref(false), effectiveTaskId: ref('task-1'),
      taskFormDTO: ref({ subTableBatchRuntimeEnabled: true, subTableBatchCapabilities: capabilities, fieldValues: { __subTables__: {
        'dw:lines': [{ line_key: 'L1', case_ref: 'C1', description: 'saved', _wsRowVersion: 3 }],
      } } }),
      primaryTableBinding: ref({ tableId: 10, primaryKeyFields: ['case_key'] }),
    })
    form.formData.value = { case_key: 'C1', __subTables__: { '101': [], 'dw:lines': [] } }
    form.recordSubTableBatchChange({ batchControlId: 'grid', bindingId: '101', operation: 'DELETE',
      rowPatches: [{ rowId: 'L1', rowKey: { line_key: 'L1' } }] })
    const payload = form.buildCurrentTaskFormSubmitPayload()
    expect(payload.emptiedSubTableKeys).toContain('dw:lines')
    expect(payload.subTableBindingScopes).toEqual([{ bindingId: '101', storeKey: 'dw:lines', rowKeys: [],
      emptied: true, deletedRows: [{ line_key: 'L1', _wsRowVersion: 3 }] }])
    expect(payload.formData.__subTables__['dw:lines']).toEqual([])
  })
})
