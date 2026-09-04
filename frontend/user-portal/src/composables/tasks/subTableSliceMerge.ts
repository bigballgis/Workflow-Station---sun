/**
 * Merging of {@code __subTables__} slices that belong to one relation table
 * (top-level binding-id / table-name keys plus nested {@code row.__subTables__} chains).
 */

import { normalizeSubTableName } from './subTableCore'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { isFileOnlySubTableBinding, isMiDashboardSubTableBinding } from './subTableBindingKinds'
import {
  miChildFkConfigOfBinding,
  resolveMiChildStructuralParentFk,
} from './miLinkChildIdentity'

/**
 * Merge {@code __subTables__} slices that belong to one relation table (by designer {@code tableId} / table name),
 * without pulling unrelated MI participant slices (subtable id 343 must not appear on attachment).
 */
export function mergeSubTableSlicesForRelationTableId(
  savedSubTables: Record<string, unknown> | null | undefined,
  tableId: number,
  bindingTableById: Map<number, number | null>,
  pkFieldNames?: string[] | null,
  tableName?: string | null,
  physicalTableName?: string | null,
): any[] {
  if (!savedSubTables || typeof savedSubTables !== 'object') return []
  if (!Number.isFinite(tableId)) return []

  const seenArrays = new Set<unknown>()
  let merged: any[] = []

  const ingest = (val: unknown) => {
    if (!Array.isArray(val) || val.length === 0 || seenArrays.has(val)) return
    seenArrays.add(val)
    merged = mergeSubTableRowsByRowId(merged, val as any[], pkFieldNames ?? null)
  }

  for (const label of [tableName, physicalTableName]) {
    if (label == null || String(label).trim() === '') continue
    const t = String(label).trim()
    ingest(savedSubTables[t])
    ingest(savedSubTables[normalizeSubTableName(t)])
  }

  for (const [key, val] of Object.entries(savedSubTables)) {
    const kid = Number(key)
    if (!Number.isFinite(kid)) continue
    const otherTid = bindingTableById.get(kid)
    if (otherTid == null || Number.isNaN(Number(otherTid))) continue
    if (Number(otherTid) !== tableId) continue
    ingest(val)
  }

  return merged
}

/**
 * Like {@link mergeSubTableSlicesForRelationTableId} but returns the RAW concatenated rows of all
 * same-relation-table slices WITHOUT collapsing by primary key.
 *
 * MI participant-scoped bindings can legitimately have rows from different participants that share the
 * same row PK (legacy/sequential PK allocation). Merging by id BEFORE the participant filter collapses
 * those distinct rows, letting another participant's slice overwrite the current participant's data
 * (e.g. People age/sex), which then gets dropped by the participant filter — surfacing as a blank form.
 * Callers MUST filter to the current participant first, then merge.
 */
export function collectSubTableSliceRowsForRelationTableId(
  savedSubTables: Record<string, unknown> | null | undefined,
  tableId: number,
  bindingTableById: Map<number, number | null>,
  tableName?: string | null,
  physicalTableName?: string | null,
): any[] {
  if (!savedSubTables || typeof savedSubTables !== 'object') return []
  if (!Number.isFinite(tableId)) return []

  const seenArrays = new Set<unknown>()
  const out: any[] = []
  const ingest = (val: unknown) => {
    if (!Array.isArray(val) || val.length === 0 || seenArrays.has(val)) return
    seenArrays.add(val)
    for (const r of val) out.push(r)
  }

  for (const label of [tableName, physicalTableName]) {
    if (label == null || String(label).trim() === '') continue
    const t = String(label).trim()
    ingest(savedSubTables[t])
    ingest(savedSubTables[normalizeSubTableName(t)])
  }

  for (const [key, val] of Object.entries(savedSubTables)) {
    const kid = Number(key)
    if (!Number.isFinite(kid)) continue
    const otherTid = bindingTableById.get(kid)
    if (otherTid == null || Number.isNaN(Number(otherTid))) continue
    if (Number(otherTid) !== tableId) continue
    ingest(val)
  }

  return out
}

/**
 * Deep walk {@code row.__subTables__} chains for slices keyed by binding id / table name
 * (attachment rows often persist only under MI parent rows until flattened).
 */
export function collectAllNestedSlicesForBindingDeep(
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

  const ingestNest = (nest: Record<string, unknown>) => {
    for (const key of candidates) {
      const arr = nest[key]
      if (!Array.isArray(arr) || arr.length === 0) continue
      if (seen.has(arr as object)) continue
      seen.add(arr as object)
      out.push(arr)
    }
  }

  const walkRows = (rows: unknown[]) => {
    for (const row of rows) {
      if (!row || typeof row !== 'object') continue
      const nest = (row as Record<string, unknown>).__subTables__
      if (!nest || typeof nest !== 'object') continue
      ingestNest(nest as Record<string, unknown>)
      for (const val of Object.values(nest)) {
        if (Array.isArray(val)) walkRows(val)
      }
    }
  }

  for (const val of Object.values(savedSubTables)) {
    if (Array.isArray(val)) walkRows(val)
  }
  return out
}

/** Top-level + nested slices for one shared process sub-table (attachment.main_id, etc.). */
export function mergeAllSlicesForSharedProcessSubTableBinding(
  savedSubTables: Record<string, unknown> | null | undefined,
  binding: {
    bindingId: number
    tableId?: number | null
    tableName?: string
    physicalTableName?: string
    primaryKeyFields?: string[] | null
  },
  bindingTableById: Map<number, number | null>,
  options?: { omitNestedSlices?: boolean },
): any[] {
  if (!savedSubTables || typeof savedSubTables !== 'object') return []

  const pk = binding.primaryKeyFields ?? null
  let merged: any[] = []
  const ingest = (rows: unknown) => {
    if (!Array.isArray(rows) || rows.length === 0) return
    merged = mergeSubTableRowsByRowId(merged, rows as any[], pk)
  }

  const own =
    savedSubTables[binding.bindingId] ?? savedSubTables[String(binding.bindingId)]
  ingest(own)

  const tableIdRaw =
    binding.tableId != null ? Number(binding.tableId) : bindingTableById.get(binding.bindingId)
  if (tableIdRaw != null && Number.isFinite(tableIdRaw)) {
    ingest(
      mergeSubTableSlicesForRelationTableId(
        savedSubTables,
        Number(tableIdRaw),
        bindingTableById,
        pk,
        binding.tableName,
        binding.physicalTableName,
      ),
    )
    for (const [bid, tid] of bindingTableById.entries()) {
      if (Number(tid) !== Number(tableIdRaw)) continue
      const slice = savedSubTables[bid] ?? savedSubTables[String(bid)]
      ingest(slice)
    }
  }

  for (const label of [binding.tableName, binding.physicalTableName]) {
    if (label == null || String(label).trim() === '') continue
    ingest(savedSubTables[String(label).trim()])
    ingest(savedSubTables[normalizeSubTableName(String(label))])
  }

  if (!options?.omitNestedSlices) {
    for (const chunk of collectAllNestedSlicesForBindingDeep(savedSubTables, {
      bindingId: binding.bindingId,
      tableName: binding.tableName ?? '',
      physicalTableName: binding.physicalTableName,
    })) {
      ingest(chunk)
    }

    if (tableIdRaw != null && Number.isFinite(tableIdRaw)) {
      for (const [bid, tid] of bindingTableById.entries()) {
        if (Number(tid) !== Number(tableIdRaw) || bid === binding.bindingId) continue
        for (const chunk of collectAllNestedSlicesForBindingDeep(savedSubTables, {
          bindingId: bid,
          tableName: binding.tableName ?? '',
          physicalTableName: binding.physicalTableName,
        })) {
          ingest(chunk)
        }
      }
    }
  }

  return merged
}

/**
 * #1446 — MI subtask Save: propagate the edited link-form (e.g. People) rows into stale sibling
 * slices of the same relation table (another node's binding id / table-name keys), matching by the
 * binding's row primary key only. Update-only: never appends rows, never touches MI collection
 * slices ({@code collectionSliceKeys}, preserving the 09be69f8 / #1442 leak guards), and ignores
 * MI dashboard / file-only source bindings entirely. Rows are matched on the DESIGNER primary key
 * (no literal {@code id} fallback); a row whose PK value equals its own structural parent FK is
 * skipped — that is a participant key copied into the PK column (e.g. {@code id_idw: "Test-000074"}),
 * not the row identity, and letting it match would smear link-form payload onto collection rows.
 */

export function syncMiLinkChildEditedRowsIntoSiblingSlices(
  subTables: Record<string, any>,
  binding: {
    bindingId: number
    tableName?: string
    primaryKeyFields?: string[] | null
    columns?: Array<{ field?: string }> | null
  },
  editedRows: unknown[] | undefined | null,
  collectionSliceKeys: Set<string>,
  /**
   * MI collection 的设计器主键列。传入时用于识别「这个 binding 的主键就是参与者键」——
   * 那种 binding 的 PK 不是行身份，不能驱动同步（#1446）。省略则退回按行内 FK 判断。
   */
  collectionPrimaryKeyFields?: string[] | null,
): void {
  if (isMiDashboardSubTableBinding(binding) || isFileOnlySubTableBinding(binding)) return
  if (!Array.isArray(editedRows) || editedRows.length === 0) return
  // 主键列**只认设计器配置**。曾经退回字面量 ['id']：主键叫 correspondence_id / idqc 的表上
  // 永远取不到值，却让下面的匹配以为拿到了确定答案。拿不到配置就直接不同步 —— 少同步一次只是
  // 别的切片保持原样，而猜错主键会把一行的编辑抹到另一行上。
  const pkCols = (Array.isArray(binding.primaryKeyFields) ? binding.primaryKeyFields : [])
    .map(f => String(f).trim())
    .filter(Boolean)
  if (pkCols.length === 0) return

  const fkConfig = miChildFkConfigOfBinding(binding as never)
  // 这个 binding 的主键列**就是 MI collection 的主键列** = 它的 PK 装的是参与者键而非行身份。
  // 判据来自设计器配置（collection 的 primaryKeyFields），不是列名字面量。
  const collectionPk = (collectionPrimaryKeyFields ?? [])
    .map(f => String(f ?? '').trim())
    .filter(Boolean)
  if (collectionPk.length > 0 && pkCols.every(c => collectionPk.includes(c))) return

  const pkOf = (row: unknown): string | null => {
    if (!row || typeof row !== 'object') return null
    const rec = row as Record<string, unknown>
    const parts: string[] = []
    for (const col of pkCols) {
      const v = rec[col]
      if (v == null) return null
      const s = String(v).trim()
      if (!s) return null
      parts.push(s)
    }
    const key = parts.join('')
    // 主键值**恰好等于这一行指向父行的外键值**时不参与同步：那不是"这一行的身份"，
    // 而是 participant key 被复制进了主键列（#1446 的原始事故 —— 一个
    // `primaryKeyFields: ['id_idw']` 的 binding 会把 link-form 内容抹到每条 id_idw
    // 相同的 collection 行上）。
    //
    // 判据由「值长得像 UUID」改为「主键 != 这一行自己的父外键」：前者假设主键一定是 uuid
    // 策略，而设计器 pk_generation_json 还有 prefixedSequence（Corr-000004 /
    // ATM-DC-PW-TRANS-000004），那些表的行会**全部**被判成"不是已分配主键"而永远同步不到
    // 兄弟切片 —— 同一个硬编码假设，另一处表现。
    //
    // FK 读设计器配置；解析不出时不做这层判断（上面的 collection 主键比对已经挡住了
    // 「主键就是参与者键」的 binding 这一主要风险）。
    const structuralFk = resolveMiChildStructuralParentFk(rec, fkConfig)
    if (structuralFk != null && key === structuralFk) return null
    return key
  }

  const editedByPk = new Map<string, Record<string, unknown>>()
  for (const row of editedRows) {
    const key = pkOf(row)
    if (key != null) editedByPk.set(key, row as Record<string, unknown>)
  }
  if (editedByPk.size === 0) return

  const ownKeys = new Set<string>([String(binding.bindingId)])
  if (binding.tableName) {
    ownKeys.add(String(binding.tableName).trim())
    ownKeys.add(normalizeSubTableName(String(binding.tableName)))
  }

  for (const sliceKey of Object.keys(subTables)) {
    if (ownKeys.has(sliceKey)) continue
    if (collectionSliceKeys.has(sliceKey)) continue
    const rows = subTables[sliceKey]
    if (!Array.isArray(rows) || rows.length === 0) continue
    let touched = false
    const next = rows.map(row => {
      const key = pkOf(row)
      if (key == null) return row
      const edited = editedByPk.get(key)
      if (!edited) return row
      touched = true
      return { ...(row as Record<string, unknown>), ...edited }
    })
    if (touched) subTables[sliceKey] = next
  }
}
