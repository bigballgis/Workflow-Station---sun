import { describe, expect, it } from 'vitest'
import { mergeBatchChanges, beginBatchSave, type SubTableBatchChange } from '../subTableBatchChanges'
import { ref } from 'vue'

function change(operation: SubTableBatchChange['operation'], rowId = 'L1'): SubTableBatchChange {
  return { batchControlId: 'grid', bindingId: '101', operation,
    rowPatches: [{ rowId, rowKey: { item_key: rowId }, ...(operation === 'DELETE' ? {} : { fields: ['description'] }) }] }
}

describe('batch page declarations', () => {
  it('retains a deletion made while its add is in flight for the next save', () => {
    const pending = ref([change('ADD')])
    beginBatchSave(pending, { subTableBatchChanges: pending.value })
    pending.value = mergeBatchChanges(pending.value, change('DELETE'))
    expect(pending.value).toEqual([change('DELETE')])
  })
  it('restores and normalizes the submitted generation after failure', () => {
    const pending = ref([change('ADD')])
    const restore = beginBatchSave(pending, { subTableBatchChanges: pending.value })
    pending.value = mergeBatchChanges(pending.value, change('DELETE'))
    restore()
    expect(pending.value).toEqual([])
  })
  it('does not clear edits applied while a previous edit was saving', () => {
    const pending = ref([change('EDIT')])
    const restore = beginBatchSave(pending, { subTableBatchChanges: pending.value })
    pending.value = mergeBatchChanges(pending.value, change('EDIT', 'L2'))
    expect(pending.value).toEqual([change('EDIT', 'L2')])
    restore()
    expect(pending.value[0]?.rowPatches.map(patch => patch.rowId)).toEqual(['L1', 'L2'])
  })
  it('normalizes an edited then deleted saved row to one deletion', () => {
    expect(mergeBatchChanges([change('EDIT'), change('ASSIGN')], change('DELETE'))).toEqual([change('DELETE')])
  })
  it('cancels an added then deleted unsaved row without a persistence deletion', () => {
    expect(mergeBatchChanges([change('ADD')], change('DELETE'))).toEqual([])
  })
  it('keeps other binding rows and independently authorized operations', () => {
    const sibling = { ...change('EDIT'), bindingId: '102' }
    const result = mergeBatchChanges([sibling, change('EDIT', 'L2')], change('DELETE'))
    expect(result).toEqual([sibling, change('EDIT', 'L2'), change('DELETE')])
  })
})
