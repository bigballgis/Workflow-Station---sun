/** Sub-table row merging by primary key / rowKey, including MI dashboard status-aware conflict resolution. */

import {
  incomingIsStrictNonMiKeySubset, mergeMiCurrentNodeForTerminal, mergeMiCurrentNodeInFlight,
  mergeMiCurrentNodePreferPrevious, mergeMiTaskStatusPreferTerminal, miDashboardSliceRichness,
  miSubFormOrdinalHint, miTaskStatusIsTerminal, roughNonEmptyFieldCount,
} from './internal'
import {
  resolveMiDashboardFieldNames,
  type MiDashboardFieldNames,
} from './subTableBindingKinds'

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

/**
 * True when every designer PK column resolves on the row (incl. nested {@code rowKey} /
 * {@code id}↔{@code id_idw} aliases used by {@link mergeSubTableRowsByRowId}).
 */
export function rowResolvesDesignerPrimaryKey(
  row: unknown,
  pkFieldNames?: string[] | null,
): boolean {
  if (!row || typeof row !== 'object') return false
  const pkCols =
    Array.isArray(pkFieldNames) && pkFieldNames.length > 0
      ? pkFieldNames.map(f => String(f).trim()).filter(Boolean)
      : []
  if (pkCols.length === 0) return false
  return compositePkMergeKey(row as Record<string, unknown>, pkCols) != null
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
 *
 * There is deliberately NO `row_id` step: `row_id` is a per-snapshot frontend value, so the same
 * physical row carries different ones in the engine-variables copy and the portal `subTableData`
 * copy, and keying on it merged one MI participant's row over another's and submitted the wrong
 * row. A table whose designer PK genuinely IS `row_id` (e.g. ATM_Transaction) still keys on it —
 * through `pkFieldNames` like any other PK, not through a hardcoded column name.
 */
export function mergeSubTableRowsByRowId(
  existing: any[] | undefined,
  incoming: any[],
  pkFieldNames?: string[] | null,
  miFields?: MiDashboardFieldNames | null
): any[] {
  const { statusField: miStatusField, currentNodeField: miCurrentNodeField } =
    resolveMiDashboardFieldNames(miFields)
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
    const MI_STATUS_KEY = miStatusField
    const MI_NODE_KEY = miCurrentNodeField
    const out: Record<string, unknown> = { ...previous }
    for (const [key, val] of Object.entries(incoming)) {
      if (val === undefined) continue
      if (key === MI_STATUS_KEY || key === MI_NODE_KEY) continue

      if (key === 'assignee' || key === 'assignee_user_id' || key === 'assignee_id') {
        out[key] = val
        if (!('assignee_display_name' in incoming)) {
          delete out.assignee_display_name
        }
        continue
      }

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
    const rp = miDashboardSliceRichness(previous, miStatusField, miCurrentNodeField) * 512 + roughNonEmptyFieldCount(previous)
    const ri = miDashboardSliceRichness(incoming, miStatusField, miCurrentNodeField) * 512 + roughNonEmptyFieldCount(incoming)
    let mergedNode: string | undefined
    if (terminal) {
      mergedNode = mergeMiCurrentNodeForTerminal(out[MI_NODE_KEY], incoming[MI_NODE_KEY])
    } else {
      const oAccum = miSubFormOrdinalHint(out[MI_NODE_KEY])
      const oIncoming = miSubFormOrdinalHint(incoming[MI_NODE_KEY])
      // Furthest BPMN sub form step wins over slice richness (thin id row vs fat id_idw-only stale row).
      if (oAccum !== null && oIncoming !== null && oIncoming !== oAccum) {
        mergedNode =
          oIncoming > oAccum
            ? String(incoming[MI_NODE_KEY] ?? '').trim()
            : String(out[MI_NODE_KEY] ?? '').trim()
      } else if (ri > rp) {
        mergedNode = mergeMiCurrentNodeInFlight(out[MI_NODE_KEY], incoming[MI_NODE_KEY])
      } else if (rp > ri) {
        mergedNode = mergeMiCurrentNodePreferPrevious(out[MI_NODE_KEY], incoming[MI_NODE_KEY])
      } else if (incomingIsStrictNonMiKeySubset(previous, incoming, miStatusField, miCurrentNodeField)) {
        mergedNode = mergeMiCurrentNodePreferPrevious(out[MI_NODE_KEY], incoming[MI_NODE_KEY])
      } else {
        mergedNode = mergeMiCurrentNodeInFlight(out[MI_NODE_KEY], incoming[MI_NODE_KEY])
      }
    }
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
