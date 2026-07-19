import { ref, type Ref } from 'vue'
import type { MainTableViewDataRow, MainTableViewFieldColumn } from '@/api/mainTableView'
import { fetchLookupRowByPrimaryKey } from '@/components/lookup/fetchLookupRowByPrimaryKey'
import {
  extractLookupPrimaryKey,
  formatLookupAwareMainTableViewCell,
  isLookupRelatedColumn,
} from '@/utils/mainTableViewLookupDisplay'

type HydratedCellMap = Map<string, Map<string, string>>

function cellKey(processInstanceId: string, fieldName: string): string {
  return `${processInstanceId}::${fieldName}`
}

/**
 * Batch-resolve lookup / lookup_display cells for the current Main Table View page.
 * Reuses {@link fetchLookupRowByPrimaryKey} module cache — no N+1 per cell.
 */
export function useMainTableViewLookupHydration(
  columns: Ref<MainTableViewFieldColumn[]>,
  rows: Ref<MainTableViewDataRow[]>,
) {
  const hydratedLabels = ref<HydratedCellMap>(new Map())
  const hydrating = ref(false)

  async function hydrateLookupCells(): Promise<void> {
    const cols = columns.value.filter(isLookupRelatedColumn)
    if (!cols.length || !rows.value.length) {
      hydratedLabels.value = new Map()
      return
    }

    hydrating.value = true
    try {
      const pkByTable = new Map<string, {
        tableId: number
        pk: string
        searchFields: string[]
        displayField: string
      }>()

      for (const row of rows.value) {
        for (const col of cols) {
          const raw = row.values?.[col.fieldName]
          const pk = extractLookupPrimaryKey(raw)
          if (!pk || col.lookupTableId == null) continue
          // Object already has attributes — still register so we can read selected/display fields
          // without an extra fetch when possible.
          if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
            continue
          }
          const cacheKey = `${col.lookupTableId}:${pk}`
          if (pkByTable.has(cacheKey)) continue
          pkByTable.set(cacheKey, {
            tableId: col.lookupTableId,
            pk,
            searchFields: col.lookupSearchFields?.length ? col.lookupSearchFields : ['id'],
            displayField: col.lookupSelectedDisplayField || col.lookupDisplayField || 'id',
          })
        }
      }

      const resolvedRows = new Map<string, Record<string, unknown> | null>()
      await Promise.all(
        [...pkByTable.entries()].map(async ([key, req]) => {
          const row = await fetchLookupRowByPrimaryKey(req.tableId, req.pk, {
            searchFields: req.searchFields,
            displayField: req.displayField,
            primaryKeyField: 'id',
          })
          resolvedRows.set(key, row)
        }),
      )

      const next: HydratedCellMap = new Map()
      for (const row of rows.value) {
        const byField = new Map<string, string>()
        for (const col of cols) {
          const raw = row.values?.[col.fieldName]
          let hydrated: Record<string, unknown> | null = null
          if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
            hydrated = raw as Record<string, unknown>
          } else {
            const pk = extractLookupPrimaryKey(raw)
            if (pk && col.lookupTableId != null) {
              hydrated = resolvedRows.get(`${col.lookupTableId}:${pk}`) ?? null
            }
          }
          byField.set(
            col.fieldName,
            formatLookupAwareMainTableViewCell(col, raw, hydrated),
          )
        }
        next.set(row.processInstanceId, byField)
      }
      hydratedLabels.value = next
    } finally {
      hydrating.value = false
    }
  }

  function formatHydratedCell(col: MainTableViewFieldColumn, row: MainTableViewDataRow): string {
    const fromMap = hydratedLabels.value.get(row.processInstanceId)?.get(col.fieldName)
    if (fromMap != null) return fromMap
    return formatLookupAwareMainTableViewCell(col, row.values?.[col.fieldName], null)
  }

  return {
    hydratedLabels,
    hydrating,
    hydrateLookupCells,
    formatHydratedCell,
    cellKey,
  }
}
