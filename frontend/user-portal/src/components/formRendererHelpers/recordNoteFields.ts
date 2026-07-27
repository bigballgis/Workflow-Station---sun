import type { FormField } from './formRendererTypes'

/**
 * RecordNote helpers shared by every sub-table display path (row Add/Edit
 * dialog, form-below-table inline form, Link Form modal).
 */

/**
 * Broadcast after a note is added / edited / deleted so the Change History panel can
 * refresh. A window event rather than props: the panel is a page-level sibling while the
 * panels that mutate notes sit arbitrarily deep inside forms and stacked row dialogs.
 */
export const RECORD_NOTE_CHANGED_EVENT = 'ws:record-note-changed'

export function notifyRecordNoteChanged(processInstanceId?: string | null): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(RECORD_NOTE_CHANGED_EVENT, { detail: { processInstanceId } }))
}

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
