import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useFieldImport } from '../useFieldImport'

vi.mock('@/api/functionUnit', () => ({ functionUnitApi: {} }))
vi.mock('@/api/relationTable', () => ({ relationTableBindingApi: {} }))
vi.mock('@/utils/formDesigner', () => ({
  injectUploadButtonLabels: vi.fn(),
  flattenRuleLayoutContainers: (rules: unknown[]) => rules,
}))

function fixture() {
  const ordinary = [{ field: 'handler' }]
  const batch = ref<unknown[]>([])
  const ordinarySet = vi.fn()
  const list = ref({ 7: { allFields: [], viewFields: [] } })
  const field = { id: 1, fieldName: 'handler', dataType: 'VARCHAR', tableId: 3 }
  const options = {
    functionUnitId: 1, store: { tables: [{ id: 3, tableType: 'SUB', fieldDefinitions: [field] }], fetchTables: vi.fn() },
    selectedForm: ref({ id: 1 }), designerRef: ref(null),
    subDesignerRefs: ref([{ getRule: () => ordinary, setRule: ordinarySet }]),
    batchDesignerRefs: ref([{ getRule: () => batch.value, setRule: (rules: unknown[]) => { batch.value = rules } }]),
    subTableActiveTab: ref('batchForm'), activeDesignerTab: ref('7'),
    designerSubBindings: computed(() => [{ bindingId: 7, bindingType: 'SUB', tableId: 3, tableName: 'renamed' }]),
    relationViewState: ref({}), subTableViewState: list,
    getSubTableListViewBaseColumns: vi.fn(), appendSubTableListFieldColumns: vi.fn(),
    mapFieldsToFormRules: () => [{ type: 'input', field: 'handler' }],
    getRequestIdConfigByTableId: vi.fn(), mergeTaskPermissionsForFields: vi.fn(),
    refreshFormRulesFromTableMetadata: vi.fn(), t: (key: string) => key,
  }
  const api = useFieldImport(options as unknown as Parameters<typeof useFieldImport>[0])
  api.importTableId.value = 3
  api.selectedImportFields.value = [field as never]
  return { api, options, ordinarySet, list, batch: () => batch.value, field }
}

describe('independent Batch Form imports', () => {
  it('deduplicates and writes the batch canvas without touching ordinary Form or List View', async () => {
    const f = fixture()
    const listBefore = JSON.stringify(f.list.value)
    expect(f.api.isFieldAlreadyImported(f.field as never)).toBe(false)
    await f.api.handleConfirmImportFields()
    expect(f.batch()).toEqual([{ type: 'input', field: 'handler' }])
    expect(f.ordinarySet).not.toHaveBeenCalled()
    expect(f.options.refreshFormRulesFromTableMetadata).not.toHaveBeenCalled()
    expect(JSON.stringify(f.list.value)).toBe(listBefore)
    expect(f.api.isFieldAlreadyImported(f.field as never)).toBe(true)
    await f.api.handleConfirmImportFields()
    expect(f.batch()).toHaveLength(1)
  })

  it('continues using the ordinary canvas on Form Design', () => {
    const f = fixture()
    f.options.subTableActiveTab.value = 'form'
    expect(f.api.isFieldAlreadyImported(f.field as never)).toBe(true)
  })
})
