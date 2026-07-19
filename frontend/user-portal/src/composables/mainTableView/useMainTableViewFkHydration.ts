import type { Ref } from 'vue'
import type { MainTableViewDataRow, MainTableViewFieldColumn } from '@/api/mainTableView'
import { formatLookupAwareMainTableViewCell, isFkDisplayColumn } from '@/utils/mainTableViewLookupDisplay'
import { resolveFkDisplayAttribute } from '@/utils/mainTableViewFkDisplay'

/**
 * Format {@code fk_display} cells. Backend usually resolves attributes during projection;
 * when a row still carries a raw FK scalar plus {@code relatedMainValues}, hydrate client-side.
 */
export function formatFkDisplayCell(
  col: MainTableViewFieldColumn,
  row: MainTableViewDataRow,
): string {
  if (!isFkDisplayColumn(col)) {
    return formatLookupAwareMainTableViewCell(col, row.values?.[col.fieldName], null)
  }
  const raw = row.values?.[col.fieldName]
  const related = (row as MainTableViewDataRow & {
    relatedMainValues?: Record<string, unknown>
  }).relatedMainValues
  if (related && col.lookupDisplayField) {
    const resolved = resolveFkDisplayAttribute(
      related,
      raw,
      col.refPrimaryKeyFields,
      col.lookupDisplayField,
    )
    if (resolved !== undefined) {
      return formatLookupAwareMainTableViewCell(col, resolved, null)
    }
  }
  return formatLookupAwareMainTableViewCell(col, raw, null)
}

/** No-op async hook so page load can await FK path alongside lookup hydration. */
export function useMainTableViewFkHydration(
  _columns: Ref<MainTableViewFieldColumn[]>,
  _rows: Ref<MainTableViewDataRow[]>,
) {
  async function hydrateFkCells(): Promise<void> {
    // Backend projects resolved fk_display values; nothing to batch-fetch.
  }

  return { hydrateFkCells, formatFkDisplayCell }
}
