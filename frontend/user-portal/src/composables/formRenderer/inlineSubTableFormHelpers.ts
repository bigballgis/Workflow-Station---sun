import { scoreMiLinkChildRowQuality } from '../tasks/shared'
import {
  bindingForeignKeyFieldIsRowPrimaryKey,
  seedLinkChildForeignKeysFromParentRow,
} from '../../utils/subTableRowRuntime'
import type { SubTableBinding } from './useSubTableBindings'

// ---------------------------------------------------------------------------
// Pure (stateless) helpers for the inline form-below-table runtime.
// Extracted from useInlineSubTableForm to keep that composable under the size cap;
// behavior is byte-for-byte identical — these take explicit arguments only.
// ---------------------------------------------------------------------------

/** MI link-form inline: pre-fill empty FK columns (People.id / sub_task_id ← parent id_idw). */
export function seedMiLinkInlineRowFkFromParent(
  row: Record<string, any> | null,
  target: SubTableBinding,
  parentId: string | number | null | undefined,
  isLinkTarget: boolean,
  parentBinding?: SubTableBinding | null,
  parentRow?: Record<string, unknown> | null,
): Record<string, any> | null {
  if (!row || parentId == null || String(parentId).trim() === '') return row
  if (!isLinkTarget) return row
  const parentParticipantRow =
    parentRow && typeof parentRow === 'object'
      ? parentRow
      : ({ id_idw: parentId } as Record<string, unknown>)
  const parentTableId =
    parentBinding?.tableId != null && Number.isFinite(Number(parentBinding.tableId))
      ? Number(parentBinding.tableId)
      : null
  return seedLinkChildForeignKeysFromParentRow(row, target.fieldDefinitions, {
    bindingForeignKeyField: target.foreignKeyField,
    bindingLinkMode: target.bindingLinkMode,
    primaryKeyFields: target.primaryKeyFields,
    parentParticipantRow,
    parentTableId,
    legacyFkSeed: parentId,
  }) as Record<string, any>
}

/** FK candidates used to align a child (linkForm target) row to a parent row. */
export function resolveLinkFkCandidates(target: SubTableBinding): string[] {
  const list: string[] = []
  for (const fd of target.fieldDefinitions ?? []) {
    if (fd.isForeignKey && fd.fieldName && !list.includes(fd.fieldName)) {
      list.push(fd.fieldName)
    }
  }
  const explicit = (target as any).foreignKeyField
  if (
    explicit
    && String(explicit).trim()
    && !bindingForeignKeyFieldIsRowPrimaryKey(String(explicit), {
      primaryKeyFields: target.primaryKeyFields,
      fieldDefinitions: target.fieldDefinitions,
    })
    && !list.includes(String(explicit))
  ) {
    list.push(String(explicit))
  }
  for (const k of ['sub_task_id', 'participant_id', 'participantId', 'parent_id', 'parentId', 'meeting_participant_id']) {
    if (!list.includes(k)) list.push(k)
  }
  return list
}

/** Match sub-table row to Flowable multi-instance element id (designer PK e.g. id_idw). */
export function rowMatchesMiElementId(rec: Record<string, unknown>, parentId: string | number): boolean {
  const keys = ['id', 'rowId', 'id_idw', 'ID', 'RowId'] as const
  for (const k of keys) {
    const v = rec[k]
    if (v != null && v !== '' && String(v) === String(parentId)) return true
  }
  return false
}

/**
 * Persisted {@code target.data} may be empty/thin while child rows still live under parent rows'
 * {@code __subTables__}; merge those for inline form-below-table display and save.
 */
export function buildBindingTableIdMap(peers: SubTableBinding[]): Map<number, number | null> {
  const m = new Map<number, number | null>()
  for (const b of peers) {
    const tid = b.tableId != null ? Number(b.tableId) : null
    if (tid != null && Number.isFinite(tid)) m.set(b.bindingId, tid)
  }
  return m
}

export function findInlineRowIndexForMi(
  rows: any[],
  pack: { target: SubTableBinding; isLinkTarget: boolean },
  parentId: string | number | null | undefined,
): number {
  if (parentId == null || String(parentId).trim() === '') return -1
  const fkList = resolveLinkFkCandidates(pack.target)
  const matched: number[] = []
  rows.forEach((r, i) => {
    if (!r || typeof r !== 'object') return
    const rec = r as Record<string, unknown>
    const hit = fkList.some(k => {
      const v = rec[k]
      return v != null && v !== '' && String(v) === String(parentId)
    })
    if (hit) matched.push(i)
  })
  if (matched.length === 1) return matched[0]!
  if (matched.length > 1) {
    let bestIdx = matched[0]!
    let bestScore = scoreMiLinkChildRowQuality(rows[bestIdx] as Record<string, unknown>)
    for (let j = 1; j < matched.length; j++) {
      const idx = matched[j]!
      const s = scoreMiLinkChildRowQuality(rows[idx] as Record<string, unknown>)
      if (s > bestScore) {
        bestIdx = idx
        bestScore = s
      }
    }
    return bestIdx
  }
  let idx = rows.findIndex(r => {
    if (!r || typeof r !== 'object') return false
    return rowMatchesMiElementId(r as Record<string, unknown>, parentId)
  })
  return idx
}
