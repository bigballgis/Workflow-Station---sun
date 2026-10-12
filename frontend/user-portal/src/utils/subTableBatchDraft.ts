/**
 * Pure, side-effect-free merge for the Batch Edit workspace.  It deliberately
 * operates on complete rows: fields not present in an edit patch survive unchanged.
 * Persistence, row scope and permission checks remain server-side concerns.
 */
export type BatchFieldIntent =
  | { kind: 'set'; value: unknown }
  | { kind: 'clear' }

export type BatchRowPatch = {
  rowId: string
  fields: Record<string, BatchFieldIntent>
}

export type BatchUniformPatch = {
  rowIds: string[]
  fields: Record<string, BatchFieldIntent>
}

export interface BatchDraftChangeSet {
  rowPatches?: BatchRowPatch[]
  uniformPatches?: BatchUniformPatch[]
  deletedRowIds?: string[]
}

function applyFields(row: Record<string, unknown>, fields: Record<string, BatchFieldIntent>): Record<string, unknown> {
  const next = { ...row }
  for (const [field, intent] of Object.entries(fields)) {
    // A clear is intentional and distinct from omitting the field from the patch.
    next[field] = intent.kind === 'clear' ? null : intent.value
  }
  return next
}

/**
 * Applies the current workspace's row edits followed by its explicit uniform edits.
 * A deleted row is never reintroduced by a field patch; this makes a mixed
 * edit/delete batch normalize to a single delete operation for that identity.
 */
export function applyBatchDraft<T extends Record<string, unknown>>(
  rows: readonly T[],
  rowIdentity: (row: T) => string,
  changes: BatchDraftChangeSet,
): T[] {
  const deleted = new Set(changes.deletedRowIds || [])
  const patches = new Map((changes.rowPatches || []).map((patch) => [patch.rowId, patch.fields]))
  const uniform = changes.uniformPatches || []

  return rows
    .filter((row) => !deleted.has(rowIdentity(row)))
    .map((row) => {
      const rowId = rowIdentity(row)
      let next: Record<string, unknown> = row
      const rowPatch = patches.get(rowId)
      if (rowPatch) next = applyFields(next, rowPatch)
      for (const patch of uniform) {
        if (patch.rowIds.includes(rowId)) next = applyFields(next, patch.fields)
      }
      return next as T
    })
}
