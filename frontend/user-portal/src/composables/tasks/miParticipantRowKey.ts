/**
 * Multi-instance participant row-id primitives: PK column reads + designer-key matching.
 * Pure helpers shared by miSubProcessScope; no BPMN / DOM dependency. Behaviour-preserving extract.
 */

export type MiParticipantRowId = string | number

export type SubTableBindingLike = {
  bindingId?: number | string
  tableName?: string
  physicalTableName?: string
  tableId?: number | null
  primaryKeyFields?: string[] | null
  columns?: Array<{ field?: string }> | null
  foreignKeyField?: string | null
}

function normalizeSubTableNameLocal(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

function stripLinkFormDesignerTableLabelLocal(raw?: string): string {
  return String(raw || '').trim().replace(/^ADD\s*\+\s*/i, '').trim()
}

function compactTableKey(name?: string | null): string {
  return normalizeSubTableNameLocal(stripLinkFormDesignerTableLabelLocal(String(name || ''))).replace(/\s+/g, '')
}

/** Flowable {@code _currentItem.rowId} — numeric or string (UUID) primary key value. */
export function normalizeMiParticipantRowId(raw: unknown): MiParticipantRowId | null {
  if (raw == null) return null
  if (typeof raw === 'number' && !Number.isNaN(raw)) return raw
  if (typeof raw === 'string') {
    const s = raw.trim()
    return s.length > 0 ? s : null
  }
  const s = String(raw).trim()
  return s.length > 0 ? s : null
}

export function hasConfiguredPrimaryKeyFields(primaryKeyFields?: string[] | null): boolean {
  return (primaryKeyFields ?? []).some(f => String(f).trim().length > 0)
}

export function describeSubTableBindingLabel(binding: SubTableBindingLike): string {
  return (
    binding.physicalTableName?.trim() ||
    binding.tableName?.trim() ||
    (binding.bindingId != null ? String(binding.bindingId) : '')
  )
}

/** Compare binding labels to BPMN {@code subTableName}. */
export function bindingMatchesMiSubTableName(
  binding: SubTableBindingLike,
  subTableName: string | null | undefined,
): boolean {
  if (!subTableName || !String(subTableName).trim()) return false
  const want = compactTableKey(subTableName)
  if (!want) return false
  const candidates = [binding.physicalTableName, binding.tableName].filter(Boolean) as string[]
  return candidates.some(c => compactTableKey(c) === want)
}

export function findBindingForMiSubTableName<T extends SubTableBindingLike>(
  bindings: T[],
  subTableName: string | null | undefined,
): T | undefined {
  if (!subTableName) return undefined
  return bindings.find(b => bindingMatchesMiSubTableName(b, subTableName))
}

/** Compare sub-table cell value to MI participant row id (numeric or string / UUID). */
export function miParticipantRowIdsEqual(a: unknown, b: MiParticipantRowId): boolean {
  if (a == null || a === '') return false
  const bs = String(b).trim()
  if (bs !== '' && String(a).trim() === bs) return true
  const an = Number(a)
  const bn = Number(b)
  return !Number.isNaN(an) && !Number.isNaN(bn) && an === bn
}

function readJuelPath(obj: Record<string, unknown>, path: string): unknown {
  const parts = path.split('.').filter(Boolean)
  let cur: unknown = obj
  for (const p of parts) {
    if (!cur || typeof cur !== 'object') return undefined
    cur = (cur as Record<string, unknown>)[p]
  }
  return cur
}

/** Case-insensitive field read on sub-table / Flowable collection rows. */
export function getSubTableRowValueIgnoreCase(
  row: Record<string, unknown>,
  field: string,
): unknown {
  if (Object.prototype.hasOwnProperty.call(row, field)) return row[field]
  const fl = field.toLowerCase()
  for (const k of Object.keys(row)) {
    if (k.toLowerCase() === fl) return row[k]
  }
  return undefined
}

/** PK column value from row envelope and/or nested {@code rowKey} (aligns with backend rowKeyFromVariableRow). */
export function subTableRowPkValue(row: Record<string, unknown>, pkField: string): unknown {
  let v = getSubTableRowValueIgnoreCase(row, pkField)
  if (v != null && String(v).trim() !== '') return v
  const rk = row.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    v = getSubTableRowValueIgnoreCase(rk as Record<string, unknown>, pkField)
  }
  if (v != null && String(v).trim() !== '') return v
  if (pkField.toLowerCase() !== 'rowid') {
    v = getSubTableRowValueIgnoreCase(row, 'rowId')
  }
  return v
}

/**
 * Match a sub-table row to a MI participant id using designer {@code primaryKeyFields} only.
 * When PK metadata is missing, returns {@code false} — callers must surface a configuration error.
 */
export function rowMatchesSubTablePrimaryKey(
  row: unknown,
  participantRowId: MiParticipantRowId,
  primaryKeyFields?: string[] | null,
): boolean {
  if (!row || typeof row !== 'object') return false
  const rec = row as Record<string, unknown>
  const pks = (primaryKeyFields ?? []).map(f => String(f).trim()).filter(Boolean)
  if (pks.length === 0) return false

  if (pks.length === 1) {
    return miParticipantRowIdsEqual(subTableRowPkValue(rec, pks[0]!), participantRowId)
  }
  const parts = String(participantRowId).split('|').map(s => s.trim())
  if (parts.length === pks.length) {
    return pks.every((pk, i) => miParticipantRowIdsEqual(subTableRowPkValue(rec, pk), parts[i]!))
  }
  return pks.every(pk => miParticipantRowIdsEqual(subTableRowPkValue(rec, pk), participantRowId))
}

/** Match MI participant row via designer {@code primaryKeyFields} (no legacy column fallbacks). */
export function expansionKeyMatchesParticipantRow(
  row: unknown,
  myRowId: MiParticipantRowId,
  primaryKeyFields?: string[] | null,
): boolean {
  return rowMatchesSubTablePrimaryKey(row, myRowId, primaryKeyFields)
}

/**
 * Build MI participant row id from a PK map using designer column order.
 * Single PK → scalar; composite → {@code v1|v2|...}.
 */
export function participantRowIdFromPkMap(
  map: Record<string, unknown>,
  primaryKeyFields: string[],
): MiParticipantRowId | null {
  const pks = primaryKeyFields.map(f => String(f).trim()).filter(Boolean)
  if (pks.length === 0) return null

  if (pks.length === 1) {
    return normalizeMiParticipantRowId(subTableRowPkValue(map, pks[0]!))
  }

  const parts: string[] = []
  for (const pk of pks) {
    const v = subTableRowPkValue(map, pk)
    if (v == null || String(v).trim() === '') return null
    parts.push(String(v).trim())
  }
  return parts.join('|')
}

/** Build participant row id from a hydrated sub-table row + designer PK fields. */
export function buildParticipantRowIdFromSubTableRow(
  row: unknown,
  primaryKeyFields?: string[] | null,
): MiParticipantRowId | null {
  if (!row || typeof row !== 'object') return null
  const pks = (primaryKeyFields ?? []).map(f => String(f).trim()).filter(Boolean)
  if (pks.length === 0) return null
  return participantRowIdFromPkMap(row as Record<string, unknown>, pks)
}

/**
 * Resolve the current MI participant row id from Flowable {@code _currentItem} using designer PK fields.
 * Mirrors {@code SubTableRowKeySupport.rowKeyFromCurrentItem}: prefer {@code rowKey} map; single PK may use {@code rowId}.
 */
export function extractMiParticipantRowIdFromCurrentItem(
  currentItem: Record<string, unknown> | null | undefined,
  primaryKeyFields?: string[] | null,
  options?: { rowIdVariable?: string | null },
): MiParticipantRowId | null {
  if (!currentItem || typeof currentItem !== 'object') return null
  const pks = (primaryKeyFields ?? []).map(f => String(f).trim()).filter(Boolean)
  const rowIdVar = (options?.rowIdVariable ?? 'currentItem.rowId').trim()
  const juelPath = rowIdVar.replace(/^currentItem\./, '')

  const rawRowKey = currentItem.rowKey
  if (rawRowKey && typeof rawRowKey === 'object' && !Array.isArray(rawRowKey) && pks.length > 0) {
    const fromRowKey = participantRowIdFromPkMap(rawRowKey as Record<string, unknown>, pks)
    if (fromRowKey != null) return fromRowKey
  }

  if (juelPath && juelPath !== 'rowId') {
    const fromPath = readJuelPath(currentItem, juelPath)
    if (fromPath != null) {
      if (typeof fromPath === 'object' && !Array.isArray(fromPath) && pks.length > 0) {
        const fromMap = participantRowIdFromPkMap(fromPath as Record<string, unknown>, pks)
        if (fromMap != null) return fromMap
      }
      const scalar = normalizeMiParticipantRowId(fromPath)
      if (scalar != null) return scalar
    }
  }

  if (pks.length === 1) {
    const col = pks[0]!
    let v = getSubTableRowValueIgnoreCase(currentItem, 'rowId')
    if (v == null) v = getSubTableRowValueIgnoreCase(currentItem, col)
    return normalizeMiParticipantRowId(v)
  }

  if (pks.length > 1) {
    return participantRowIdFromPkMap(currentItem, pks)
  }

  return normalizeMiParticipantRowId(currentItem.rowId)
}
