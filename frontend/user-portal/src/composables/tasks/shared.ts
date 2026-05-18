/**
 * Shared utility functions for task detail composables.
 * Pure helpers — no reactive state, no Vue/API dependencies.
 */

export function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

/** Strip designer "ADD + …" prefix; nested {@code parentRow.__subTables__} keys often keep this label. */
export function stripLinkFormDesignerTableLabel(raw?: string): string {
  return String(raw || '').trim().replace(/^ADD\s*\+\s*/i, '').trim()
}

function compactLinkFormTableKey(name: string): string {
  return normalizeSubTableName(stripLinkFormDesignerTableLabel(name)).replace(/\s+/g, '')
}

/**
 * Resolve child sub-table rows under one parent's {@code __subTables__} object (string keys).
 * Aligns with {@code SubTableField.handleLinkFormClick} lookups: binding id, raw / normalized names,
 * stripped "ADD + …" labels, and fuzzy key scan when BPMN copy left mismatched keys.
 */
function findNestedChildRowsInSto(
  sto: Record<string, unknown>,
  child: { bindingId: number; tableName: string; physicalTableName?: string }
): any[] | null {
  const tn = (name?: string) => normalizeSubTableName(String(name || ''))
  const nameRaw = String(child.tableName || '').trim()
  const nameStripped = stripLinkFormDesignerTableLabel(nameRaw)

  const candidates: unknown[] = [
    sto[child.bindingId],
    sto[String(child.bindingId)],
    sto[nameRaw],
    sto[tn(nameRaw)]
  ]
  if (nameStripped !== nameRaw) {
    candidates.push(sto[nameStripped], sto[tn(nameStripped)])
  }
  if (child.physicalTableName) {
    const p = String(child.physicalTableName).trim()
    candidates.push(sto[p], sto[tn(p)])
  }
  for (const v of candidates) {
    if (Array.isArray(v) && v.length > 0) return v as any[]
  }

  const wantName = compactLinkFormTableKey(nameRaw)
  if (wantName) {
    for (const rk of Object.keys(sto)) {
      if (compactLinkFormTableKey(rk) !== wantName) continue
      const v = sto[rk]
      if (Array.isArray(v) && v.length > 0) return v as any[]
    }
  }
  if (child.physicalTableName) {
    const wantPhys = compactLinkFormTableKey(String(child.physicalTableName))
    if (wantPhys) {
      for (const rk of Object.keys(sto)) {
        if (compactLinkFormTableKey(rk) !== wantPhys) continue
        const v = sto[rk]
        if (Array.isArray(v) && v.length > 0) return v as any[]
      }
    }
  }
  return null
}

export function subTableBindingMatches(
  target: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null },
  source: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null }
): boolean {
  const targetPhysicalName = normalizeSubTableName(target.physicalTableName)
  const sourcePhysicalName = normalizeSubTableName(source.physicalTableName)
  if (targetPhysicalName && sourcePhysicalName && targetPhysicalName === sourcePhysicalName) return true
  const targetName = normalizeSubTableName(target.tableName)
  const sourceName = normalizeSubTableName(source.tableName)
  const samePhysicalTable = target.tableId != null && source.tableId != null && Number(target.tableId) === Number(source.tableId)
  return target.bindingId === source.bindingId || samePhysicalTable || (!!targetName && targetName === sourceName)
}

export function cloneSubTableRows(rows: any[]): any[] {
  try {
    return JSON.parse(JSON.stringify(rows))
  } catch {
    return rows.map(row => ({ ...row }))
  }
}

export function cloneSubTableBindings<T extends Array<{ data: any[] }>>(bindings: T): T {
  return bindings.map(binding => ({
    ...binding,
    data: cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
  })) as T
}

const ROW_KEY_MERGE_SEP = '\u001f'

/**
 * Normalize PK / rowKey scalar values so merge maps treat `555` and `"555"` as the same row
 * (JSON.stringify would not; this caused duplicate MI rows in My Request when variables mixed types).
 */
function scalarForMergeKey(v: unknown): string | null {
  if (v == null) return null
  if (typeof v === 'number' && Number.isFinite(v)) return String(v)
  if (typeof v === 'bigint') return String(v)
  if (typeof v === 'string') {
    const t = v.trim()
    return t === '' ? null : t
  }
  if (typeof v === 'boolean') return v ? 'true' : 'false'
  try {
    return JSON.stringify(v)
  } catch {
    const s = String(v).trim()
    return s === '' ? null : s
  }
}

function canonicalRowKeyFromPayload(r: Record<string, unknown>): string | null {
  const rk = r.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    const o = rk as Record<string, unknown>
    return Object.keys(o)
      .sort()
      .map(k => {
        const part = scalarForMergeKey(getRowValueIgnoreCase(o, k) ?? o[k])
        return part != null ? `${k}=${part}` : `${k}=${JSON.stringify(o[k])}`
      })
      .join(ROW_KEY_MERGE_SEP)
  }
  return null
}

/**
 * Same idea as {@code SubTableRowKeySupport.getRowValueIgnoreCase} — form rows use mixed key casing.
 */
function getRowValueIgnoreCase(row: Record<string, unknown>, key: string): unknown {
  if (!row || !key) return undefined
  if (Object.prototype.hasOwnProperty.call(row, key)) {
    const v = row[key]
    if (v != null && !(typeof v === 'string' && v.trim() === '')) return v
  }
  const lower = key.toLowerCase()
  for (const k of Object.keys(row)) {
    if (k.startsWith('__')) continue
    if (k.toLowerCase() === lower) {
      const v = row[k]
      if (v != null && !(typeof v === 'string' && v.trim() === '')) return v
    }
  }
  return undefined
}

/**
 * PK column value on row or nested {@code rowKey}. For a single-column PK, matches
 * {@code SubTableRowKeySupport.rowKeyFromVariableRow}: {@code rowId}, and the platform {@code id}/{@code id_idw} alias pair.
 */
function rowValueForPkFieldSingle(row: Record<string, unknown>, field: string, pkFieldCount: number): unknown {
  const f = String(field || '').trim()
  if (!f) return undefined

  let v = getRowValueIgnoreCase(row, f)
  if (v != null) return v

  const rk = row.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    const rko = rk as Record<string, unknown>
    v = getRowValueIgnoreCase(rko, f)
    if (v != null) return v
  }

  if (pkFieldCount !== 1) return undefined

  v = getRowValueIgnoreCase(row, 'rowId')
  if (v != null) return v
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    v = getRowValueIgnoreCase(rk as Record<string, unknown>, 'rowId')
    if (v != null) return v
  }

  const fl = f.toLowerCase()
  if (fl === 'id') {
    v = getRowValueIgnoreCase(row, 'id_idw')
    if (v != null) return v
    if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
      v = getRowValueIgnoreCase(rk as Record<string, unknown>, 'id_idw')
    }
    if (v != null) return v
  } else if (fl === 'id_idw') {
    v = getRowValueIgnoreCase(row, 'id')
    if (v != null) return v
    if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
      v = getRowValueIgnoreCase(rk as Record<string, unknown>, 'id')
    }
    if (v != null) return v
  }

  return undefined
}

function compositePkMergeKey(row: Record<string, unknown>, pkFieldNames: string[]): string | null {
  const parts: string[] = []
  const n = pkFieldNames.length
  for (const f of pkFieldNames) {
    const part = scalarForMergeKey(rowValueForPkFieldSingle(row, f, n))
    if (part == null) return null
    parts.push(`${f}=${part}`)
  }
  return `__pk__${parts.join('\u001e')}`
}

/** Rank for MI dashboard {@code task_status}; higher wins when merging conflicting snapshots. */
function miTaskStatusRank(raw: string): number {
  const u = raw.trim().toUpperCase().replace(/\s+/g, '_')
  if (u === 'COMPLETED' || u === 'CANCELLED') return 3
  if (u === 'IN_PROGRESS' || u === 'ASSIGNED' || u === 'CREATED' || u === 'ACTIVE') return 2
  if (u === 'PENDING') return 1
  return 0
}

function miTaskStatusIsTerminal(raw: string): boolean {
  const u = raw.trim().toUpperCase().replace(/\s+/g, '_')
  return u === 'COMPLETED' || u === 'CANCELLED'
}

/** When two bindings merge the same PK row, stale IN_PROGRESS must not overwrite COMPLETED. */
function mergeMiTaskStatusPreferTerminal(prev: unknown, next: unknown): string | undefined {
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

function mergeMiCurrentNodeForTerminal(prevNode: unknown, nextNode: unknown): string | undefined {
  const p = String(prevNode ?? '').trim()
  const n = String(nextNode ?? '').trim()
  const pe = p.toLowerCase() === 'end'
  const ne = n.toLowerCase() === 'end'
  if (pe && ne) return p || n
  if (ne) return n
  if (pe) return p
  return n || p || 'end'
}

function mergeMiCurrentNodeInFlight(prevNode: unknown, nextNode: unknown): string | undefined {
  const p = String(prevNode ?? '').trim()
  const n = String(nextNode ?? '').trim()
  if (!p && !n) return undefined
  if (!p) return n
  if (!n) return p
  return n
}

/**
 * Merge sub-table rows for the same logical table. Later rows win on field conflicts.
 *
 * {@code task_status} / {@code task_current_node} (multi-instance dashboard mirrors) use terminal-wins merge so a stale
 * {@code IN_PROGRESS} slice cannot overwrite {@code COMPLETED} when bindings align the same physical row.
 *
 * Key resolution order:
 * 1. Designer primary key columns (`pkFieldNames`) when present and all parts are resolvable
 *    (from top-level row and/or nested `rowKey`). This must beat raw `rowKey` so My Request
 *    merges snapshots that use only `id` with ones that attach a full `rowKey` map.
 * 2. Legacy `id` / `rowId` when no designer PK list — before bare `rowKey`, so mixed payloads dedupe.
 * 3. Flowable / platform `rowKey` object canonical string.
 * 4. Stable content fingerprint — when PK values are missing so rows are not dropped.
 */
export function mergeSubTableRowsByRowId(
  existing: any[] | undefined,
  incoming: any[],
  pkFieldNames?: string[] | null
): any[] {
  const byId = new Map<string, any>()
  let keyFallbackSeq = 0
  const pkCols =
    Array.isArray(pkFieldNames) && pkFieldNames.length > 0
      ? pkFieldNames.map(f => String(f).trim()).filter(Boolean)
      : null

  const contentKeyNoPk = (r: Record<string, unknown>): string => {
    try {
      const keys = Object.keys(r).filter(k => k !== '__subTables__' && !k.startsWith('__'))
      if (keys.length === 0) return '__empty__'
      return keys.map(k => `${k}:${JSON.stringify(r[k])}`).join('\u001f')
    } catch {
      return `__key_err__${keyFallbackSeq++}`
    }
  }

  /**
   * Two snapshots may describe the same PK row; a later "list-only" payload often carries {@code ''} / {@code null}
   * or an empty {@code __subTables__} map and must not wipe richer fields from process start / nested hydration.
   */
  const mergeRowSnapshotsPreferFilled = (
    previous: Record<string, unknown>,
    incoming: Record<string, unknown>
  ): Record<string, unknown> => {
    const MI_STATUS_KEY = 'task_status'
    const MI_NODE_KEY = 'task_current_node'
    const out: Record<string, unknown> = { ...previous }
    for (const [key, val] of Object.entries(incoming)) {
      if (val === undefined) continue
      if (key === MI_STATUS_KEY || key === MI_NODE_KEY) continue

      if (key === '__subTables__') {
        const pSub = previous[key]
        if (val !== null && typeof val === 'object' && !Array.isArray(val)) {
          const nObj = val as Record<string, unknown>
          if (Object.keys(nObj).length === 0) {
            continue
          }
          if (pSub !== null && typeof pSub === 'object' && !Array.isArray(pSub)) {
            out[key] = { ...(pSub as Record<string, unknown>), ...nObj }
          } else {
            out[key] = { ...nObj }
          }
        } else if (val !== null && val !== undefined) {
          out[key] = val
        }
        continue
      }

      if (val === null) {
        const cur = out[key]
        if (cur !== undefined && cur !== null) continue
        out[key] = val
        continue
      }

      if (typeof val === 'string' && val.trim() === '') {
        const cur = out[key]
        if (
          cur !== undefined &&
          cur !== null &&
          !(typeof cur === 'string' && cur.trim() === '')
        ) {
          continue
        }
      }

      out[key] = val
    }

    const mergedStatus = mergeMiTaskStatusPreferTerminal(out[MI_STATUS_KEY], incoming[MI_STATUS_KEY])
    if (mergedStatus !== undefined) {
      out[MI_STATUS_KEY] = mergedStatus
    }
    const statusStr = String(out[MI_STATUS_KEY] ?? '').trim()
    const terminal = miTaskStatusIsTerminal(statusStr)
    const mergedNode = terminal
      ? mergeMiCurrentNodeForTerminal(out[MI_NODE_KEY], incoming[MI_NODE_KEY])
      : mergeMiCurrentNodeInFlight(out[MI_NODE_KEY], incoming[MI_NODE_KEY])
    if (mergedNode !== undefined) {
      out[MI_NODE_KEY] = mergedNode
    }

    return out
  }

  const add = (r: any) => {
    if (!r || typeof r !== 'object') return
    const o = r as Record<string, unknown>
    let k: string | null = null

    if (pkCols?.length) {
      const pkKey = compositePkMergeKey(o, pkCols)
      if (pkKey != null) k = pkKey
    }

    if (k == null && !pkCols?.length) {
      const rawId = rowValueForPkFieldSingle(o, 'id', 1)
      const idPart = scalarForMergeKey(rawId)
      if (idPart != null) k = idPart
    }

    if (k == null) {
      const canon = canonicalRowKeyFromPayload(o)
      if (canon != null && canon !== '') {
        k = `__rowKey__${canon}`
      }
    }

    if (k == null) {
      k = `__noid__${contentKeyNoPk(o)}`
    }

    const cur = byId.get(k)
    byId.set(
      k,
      cur ? mergeRowSnapshotsPreferFilled(cur as Record<string, unknown>, o) : { ...r },
    )
  }
  for (const r of existing || []) add(r)
  for (const r of incoming || []) add(r)
  return Array.from(byId.values())
}

/**
 * Prefer API {@code primaryKeyFields} (admin-center / dw_field_definitions); if missing, infer from
 * designer sub-list columns marked {@code isPrimaryKey}, then subForm rules — never a fixed column name.
 */
export function resolveSubTablePrimaryKeyFields(
  apiPrimaryKeys: string[] | undefined | null,
  bindingId: number | string | undefined | null,
  formConfig?: Record<string, any> | null
): string[] | undefined {
  const trimmed = (apiPrimaryKeys || [])
    .map(f => String(f).trim())
    .filter(Boolean)
  if (trimmed.length > 0) return trimmed
  if (bindingId == null || bindingId === '' || formConfig == null) return undefined

  const lv =
    formConfig.subListViews?.[bindingId] ?? formConfig.subListViews?.[String(bindingId)]
  const cols = lv?.columns
  if (Array.isArray(cols)) {
    const fromList = cols
      .filter(
        (c: any) =>
          c &&
          c.isPrimaryKey === true &&
          typeof c.fieldName === 'string' &&
          c.fieldName.trim().length > 0
      )
      .map((c: any) => String(c.fieldName).trim())
    if (fromList.length > 0) return fromList
  }

  const subForms = formConfig.subForms
  if (subForms && typeof subForms === 'object') {
    const sf = subForms[bindingId] ?? subForms[String(bindingId)]
    const rule = sf?.rule
    if (Array.isArray(rule)) {
      const fromRule = rule
        .filter(
          (r: any) =>
            r &&
            (r.isPrimaryKey === true || r.props?.isPrimaryKey === true) &&
            typeof r.field === 'string' &&
            r.field.trim().length > 0
        )
        .map((r: any) => String(r.field).trim())
      if (fromRule.length > 0) return fromRule
    }
  }

  return undefined
}

export function getSavedSubTableRows(subTables: Record<string, any>, binding: {
  bindingId: number
  tableName: string
  physicalTableName?: string
  tableId?: number | null
}): any[] | undefined {
  const key = String(binding.bindingId)
  const byId = (subTables[key] as any[] | undefined) || (subTables[String(binding.bindingId)] as any[] | undefined)
  if (Array.isArray(byId)) return byId as any[]
  if (binding.tableName && Array.isArray(subTables[binding.tableName])) return subTables[binding.tableName] as any[]
  return undefined
}

/**
 * Some gateways / serializers deliver {@code __subTables__} as a JSON string. Without coercion,
 * portal hydration skips flatten/backfill ({@code typeof === 'object'} gate) and Link Form stays empty.
 */
export function coerceSubTablesVariableToMap(raw: unknown): Record<string, unknown> | null {
  if (raw == null) return null
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return null
    try {
      const parsed = JSON.parse(t) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>
      }
    } catch {
      return null
    }
    return null
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    return raw as Record<string, unknown>
  }
  return null
}

/**
 * All {@code Record} values that are non-empty object row arrays, including slices nested under
 * {@code row.__subTables__} (Link Form persistence). Used when top-level keys alone miss child data.
 */
export function collectSubTableSliceArraysDeep(saved: Record<string, unknown>): unknown[][] {
  const out: unknown[][] = []
  const seenArrays = new WeakSet<object>()
  const walkMap = (m: Record<string, unknown>) => {
    for (const val of Object.values(m)) {
      if (!Array.isArray(val) || val.length === 0) continue
      if (seenArrays.has(val)) continue
      const row0 = val[0]
      if (!row0 || typeof row0 !== 'object') continue
      seenArrays.add(val)
      out.push(val)
      for (const row of val) {
        if (!row || typeof row !== 'object') continue
        const nest = (row as Record<string, unknown>).__subTables__
        if (nest && typeof nest === 'object' && !Array.isArray(nest)) {
          walkMap(nest as Record<string, unknown>)
        }
      }
    }
  }
  walkMap(saved)
  return out
}

/**
 * Link Form / 「表格下表单」在编辑态常把子表行只写在 {@code parentRow.__subTables__[childBindingId]}，而流程变量提交
 * ({@code __subTables__} 顶层 map) 需要同一份行也挂在 {@code __subTables__[childKey]}，待办加载的
 * {@code getSavedSubTableRows} 才能命中。本函数在原位多轮提升（处理链式嵌套）；入参应为普通 JSON 形态的对象。
 */
export function flattenNestedSubTableRowsIntoPayload(subTables: Record<string, unknown>, maxPasses = 8): void {
  for (let pass = 0; pass < maxPasses; pass++) {
    let touched = false
    for (const val of Object.values(subTables)) {
      if (!Array.isArray(val)) continue
      for (const row of val) {
        if (!row || typeof row !== 'object') continue
        const nest = (row as Record<string, unknown>).__subTables__
        if (!nest || typeof nest !== 'object') continue
        for (const [childKey, childVal] of Object.entries(nest)) {
          if (!Array.isArray(childVal) || childVal.length === 0) continue
          const prev = subTables[childKey]
          const prevRows = Array.isArray(prev) ? [...(prev as any[])] : []
          const merged = mergeSubTableRowsByRowId(prevRows, childVal as any[], null)
          subTables[childKey] = merged
          subTables[String(childKey)] = merged
          touched = true
        }
      }
    }
    if (!touched) break
  }
}

/**
 * Across BPMN steps the same RelationTable may keep variables under an older {@code bindingId} (e.g. initiator slice "66")
 * while the current Task Form uses a copied binding id ("90"). Keys in {@code __subTables__} are still keyed by the old id,
 * so hydrate empty bindings by matching {@code tableId} from designer {@code tableBindings} metadata across all FU forms.
 */
export function buildBindingIdToRelationTableIdMap(contentForms: any[] | undefined): Map<number, number | null> {
  const m = new Map<number, number | null>()
  for (const f of contentForms || []) {
    const tbs = (f as { tableBindings?: unknown }).tableBindings as unknown[] | undefined
    if (!Array.isArray(tbs)) continue
    for (const tb of tbs) {
      const raw = tb as { bindingId?: unknown; tableId?: unknown }
      if (raw?.bindingId == null) continue
      const bid = Number(raw.bindingId)
      if (!Number.isFinite(bid)) continue
      if (m.has(bid)) continue
      const tid = raw.tableId != null ? Number(raw.tableId) : null
      m.set(bid, tid != null && Number.isFinite(tid) ? tid : null)
    }
  }
  return m
}

function extractRowIdentityForTableMatch(row: unknown): string | null {
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
function claimedNumericSubTableSliceKeys(
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
 */
function mergeRowsFromSoleUnclaimedNumericSlice(
  b: { bindingId: number; data: any[] },
  savedSubTables: Record<string, unknown>,
  claimedNumericKeys: Set<number>
): any[] {
  const candidates: number[] = []
  for (const [key, val] of Object.entries(savedSubTables)) {
    const kid = Number(key)
    if (!Number.isFinite(kid) || kid === b.bindingId) continue
    if (!Array.isArray(val) || val.length === 0) continue
    if (claimedNumericKeys.has(kid)) continue
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
 */
function mergeRowsFromRichestUnclaimedNumericSlice(
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
  const pool = matched.length > 0 ? matched : all
  pool.sort((a, b) => b.score - a.score || b.val.length - a.val.length)
  const pick = pool[0]
  return pick ? [...pick.val] : []
}

function buildBindingTableIdMapFromPeers<T extends { bindingId: number; tableId?: number | null }>(
  peers: T[],
): Map<number, number | null> {
  const m = new Map<number, number | null>()
  for (const b of peers) {
    const tid = b.tableId != null ? Number(b.tableId) : null
    if (tid != null && Number.isFinite(tid)) m.set(b.bindingId, tid)
  }
  return m
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
function inferOrphanRelationTableId(
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

export function hydrateBindingsRowsFromVariablesBySharedRelationTableId<
  T extends {
    bindingId: number
    tableId?: number | null
    data: any[]
    primaryKeyFields?: string[] | null | undefined
  },
>(
  bindings: T[],
  savedSubTables: Record<string, unknown>,
  bindingTableById: Map<number, number | null>,
): void {
  for (const b of bindings) {
    /**
     * Same failure mode as {@link hydrateChildSubTablesFromParentsNestedRows}: “thin” rows loaded from a wrong
     * binding key still count as {@code length > 0}, so we skipped relation-table hydration and never merged the
     * rich slice keyed by the initiator/copied binding id.
     */
    const existing = Array.isArray(b.data) ? b.data : []

    const claimedKeys = claimedNumericSubTableSliceKeys(bindings, savedSubTables)

    let selfTidRaw = b.tableId != null ? Number(b.tableId) : bindingTableById.get(b.bindingId)
    if (selfTidRaw == null || Number.isNaN(selfTidRaw)) {
      const inferred = inferOrphanRelationTableId(b, bindings, bindingTableById, savedSubTables)
      if (inferred != null && Number.isFinite(inferred)) {
        selfTidRaw = inferred
      }
    }

    const chunks: any[] = []
    if (selfTidRaw != null && !Number.isNaN(selfTidRaw)) {
      for (const [key, val] of Object.entries(savedSubTables)) {
        if (!Array.isArray(val) || val.length === 0) continue
        const kid = Number(key)
        if (!Number.isFinite(kid) || kid === b.bindingId) continue
        const otherTid = bindingTableById.get(kid)
        if (otherTid == null || Number.isNaN(Number(otherTid))) continue
        if (Number(otherTid) !== selfTidRaw) continue
        chunks.push(...val)
      }
    }

    if (chunks.length === 0) {
      chunks.push(...mergeRowsFromSoleUnclaimedNumericSlice(b, savedSubTables, claimedKeys))
    }

    if (chunks.length === 0 && existing.length === 0) {
      chunks.push(
        ...mergeRowsFromRichestUnclaimedNumericSlice(
          b,
          savedSubTables,
          claimedKeys,
          bindingTableById,
          selfTidRaw != null && !Number.isNaN(Number(selfTidRaw)) ? Number(selfTidRaw) : null,
        ),
      )
    }

    if (chunks.length === 0) continue
    b.data = cloneSubTableRows(
      mergeSubTableRowsByRowId(existing, chunks, b.primaryKeyFields ?? null),
    )
  }
}

/**
 * Walk every top-level row in {@code savedSubTables} and collect distinct nested {@code row.__subTables__[key]}
 * arrays that match the binding (numeric id, table name, physical name). Used when child rows only exist under
 * parent rows while the top-level slice for the child binding is thin or missing.
 */
export function collectNestedSlicesForBindingFromSubTablesWalk(
  savedSubTables: Record<string, unknown> | null | undefined,
  binding: { bindingId: number; tableName: string; physicalTableName?: string },
): unknown[][] {
  if (!savedSubTables || typeof savedSubTables !== 'object') return []
  const candidates: string[] = []
  const add = (s?: string) => {
    if (s == null || s === '') return
    const t = String(s)
    if (!candidates.includes(t)) candidates.push(t)
    const n = normalizeSubTableName(s)
    if (n && n !== t && !candidates.includes(n)) candidates.push(n)
  }
  add(String(binding.bindingId))
  const bid = Number(binding.bindingId)
  if (Number.isFinite(bid)) add(String(bid))
  add(binding.tableName)
  add(binding.physicalTableName)

  const out: unknown[][] = []
  const seen = new WeakSet<object>()
  for (const val of Object.values(savedSubTables)) {
    if (!Array.isArray(val)) continue
    for (const row of val) {
      if (!row || typeof row !== 'object') continue
      const nest = (row as Record<string, unknown>).__subTables__
      if (!nest || typeof nest !== 'object') continue
      for (const key of candidates) {
        const arr = (nest as Record<string, unknown>)[key]
        if (!Array.isArray(arr) || arr.length === 0) continue
        if (seen.has(arr as object)) break
        seen.add(arr as object)
        out.push(arr)
        break
      }
    }
  }
  return out
}

/**
 * Flow / MI often mirror a thin row at the top-level slice (name, assignee…) while lookup / id / custom fields
 * remain only under {@code parentRow.__subTables__[childBindingId|legacyKey]}. Fill missing fields on child rows.
 */
export function enrichChildBindingRowsFromParentsNestedSubTables<
  T extends {
    bindingId: number
    tableName?: string
    physicalTableName?: string
    tableId?: number | null
    data: any[]
    primaryKeyFields?: string[] | null | undefined
  },
>(bindings: T[]): void {
  const countDataKeys = (row: unknown): number => {
    if (!row || typeof row !== 'object') return 0
    return Object.keys(row as object).filter(k => !k.startsWith('__')).length
  }

  /** True when patch can supply non-empty values for keys that are empty/missing on target (same field count ≠ same fields). */
  const patchCanFillEmptyKeys = (target: unknown, patch: unknown): boolean => {
    if (!target || typeof target !== 'object' || !patch || typeof patch !== 'object') return false
    const t = target as Record<string, unknown>
    const p = patch as Record<string, unknown>
    for (const [k, val] of Object.entries(p)) {
      if (k.startsWith('__')) continue
      if (val === undefined || val === null || val === '') continue
      const cur = t[k]
      if (cur === undefined || cur === null || cur === '') return true
    }
    return false
  }

  const mergePatchIntoRow = (
    target: Record<string, unknown>,
    patch: Record<string, unknown>,
  ): boolean => {
    let changed = false
    for (const [k, val] of Object.entries(patch)) {
      if (k.startsWith('__')) continue
      if (val === undefined || val === null || val === '') continue
      const cur = target[k]
      if (cur !== undefined && cur !== null && cur !== '') continue
      target[k] = val
      changed = true
    }
    return changed
  }

  const peerMap = buildBindingTableIdMapFromPeers(bindings)

  for (const child of bindings) {
    if (!Array.isArray(child.data)) child.data = [] as any
    if (child.data.length === 0) {
      let incoming: any[] = []
      for (const parent of bindings) {
        if (parent.bindingId === child.bindingId) continue
        incoming.push(
          ...pullNestedRowsForBindingFromParentRows(
            {
              bindingId: child.bindingId,
              tableName: child.tableName ?? '',
              physicalTableName: child.physicalTableName,
              tableId: child.tableId ?? null,
            },
            Array.isArray(parent.data) ? parent.data : [],
            peerMap,
          ),
        )
      }
      if (incoming.length > 0) {
        child.data = cloneSubTableRows(
          mergeSubTableRowsByRowId([], incoming, child.primaryKeyFields ?? null),
        ) as any
      }
    }

    if (!Array.isArray(child.data) || child.data.length === 0) continue
    for (const parent of bindings) {
      if (parent.bindingId === child.bindingId) continue
      const pr0 = Array.isArray(parent.data) && parent.data[0] ? parent.data[0] : null
      if (!pr0 || typeof pr0 !== 'object') continue
      const nest = (pr0 as Record<string, unknown>).__subTables__
      if (!nest || typeof nest !== 'object') continue
      const sto = nest as Record<string, unknown>

      const tryPatches = (arrays: unknown[]): boolean => {
        for (const arr of arrays) {
          if (!Array.isArray(arr) || arr.length === 0) continue
          const c0 = child.data[0]
          if (!c0 || typeof c0 !== 'object') continue
          const patch0 = arr[0]
          if (!patch0 || typeof patch0 !== 'object') continue
          const hasMoreKeys = countDataKeys(patch0) > countDataKeys(c0)
          if (!hasMoreKeys && !patchCanFillEmptyKeys(c0, patch0)) continue
          let any = false
          for (let i = 0; i < child.data.length; i++) {
            const srcRow = arr[Math.min(i, arr.length - 1)]
            if (!srcRow || typeof srcRow !== 'object') continue
            if (mergePatchIntoRow(child.data[i] as Record<string, unknown>, srcRow as Record<string, unknown>)) {
              any = true
            }
          }
          if (any) return true
        }
        return false
      }

      const prioritized: unknown[] = []
      const d1 = sto[child.bindingId]
      const d2 = sto[String(child.bindingId)]
      if (Array.isArray(d1)) prioritized.push(d1)
      if (Array.isArray(d2) && d1 !== d2) prioritized.push(d2)

      if (tryPatches(prioritized)) break

      const fallback: unknown[] = []
      for (const v of Object.values(sto)) {
        if (!Array.isArray(v) || v.length === 0) continue
        fallback.push(v)
      }
      fallback.sort(
        (a, b) =>
          countDataKeys((b as any[])[0]) - countDataKeys((a as any[])[0]),
      )
      if (tryPatches(fallback)) break
    }
  }
}

/**
 * Gather child-table rows nested under {@code parentRows[*].__subTables__} for the given child binding.
 * Exported for FormRenderer inline form-below-table when {@code target.data} is thin/empty but rows nest under
 * parent rows (e.g. legacy {@code bindingId} keys after BPMN form copy).
 */
export function pullNestedRowsForBindingFromParentRows(
  child: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null },
  parentRows: any[],
  bindingTableById?: Map<number, number | null>
): any[] {
  const out: any[] = []
  const childTid =
    child.tableId != null && Number.isFinite(Number(child.tableId))
      ? Number(child.tableId)
      : bindingTableById?.get(child.bindingId) ?? null

  for (const row of parentRows) {
    if (!row || typeof row !== 'object') continue
    const st = (row as Record<string, unknown>).__subTables__
    if (!st || typeof st !== 'object') continue
    const sto = st as Record<string, unknown>
    const rowOutBefore = out.length
    const nested = findNestedChildRowsInSto(sto, child)
    if (nested) {
      out.push(...nested)
    }

    // Nested maps may still key child rows by an older binding id (initiator / prior userTask).
    if (bindingTableById != null && childTid != null && Number.isFinite(childTid)) {
      for (const [k, v] of Object.entries(sto)) {
        const kid = Number(k)
        if (!Number.isFinite(kid) || kid === child.bindingId) continue
        const otid = bindingTableById.get(kid)
        if (otid == null || Number.isNaN(Number(otid))) continue
        if (Number(otid) !== Number(childTid)) continue
        if (Array.isArray(v) && v.length > 0) {
          out.push(...v)
        }
      }
    }

    // No direct / tableId match on this row: exactly one other numeric-keyed array → sole child slice.
    if (out.length === rowOutBefore) {
      const ambiguous: any[][] = []
      for (const [k, v] of Object.entries(sto)) {
        const kid = Number(k)
        if (!Number.isFinite(kid) || kid === child.bindingId) continue
        if (Array.isArray(v) && v.length > 0) ambiguous.push(v)
      }
      if (ambiguous.length === 1) {
        out.push(...ambiguous[0]!)
      }
    }
  }
  return out
}

/**
 * Pull nested child rows from every peer binding's row payloads (same idea as hydrate, without mutating bindings).
 */
export function collectNestedChildRowsFromPeerBindings<
  T extends {
    bindingId: number
    tableName: string
    physicalTableName?: string
    tableId?: number | null
    data: any[]
  },
>(
  target: T,
  peers: T[],
  bindingTableById?: Map<number, number | null> | null,
): any[] {
  const map =
    bindingTableById != null
      ? bindingTableById
      : (() => {
          const m = new Map<number, number | null>()
          for (const b of peers) {
            const tid = b.tableId != null ? Number(b.tableId) : null
            if (tid != null && Number.isFinite(tid)) m.set(b.bindingId, tid)
          }
          return m
        })()
  const acc: any[] = []
  for (const pb of peers) {
    if (pb.bindingId === target.bindingId) continue
    acc.push(
      ...pullNestedRowsForBindingFromParentRows(
        target,
        Array.isArray(pb.data) ? pb.data : [],
        map,
      ),
    )
  }
  return acc
}

export function hydrateChildSubTablesFromParentsNestedRows<
  T extends {
    bindingId: number
    tableName: string
    physicalTableName?: string
    tableId?: number | null
    data: any[]
    primaryKeyFields?: string[] | null | undefined
  },
>(
  bindings: T[],
  savedSubTables?: Record<string, unknown> | null,
  bindingTableById?: Map<number, number | null>
): void {
  for (const child of bindings) {
    /**
     * Flat {@code __subTables__[childBindingId]} may contain thin placeholder rows (assignee-only).
     * Previously we skipped nested hydration whenever {@code child.data.length > 0}, so fields that only
     * exist under {@code parent.__subTables__[childBindingId]} never merged — Link-target inline forms stayed empty.
     */
    const existing = Array.isArray(child.data) ? child.data : []

    let mergedIncoming: any[] = []
    for (const parent of bindings) {
      if (parent.bindingId === child.bindingId) continue
      mergedIncoming.push(
        ...pullNestedRowsForBindingFromParentRows(
          child,
          Array.isArray(parent.data) ? parent.data : [],
          bindingTableById
        )
      )
    }

    if (
      mergedIncoming.length === 0 &&
      savedSubTables &&
      typeof savedSubTables === 'object'
    ) {
      const flattened: any[] = []
      for (const val of Object.values(savedSubTables)) {
        if (Array.isArray(val)) flattened.push(...val)
      }
      mergedIncoming = pullNestedRowsForBindingFromParentRows(child, flattened, bindingTableById)
    }

    if (mergedIncoming.length === 0) continue
    const pk = child.primaryKeyFields ?? null
    child.data = cloneSubTableRows(mergeSubTableRowsByRowId(existing, mergedIncoming, pk))
  }
}
