/**
 * Multi-instance sub-process scope from Developer Workstation Process Design (BPMN extensions).
 * Uses configured Sub-table name + designer primary key — not hard-coded column names.
 *
 * Public surface is unchanged: row-key primitives live in {@code ./miParticipantRowKey}, BPMN
 * resolution in {@code ./miSubProcessScopeBpmn}, and both are re-exported here verbatim.
 */

import {
  bindingMatchesMiSubTableName,
  buildParticipantRowIdFromSubTableRow,
  extractMiParticipantRowIdFromCurrentItem,
  findBindingForMiSubTableName,
  hasConfiguredPrimaryKeyFields,
  miParticipantRowIdsEqual,
  rowMatchesSubTablePrimaryKey,
  type MiParticipantRowId,
  type SubTableBindingLike,
} from './miParticipantRowKey'
import type { MiSubProcessScopeConfig } from './miSubProcessScopeBpmn'

export type { MiParticipantRowId, SubTableBindingLike } from './miParticipantRowKey'
export type { MiSubProcessScopeConfig } from './miSubProcessScopeBpmn'

export {
  normalizeMiParticipantRowId,
  hasConfiguredPrimaryKeyFields,
  describeSubTableBindingLabel,
  bindingMatchesMiSubTableName,
  findBindingForMiSubTableName,
  miParticipantRowIdsEqual,
  rowMatchesSubTablePrimaryKey,
  expansionKeyMatchesParticipantRow,
  getSubTableRowValueIgnoreCase,
  subTableRowPkValue,
  participantRowIdFromPkMap,
  buildParticipantRowIdFromSubTableRow,
  extractMiParticipantRowIdFromCurrentItem,
} from './miParticipantRowKey'

export { resolveMiSubProcessScopeFromBpmn } from './miSubProcessScopeBpmn'

function rowHasAttachmentFile(row: unknown): boolean {
  if (!row || typeof row !== 'object') return false
  const rec = row as Record<string, unknown>
  const file = rec.file
  if (file == null || file === '') return false
  if (typeof file === 'string') return file.trim().length > 0
  if (Array.isArray(file)) return file.length > 0
  if (typeof file === 'object') return Object.keys(file as object).length > 0
  return true
}

export function extractParticipantRowIdFromVariables(
  variables: Record<string, unknown> | null | undefined,
  scope: MiSubProcessScopeConfig,
  primaryKeyFields?: string[] | null,
): MiParticipantRowId | null {
  if (!variables) return null
  const ci = (variables._currentItem ?? variables.currentItem) as Record<string, unknown> | undefined
  return extractMiParticipantRowIdFromCurrentItem(ci, primaryKeyFields, {
    rowIdVariable: scope.rowIdVariable,
  })
}

function extractUserIdFromAssigneeCell(raw: unknown): string | null {
  if (raw == null || raw === '') return null
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim()
    return s.length > 0 ? s : null
  }
  if (typeof raw === 'object') {
    const uid =
      (raw as { userId?: unknown; id?: unknown }).userId ?? (raw as { id?: unknown }).id
    if (uid == null || uid === '') return null
    const s = String(uid).trim()
    return s.length > 0 ? s : null
  }
  return null
}

/**
 * On My Request: locate the viewer's MI participant row id via BPMN assigneeField + designer PK.
 */
export function resolveViewerParticipantRowIdFromCollectionBinding(
  scope: MiSubProcessScopeConfig,
  collectionBinding: SubTableBindingLike & { data?: unknown[] },
  viewerUserId: string,
): MiParticipantRowId | null {
  if (!viewerUserId.trim() || !scope.assigneeField) return null
  const pk = (collectionBinding.primaryKeyFields ?? [])
    .map(f => String(f).trim())
    .filter(Boolean)
  if (pk.length === 0) return null

  const rows = Array.isArray(collectionBinding.data) ? collectionBinding.data : []
  for (const row of rows) {
    if (!row || typeof row !== 'object') continue
    const rec = row as Record<string, unknown>
    if (extractUserIdFromAssigneeCell(rec[scope.assigneeField]) !== viewerUserId.trim()) continue
    const participantId = buildParticipantRowIdFromSubTableRow(rec, pk)
    if (participantId != null) return participantId
  }
  return null
}

/** Filter bindings to the single MI participant row using Process Design sub-table PK. */
export function filterBindingsToMiParticipantRow<T extends SubTableBindingLike & { data?: unknown[] }>(
  bindings: T[],
  scope: MiSubProcessScopeConfig,
  participantRowId: MiParticipantRowId,
  options?: {
    includeParticipantScopedChildren?: boolean
    /** When the collection binding lives on another form (e.g. previousForms), pass its PK here. */
    participantPrimaryKeyFields?: string[] | null
    /** Shared relation-table id for MI collection bindings copied across forms (binding 66 vs 69). */
    collectionTableId?: number | null
  },
): void {
  const collectionBinding = findBindingForMiSubTableName(bindings, scope.subTableName)
  const participantPk =
    options?.participantPrimaryKeyFields ?? collectionBinding?.primaryKeyFields ?? null
  if (!hasConfiguredPrimaryKeyFields(participantPk)) return
  const includeChildren = options?.includeParticipantScopedChildren !== false
  const collectionTid =
    options?.collectionTableId ??
    (collectionBinding?.tableId != null && Number.isFinite(Number(collectionBinding.tableId))
      ? Number(collectionBinding.tableId)
      : null)

  for (const binding of bindings) {
    const isCollection =
      bindingMatchesMiSubTableName(binding, scope.subTableName)
      || (collectionTid != null
        && binding.tableId != null
        && Number(binding.tableId) === collectionTid)
    if (!isCollection && !includeChildren) continue

    const rows = Array.isArray(binding.data) ? binding.data : []
    if (rows.length === 0) continue

    if (isCollection) {
      binding.data = rows.filter(row =>
        rowMatchesSubTablePrimaryKey(row, participantRowId, participantPk),
      ) as T['data']
      continue
    }

    if (!includeChildren) continue

    const fk = String(binding.foreignKeyField || '').trim()
    const fkIsOwnPk =
      participantPk?.some(p => String(p).trim() === fk) ||
      (fk.toLowerCase() === 'id' && !bindingMatchesMiSubTableName(binding, scope.subTableName))

    const filtered = rows.filter(row => {
      if (!row || typeof row !== 'object') return false
      const rec = row as Record<string, unknown>
      if (
        fk &&
        !fkIsOwnPk &&
        rec[fk] != null &&
        miParticipantRowIdsEqual(rec[fk], participantRowId)
      ) {
        return true
      }
      if (rowMatchesSubTablePrimaryKey(row, participantRowId, participantPk)) {
        return true
      }
      // HMDC-style child tables (FK = parent PK e.g. row_id): case-level attachment rows often
      // have file only — keep them visible for the active MI participant / initiator snapshot.
      if (fkIsOwnPk && rowHasAttachmentFile(row)) {
        return true
      }
      return false
    })
    if (filtered.length > 0) {
      binding.data = filtered as T['data']
    }
  }
}
