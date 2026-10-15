import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useFormSave } from '../useFormSave'
import type { FormDefinition } from '@/api/functionUnit'

vi.mock('@/api/functionUnit', () => ({ functionUnitApi: { getDataTableColumns: vi.fn().mockResolvedValue({ data: [] }) } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() } }))

function setup(defaultValue?: string, loading = false) {
  const rowRule = [{ type: 'inputNumber', field: 'amount', title: 'Amount' }]
  const rule = [{ type: 'subTable', field: 'lines', _bindingId: 101, batchControlId: 'synthetic-grid',
    props: { _bindingId: 101, allowBatchAdd: true } }]
  const form = ref<FormDefinition>({ id: 1, formName: 'Synthetic', formType: 'TASK',
    fieldPermissions: { '101:amount': 'READONLY' },
    tableBindings: [{ id: 101, tableId: 20, bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'structuralFk', sortOrder: 0 }],
    configJson: { rule, batchForms: { '101': { schemaVersion: 1, rule: rowRule, options: {} } } },
  })
  const getBatchTableFields = vi.fn(() => [{ fieldName: 'amount', dataType: 'INTEGER', nullable: false,
    isPrimaryKey: false, ...(defaultValue === undefined ? {} : { defaultValue }) }])
  const store = { updateForm: vi.fn().mockResolvedValue({}) }
  const designer = { getRule: () => rule, getOption: () => ({}) }
  const save = useFormSave({ configHydrationBlocked: ref(loading), functionUnitId: 1, store, selectedForm: form, designerRef: ref(designer),
    subDesignerRefs: ref([]), batchDesignerRefs: ref([]), designerSubBindings: computed(() => [
      { bindingId: 101, bindingType: 'SUB', assignmentTableName: 'lines', tableName: 'lines' },
    ]),
    subFormCache: ref({}), batchFormCache: ref({}), batchFormVisited: ref(new Set()),
    relationViewState: ref({}), subTableViewState: ref({}), subTableListViewRefs: ref({}),
    getActiveDesignerRef: () => designer, getPrimaryBindingFieldDefinitions: () => [], getBatchTableFields,
    syncSubTableListViewFromFormRules: vi.fn(), loadForms: vi.fn(), autoSaving: ref(false), lastAutoSaveTime: ref(null),
    getBpmnXml: () => undefined, t: key => key,
  })
  return { save, store, getBatchTableFields }
}

describe('actual designer save membership wiring', () => {
  it('never reads or persists a canvas while its configuration is loading', async () => {
    const { save, store, getBatchTableFields } = setup('0', true)
    expect(await save.handleSaveForm(true)).toBe(false)
    expect(getBatchTableFields).not.toHaveBeenCalled()
    expect(store.updateForm).not.toHaveBeenCalled()
  })
  it('reads injected table fields rather than canvas form options and persists a valid zero default', async () => {
    const { save, store, getBatchTableFields } = setup('0')
    expect(await save.handleSaveForm()).toBe(true)
    expect(getBatchTableFields).toHaveBeenCalledWith(20)
    expect(store.updateForm).toHaveBeenCalledOnce()
  })

  it('does not call persistence when a mandatory readonly default is absent', async () => {
    const { save, store, getBatchTableFields } = setup()
    expect(await save.handleSaveForm()).toBe(false)
    expect(getBatchTableFields).toHaveBeenCalledWith(20)
    expect(store.updateForm).not.toHaveBeenCalled()
  })
})
