import { ref, watch, type Ref } from 'vue'
import type { ChangeBatch } from './useChangeHistoryBatches'

export interface ChangeHistoryExpansion {
  /** 整块「变更历史」折叠；默认展开 */
  sectionExpandedNames: Ref<string[]>
  /** 每批次的明细表格是否展开；默认展开 */
  batchTableOpen: Ref<Record<number, boolean>>
  toggleBatchTable: (index: number) => void
  isBatchTableOpen: (index: number) => boolean
}

/** 折叠面板与各批次明细表格的展开/收起状态；随批次变化同步默认展开。 */
export function useChangeHistoryExpansion(
  groupedBatches: Ref<ChangeBatch[]>,
): ChangeHistoryExpansion {
  /** 整块「变更历史」折叠；默认展开 */
  const sectionExpandedNames = ref<string[]>(['history'])

  /** 每批次的明细表格是否展开；默认展开 */
  const batchTableOpen = ref<Record<number, boolean>>({})

  watch(
    groupedBatches,
    (batches) => {
      const next: Record<number, boolean> = {}
      batches.forEach((_, i) => {
        next[i] = batchTableOpen.value[i] ?? true
      })
      batchTableOpen.value = next
    },
    { immediate: true },
  )

  function toggleBatchTable(index: number) {
    const cur = batchTableOpen.value[index] ?? true
    batchTableOpen.value = { ...batchTableOpen.value, [index]: !cur }
  }

  function isBatchTableOpen(index: number): boolean {
    return batchTableOpen.value[index] !== false
  }

  return {
    sectionExpandedNames,
    batchTableOpen,
    toggleBatchTable,
    isBatchTableOpen,
  }
}
