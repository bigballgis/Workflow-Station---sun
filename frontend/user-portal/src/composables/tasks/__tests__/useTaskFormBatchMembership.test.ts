import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useTaskForm } from '../useTaskForm'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/api/processForm', () => ({ submitTaskForm: vi.fn(), getTaskFormData: vi.fn() }))

describe('batch membership submit payload', () => {
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
      taskFormDTO: ref({ subTableBatchCapabilities: capabilities, fieldValues: { __subTables__: {
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
