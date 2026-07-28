/**
 * Pulling child sub-table rows nested under {@code parentRow.__subTables__} (Link Form persistence)
 * and hydrating child bindings from those nested slices.
 */

import {
  cloneSubTableRows,
  normalizeSubTableName,
  stripLinkFormDesignerTableLabel,
} from './subTableCore'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'

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

/** True when a `__subTables__` slice key addresses this binding (numeric id or table-name alias). */
function subTableSliceKeyBelongsToBinding(
  key: string,
  binding: { bindingId: number; tableName?: string; physicalTableName?: string },
): boolean {
  const k = String(key).trim()
  if (!k) return false
  if (k === String(binding.bindingId)) return true
  const norm = normalizeSubTableName(k)
  for (const name of [binding.tableName, binding.physicalTableName]) {
    if (!name) continue
    const raw = String(name).trim()
    if (!raw) continue
    if (k === raw || norm === normalizeSubTableName(raw)) return true
    const stripped = stripLinkFormDesignerTableLabel(raw)
    if (stripped !== raw && (k === stripped || norm === normalizeSubTableName(stripped))) return true
  }
  return false
}

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
        // A slice we can positively attribute to ANOTHER table is never this binding's rows.
        // Without this, a 3-level nest fed the grandchild slice (nst_package) to the middle
        // binding (nst_shipment) — phantom parent rows, grandchild rows detached from the parent.
        if (bindingTableById != null && childTid != null) {
          const otid = bindingTableById.get(kid)
          if (otid != null && !Number.isNaN(Number(otid)) && Number(otid) !== Number(childTid)) {
            continue
          }
        }
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
  // Peers already carry tableId; deriving the map when the caller passes none lets the
  // "sole nested slice" fallback reject slices that belong to a different table.
  const tableById =
    bindingTableById ??
    (() => {
      const m = new Map<number, number | null>()
      for (const b of bindings) {
        const tid = b.tableId != null ? Number(b.tableId) : null
        if (tid != null && Number.isFinite(tid)) m.set(Number(b.bindingId), tid)
      }
      return m.size > 0 ? m : undefined
    })()

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
          tableById
        )
      )
    }

    if (
      mergedIncoming.length === 0 &&
      savedSubTables &&
      typeof savedSubTables === 'object'
    ) {
      const flattened: any[] = []
      for (const [key, val] of Object.entries(savedSubTables)) {
        if (!Array.isArray(val)) continue
        // Skip the child's OWN slices (numeric id / table-name aliases): those rows are the
        // binding's own data, not parents. Scanning them made a middle sub-table adopt its
        // grandchildren (nst_shipment pulling nst_package rows) once nesting is 3 levels deep.
        if (subTableSliceKeyBelongsToBinding(key, child)) continue
        flattened.push(...val)
      }
      mergedIncoming = pullNestedRowsForBindingFromParentRows(child, flattened, tableById)
    }

    if (mergedIncoming.length === 0) continue
    const pk = child.primaryKeyFields ?? null
    child.data = cloneSubTableRows(mergeSubTableRowsByRowId(existing, mergedIncoming, pk))
  }
}
