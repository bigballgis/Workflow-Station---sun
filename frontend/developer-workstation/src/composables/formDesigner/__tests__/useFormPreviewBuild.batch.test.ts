import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { FormDefinition } from '@/api/functionUnit'
import { useFormPreviewBuild } from '../useFormPreviewBuild'

vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), error: vi.fn() } }))
const saved = { schemaVersion: 1, rule: [{ type: 'input', field: 'saved_field' }] }
const live = { schemaVersion: 1, rule: [{ type: 'input', field: 'live_field', title: 'Live field' }] }
function control(id: string, props: Record<string, unknown>) {
  return { type: 'subTable', batchControlId: id, props: { _bindingId: 21, ...props } }
}
function preview(rules: unknown[]) {
  const form = ref<FormDefinition>({ id: 10, formName: 'Synthetic preview', formType: 'TASK',
    configJson: { rule: rules, batchForms: { 21: saved }, subForms: {} },
    fieldPermissions: { '21:live_field': 'READONLY' },
    tableBindings: [{ id: 21, tableId: 200, bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'structuralFk', sortOrder: 1 }] })
  const getBatchForm = vi.fn(() => live)
  const build = useFormPreviewBuild({ functionUnitId: 1,
    store: { tables: [{ id: 200, tableName: 'synthetic_lines', fieldDefinitions: [{ fieldName: 'live_field', dataType: 'VARCHAR' }] }], fetchTables: async () => {} },
    selectedForm: form, designerRef: ref({ getRule: () => rules, getOption: () => ({}) }), subDesignerRefs: ref([]),
    subFormCache: ref({}), designerSubBindings: computed(() => [{ bindingId: 21, tableId: 200 }]),
    getActiveDesignerRef: () => undefined, getTableFieldDefinitions: () => [], getPrimaryBindingFieldDefinitions: () => [],
    toSubTablePreviewColumns: () => [{ field: 'live_field', label: 'Live field' }], makeLookupPreviewItem: () => undefined,
    getTableName: () => 'Synthetic lines', getAssignmentConfig: () => undefined, getBatchForm, t: key => key,
  })
  return { ...build, form, getBatchForm }
}

describe('live batch form preview wiring', () => {
  it('uses unsaved batch schema without mutating saved or single-row rules', async () => {
    const build = preview([control('one', { allowBatchEdit: true })])
    await build.handlePreview()
    const item = build.previewItems.value[0]
    expect(item?.kind).toBe('subTable')
    if (item?.kind !== 'subTable') throw new Error('Missing preview table')
    expect(item.binding.batchPreview?.rule).toEqual(live.rule)
    expect(item.binding.batchPreview?.fieldWriteModes.live_field).toBe('READONLY')
    expect(build.form.value.configJson.batchForms[21]).toEqual(saved)
    expect(item.binding.rule).toEqual([])
  })
  it('keeps same-binding controls independent instead of losing the second or OR-ing flags', async () => {
    const build = preview([control('one', { allowBatchEdit: true }), control('two', { allowBatchDelete: true })])
    await build.handlePreview()
    const items = build.previewItems.value.filter(item => item.kind === 'subTable')
    expect(items).toHaveLength(2)
    expect(items.map(item => item.kind === 'subTable' && item.binding.batchPreview?.batchControlId)).toEqual(['one', 'two'])
    expect(items.map(item => item.kind === 'subTable' && item.binding.batchPreview?.flags)).toEqual([
      { add: false, edit: true, delete: false, assign: false }, { add: false, edit: false, delete: true, assign: false },
    ])
  })
  it('flags duplicate stable IDs rather than picking a control by binding or position', async () => {
    const build = preview([control('same', { allowBatchEdit: true }), control('same', { allowBatchDelete: true })])
    await build.handlePreview()
    const tables = build.previewItems.value.filter(item => item.kind === 'subTable')
    expect(tables).toHaveLength(2)
    expect(tables.every(item => item.kind === 'subTable' && item.binding.batchPreview?.invalid)).toBe(true)
  })
  it('leaves legacy preview untouched without reading a batch designer', async () => {
    const build = preview([control('old', {})])
    await build.handlePreview()
    expect(build.getBatchForm).not.toHaveBeenCalled()
    const item = build.previewItems.value[0]
    expect(item?.kind === 'subTable' && item.binding.batchPreview).toBeUndefined()
  })
})
