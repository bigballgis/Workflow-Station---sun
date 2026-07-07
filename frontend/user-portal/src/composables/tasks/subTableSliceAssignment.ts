/**
 * Internal helpers for assigning {@code __subTables__} numeric slices to bindings when binding ids
 * changed across BPMN steps (copied forms). NOT part of the public {@code shared.ts} barrel.
 */

export function extractRowIdentityForTableMatch(row: unknown): string | null {
  if (!row || typeof row !== 'object') return null
  const o = row as Record<string, unknown>
  const candidates = [o.id, o.rowId, o.row_id, (o as Record<string, unknown>).id_idw]
  for (const c of candidates) {
    if (c != null && c !== '') return String(c)
  }
  return null
}

function sortSubTableKeysNumericFirst(keysEntries: [string, unknown][]): [string, unknown][] {
  return [...keysEntries].sort(([a], [b]) => {
    const na = Number(a)
    const nb = Number(b)
    const fa = Number.isFinite(na)
    const fb = Number.isFinite(nb)
    if (fa && fb) return na - nb
    if (fa) return -1
    if (fb) return 1
    return String(a).localeCompare(String(b))
  })
}

/**
 * Numeric keys in {@code __subTables__} that are already aligned with another binding's hydrated rows
 * (same stable row id on first row), even when {@code bindingTableById.get(kid)} is null.
 */
export function claimedNumericSubTableSliceKeys(
  bindings: Array<{ bindingId: number; data: any[] }>,
  savedSubTables: Record<string, unknown>
): Set<number> {
  const claimed = new Set<number>()
  const ordered = sortSubTableKeysNumericFirst(Object.entries(savedSubTables))

  for (const bb of bindings) {
    if (!Array.isArray(bb.data) || bb.data.length === 0) continue
    const id0 = extractRowIdentityForTableMatch(bb.data[0])
    if (id0 == null) continue
    for (const [key, val] of ordered) {
      const kid = Number(key)
      if (!Array.isArray(val) || val.length === 0) continue
      const idV = extractRowIdentityForTableMatch(val[0])
      if (idV != null && idV === id0 && Number.isFinite(kid)) {
        claimed.add(kid)
        break
      }
    }
  }
  return claimed
}

/**
 * When {@code bindingTableById.get(kid)} is missing for some keys, merge the single numeric slice
 * not claimed by any sibling binding that already has rows (initiator 64 vs subtable2 66 scenario).
 * When {@code selfTidRaw} is known, only slices whose relation-table id matches — never pull attachment
 * (273) into transaction (271) just because it is the sole unclaimed numeric key (HMDC Case Submission diagram).
 */
export function mergeRowsFromSoleUnclaimedNumericSlice(
  b: { bindingId: number; data: any[] },
  savedSubTables: Record<string, unknown>,
  claimedNumericKeys: Set<number>,
  bindingTableById?: Map<number, number | null>,
  selfTidRaw?: number | null,
): any[] {
  const wantTid =
    selfTidRaw != null && Number.isFinite(Number(selfTidRaw)) && !Number.isNaN(Number(selfTidRaw))
      ? Number(selfTidRaw)
      : null
  const candidates: number[] = []
  for (const [key, val] of Object.entries(savedSubTables)) {
    const kid = Number(key)
    if (!Number.isFinite(kid) || kid === b.bindingId) continue
    if (!Array.isArray(val) || val.length === 0) continue
    if (claimedNumericKeys.has(kid)) continue
    if (wantTid != null && bindingTableById != null) {
      const otid = bindingTableById.get(kid)
      if (otid == null || Number.isNaN(Number(otid)) || Number(otid) !== wantTid) continue
    }
    candidates.push(kid)
  }
  if (candidates.length !== 1) {
    return []
  }
  const onlyKey = candidates[0]!
  const val = savedSubTables[String(onlyKey)] ?? savedSubTables[onlyKey]
  return Array.isArray(val) ? [...val] : []
}

function countNonMetaRowKeys(row: unknown): number {
  if (!row || typeof row !== 'object') return 0
  return Object.keys(row as object).filter(k => !k.startsWith('__')).length
}

/**
 * Copied BPMN forms → new binding ids; variables keep multiple numeric {@code __subTables__} slices.
 * {@link mergeRowsFromSoleUnclaimedNumericSlice} yields nothing when 2+ numeric keys remain unclaimed.
 * For bindings that still have no rows, take the richest unclaimed slice, preferring the slice whose
 * {@code bindingTableById} tid matches {@code selfTidRaw} when that is known.
 * When {@code selfTidRaw} is known, a non-matching slice is NEVER pulled — otherwise a binding with a real
 * tableId (e.g. ATM_Comment 50326) absorbs an unrelated RELATED slice such as the {@code sys_users}
 * virtual table ({@code tableId = -1000000001}) that pools every case sub-table row, surfacing "-" ghost
 * rows in My Request. Only when {@code selfTidRaw} is unknown (copied form dropped its tableId) do we fall
 * back to the richest slice regardless of tid. Mirrors {@link mergeRowsFromSoleUnclaimedNumericSlice}.
 */
export function mergeRowsFromRichestUnclaimedNumericSlice(
  b: { bindingId: number },
  savedSubTables: Record<string, unknown>,
  claimedNumericKeys: Set<number>,
  bindingTableById: Map<number, number | null>,
  selfTidRaw: number | null,
): any[] {
  type Cand = { kid: number; val: any[]; score: number; otid: number | null }
  const all: Cand[] = []
  for (const [key, val] of Object.entries(savedSubTables)) {
    const kid = Number(key)
    if (!Number.isFinite(kid) || kid === b.bindingId) continue
    if (!Array.isArray(val) || val.length === 0) continue
    if (claimedNumericKeys.has(kid)) continue
    const rawTid = bindingTableById.get(kid)
    const otid =
      rawTid != null && Number.isFinite(Number(rawTid)) ? Number(rawTid) : null
    const score = countNonMetaRowKeys(val[0])
    if (score <= 0) continue
    all.push({ kid, val, score, otid })
  }
  if (all.length === 0) return []

  const tidOk =
    selfTidRaw != null && Number.isFinite(selfTidRaw) && !Number.isNaN(selfTidRaw)
  const matched = tidOk ? all.filter(c => c.otid != null && c.otid === selfTidRaw) : []
  // When this binding's relation table is known, never fall back to a tid-mismatched slice: a real
  // tableId must not absorb an unrelated slice (e.g. sys_users RELATED tid=-1000000001 that pools every
  // case sub-table row). Only tableId-less copied-form bindings may take the richest slice regardless.
  const pool = tidOk ? matched : all
  if (pool.length === 0) return []
  pool.sort((a, b) => b.score - a.score || b.val.length - a.val.length)
  const pick = pool[0]
  return pick ? [...pick.val] : []
}

/**
 * When designer metadata omits {@code tableId} for a binding (common on copied forms), we still
 * need to know which relation-table slices are already "consumed" by sibling bindings that have rows.
 * Match hydrated {@code bb.data[0]} to a numeric-key slice in variables by stable row id.
 */
function inferFilledRelationTableIds(
  bindings: Array<{ bindingId: number; tableId?: number | null; data: any[] }>,
  bindingTableById: Map<number, number | null>,
  savedSubTables: Record<string, unknown>
): Set<number> {
  const filled = new Set<number>()
  for (const bb of bindings) {
    if (!Array.isArray(bb.data) || bb.data.length === 0) continue
    let t = bb.tableId != null ? Number(bb.tableId) : bindingTableById.get(bb.bindingId)
    if (t != null && Number.isFinite(Number(t))) {
      filled.add(Number(t))
      continue
    }
    const id0 = extractRowIdentityForTableMatch(bb.data[0])
    if (id0 == null) continue
    for (const [key, val] of Object.entries(savedSubTables)) {
      const kid = Number(key)
      if (!Number.isFinite(kid) || kid === bb.bindingId) continue
      if (!Array.isArray(val) || val.length === 0) continue
      const v0 = val[0]
      const idV = extractRowIdentityForTableMatch(v0)
      if (idV != null && idV === id0) {
        const otid = bindingTableById.get(kid)
        if (otid != null && Number.isFinite(Number(otid))) {
          filled.add(Number(otid))
        }
        break
      }
    }
  }
  return filled
}

/**
 * Copied BPMN userTask forms (e.g. subform_copy) get new bindingIds; metadata may omit {@code tableId}.
 * Process variables still use initiator binding ids (64, 66, …). When {@code selfTid} cannot be resolved,
 * infer the relation table id as the unique tid present in variables that is not already carried by
 * another binding that has successfully hydrated rows.
 */
export function inferOrphanRelationTableId(
  b: { bindingId: number; tableId?: number | null; data: any[] },
  bindings: Array<{ bindingId: number; tableId?: number | null; data: any[] }>,
  bindingTableById: Map<number, number | null>,
  savedSubTables: Record<string, unknown>
): number | null {
  const filledTids = inferFilledRelationTableIds(bindings, bindingTableById, savedSubTables)

  const tidsSeenInVariables = new Set<number>()
  for (const [key, val] of Object.entries(savedSubTables)) {
    const kid = Number(key)
    if (!Number.isFinite(kid)) continue
    if (!Array.isArray(val) || val.length === 0) continue
    const tid = bindingTableById.get(kid)
    if (tid != null && Number.isFinite(Number(tid))) {
      tidsSeenInVariables.add(Number(tid))
    }
  }

  const orphan = [...tidsSeenInVariables].filter(t => !filledTids.has(t))
  if (orphan.length !== 1) {
    return null
  }
  return orphan[0]!
}

/** Resolved from designer / FU metadata only (no orphan infer) — used to detect multiple sub-table placements of the same relation table. */
export function metadataRelationTableId(
  bb: { bindingId: number; tableId?: number | null },
  bindingTableById: Map<number, number | null>,
): number | null {
  const t = bb.tableId != null ? Number(bb.tableId) : bindingTableById.get(bb.bindingId)
  return t != null && Number.isFinite(t) ? t : null
}

/**
 * When two+ bindings in one form share the same {@code metadataRelationTableId}, do not merge every
 * {@code __subTables__} slice of that tid into every binding (would duplicate the same rows for sub form1/sub form2).
 * Assign: own {@code bindingId} slice first, then pair remaining bindings to remaining numeric keys by stable sort.
 */
export function assignRowsPerBindingForSharedMetadataTid<T extends { bindingId: number; tableId?: number | null; data: any[] }>(
  tid: number,
  bindings: T[],
  bindingTableById: Map<number, number | null>,
  savedSubTables: Record<string, unknown>,
): Map<number, any[]> {
  const peers = bindings.filter(bb => metadataRelationTableId(bb, bindingTableById) === tid)
  if (peers.length <= 1) return new Map()

  const assignment = new Map<number, any[]>()
  const usedNumericKeys = new Set<number>()

  for (const bb of [...peers].sort((a, c) => a.bindingId - c.bindingId)) {
    const own = savedSubTables[bb.bindingId] ?? savedSubTables[String(bb.bindingId)]
    if (Array.isArray(own) && own.length > 0) {
      assignment.set(bb.bindingId, [...own])
      usedNumericKeys.add(bb.bindingId)
    }
  }

  const candidateKeysForTid = (): number[] => {
    const keys: number[] = []
    for (const [key, val] of Object.entries(savedSubTables)) {
      const kid = Number(key)
      if (!Number.isFinite(kid)) continue
      if (!Array.isArray(val) || val.length === 0) continue
      const otid = bindingTableById.get(kid)
      if (otid == null || Number.isNaN(Number(otid))) continue
      if (Number(otid) !== tid) continue
      keys.push(kid)
    }
    return keys.sort((a, c) => a - c)
  }

  const orphans = candidateKeysForTid().filter(k => !usedNumericKeys.has(k))
  const stillNeed = peers.filter(bb => !assignment.has(bb.bindingId)).sort((a, c) => a.bindingId - c.bindingId)
  for (let i = 0; i < Math.min(stillNeed.length, orphans.length); i++) {
    const bb = stillNeed[i]!
    const k = orphans[i]!
    const val = savedSubTables[k] ?? savedSubTables[String(k)]
    if (!Array.isArray(val) || val.length === 0) continue
    assignment.set(bb.bindingId, [...val])
    usedNumericKeys.add(k)
  }

  return assignment
}
