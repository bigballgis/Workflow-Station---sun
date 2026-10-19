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
 * Stable note anchor: the declared single PK, or the persisted platform UUID for
 * composite/keyless rows. Business aliases do not establish row ownership.
 */
export function resolveRowStableId(
  row: Record<string, unknown> | null | undefined,
  primaryKeyFields?: string[] | null,
): string | null {
  if (!row || typeof row !== 'object') return null
  const pk = primaryKeyFields?.length === 1 ? primaryKeyFields[0] : undefined
  const value = pk ? row[pk] : row.platformRowUuid
  return value != null && String(value).trim() !== '' ? String(value) : null
}

/** Shared by To Do, New Request, My Request/Audit and nested form extraction. */
export function createRecordNoteFormField(props: Record<string, unknown> = {}): FormField {
  const scope = props.scope === 'TABLE' ? 'TABLE' : 'RECORD'
  return {
    key: typeof props.controlId === 'string' ? `__recordNote_${props.controlId}` : `__recordNote_${scope.toLowerCase()}`,
    label: '', type: 'recordNote', span: 24,
    _recordNote: {
      formKey: typeof props.formKey === 'string' ? props.formKey : undefined,
      controlId: typeof props.controlId === 'string' ? props.controlId : undefined,
      scope,
      panelTitle: typeof props.panelTitle === 'string' ? props.panelTitle : undefined,
      allowAttachment: props.allowAttachment !== false,
      maxFileSizeMb: Number(props.maxFileSizeMb) || 10,
      allowEditOwn: props.allowEditOwn !== false,
      allowDelete: props.allowDelete === true,
      readonly: props.readonly === true,
      pageSize: Number(props.pageSize) || 5,
    },
  }
}
