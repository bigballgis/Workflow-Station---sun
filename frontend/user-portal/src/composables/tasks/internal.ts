/**
 * Internal cross-module helpers for the task sub-table composables split out of {@code shared.ts}.
 * NOT part of the public {@code shared.ts} barrel — import only from sibling modules.
 */

export const SUB_TABLE_ROW_META_KEYS = new Set([
  '__subTables__',
  'rowKey',
  'task_status',
  'task_current_node',
  'task_id',
  'task_definition_key',
  'assignee',
  'assignee_user_id',
  'assignee_display_name',
  'participant_id',
  'parent_id',
])

export function pickNonEmptyAttachmentFile(row: unknown): boolean {
  if (!row || typeof row !== 'object') return false
  const file = (row as Record<string, unknown>).file
  if (file == null || String(file).trim() === '' || String(file).trim() === '-') return false
  return true
}

export const MI_LINK_CHILD_SCALAR_KEYS = new Set([
  'id',
  'id_idw',
  'assignee',
  'task_status',
  'task_current_node',
  'participant_id',
  'parent_id'
])

export function isAllocatedUuidPrimaryKey(value: unknown): boolean {
  const s = value == null ? '' : String(value).trim()
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(s)
}

export function normalizeMiLinkMatchId(v: unknown): string | null {
  if (v === undefined || v === null) return null
  const s = String(v).trim()
  return s === '' ? null : s
}

/** Structural FK field names linking a child row to its MI participant (sub task) row. */
export const MI_STRUCTURAL_PARENT_FK_FIELDS = [
  'sub_task_id',
  'participant_id',
  'participantId',
  'parent_id',
  'parentId',
  'meeting_participant_id',
] as const

/** Rank for MI dashboard {@code task_status}; higher wins when merging conflicting snapshots. */
export function miTaskStatusRank(raw: string): number {
  const u = raw.trim().toUpperCase().replace(/\s+/g, '_')
  if (u === 'COMPLETED' || u === 'CANCELLED') return 3
  if (u === 'IN_PROGRESS' || u === 'ASSIGNED' || u === 'CREATED' || u === 'ACTIVE') return 2
  if (u === 'PENDING') return 1
  return 0
}

export function miTaskStatusIsTerminal(raw: string): boolean {
  const u = raw.trim().toUpperCase().replace(/\s+/g, '_')
  return u === 'COMPLETED' || u === 'CANCELLED'
}

/** When two bindings merge the same PK row, stale IN_PROGRESS must not overwrite COMPLETED. */
export function mergeMiTaskStatusPreferTerminal(prev: unknown, next: unknown): string | undefined {
  const ps = String(prev ?? '').trim()
  const ns = String(next ?? '').trim()
  if (!ps && !ns) return undefined
  if (!ns) return ps
  if (!ps) return ns
  const rp = miTaskStatusRank(ps)
  const rn = miTaskStatusRank(ns)
  if (rn > rp) return ns
  if (rp > rn) return ps
  return ns
}

export function mergeMiCurrentNodeForTerminal(prevNode: unknown, nextNode: unknown): string | undefined {
  const p = String(prevNode ?? '').trim()
  const n = String(nextNode ?? '').trim()
  const pe = p.toLowerCase() === 'end'
  const ne = n.toLowerCase() === 'end'
  if (pe && ne) return p || n
  if (ne) return n
  if (pe) return p
  return n || p || 'end'
}

export function mergeMiCurrentNodeInFlight(prevNode: unknown, nextNode: unknown): string | undefined {
  const p = String(prevNode ?? '').trim()
  const n = String(nextNode ?? '').trim()
  if (!p && !n) return undefined
  if (!p) return n
  if (!n) return p
  if (p === n) return p
  const oP = miSubFormOrdinalHint(p)
  const oN = miSubFormOrdinalHint(n)
  if (oP !== null && oN !== null) {
    return oN >= oP ? n : p
  }
  return n
}

/**
 * When consolidating two payloads for the same PK row (e.g. multiple subTable bindings merged in My Requests),
 * prefer the incumbent node if the competitor slice carries fewer MI snapshot signals — otherwise a stale slice
 * merged second overwrites the correct overlay (e.g. sub form2 reverting to sub form1).
 */
export function mergeMiCurrentNodePreferPrevious(prevNode: unknown, nextNode: unknown): string | undefined {
  const p = String(prevNode ?? '').trim()
  const n = String(nextNode ?? '').trim()
  if (!p && !n) return undefined
  if (!p) return n
  if (!n) return p
  return p
}

/** Heuristic richness: which snapshot likely came from fuller portal/backend MI hydration vs a thin duplicate binding. */
export function miDashboardSliceRichness(
  rec: Record<string, unknown>,
  statusField = 'task_status',
  currentNodeField = 'task_current_node',
): number {
  let s = 0
  const au = rec['assignee_user_id']
  if (au !== undefined && au !== null && String(au).trim() !== '') s += 4
  const ad = rec['assignee_display_name']
  if (ad !== undefined && ad !== null && String(ad).trim() !== '') s += 3
  const tk = rec['task_definition_key'] ?? rec['taskDefinitionKey']
  if (tk !== undefined && tk !== null && String(tk).trim() !== '') s += 2
  const ti = rec['task_id'] ?? rec['taskId']
  if (ti !== undefined && ti !== null && String(ti).trim() !== '') s += 2
  const st = rec[statusField]
  if (st !== undefined && st !== null && String(st).trim() !== '') s += 1
  const node = rec[currentNodeField]
  if (node !== undefined && node !== null && String(node).trim() !== '') s += 1
  return s
}

/** Count non-empty non-meta fields as a coarse tie-breaker when MI slice richness ties. */
export function roughNonEmptyFieldCount(rec: Record<string, unknown>): number {
  let c = 0
  for (const [key, val] of Object.entries(rec)) {
    if (key.startsWith('__')) continue
    if (val === undefined || val === null) continue
    if (typeof val === 'string' && val.trim() === '') continue
    c++
    if (c > 999) break
  }
  return c
}

/** True when incoming has strictly fewer NON-MI meta keys and every incoming key exists on prior (duplicate binding slim row). */
export function incomingIsStrictNonMiKeySubset(
  prior: Record<string, unknown>,
  incoming: Record<string, unknown>,
  statusField = 'task_status',
  currentNodeField = 'task_current_node',
): boolean {
  const meta = (k: string) =>
    k.startsWith('__') || k === statusField || k === currentNodeField
  const prevKeys = [...Object.keys(prior)].filter(k => !meta(k))
  const incKeys = [...Object.keys(incoming)].filter(k => !meta(k))
  if (incKeys.length === 0 || incKeys.length >= prevKeys.length) {
    return false
  }
  const prevSet = new Set(prevKeys)
  return incKeys.every(k => prevSet.has(k))
}

/** e.g. "sub form2" → 2; unrelated labels → null */
export function miSubFormOrdinalHint(raw: unknown): number | null {
  const m = /\bsub\s*form\s*(\d+)\b/i.exec(String(raw ?? '').trim())
  if (!m) {
    return null
  }
  const n = Number.parseInt(m[1], 10)
  return Number.isFinite(n) ? n : null
}
