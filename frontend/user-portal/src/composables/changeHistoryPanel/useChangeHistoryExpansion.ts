import { ref, watch, type Ref } from 'vue'
import type { ChangeBatch } from './useChangeHistoryBatches'

/** Stable keys preserve collapsed sessions when new records arrive or history reloads. */
export function useChangeHistoryExpansion(groupedBatches: Ref<ChangeBatch[]>) {
  const sectionExpandedNames = ref<string[]>(['history'])
  const batchTableOpen = ref<Record<string, boolean>>({})
  watch(groupedBatches, batches => {
    for (const batch of batches) {
      if (!(batch.key in batchTableOpen.value)) batchTableOpen.value[batch.key] = true
    }
  }, { immediate: true })
  function toggleBatchTable(key: string) {
    batchTableOpen.value[key] = !isBatchTableOpen(key)
  }
  function isBatchTableOpen(key: string) {
    return batchTableOpen.value[key] !== false
  }
  return { sectionExpandedNames, batchTableOpen, toggleBatchTable, isBatchTableOpen }
}
