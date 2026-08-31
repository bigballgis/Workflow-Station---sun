import { ref } from 'vue'
import { relationTableApi } from '@/api/relationTable'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'

export const LOOKUP_PAGE_SIZE = 200
export const LOOKUP_MAX_ROWS = 10000

export interface LookupFieldRowsParams {
  tableId: () => number
  searchFields: () => string[]
  displayField: () => string
  filterConditions: () => LookupFilterCondition[]
  prefetchLimit: () => number | undefined
  remoteFilter: () => boolean
}

export function useLookupFieldRows(params: LookupFieldRowsParams) {
  const allRows = ref<Record<string, any>[]>([])
  const loading = ref(false)
  const dataLoaded = ref(false)
  const lastRemoteKeyword = ref<string | null>(null)
  let searchSeq = 0

  function queryLimit(): number {
    return params.prefetchLimit() ?? LOOKUP_MAX_ROWS
  }

  async function fetchLookupRows(
    keyword: string,
    maxRows: number,
  ): Promise<Record<string, any>[]> {
    const rows: Record<string, any>[] = []
    for (let offset = 0; offset < maxRows; offset += LOOKUP_PAGE_SIZE) {
      const limit = Math.min(LOOKUP_PAGE_SIZE, maxRows - offset)
      const res = await relationTableApi.searchForLookup(params.tableId(), {
        keyword,
        searchFields: params.searchFields() || [],
        displayField: params.displayField() || '',
        filterConditions: params.filterConditions() || [],
        limit,
        offset,
      })
      const batch = res.data || []
      rows.push(...batch)
      allRows.value = rows.slice()
      if (batch.length < limit) return rows
    }
    if (maxRows >= LOOKUP_MAX_ROWS) {
      console.warn(
        `[LookupField] table ${params.tableId()} exceeds ${LOOKUP_MAX_ROWS} rows; dropdown truncated`,
      )
    }
    return rows
  }

  async function loadInitial(): Promise<void> {
    if (!params.tableId()) return
    if (!params.remoteFilter() && dataLoaded.value) return
    if (params.remoteFilter() && dataLoaded.value && lastRemoteKeyword.value === '') return

    const seq = ++searchSeq
    loading.value = true
    try {
      const rows = await fetchLookupRows('', queryLimit())
      if (seq !== searchSeq) return
      allRows.value = rows
      lastRemoteKeyword.value = ''
      dataLoaded.value = true
    } catch (e) {
      if (seq !== searchSeq) return
      console.error('[LookupField] load error:', e)
      allRows.value = []
    } finally {
      if (seq === searchSeq) loading.value = false
    }
  }

  async function searchRemote(keyword: string): Promise<void> {
    if (!params.tableId()) return
    const trimmed = keyword.trim()
    if (trimmed === lastRemoteKeyword.value && dataLoaded.value) return

    const seq = ++searchSeq
    loading.value = true
    try {
      const rows = await fetchLookupRows(trimmed, queryLimit())
      if (seq !== searchSeq) return
      allRows.value = rows
      lastRemoteKeyword.value = trimmed
      dataLoaded.value = true
    } catch (e) {
      if (seq !== searchSeq) return
      console.error('[LookupField] search error:', e)
      allRows.value = []
    } finally {
      if (seq === searchSeq) loading.value = false
    }
  }

  function resetRows(): void {
    allRows.value = []
    dataLoaded.value = false
    lastRemoteKeyword.value = null
    searchSeq += 1
  }

  return {
    allRows,
    loading,
    dataLoaded,
    loadInitial,
    searchRemote,
    resetRows,
  }
}
