import {
  collapseSubTableRowsPreferFilled,
  stripLinkFormDesignerTableLabel
} from '@/composables/tasks/shared'
import { getActiveMiFieldNames } from '@/composables/tasks/useMiConfig'
import type { SubTableBinding } from './subTableFieldTypes'

export function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

/** Designer list views may store "ADD + …"; runtime bindings use display names — align with {@link stripLinkFormDesignerTableLabel}. */
export function linkFormTableMatchKey(name?: string): string {
  return normalizeSubTableName(stripLinkFormDesignerTableLabel(String(name || '')).replace(/\s+/g, ''))
}

export function subTableBindingMatches(
  target?: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null } | null,
  source?: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null } | null
): boolean {
  if (!target || !source) return false
  if (target.bindingId === source.bindingId) return true
  if (target.tableId != null && source.tableId != null && Number(target.tableId) === Number(source.tableId)) return true
  const targetPhysicalName = normalizeSubTableName(target.physicalTableName)
  const sourcePhysicalName = normalizeSubTableName(source.physicalTableName)
  if (targetPhysicalName && sourcePhysicalName && targetPhysicalName === sourcePhysicalName) return true
  const targetName = normalizeSubTableName(target.tableName)
  const sourceName = normalizeSubTableName(source.tableName)
  return !!targetName && targetName === sourceName
}

export type LinkFormHostGrid = {
  bindingId?: number | null
  title?: string
  tableId?: number | null
}

function hostGridBindingIdent(host: LinkFormHostGrid): {
  bindingId: number
  tableName: string
  tableId: number | null
} {
  const bindingId = host.bindingId != null ? Number(host.bindingId) : Number.NaN
  const tableIdRaw = host.tableId != null ? Number(host.tableId) : Number.NaN
  return {
    bindingId: Number.isFinite(bindingId) ? bindingId : Number.MIN_SAFE_INTEGER,
    tableName: String(host.title ?? '').trim(),
    tableId: Number.isFinite(tableIdRaw) ? tableIdRaw : null,
  }
}

/**
 * Designer Link Form on a sub-table often binds to the same table as the grid
 * ({@code boundSubTableBindingId === host bindingId}, or a sibling MI binding of
 * the same physical table). Details must use the clicked row itself — there is
 * no nested {@code row.__subTables__[boundId]} child slice.
 */
export function isLinkFormBoundToHostGrid(
  col: { props?: { boundSubTableBindingId?: number; boundSubTableName?: string } } | null | undefined,
  host: LinkFormHostGrid,
  resolvedLinkBinding?: {
    bindingId: number
    tableName: string
    physicalTableName?: string
    tableId?: number | null
  } | null,
): boolean {
  const hostIdent = hostGridBindingIdent(host)
  const boundId = col?.props?.boundSubTableBindingId
  if (
    boundId != null
    && hostIdent.bindingId !== Number.MIN_SAFE_INTEGER
    && Number(boundId) === hostIdent.bindingId
  ) {
    return true
  }
  if (resolvedLinkBinding) {
    return subTableBindingMatches(resolvedLinkBinding, hostIdent)
  }
  const boundName = col?.props?.boundSubTableName
  return !!(
    boundName
    && hostIdent.tableName
    && linkFormTableMatchKey(boundName) === linkFormTableMatchKey(hostIdent.tableName)
  )
}

/** Self-bound Details save: merge modal fields into the grid row, keep nested children. */
export function mergeSelfBoundLinkFormIntoParentRow(
  parentRow: Record<string, any>,
  formRow: Record<string, any>,
): Record<string, any> {
  const nestedParent =
    parentRow.__subTables__ && typeof parentRow.__subTables__ === 'object'
      ? (parentRow.__subTables__ as Record<string, unknown>)
      : null
  const nestedForm =
    formRow.__subTables__ && typeof formRow.__subTables__ === 'object'
      ? (formRow.__subTables__ as Record<string, unknown>)
      : null
  const merged: Record<string, any> = { ...parentRow, ...formRow }
  if (nestedParent || nestedForm) {
    merged.__subTables__ = { ...(nestedParent || {}), ...(nestedForm || {}) }
  }
  return merged
}

/** Lowercase trimmed string for FK equality (8778 ↔ "8778"; UUID case-insensitive). */
export function normalizeFkIdForMatch(v: unknown): string | null {
  if (v == null || v === '') return null
  if (typeof v === 'object') return null
  const s = String(v).trim()
  if (!s) return null
  return s.toLowerCase()
}

export function buildFkListForChildMatch(binding?: SubTableBinding): string[] {
  const fkList: string[] = []
  if (binding?.foreignKeyField && String(binding.foreignKeyField).trim()) {
    fkList.push(String(binding.foreignKeyField))
  }
  for (const k of [
    'sub_task_id',
    'subTaskId',
    'participant_id',
    'participantId',
    'parent_id',
    'parentId',
    'id_idw',
    'meeting_participant_id',
    'user_id',
    'userId',
    'assignee_id',
    'assigneeId',
    'owner_id',
    'ownerId'
  ]) {
    if (!fkList.includes(k)) fkList.push(k)
  }
  return fkList
}

export function rowHasAnyFkColumn(r: unknown, fkList: string[]): boolean {
  if (!r || typeof r !== 'object') return false
  const o = r as Record<string, unknown>
  return fkList.some(k => o[k] != null && String(o[k]).trim() !== '')
}

export function rowMatchesParentFk(r: unknown, parentIds: Set<string>, fkList: string[]): boolean {
  if (!r || typeof r !== 'object' || parentIds.size === 0) return false
  const o = r as Record<string, unknown>
  for (const k of fkList) {
    const nv = normalizeFkIdForMatch(o[k])
    if (nv != null && parentIds.has(nv)) return true
  }
  return false
}

/**
 * When designer FK column names are absent from {@link buildFkListForChildMatch}, child rows may still store the
 * parent MI id or assignee user id in arbitrary scalar fields — pick rows that reference any {@code parentIds} value.
 */
export function shallowScalarMatchesAnyParentId(r: unknown, parentIds: Set<string>): boolean {
  if (!r || typeof r !== 'object' || parentIds.size === 0) return false
  for (const [k, v] of Object.entries(r as Record<string, unknown>)) {
    if (k.startsWith('__')) continue
    const nv = normalizeFkIdForMatch(v)
    if (nv != null && parentIds.has(nv)) return true
  }
  return false
}

export function rowMatchesParentForLinkModal(
  r: unknown,
  parentIds: Set<string>,
  fkList: string[],
  allowShallowFallback: boolean
): boolean {
  if (rowMatchesParentFk(r, parentIds, fkList)) return true
  if (!allowShallowFallback) return false
  return shallowScalarMatchesAnyParentId(r, parentIds)
}

export function narrowRowsByParentIdSetWithFk(rows: any[], pid: Set<string>, fkList: string[]): any[] {
  if (pid.size === 0) return []
  let filtered = rows.filter(r => rowMatchesParentFk(r, pid, fkList))
  if (filtered.length === 0) {
    filtered = rows.filter(r => shallowScalarMatchesAnyParentId(r, pid))
  }
  return filtered
}

export function isMiStyleParentRowForLinkForm(parentRow: Record<string, unknown> | null | undefined): boolean {
  if (!parentRow || typeof parentRow !== 'object') return false
  return (
    parentRow.task_status !== undefined
    && parentRow.task_status !== null
    && String(parentRow.task_status).trim() !== ''
  )
}

export function parentChildTaskStatusesMatch(parentRow: Record<string, any>, childRow: unknown): boolean {
  const ps = String(parentRow.task_status ?? '').trim().toUpperCase()
  if (!ps) return true
  const cs = String((childRow as { task_status?: unknown })?.task_status ?? '').trim().toUpperCase()
  return !!cs && ps === cs
}

/** MI / assignee list: completed rows often lose nested {@code __subTables__} in API — must use legacy fallback + merge. */
export function isTerminalMiParticipantRow(r: Record<string, any> | undefined | null): boolean {
  if (!r || typeof r !== 'object') return false
  const ts = String((r as { task_status?: unknown }).task_status ?? '')
    .trim()
    .toUpperCase()
    .replace(/\s+/g, '_')
  if (
    ts === 'COMPLETED'
    || ts === 'CANCELLED'
    || ts === 'REJECTED'
    || ts === 'WITHDRAWN'
    || ts === 'COMPLETE'
  ) {
    return true
  }
  const node = String((r as { task_current_node?: unknown }).task_current_node ?? '').trim().toLowerCase()
  if (node === 'end') return true
  return false
}

export function linkFormChildRowHasBusinessPayload(row: unknown): boolean {
  if (!row || typeof row !== 'object') return false
  const rec = row as Record<string, unknown>
  // MI 状态/节点列名来自当前 FU 的 Sub-Task Config；下面的字面量是跨 FU 的已知名字兜底。
  // 漏判会把运行时元数据当成业务数据，空行被误判为「已填写」。
  const { statusField, currentNodeField } = getActiveMiFieldNames()
  for (const [k, v] of Object.entries(rec)) {
    if (k.startsWith('__')) continue
    if (
      k === statusField
      || k === currentNodeField
      || k === 'task_status'
      || k === 'task_current_node'
      || k === 'task_id'
      || k === 'task_definition_key'
      || k === 'assignee'
      || k === 'assignee_user_id'
      || k === 'assignee_display_name'
      || k === 'sub_task_status'
      || k === 'sub_task_current_node'
    ) {
      continue
    }
    if (v === undefined || v === null || v === '') continue
    return true
  }
  return false
}

/** MI Details: never pick another participant's link-form row purely because it has more filled fields. */
export function filterLinkedChildRowsByMiTaskStatus(parentRow: Record<string, any>, rows: any[]): any[] {
  if (!isMiStyleParentRowForLinkForm(parentRow) || !Array.isArray(rows) || rows.length === 0) return rows
  const withStatus = rows.filter(
    r => String((r as { task_status?: unknown })?.task_status ?? '').trim() !== '',
  )
  /** Link-child rows (People) omit task_status — FK / miLinkFormChildRowMatchesParent scopes them. */
  if (withStatus.length === 0) return rows
  const matched = withStatus.filter(r => parentChildTaskStatusesMatch(parentRow, r))
  if (matched.length > 0) {
    const withPayload = matched.filter(r => linkFormChildRowHasBusinessPayload(r))
    if (withPayload.length > 0) return withPayload
    /** Stale slice: status-matched row is MI placeholder only (e.g. IN_PROGRESS shell); keep FK-scoped rows. */
    return rows
  }
  if (isTerminalMiParticipantRow(parentRow)) return []
  return rows
}

/** Parent MI sub-table row id ({@code id_idw} e.g. 8778 / 4554) is the stable key for link-form child rows. */
export function filterLinkedChildRowsByParentIdIdw(parentRow: Record<string, any>, rows: any[]): any[] {
  const want = normalizeFkIdForMatch(parentRow.id_idw)
  if (!want) return rows
  const matched = rows.filter(r => normalizeFkIdForMatch(r?.id_idw) === want)
  if (matched.length > 0) return matched
  // Child link rows often carry their own id_idw (not the parent's). For MI, "no match" must not mean "all rows".
  if (isMiStyleParentRowForLinkForm(parentRow)) return []
  return rows
}

/** Collapse split nested slices without mixing another MI participant's {@code task_status} / payload. */
export function collapseMiLinkFormRowsForParent(parentRow: Record<string, any>, rows: any[]): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  if (rows.length === 1) return [...rows]
  let pool = filterLinkedChildRowsByParentIdIdw(parentRow, rows)
  const statusPool = filterLinkedChildRowsByMiTaskStatus(parentRow, pool)
  if (statusPool.length > 0) {
    pool = statusPool
  } else if (isTerminalMiParticipantRow(parentRow)) {
    return pool.length > 0 ? [...pool] : []
  }
  if (pool.length <= 1) return [...pool]
  return collapseSubTableRowsPreferFilled(pool)
}
