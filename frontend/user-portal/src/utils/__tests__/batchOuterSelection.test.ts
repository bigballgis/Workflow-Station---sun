import { nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useBatchSelection } from '@platform-shared/batch/useBatchSelection'

describe('outer batch selection identity', () => {
  it('ignores reserved selections that no longer exist in the table', () => {
    const rows = ref([{ token: 'B' }])
    const selection = useBatchSelection(rows, row => String(row.token))
    selection.onSelectionChange([{ token: 'deleted-A' }, rows.value[0]!])
    expect(selection.selectedIds.value).toEqual(['B'])
  })
  it('clears the Element Plus reserved selection store as well as application state', () => {
    const rows = ref([{ token: 'A' }])
    const selection = useBatchSelection(rows, row => String(row.token))
    const clearSelection = vi.fn()
    selection.selectionTable.value = { clearSelection, toggleRowSelection: vi.fn() }
    selection.onSelectionChange(rows.value)
    selection.clearSelection()
    expect(selection.selectedIds.value).toEqual([])
    expect(clearSelection).toHaveBeenCalledOnce()
  })
  it('retains selection across reordered or refreshed rows and removes deleted identities', async () => {
    const rows = ref([{ token: 'A' }, { token: 'B' }])
    const selection = useBatchSelection(rows, row => String(row.token))
    selection.onSelectionChange(rows.value)
    rows.value = [{ token: 'B' }, { token: 'A' }]
    await nextTick()
    expect(selection.selectedIds.value).toEqual(['A', 'B'])
    rows.value = [{ token: 'B' }]
    await nextTick(); await nextTick()
    expect(selection.selectedIds.value).toEqual(['B'])
  })
})
