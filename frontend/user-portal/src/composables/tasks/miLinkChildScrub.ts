/**
 * MI link-child corruption scrub / repair for {@code __subTables__} payloads, plus nested-row
 * flattening into the top-level variables map before submit / after load.
 */

import { isAllocatedUuidPrimaryKey, MI_LINK_CHILD_SCALAR_KEYS } from './internal'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { stripForeignParticipantIdIdwFromLinkChildRow } from './miLinkChildIdentity'

/**
 * Link Form / 「表格下表单」在编辑态常把子表行只写在 {@code parentRow.__subTables__[childBindingId]}，而流程变量提交
 * ({@code __subTables__} 顶层 map) 需要同一份行也挂在 {@code __subTables__[childKey]}，待办加载的
 * {@code getSavedSubTableRows} 才能命中。本函数在原位多轮提升（处理链式嵌套）；入参应为普通 JSON 形态的对象。
 */
function normalizeFkIdForMatchLocal(v: unknown): string | null {
  if (v === undefined || v === null) return null
  const s = String(v).trim()
  return s === '' ? null : s
}

function miLinkChildRowHasFormPayload(rec: Record<string, unknown>): boolean {
  for (const [k, v] of Object.entries(rec)) {
    if (k.startsWith('__') || MI_LINK_CHILD_SCALAR_KEYS.has(k)) continue
    if (v === undefined || v === null) continue
    if (typeof v === 'string' && v.trim() === '') continue
    return true
  }
  return false
}

function repairMiCorruptLinkChildRowId(
  rec: Record<string, unknown>,
  parentKey: string
): Record<string, unknown> {
  const n = Number(parentKey)
  const fixedId = Number.isFinite(n) && String(n) === parentKey ? n : parentKey
  return { ...rec, id: fixedId }
}

/**
 * MI link-child rows may carry another participant's stale {@code id} while {@code id_idw} matches the parent
 * expansion key (runtime: id=44, id_idw=88). Thin placeholders are dropped; rows with real form payload keep
 * sex/age/etc. and get {@code id} aligned to the parent expansion key.
 *
 * When {@code id} is already an allocated UUID PK, {@code id_idw} mirroring the parent expansion key is
 * erroneous — strip {@code id_idw} instead of overwriting the UUID (People Save path).
 */
export function scrubMiCorruptLinkChildRowsForParent(
  subTables: Record<string, unknown>,
  parentIdIdw: string | number,
  options?: { skipSliceKeys?: Set<string> | null },
): void {
  const key = normalizeFkIdForMatchLocal(parentIdIdw)
  if (key == null) return
  const skipKeys = options?.skipSliceKeys ?? null

  /**
   * MI collection (Sub Task / dashboard) slices keep {@code id_idw} as the participant primary key, so
   * {@code id_idw === parentIdIdw} is legitimate — never strip it there. The strip/repair logic targets
   * link-child slices (People etc.) only; on collection slices we leave rows untouched but still recurse
   * nested so genuine link-child rows under a participant parent are still cleaned.
   */
  const repairSlice = (rows: unknown[], isCollectionSlice: boolean): unknown[] => {
    const out: unknown[] = []
    for (const row of rows) {
      if (!row || typeof row !== 'object') {
        out.push(row)
        continue
      }
      const rec = row as Record<string, unknown>
      const cidw = normalizeFkIdForMatchLocal(rec.id_idw)
      const cid = normalizeFkIdForMatchLocal(rec.id)
      if (!isCollectionSlice && cidw === key && cid != null && cid !== key) {
        if (miLinkChildRowHasFormPayload(rec)) {
          if (isAllocatedUuidPrimaryKey(rec.id)) {
            const cleaned = { ...rec }
            delete cleaned.id_idw
            const nest = cleaned.__subTables__
            if (nest && typeof nest === 'object' && !Array.isArray(nest)) {
              scrubMiCorruptLinkChildRowsForParent(nest as Record<string, unknown>, parentIdIdw, options)
            }
            out.push(cleaned)
          } else {
            const repaired = repairMiCorruptLinkChildRowId(rec, key)
            const nest = repaired.__subTables__
            if (nest && typeof nest === 'object' && !Array.isArray(nest)) {
              scrubMiCorruptLinkChildRowsForParent(nest as Record<string, unknown>, parentIdIdw, options)
            }
            out.push(repaired)
          }
        }
        continue
      }
      // Heal a current-participant link-child row that carries a DIFFERENT participant's id_idw (legacy
      // corruption from the seed/collapse leak, #1444): structural FK already anchors it here and id is a
      // UUID, so the foreign id_idw is spurious and would make load-side participant filters reject the row.
      const healed =
        !isCollectionSlice
          ? stripForeignParticipantIdIdwFromLinkChildRow(rec, parentIdIdw)
          : rec
      const nest = healed.__subTables__
      if (nest && typeof nest === 'object' && !Array.isArray(nest)) {
        scrubMiCorruptLinkChildRowsForParent(nest as Record<string, unknown>, parentIdIdw, options)
      }
      out.push(healed)
    }
    return out
  }

  for (const [sliceKey, val] of Object.entries(subTables)) {
    if (!Array.isArray(val)) continue
    const cleaned = repairSlice(val, skipKeys?.has(String(sliceKey)) ?? false)
    subTables[sliceKey] = cleaned
    subTables[String(sliceKey)] = cleaned
  }
}

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
          // The existing top-level slice is the authoritative binding data; a nested copy is a
          // derivative cache that may lag behind (e.g. a prior userTask's collection row still holds a
          // stale link-child age while the current task already persisted a fresh one). Merge with the
          // nested copy as the base so the authoritative top-level row's filled fields win, while
          // nested-only rows are still hoisted and empty top-level fields are filled. (#1443)
          const merged =
            prevRows.length > 0
              ? mergeSubTableRowsByRowId(childVal as any[], prevRows, null)
              : mergeSubTableRowsByRowId(prevRows, childVal as any[], null)
          subTables[childKey] = merged
          subTables[String(childKey)] = merged
          touched = true
        }
      }
    }
    if (!touched) break
  }
}
