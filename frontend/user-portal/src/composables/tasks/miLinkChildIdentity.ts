/**
 * MI link-child row identity: structural FK resolution, participant ownership checks,
 * parent/child row pairing and row quality scoring.
 */

import {
  isAllocatedUuidPrimaryKey,
  MI_LINK_CHILD_SCALAR_KEYS,
  MI_STRUCTURAL_PARENT_FK_FIELDS,
  normalizeMiLinkMatchId,
} from './internal'

/** Count designer business columns filled on a link-child row (excludes id/FK/MI meta). */
export function miLinkChildRowBusinessFieldRank(row: Record<string, unknown>): number {
  let n = 0
  for (const [k, v] of Object.entries(row)) {
    if (k.startsWith('__') || MI_LINK_CHILD_SCALAR_KEYS.has(k)) continue
    if (k === 'sub_task_id' || k === 'main_id') continue
    if (v === undefined || v === null) continue
    if (typeof v === 'string' && v.trim() === '') continue
    n++
  }
  return n
}

/**
 * MI parent row ↔ child binding row pairing for nested {@code __subTables__} patch.
 * Must be the same multi-instance element — never match on shared {@code task_status} alone (all active rows are IN_PROGRESS).
 */
/** First non-empty structural FK on a link-child row (e.g. People.sub_task_id → participant). */
export function resolveMiChildStructuralParentFk(childRow: Record<string, unknown>): string | null {
  for (const fk of MI_STRUCTURAL_PARENT_FK_FIELDS) {
    const cv = normalizeMiLinkMatchId(childRow[fk])
    if (cv) return cv
  }
  return null
}

/**
 * Legacy People rows sometimes carry another participant's stale {@code sub_task_id} while
 * {@code id}/{@code id_idw} already match the current MI element (sub form1 save → sub form2 load).
 * Participant filter would drop those rows and lose age/sex/name from the prior step.
 */
export function repairMisassignedLinkChildStructuralFk(
  row: Record<string, unknown>,
  participantId: string | number,
): Record<string, unknown> {
  const pid = normalizeMiLinkMatchId(participantId)
  if (!pid) return row

  const structuralFk = resolveMiChildStructuralParentFk(row)
  if (structuralFk === pid) return row

  const childIdIdw = normalizeMiLinkMatchId(row.id_idw)
  const legacyId = normalizeMiLinkMatchId(row.id)
  const rowKeyedToParticipant =
    (childIdIdw === pid && !isAllocatedUuidPrimaryKey(childIdIdw))
    || (legacyId === pid && !isAllocatedUuidPrimaryKey(legacyId))

  if (!rowKeyedToParticipant) return row

  const out = { ...row }
  for (const fk of MI_STRUCTURAL_PARENT_FK_FIELDS) {
    const cv = normalizeMiLinkMatchId(out[fk])
    if (!cv || cv !== pid) {
      out[fk] = pid
    }
  }
  return out
}

/**
 * True when a link-child row already belongs to a DIFFERENT MI participant (e.g. People placeholder
 * rows for sibling sub-tasks carried in the same binding). The current participant's Save MUST NOT
 * seed the current FK / allocate a PK on these rows — doing so makes them falsely claim the current
 * participant and {@link collapseMiLinkChildRowsToOnePerParticipant} then merges them into one corrupt
 * row (cross-participant {@code id_idw} contamination, #1444). Fresh rows with no participant identity
 * yet (new current row) and the current participant's own rows are NOT foreign.
 */
export function linkChildRowIsForeignParticipantPlaceholder(
  row: Record<string, unknown>,
  myRowId: string | number,
): boolean {
  const pid = normalizeMiLinkMatchId(myRowId)
  if (!pid) return false
  const structuralFk = resolveMiChildStructuralParentFk(row)
  if (structuralFk) return structuralFk !== pid
  const idIdw = normalizeMiLinkMatchId(row.id_idw)
  // No structural FK yet: a participant-style id_idw pointing at someone else marks a foreign placeholder.
  return !!idIdw && idIdw !== pid && !isAllocatedUuidPrimaryKey(idIdw)
}

/**
 * The current participant's link-child row (People) uses {@code id} (UUID) as PK and a structural FK
 * ({@code sub_task_id}) as the participant link. Its own {@code id_idw} must NEVER hold a participant id
 * — least of all a DIFFERENT participant's — or load-side participant filters reject the fresh row and
 * fall back to a stale nested copy (#1444). Strip such a corrupt {@code id_idw} when the structural FK
 * already anchors the row to the current participant and {@code id} is an allocated UUID.
 */
export function stripForeignParticipantIdIdwFromLinkChildRow(
  row: Record<string, unknown>,
  myRowId: string | number,
): Record<string, unknown> {
  const pid = normalizeMiLinkMatchId(myRowId)
  if (!pid) return row
  const idIdw = normalizeMiLinkMatchId(row.id_idw)
  if (!idIdw || idIdw === pid) return row
  if (isAllocatedUuidPrimaryKey(idIdw)) return row
  const structuralFk = resolveMiChildStructuralParentFk(row)
  if (structuralFk !== pid) return row
  if (!isAllocatedUuidPrimaryKey(normalizeMiLinkMatchId(row.id))) return row
  const out = { ...row }
  delete out.id_idw
  return out
}

/** Prefer rows with backend-allocated PK (UUID) over legacy rows where {@code id} was copied from participant id. */
export function scoreMiLinkChildRowQuality(row: Record<string, unknown>): number {
  let score = 0
  const structuralFk = resolveMiChildStructuralParentFk(row)
  const id = normalizeMiLinkMatchId(row.id)
  if (id) {
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
      score += 100
    } else if (structuralFk && id !== structuralFk) {
      score += 40
    } else if (structuralFk && id === structuralFk) {
      score -= 80
    }
  } else {
    score -= 50
  }
  for (const [k, v] of Object.entries(row)) {
    if (k.startsWith('__') || MI_LINK_CHILD_SCALAR_KEYS.has(k)) continue
    if (v === undefined || v === null) continue
    if (typeof v === 'string' && v.trim() === '') continue
    score += 1
  }
  return score
}

export function miParentRowAlignsWithChildRow(
  parentRow: Record<string, unknown>,
  childRow: Record<string, unknown>,
): boolean {
  const parentPk =
    parentRow.id_idw
    ?? parentRow.rowId
    ?? parentRow.participant_id
    ?? parentRow.participantId
    ?? parentRow.id
  const parentPkNorm = normalizeMiLinkMatchId(parentPk)

  /**
   * Structural FK is authoritative for link-child rows: a child's own {@code id_idw} is its OWN PK
   * (sequential ids can collide with another participant's id), so once {@code sub_task_id} etc. is set
   * parentage MUST be decided by it alone — never by comparing the child's id_idw.
   */
  const structuralFk = resolveMiChildStructuralParentFk(childRow)
  if (structuralFk) {
    return parentPkNorm != null && structuralFk === parentPkNorm
  }

  const parentIdIdw = normalizeMiLinkMatchId(parentRow.id_idw)
  const childIdIdw = normalizeMiLinkMatchId(childRow.id_idw)
  if (parentIdIdw && childIdIdw && parentIdIdw === childIdIdw) return true

  const parentId = normalizeMiLinkMatchId(parentRow.id)
  const childId = normalizeMiLinkMatchId(childRow.id)
  if (parentId && childId && parentId === childId) return true

  if (!parentPkNorm) return false
  /** Legacy link-form rows keyed child PK {@code id} to parent id_idw when no structural FK. */
  const cv = normalizeMiLinkMatchId(childRow.id)
  if (cv && cv === parentPkNorm) return true
  return false
}

/**
 * True when a link-child row (People, subtable2, …) belongs to the current MI participant.
 * Normally {@code sub_task_id} (structural FK) is authoritative; the child's own {@code id_idw} is NOT a
 * participant key (it is the child's own PK and may collide with another participant's id).
 * Exception: when structural FK is stale but {@code id_idw} + allocated UUID {@code id} still anchor the row
 * to the current participant (sub form1 save → sub form2 load).
 */
export function miLinkChildRowBelongsToParticipant(
  row: Record<string, unknown>,
  participantId: string | number,
): boolean {
  const pid = normalizeMiLinkMatchId(participantId)
  if (!pid) return false

  const childIdIdw = normalizeMiLinkMatchId(row.id_idw)
  const legacyId = normalizeMiLinkMatchId(row.id)
  const structuralFk = resolveMiChildStructuralParentFk(row)

  if (structuralFk) {
    if (structuralFk === pid) {
      if (
        childIdIdw
        && childIdIdw !== structuralFk
        && !isAllocatedUuidPrimaryKey(childIdIdw)
        && (isAllocatedUuidPrimaryKey(legacyId) || legacyId === childIdIdw)
      ) {
        return false
      }
      return true
    }
    if (childIdIdw === pid && !isAllocatedUuidPrimaryKey(childIdIdw)) {
      if (isAllocatedUuidPrimaryKey(legacyId) || legacyId === pid) return true
      return false
    }
    return false
  }

  if (childIdIdw === pid) return true
  if (legacyId === pid && !isAllocatedUuidPrimaryKey(legacyId)) return true
  return false
}

/** Match a sub-table row to Flowable MI expansion id ({@code _currentItem.rowId} / designer {@code id_idw}). */
export function rowMatchesMiExpansionId(
  rec: Record<string, unknown>,
  miRowId: string | number,
): boolean {
  const pid = String(miRowId).trim()
  if (!pid) return false
  for (const k of ['id_idw', 'rowId', 'id', 'ID', 'RowId'] as const) {
    const v = rec[k]
    if (v != null && v !== '' && String(v) === pid) return true
  }
  return false
}
