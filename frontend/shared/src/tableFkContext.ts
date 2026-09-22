/**
 * Add-row ancestor context: ordered frames instead of one slot per table.
 * Duplicate same-table rows are not guessed (F2). Declared fill sources pick PARENT /
 * PRIMARY / a named ancestor binding instead of unique-table fallback.
 */
export type ContextFrameRole = 'PRIMARY' | 'PARENT' | 'FILTER_SIBLING'

export interface ContextFrame {
  tableId: number
  row: Record<string, unknown>
  role: ContextFrameRole
  bindingId?: number | string
}

export interface RowAddContext {
  primaryFormData: Record<string, unknown>
  /**
   * Unique-table projection of {@link contextFrames}. A table with two distinct
   * ancestor rows is omitted — callers must not treat this map as "last write wins".
   */
  ancestorRowsByTableId?: Record<number, Record<string, unknown>>
  contextFrames?: ContextFrame[]
}

export interface BindingContextInput {
  bindingId?: number | string
  tableId?: number | null
  bindingType?: string
  filterFkRefTableId?: number | null
  tableName?: string
  tableDisplayName?: string
  primaryKeyFields?: string[] | null
  columns?: Array<{
    field?: string
    fieldName?: string
    label?: string
    displayName?: string
    hidden?: boolean
    type?: string
    props?: { hidden?: boolean; [key: string]: unknown } | null
  }> | null
  data?: unknown[]
}

export function rowFieldValue(row: Record<string, unknown>, key: string): unknown {
  if (!row || !key) return undefined
  if (key in row) return row[key]
  const lower = key.toLowerCase()
  for (const k of Object.keys(row)) {
    if (k.toLowerCase() === lower) return row[k]
  }
  return undefined
}

export function sameAncestorRow(
  a: Record<string, unknown>,
  b: Record<string, unknown>,
): boolean {
  if (a === b) return true
  const ua = rowFieldValue(a, 'platformRowUuid')
  const ub = rowFieldValue(b, 'platformRowUuid')
  if (ua != null && String(ua).trim() !== '' && ub != null && String(ub).trim() !== '') {
    return String(ua) === String(ub)
  }
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  let compared = 0
  for (const k of keys) {
    const va = rowFieldValue(a, k)
    const vb = rowFieldValue(b, k)
    if (va == null && vb == null) continue
    if (va == null || vb == null) continue
    compared += 1
    if (String(va) !== String(vb)) return false
  }
  if (compared > 0) return true
  return Object.keys(a).length === 0 && Object.keys(b).length === 0
}

function ancestorHasTable(
  ancestors: Record<number, Record<string, unknown>>,
  refTableId: number | undefined,
): boolean {
  if (refTableId == null) return false
  return Object.prototype.hasOwnProperty.call(ancestors, refTableId)
    || Object.prototype.hasOwnProperty.call(ancestors, String(refTableId))
}

export function framesMatchingTable(ctx: RowAddContext, refTableId: number): ContextFrame[] {
  const fromFrames = (ctx.contextFrames ?? []).filter(
    f => Number(f.tableId) === refTableId && f.row && typeof f.row === 'object' && !Array.isArray(f.row),
  )
  if (fromFrames.length > 0) return fromFrames
  const ancestors = ctx.ancestorRowsByTableId
  if (!ancestors) return []
  const hit = ancestorHasTable(ancestors, refTableId)
    ? ancestors[refTableId] ?? ancestors[Number(refTableId)]
    : undefined
  if (hit && typeof hit === 'object' && !Array.isArray(hit)) {
    return [{ tableId: refTableId, row: hit, role: 'PARENT' }]
  }
  return []
}

/** Single ancestor row for a table, or null when missing or two distinct rows exist. */
export function uniqueAncestorRow(
  ctx: RowAddContext,
  refTableId: number | undefined,
): Record<string, unknown> | null {
  if (refTableId == null) return null
  const frames = framesMatchingTable(ctx, Number(refTableId))
  if (frames.length === 0) return null
  const first = frames[0].row
  for (let i = 1; i < frames.length; i += 1) {
    if (!sameAncestorRow(first, frames[i].row)) return null
  }
  return first
}

export function hasAmbiguousAncestorTable(ctx: RowAddContext, tableId: number): boolean {
  if (uniqueAncestorRow(ctx, tableId) != null) return false
  return (ctx.contextFrames ?? []).some(frame => Number(frame.tableId) === tableId)
}

export function ancestorMapFromUniqueFrames(
  frames: ContextFrame[],
): Record<number, Record<string, unknown>> {
  const grouped = new Map<number, ContextFrame[]>()
  for (const frame of frames) {
    const tid = Number(frame.tableId)
    if (!Number.isFinite(tid)) continue
    const list = grouped.get(tid)
    if (list) list.push(frame)
    else grouped.set(tid, [frame])
  }
  const out: Record<number, Record<string, unknown>> = {}
  for (const [tid, list] of grouped) {
    const first = list[0].row
    if (list.every(f => sameAncestorRow(first, f.row))) {
      out[tid] = first
    }
  }
  return out
}

export function contextHasScopedAncestors(ctx: RowAddContext): boolean {
  if ((ctx.contextFrames?.length ?? 0) > 0) return true
  const ancestors = ctx.ancestorRowsByTableId
  return ancestors != null && Object.keys(ancestors).length > 0
}

function attachFilterSiblingFrames(
  frames: ContextFrame[],
  subTableBindings: BindingContextInput[] | null | undefined,
  currentBinding: {
    bindingId?: number | string
    tableId?: number | null
    filterFkRefTableId?: number | null
  } | null | undefined,
): void {
  const currentId = currentBinding?.bindingId
  for (const sibling of subTableBindings ?? []) {
    if (currentId != null && String(sibling.bindingId) === String(currentId)) continue
    if (sibling.tableId == null || !Number.isFinite(Number(sibling.tableId))) continue
    const rows: Record<string, unknown>[] = []
    for (const raw of Array.isArray(sibling.data) ? sibling.data : []) {
      if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
        rows.push(raw as Record<string, unknown>)
      }
    }
    if (rows.length !== 1) continue
    frames.push({
      tableId: Number(sibling.tableId),
      row: rows[0],
      role: 'FILTER_SIBLING',
      bindingId: sibling.bindingId,
    })
  }
}

export function buildRowAddContext(
  primaryFormData: Record<string, unknown>,
  subTableBindings?: BindingContextInput[] | null,
  parentRow?: Record<string, unknown> | null,
  parentTableId?: number | null,
  currentBinding?: {
    bindingId?: number | string
    tableId?: number | null
    filterFkRefTableId?: number | null
  } | null,
  parentBindingId?: number | string | null,
): RowAddContext {
  const contextFrames: ContextFrame[] = []
  for (const b of subTableBindings ?? []) {
    if (b.tableId != null && b.bindingType === 'PRIMARY') {
      contextFrames.push({
        tableId: Number(b.tableId),
        row: primaryFormData,
        role: 'PRIMARY',
        bindingId: b.bindingId,
      })
    }
  }
  if (parentRow && parentTableId != null) {
    contextFrames.push({
      tableId: Number(parentTableId),
      row: parentRow,
      role: 'PARENT',
      bindingId: parentBindingId ?? undefined,
    })
  }
  attachFilterSiblingFrames(contextFrames, subTableBindings, currentBinding)
  return {
    primaryFormData,
    contextFrames,
    ancestorRowsByTableId: ancestorMapFromUniqueFrames(contextFrames),
  }
}

export function replaceAncestorRow(
  ctx: RowAddContext,
  tableId: number,
  nextRow: Record<string, unknown>,
): RowAddContext {
  const frames = (ctx.contextFrames ?? []).map(frame =>
    Number(frame.tableId) === Number(tableId) ? { ...frame, row: nextRow } : frame,
  )
  if (!frames.some(frame => Number(frame.tableId) === Number(tableId))) {
    frames.push({ tableId: Number(tableId), row: nextRow, role: 'PRIMARY' })
  }
  return {
    primaryFormData: ctx.primaryFormData,
    contextFrames: frames,
    ancestorRowsByTableId: ancestorMapFromUniqueFrames(frames),
  }
}
