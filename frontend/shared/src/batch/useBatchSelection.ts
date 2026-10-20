import { computed, nextTick, ref, watch, type Ref } from 'vue'

interface SelectionTable {
  clearSelection(): void
  toggleRowSelection(row: Record<string, unknown>, selected: boolean): void
}
/** Selection follows configured record identities, never table positions. */
export function useBatchSelection(rows: Ref<Array<Record<string, unknown>>>, identity: (row: Record<string, unknown>) => string | null) {
  const selectedIds = ref<string[]>([])
  const selectionTable = ref<SelectionTable>()
  const selectedRows = computed(() => rows.value.filter(row => selectedIds.value.includes(identity(row)!)))
  function onSelectionChange(selected: Array<Record<string, unknown>>) {
    const live = new Set(rows.value.map(identity))
    selectedIds.value = [...new Set(selected.map(identity).filter((id): id is string => id !== null && live.has(id)))]
  }
  function clearSelection() {
    selectedIds.value = []
    selectionTable.value?.clearSelection()
  }
  let generation = 0
  watch(() => rows.value.map(identity), async ids => {
    const current = ++generation
    const live = new Set(ids)
    const retained = selectedIds.value.filter(id => live.has(id))
    if (retained.length === selectedIds.value.length) return
    clearSelection()
    await nextTick()
    if (current !== generation) return
    if (selectionTable.value) {
      for (const row of rows.value) if (retained.includes(identity(row)!)) selectionTable.value.toggleRowSelection(row, true)
    } else selectedIds.value = retained
  })
  return { selectedIds, selectedRows, selectionTable, onSelectionChange, clearSelection }
}
