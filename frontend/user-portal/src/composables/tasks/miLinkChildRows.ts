/**
 * MI link-child row set operations: per-participant collapse, PK backfill from variables,
 * picking child rows for an MI parent row and expansion-id row lookup.
 */

import {
  isAllocatedUuidPrimaryKey,
  MI_STRUCTURAL_PARENT_FK_FIELDS,
  normalizeMiLinkMatchId,
} from './internal'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { isMiParticipantScopedSubTableBinding } from './subTableBindingKinds'
import { getSavedSubTableRows } from './subTableSliceResolve'
import {
  linkChildRowIsForeignParticipantPlaceholder,
  miLinkChildRowBusinessFieldRank,
  miParentRowAlignsWithChildRow,
  resolveMiChildStructuralParentFk,
  rowMatchesMiExpansionId,
  scoreMiLinkChildRowQuality,
} from './miLinkChildIdentity'

function pickAllocatedUuidFromLinkChildGroup(group: Record<string, unknown>[]): string | undefined {
  for (const r of group) {
    const id = r.id
    if (id != null && isAllocatedUuidPrimaryKey(id)) return String(id).trim()
  }
  return undefined
}

function resolveParticipantMergePkField(group: Record<string, unknown>[]): string {
  for (const rec of group) {
    for (const fk of MI_STRUCTURAL_PARENT_FK_FIELDS) {
      if (normalizeMiLinkMatchId(rec[fk])) return fk
    }
  }
  return 'sub_task_id'
}

/**
 * After {@link repairMisassignedPrimaryKeyFromParentId} clears a misassigned {@code id}, re-copy the
 * allocated PK from the persisted variables slice when the in-memory row still carries form payload
 * but lost its id. Without this, inline form-below-table reads nested stubs with empty id even though
 * {@code __subTables__} already holds the UUID from a prior Save.
 */
export function backfillMiLinkChildPrimaryKeysFromVariables<
  T extends {
    bindingId: number
    tableName?: string
    physicalTableName?: string
    data: any[]
    foreignKeyField?: string | null
    columns?: Array<{ field?: string }> | null
  },
>(
  bindings: T[],
  savedSubTables: Record<string, unknown> | null | undefined,
  myRowId: string | number | null | undefined,
): void {
  if (!savedSubTables || typeof savedSubTables !== 'object') return
  for (const binding of bindings) {
    if (!isMiParticipantScopedSubTableBinding(binding)) continue
    const saved = getSavedSubTableRows(savedSubTables, binding) ?? []
    if (!Array.isArray(binding.data) || binding.data.length === 0) continue
    for (let i = 0; i < binding.data.length; i++) {
      const row = binding.data[i]
      if (!row || typeof row !== 'object') continue
      const rec = row as Record<string, unknown>
      const existingId = rec.id
      if (existingId != null && String(existingId).trim() !== '') continue
      // A sibling participant's placeholder row (id_idw points elsewhere, no structural FK) must NOT inherit
      // the current participant's allocated id — that collides PKs and collapse then leaks its id_idw onto the
      // current row (#1444). Only backfill rows that actually belong to the current participant.
      if (myRowId != null && linkChildRowIsForeignParticipantPlaceholder(rec, myRowId)) continue
      const participantKey =
        resolveMiChildStructuralParentFk(rec)
        ?? (myRowId != null ? normalizeMiLinkMatchId(myRowId) : null)
      if (!participantKey) continue
      const donor = saved.find(s => {
        if (!s || typeof s !== 'object') return false
        const sr = s as Record<string, unknown>
        const sid = sr.id
        if (sid == null || String(sid).trim() === '') return false
        const sidNorm = normalizeMiLinkMatchId(sid)
        if (!sidNorm || sidNorm === participantKey) return false
        // Participant identity of the saved row: structural parent FK first, else its id_idw
        // (the participant discriminator for People-style link children whose PK is plain `id`).
        // NEVER the donor's own `id`: that is the allocated UUID, which can never equal the
        // parent's id_idw — the old `?? sidNorm` fallback made FK-less donors unmatchable, so
        // hydration lost the persisted UUID and every Save re-allocated a fresh PK (id churn).
        const sParticipant =
          resolveMiChildStructuralParentFk(sr) ?? normalizeMiLinkMatchId(sr.id_idw)
        return sParticipant != null && sParticipant === participantKey
      }) as Record<string, unknown> | undefined
      if (donor?.id != null && String(donor.id).trim() !== '') {
        binding.data[i] = { ...rec, id: donor.id }
      }
    }
  }
}

/** When multiple link-child rows share the same participant FK, merge payloads (sub form1 → sub form2). */
export function collapseMiLinkChildRowsToOnePerParticipant(rows: unknown[]): any[] {
  if (!Array.isArray(rows) || rows.length <= 1) return Array.isArray(rows) ? [...rows] : []
  const byParticipant = new Map<string, Record<string, unknown>[]>()
  const ungrouped: Record<string, unknown>[] = []
  for (const raw of rows) {
    if (!raw || typeof raw !== 'object') continue
    const rec = raw as Record<string, unknown>
    const pid = resolveMiChildStructuralParentFk(rec)
    if (!pid) {
      ungrouped.push(rec)
      continue
    }
    const g = byParticipant.get(pid) ?? []
    g.push(rec)
    byParticipant.set(pid, g)
  }
  const out: any[] = [...ungrouped]
  for (const group of byParticipant.values()) {
    if (group.length === 1) {
      out.push(group[0])
      continue
    }
    const pkField = resolveParticipantMergePkField(group)
    const sorted = [...group].sort(
      (a, b) =>
        miLinkChildRowBusinessFieldRank(a) - miLinkChildRowBusinessFieldRank(b)
        || scoreMiLinkChildRowQuality(a) - scoreMiLinkChildRowQuality(b),
    )
    let merged: any[] = []
    for (const r of sorted) {
      merged = mergeSubTableRowsByRowId(merged, [r], [pkField])
    }
    const allocatedId = pickAllocatedUuidFromLinkChildGroup(group)
    const row = merged[0] ?? sorted[sorted.length - 1]
    if (row && allocatedId) {
      out.push({ ...(row as Record<string, unknown>), id: allocatedId })
    } else if (row) {
      out.push(row)
    }
  }
  return out
}

/**
 * MI nested link-form slices often split placeholder ({@code id}, {@code task_status}) and real fields across
 * multiple objects in the same array — fold into one row for modal / binding hydration.
 */
export function collapseSubTableRowsPreferFilled(rows: any[]): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  if (rows.length === 1) return [...rows]
  // Same participant split across placeholder + payload fragments; first-non-empty used to
  // freeze stale task_current_node (sub form1) — use MI-aware row merge instead.
  return mergeSubTableRowsByRowId([], rows, ['id'])
}

/** Rows in a link-child binding slice that belong to the given MI parent participant row. */
export function pickMiLinkChildRowsForParent(
  parentRow: Record<string, unknown>,
  candidateRows: unknown[],
  primaryKeyFields?: string[] | null,
): any[] {
  if (!Array.isArray(candidateRows) || candidateRows.length === 0) return []
  const matched = candidateRows.filter(
    r =>
      r &&
      typeof r === 'object' &&
      miParentRowAlignsWithChildRow(parentRow, r as Record<string, unknown>),
  )
  if (matched.length === 0) return []
  const deduped = collapseMiLinkChildRowsToOnePerParticipant(matched)
  return mergeSubTableRowsByRowId([], deduped, primaryKeyFields ?? null)
}

/** Find the participant row in a sub-table binding for the current MI sub-task. */
export function findSubTableRowByMiExpansionId(
  rows: unknown[],
  miRowId: string | number | null | undefined,
): Record<string, unknown> | null {
  if (miRowId == null || String(miRowId).trim() === '') return null
  if (!Array.isArray(rows)) return null
  for (const row of rows) {
    if (row && typeof row === 'object' && rowMatchesMiExpansionId(row as Record<string, unknown>, miRowId)) {
      return row as Record<string, unknown>
    }
  }
  return null
}

/**
 * MI assignee todo: after {@link isolateMiSubTaskData} the parent sub-table usually has exactly one row for this
 * participant, but {@code _currentItem.rowId} may be the designer PK ({@code id_idw}) while the hydrated row only
 * exposes SQL {@code id} (e.g. 6532) — strict expansion match fails and link-form inline subtable2 stays empty.
 */
export function findMiIsolatedParentRow(
  rows: unknown[],
  miRowId: string | number | null | undefined,
): Record<string, unknown> | null {
  const matched = findSubTableRowByMiExpansionId(rows, miRowId)
  if (matched) return matched
  if (!Array.isArray(rows) || rows.length !== 1) return null
  const only = rows[0]
  if (!only || typeof only !== 'object') return null
  const rec = only as Record<string, unknown>
  if (miRowId != null && String(miRowId).trim() !== '') {
    const rowIdw = normalizeMiLinkMatchId(rec.id_idw)
    const mid = normalizeMiLinkMatchId(miRowId)
    if (rowIdw && mid && rowIdw !== mid) return null
  }
  return rec
}

/**
 * Gather child-table rows nested under {@code parentRows[*].__subTables__} for the given child binding.
 * Exported for FormRenderer inline form-below-table when {@code target.data} is thin/empty but rows nest under
 * parent rows (e.g. legacy {@code bindingId} keys after BPMN form copy).
 */
export function scopeMiLinkChildRowsForParentRow(
  parentRow: Record<string, unknown>,
  candidateRows: unknown[],
): Record<string, unknown>[] {
  if (!Array.isArray(candidateRows)) return []
  return candidateRows.filter(
    (r): r is Record<string, unknown> =>
      !!r &&
      typeof r === 'object' &&
      miParentRowAlignsWithChildRow(parentRow, r as Record<string, unknown>),
  )
}
