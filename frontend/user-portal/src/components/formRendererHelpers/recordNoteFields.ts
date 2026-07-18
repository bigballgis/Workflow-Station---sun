import type { FormField } from './formRendererTypes'

/**
 * RecordNote helpers shared by every sub-table display path (row Add/Edit
 * dialog, form-below-table inline form, Link Form modal).
 */

/** All recordNote components in a sub-form field tree, layout containers included. */
export function collectRecordNoteFields(fields: FormField[] | undefined | null): FormField[] {
  if (!Array.isArray(fields)) return []
  const out: FormField[] = []
  for (const f of fields) {
    if (!f) continue
    if (f.type === 'recordNote') out.push(f)
    if (Array.isArray(f.children)) out.push(...collectRecordNoteFields(f.children))
  }
  return out
}

/**
 * Stable identity of a sub-table row — the RECORD-scope note anchor. Resolution
 * mirrors subTableRowMerge: declared PK first, then rowId, then the platform
 * id / id_idw alias pair. Unsaved rows have none (callers show a hint).
 */
export function resolveRowStableId(
  row: Record<string, unknown> | null | undefined,
  primaryKeyFields?: string[] | null,
): string | null {
  if (!row || typeof row !== 'object') return null
  const candidates: unknown[] = []
  const pk = primaryKeyFields?.[0]
  if (pk) candidates.push((row as Record<string, unknown>)[pk])
  candidates.push(row.rowId, row.id, row.id_idw)
  for (const v of candidates) {
    if (v != null && String(v).trim() !== '') return String(v)
  }
  return null
}
