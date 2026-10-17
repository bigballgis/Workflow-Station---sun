import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useFormSave } from '../useFormSave'
import { ElMessage } from 'element-plus'
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
  const store = { updateForm: vi.fn(async (_unitId: number, _formId: number, _payload: Record<string, any>) => ({})) }
  const designer = { getRule: () => rule, getOption: () => ({}) }
  const batchDesignerRefs = ref<Array<{ getRule?: () => unknown; getOption?: () => unknown }>>([])
  const batchFormCache = ref<Record<number, { rule: unknown[]; options: Record<string, unknown> }>>({})
  const batchFormVisited = ref(new Set<number>())
  const save = useFormSave({ configHydrationBlocked: ref(loading), functionUnitId: 1, store, selectedForm: form, designerRef: ref(designer),
    subDesignerRefs: ref([]), batchDesignerRefs, designerSubBindings: computed(() => [
      { bindingId: 101, bindingType: 'SUB', assignmentTableName: 'lines', tableName: 'lines' },
    ]),
    subFormCache: ref({}), batchFormCache, batchFormVisited,
    relationViewState: ref({}), subTableViewState: ref({}), subTableListViewRefs: ref({}),
    getActiveDesignerRef: () => designer, getPrimaryBindingFieldDefinitions: () => [], getBatchTableFields,
    syncSubTableListViewFromFormRules: vi.fn(), loadForms: vi.fn(), autoSaving: ref(false), lastAutoSaveTime: ref(null),
    getBpmnXml: () => undefined, t: key => key,
  })
  return { save, store, getBatchTableFields, form, batchDesignerRefs, batchFormCache, batchFormVisited }
}

describe('actual designer save membership wiring', () => {
  it('rejects saving when the mounted batch canvas read fails rather than writing old cache', async () => {
    const { save, store, batchDesignerRefs, batchFormCache, batchFormVisited } = setup('0')
    batchFormVisited.value.add(101)
    batchFormCache.value[101] = { rule: [{ type: 'inputNumber', field: 'amount', title: 'Old cached canvas' }], options: {} }
    batchDesignerRefs.value[0] = { getRule: () => { throw new Error('Synthetic canvas read failed') } }
    vi.mocked(ElMessage.error).mockClear()
    expect(await save.handleSaveForm(true)).toBe(false)
    expect(store.updateForm).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('rejects a mounted batch canvas returning no rule instead of falling back', async () => {
    const { save, store, batchDesignerRefs, batchFormVisited } = setup('0')
    batchFormVisited.value.add(101)
    batchDesignerRefs.value[0] = { getRule: () => undefined }
    expect(await save.handleSaveForm()).toBe(false)
    expect(store.updateForm).not.toHaveBeenCalled()
  })

  it('does not silently downgrade an unknown saved batch schema', async () => {
    const { save, store, form } = setup('0')
    form.value.configJson!.batchForms!['101']!.schemaVersion = 2
    expect(await save.handleSaveForm()).toBe(false)
    expect(store.updateForm).not.toHaveBeenCalled()
    expect(form.value.configJson!.batchForms!['101']!.schemaVersion).toBe(2)
  })

  it('does not substitute cache when the live batch options cannot be read', async () => {
    const { save, store, batchDesignerRefs, batchFormVisited } = setup('0')
    batchFormVisited.value.add(101)
    batchDesignerRefs.value[0] = { getRule: () => [{ type: 'inputNumber', field: 'amount' }],
      getOption: () => { throw new Error('Synthetic options read failed') } }
    expect(await save.handleSaveForm()).toBe(false)
    expect(store.updateForm).not.toHaveBeenCalled()
  })

  it('keeps legitimate cached edits when the visited batch tab is unmounted', async () => {
    const { save, store, batchFormVisited, batchFormCache } = setup('0')
    batchFormVisited.value.add(101)
    batchFormCache.value[101] = { rule: [{ type: 'inputNumber', field: 'amount', title: 'Cached edits' }], options: {} }
    expect(await save.handleSaveForm()).toBe(true)
    expect(store.updateForm.mock.calls[0]![2].configJson.batchForms[101].rule[0].title).toBe('Cached edits')
  })

  it('saves a healthy live batch canvas rather than its earlier cache', async () => {
    const { save, store, batchDesignerRefs, batchFormVisited, batchFormCache } = setup('0')
    batchFormVisited.value.add(101)
    batchFormCache.value[101] = { rule: [{ type: 'inputNumber', field: 'amount', title: 'Old cache' }], options: {} }
    batchDesignerRefs.value[0] = { getRule: () => [{ type: 'inputNumber', field: 'amount', title: 'Live edits' }], getOption: () => ({}) }
    expect(await save.handleSaveForm()).toBe(true)
    expect(store.updateForm.mock.calls[0]![2].configJson.batchForms[101].rule[0].title).toBe('Live edits')
  })

  it('warns once for repeated autosave collection failures instead of claiming success', async () => {
    const { save, store, batchDesignerRefs, batchFormVisited } = setup('0')
    batchFormVisited.value.add(101)
    batchDesignerRefs.value[0] = { getRule: () => { throw new Error('Synthetic autosave read failed') } }
    vi.mocked(ElMessage.warning).mockClear()
    expect(await save.handleSaveForm()).toBe(false)
    expect(await save.handleSaveForm()).toBe(false)
    expect(store.updateForm).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledOnce()
  })

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
