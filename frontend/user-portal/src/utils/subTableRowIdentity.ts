/**
 * Sub-table JSON-row identity. Mirrors backend {@code SubTableRowIdentity}:
 * assign {@code row_id} once before any binding/name alias is stamped so deserialized
 * copies keep the same identity instead of each receiving a new UUID.
 */
export const SUB_TABLE_IDENTITY_FIELDS = [
  'row_id',
  'rowId',
  'rowID',
  'id_idw',
  '_rowKey',
  'rowKey',
  'id',
] as const

function identityValue(value: unknown): string | null {
  if (value == null) return null
  const text = String(value).trim()
  return text.length > 0 ? text : null
}

function newRowId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `row_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

export function rowHasIdentity(row: Record<string, unknown>): boolean {
  return SUB_TABLE_IDENTITY_FIELDS.some(field => identityValue(row[field]) != null)
}

/** @return true when a {@code row_id} was assigned */
export function ensureRowIdentity(row: Record<string, unknown>): boolean {
  if (rowHasIdentity(row)) return false
  row.row_id = newRowId()
  return true
}

export function ensureSliceRowIdentities(rows: unknown[]): number {
  if (!Array.isArray(rows)) return 0
  let assigned = 0
  for (const item of rows) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) continue
    const row = item as Record<string, unknown>
    if (ensureRowIdentity(row)) assigned += 1
    const nested = row.__subTables__
    if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
      assigned += ensureSubTableMapIdentities(nested as Record<string, unknown>)
    }
  }
  return assigned
}

function isNumericSliceKey(key: string): boolean {
  return key.length > 0 && /^\d+$/.test(key)
}

/**
 * Assign identity on canonical numeric binding-id slices first. Name aliases of
 * those slices must not receive a second UUID — JSON copies would otherwise
 * look like row add + delete in Change History.
 */
export function ensureSubTableMapIdentities(subTables: Record<string, unknown>): number {
  const numericKeys: string[] = []
  const aliasKeys: string[] = []
  for (const key of Object.keys(subTables)) {
    if (isNumericSliceKey(key)) numericKeys.push(key)
    else aliasKeys.push(key)
  }
  const keys = numericKeys.length > 0 ? numericKeys : aliasKeys
  let assigned = 0
  for (const key of keys) {
    const value = subTables[key]
    if (Array.isArray(value)) assigned += ensureSliceRowIdentities(value)
  }
  return assigned
}
