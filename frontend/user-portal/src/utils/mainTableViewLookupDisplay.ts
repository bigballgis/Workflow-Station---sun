import type { MainTableViewFieldColumn } from '@/api/mainTableView'
import { resolveLookupCellTagText } from '@/components/subTableAddDialogHelpers/lookup'
import { unwrapUserLikeValueToDisplayString } from '@/components/subTableAddDialogHelpers/userDisplay'
import { formatMainTableViewCell } from '@/utils/mainTableViewCsvExport'

/** Extract a scalar PK from a process-variable lookup cell (UUID string or row object). */
export function extractLookupPrimaryKey(raw: unknown): string | null {
  if (raw == null) return null
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim()
    return s || null
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    const o = raw as Record<string, unknown>
    const id = o.id ?? o.userId
    if (id != null && typeof id !== 'object') {
      const s = String(id).trim()
      return s || null
    }
  }
  return null
}

export function isLookupDisplayColumn(col: MainTableViewFieldColumn): boolean {
  return col.columnType === 'lookup_display'
}

export function isFkDisplayColumn(col: MainTableViewFieldColumn): boolean {
  return col.columnType === 'fk_display'
}

export function isLookupRelatedColumn(col: MainTableViewFieldColumn): boolean {
  return !!col.isLookup && col.lookupTableId != null
}

function pickAttributeFromRow(
  row: Record<string, unknown>,
  attribute: string,
): unknown {
  if (attribute in row) return row[attribute]
  // camelCase / snake_case aliases common on sys_users
  const aliases: Record<string, string[]> = {
    full_name: ['fullName', 'full_name'],
    fullName: ['full_name', 'fullName'],
    display_name: ['displayName', 'display_name'],
    displayName: ['display_name', 'displayName'],
  }
  for (const alt of aliases[attribute] || []) {
    if (alt in row) return row[alt]
  }
  return undefined
}

/**
 * Format a Main Table View cell that may be a lookup / lookup_display column.
 * When {@code hydratedRow} is provided (resolved relation/user row), prefer it over the raw cell.
 */
export function formatLookupAwareMainTableViewCell(
  col: MainTableViewFieldColumn,
  rawValue: unknown,
  hydratedRow?: Record<string, unknown> | null,
): string {
  const row = hydratedRow
    || (rawValue && typeof rawValue === 'object' && !Array.isArray(rawValue)
      ? rawValue as Record<string, unknown>
      : null)

  if (isLookupDisplayColumn(col) && col.lookupDisplayField) {
    if (row) {
      const attr = pickAttributeFromRow(row, col.lookupDisplayField)
      if (attr !== undefined && attr !== null && String(attr).trim() !== '') {
        return unwrapUserLikeValueToDisplayString(attr)
      }
    }
    // FALLBACK(ux): unresolved PK — show raw scalar rather than inventing a name
    if (rawValue != null && typeof rawValue !== 'object') {
      return String(rawValue)
    }
    return '-'
  }

  // Backend resolves fk_display against same-instance MAIN vars; value is already the attribute
  // (or unmatched FK scalar). Format like a normal cell.
  if (isFkDisplayColumn(col)) {
    return formatMainTableViewCell(rawValue)
  }

  if (isLookupRelatedColumn(col) && row) {
    const text = resolveLookupCellTagText({
      selectedDisplayField: col.lookupSelectedDisplayField || undefined,
      displayFields: col.lookupSearchFields || undefined,
      searchFields: col.lookupSearchFields || undefined,
    }, row)
    if (text && text !== '-') return text
  }

  return formatMainTableViewCell(rawValue)
}
