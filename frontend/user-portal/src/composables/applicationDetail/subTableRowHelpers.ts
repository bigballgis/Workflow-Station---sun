import type { FormField } from '@/components/FormRenderer.vue'
import type { HistoryRecord } from '@/types/historyRecord'
import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'
import { SHARED_ATTACHMENT_RELATION_TABLE_ID, isAuditField } from '@/components/subTableAddDialogHelpers'
import {
  mergeSubTableRowsByRowId,
  dropSubsumedSubTableRows,
  isSharedAttachmentFileBinding,
} from '@/composables/tasks/shared'
import { USER_ID_KEY, USER_KEY } from '@/api/auth'

/** Consistent with request interceptor; used to determine if the initiator is viewing their own application */
export function getPortalUserId(): string | null {
  let userId = localStorage.getItem(USER_ID_KEY)
  if (!userId) {
    const userStr = localStorage.getItem(USER_KEY)
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        userId = user.userId || user.id
      } catch {
        /* ignore */
      }
    }
  }
  return userId || null
}

/** Align with tasks/detail.vue: variables may key __subTables__ by table name or binding id. */
export function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

export function getSavedSubTableRowsFromVariables(
  savedSubTables: Record<string, any> | null | undefined,
  rawBinding: { bindingId: number; tableName?: string; tableDisplayName?: string },
  primaryKeyFields?: string[] | null
): any[] | undefined {
  if (!savedSubTables || typeof savedSubTables !== 'object') return undefined
  const keys = [
    rawBinding.bindingId,
    String(rawBinding.bindingId),
    rawBinding.tableDisplayName,
    rawBinding.tableName,
    rawBinding.tableName ? normalizeSubTableName(rawBinding.tableName) : '',
    rawBinding.tableDisplayName ? normalizeSubTableName(rawBinding.tableDisplayName) : ''
  ]
  const seenArrays = new Set<any>()
  const chunks: any[][] = []
  for (const key of keys) {
    if (key === '' || key == null) continue
    const v = savedSubTables[key as string]
    if (!Array.isArray(v) || v.length === 0 || seenArrays.has(v)) continue
    seenArrays.add(v)
    chunks.push(v)
  }
  if (chunks.length === 0) return undefined
  let merged: any[] = chunks.length === 1 ? [...chunks[0]!] : []
  if (chunks.length > 1) {
    for (const chunk of chunks) {
      merged = mergeSubTableRowsByRowId(merged, chunk, primaryKeyFields ?? null)
    }
  }
  return dropSubsumedSubTableRows(merged)
}

export type SubTableBindingAlignable = {
  bindingId?: number
  tableId?: number | null
  tableName: string
  data: any[]
  primaryKeyFields?: string[]
  physicalTableName?: string
}

/**
 * Copied forms (e.g. subform_copy) get a new bindingId while runtime data still lives under the original key;
 * MI may only persist one row under the new id — merge all bindings that share tableId (or display name) for My Request.
 *
 * Previous logic bucketed only by `tid:*` OR `tn:*`. When one form binding had `tableId` and another did not
 * (same relation table, different form metadata), they landed in separate groups and `length < 2` skipped merge —
 * common when the process advances (e.g. assignment step) and the current form uses a new binding row while
 * `__subTables__` keys still match an earlier step. Union-find merges by equal numeric tableId OR equal normalized
 * display name, then a column-overlap pass fills bindings that still have no rows.
 */
/**
 * Stable display order for sub-table rows. The row data can be populated by more than one async source
 * (process variables, MI overlay, sub-table enrichment), and whichever lands first sets the Map insertion
 * order in the merge — so the rendered order can flip between page loads. Sorting by a stable per-row key
 * here makes the order deterministic regardless of which source arrived first.
 *
 * Only sorts when EVERY row exposes the same stable key (id_idw → sub_task_id → id), so tables that have a
 * meaningful insertion order but no such key keep their original order untouched.
 */
const STABLE_ORDER_KEYS = ['id_idw', 'sub_task_id', 'id'] as const

function stableRowSortValue(row: Record<string, unknown>, key: string): string | null {
  const v = row?.[key]
  if (v == null) return null
  const s = String(v).trim()
  return s === '' ? null : s
}

function sortRowsByStableKey<T extends Record<string, unknown>>(rows: T[]): T[] {
  if (rows.length < 2) return rows
  const key = STABLE_ORDER_KEYS.find(k => rows.every(r => stableRowSortValue(r, k) != null))
  if (!key) return rows
  // Natural compare so Test-000002 < Test-000010 (numeric-aware), stable for ties.
  const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' })
  return rows
    .map((row, index) => ({ row, index }))
    .sort((a, b) => {
      const c = collator.compare(stableRowSortValue(a.row, key)!, stableRowSortValue(b.row, key)!)
      return c !== 0 ? c : a.index - b.index
    })
    .map(x => x.row)
}

/** Union-find merge of row snapshots across bindings that share tableId or display name. */
export function applyUnionFindMergeToBindingList(all: SubTableBindingAlignable[]) {
  if (all.length === 0) return

  const parent = all.map((_, i) => i)
  const find = (i: number): number => {
    if (parent[i] !== i) parent[i] = find(parent[i])
    return parent[i]
  }
  const union = (i: number, j: number) => {
    const ri = find(i)
    const rj = find(j)
    if (ri !== rj) parent[ri] = rj
  }

  for (let i = 0; i < all.length; i++) {
    for (let j = i + 1; j < all.length; j++) {
      const a = all[i]!
      const b = all[j]!
      if (
        isSharedAttachmentFileBinding(
          a as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null },
        ) ||
        isSharedAttachmentFileBinding(
          b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null },
        )
      ) {
        continue
      }
      const tnA = normalizeSubTableName(a.tableName)
      const tnB = normalizeSubTableName(b.tableName)
      const tidA = a.tableId != null && !Number.isNaN(Number(a.tableId)) ? Number(a.tableId) : null
      const tidB = b.tableId != null && !Number.isNaN(Number(b.tableId)) ? Number(b.tableId) : null
      if (tidA != null && tidB != null && tidA === tidB) {
        union(i, j)
      } else if (tnA.length > 0 && tnA === tnB) {
        union(i, j)
      }
    }
  }

  const byRoot = new Map<number, SubTableBindingAlignable[]>()
  for (let i = 0; i < all.length; i++) {
    const r = find(i)
    if (!byRoot.has(r)) byRoot.set(r, [])
    byRoot.get(r)!.push(all[i]!)
  }

  for (const group of byRoot.values()) {
    let pkFields: string[] | undefined
    for (const b of group) {
      const pks = (b as SubTableBindingAlignable).primaryKeyFields
      if (!pkFields?.length && Array.isArray(pks) && pks.length > 0) {
        pkFields = pks.map(f => String(f).trim()).filter(Boolean)
      }
    }
    let merged: any[] = []
    for (const b of group) {
      merged = mergeSubTableRowsByRowId(merged, Array.isArray(b.data) ? b.data : [], pkFields)
    }
    if (merged.length === 0) continue
    const snapshot = sortRowsByStableKey(merged.map(r => ({ ...r })))
    for (const b of group) {
      b.data = snapshot
    }
  }
}

/** Fast path: current form bindings only — enough for first paint on initiator My Request. */
/** Resolve list columns for a binding, including sibling-form / dataTables fallbacks (binding 104 empty subListViews). */
export function isPortalSharedAttachmentTableBinding(b: {
  bindingId?: number
  tableId?: number | null
  tableName?: string
  foreignKeyField?: string | null
}): boolean {
  const tableIdNum = b.tableId != null ? Number(b.tableId) : NaN
  const tn = normalizeSubTableName(String(b.tableName ?? ''))
  if (Number.isFinite(tableIdNum) && tableIdNum === SHARED_ATTACHMENT_RELATION_TABLE_ID) return true
  if (tn === 'attachment') return true
  return String(b.foreignKeyField ?? '').trim().toLowerCase() === 'main_id' && tn === 'attachment'
}

/**
 * When `__subTables__` keys do not match any bindingId/table label on the current form, merge-by-table may still
 * leave an empty `data` array. Pick a saved row list whose first row shares enough column names with the binding's
 * list-view columns (conservative threshold) so initiator My Request shows prior-step sub-table rows.
 */
/**
 * Columns + inline-form field keys used to match variable slices and detect MI placeholder rows.
 * Non-discriminative keys are excluded — they appear in every sibling sub-table's rows and would
 * let the fuzzy backfill "claim" another table's rows (e.g. an empty Attachment table adopting a
 * Transaction row):
 *  - system audit fields (created_at / created_by / updated_at / updated_by — auto-appended to every table)
 *  - structural FK / runtime row keys (row_id, id_idw, sub_task_id, …)
 *  - the binding's own foreign key to the main table (e.g. case_id / main_id)
 */
export function collectSubTableBindingMatchKeys(b: {
  columns?: Array<{ field?: string }>
  formFields?: FormField[]
  foreignKeyField?: string | null
}): Set<string> {
  const fieldSet = new Set<string>()
  const fk = String(b.foreignKeyField ?? '').trim().toLowerCase()
  const add = (key: string) => {
    const lk = key.toLowerCase()
    if (isAuditField(key)) return
    if (SUB_TABLE_STRUCTURAL_FK_KEYS.has(lk)) return
    if (fk && lk === fk) return
    fieldSet.add(key)
  }
  for (const c of b.columns || []) {
    if (typeof c?.field === 'string' && c.field.length > 0) add(c.field)
  }
  const walkFormFields = (fields?: FormField[]) => {
    if (!Array.isArray(fields)) return
    for (const f of fields) {
      if (f.type === 'tabs' && Array.isArray(f.tabs)) {
        for (const tab of f.tabs) walkFormFields(tab.fields)
      } else if (f.type === 'collapse' && Array.isArray(f.collapsePanels)) {
        for (const panel of f.collapsePanels) walkFormFields(panel.fields)
      } else if (f.type === 'card' || f.type === 'row' || f.type === 'col') walkFormFields(f.children)
      else if (typeof f.key === 'string' && f.key.length > 0) add(f.key)
    }
  }
  walkFormFields(b.formFields)
  return fieldSet
}

export const SUB_TABLE_MI_PLACEHOLDER_KEYS = new Set([
  'assignee_user_id',
  'assignee_id',
  'assignee_display_name',
  'task_status',
  'task_current_node',
  'sub_task_status',
  'sub_task_current_node',
  'task_id',
  'task_definition_key',
])

/** FK / MI keys that must not satisfy {@link subTableRowsLackSavedFieldPayload} alone (sub_task_id without age still blank). */
export const SUB_TABLE_STRUCTURAL_FK_KEYS = new Set([
  'sub_task_id',
  'sub_taskid',
  'id_idw',
  'participant_id',
  'parent_id',
  'row_id',
])

export function pickSubTableRowValueIgnoreKeyCase(o: Record<string, unknown>, key: string): unknown {
  if (Object.prototype.hasOwnProperty.call(o, key)) return o[key]
  const want = key.toLowerCase()
  for (const rk of Object.keys(o)) {
    if (rk.toLowerCase() === want) return o[rk]
  }
  return undefined
}

/**
 * True when no row carries real values for designer list / sub-form fields (only MI assignment columns, etc.).
 * Otherwise backfill from variables is skipped and Link Form modal stays blank for My Request.
 */
export function subTableRowsLackSavedFieldPayload(rows: unknown[] | undefined, fieldKeys: Set<string>): boolean {
  if (fieldKeys.size === 0) return false
  if (!Array.isArray(rows) || rows.length === 0) return true
  const checkKeys = [...fieldKeys].filter(k => {
    const lk = k.toLowerCase()
    return !SUB_TABLE_MI_PLACEHOLDER_KEYS.has(lk) && !SUB_TABLE_STRUCTURAL_FK_KEYS.has(lk)
  })
  if (checkKeys.length === 0) return true
  for (const row of rows) {
    if (!row || typeof row !== 'object') continue
    const o = row as Record<string, unknown>
    for (const k of checkKeys) {
      const v = pickSubTableRowValueIgnoreKeyCase(o, k)
      if (v === undefined || v === null || v === '') continue
      if (typeof v === 'boolean') return false
      if (typeof v === 'number' && !Number.isNaN(v)) return false
      if (typeof v === 'string' && v.trim() !== '') return false
    }
  }
  return true
}

/**
 * MI / assignee sub-table rows: process-level {@code formData} may contain another participant's link-form
 * fields (last writer). Never treat that as this row's Detail payload unless the row is terminal.
 */
export function isMultiInstanceStyleSubTableRow(row: any): boolean {
  if (!row || typeof row !== 'object') return false
  if (row.task_status !== undefined && row.task_status !== null) return true
  if (row.sub_task_status !== undefined && row.sub_task_status !== null) return true
  if (row.task_id != null && String(row.task_id).trim() !== '') return true
  if (row.task_definition_key != null && String(row.task_definition_key).trim() !== '') return true
  if (row.assignee_user_id != null && String(row.assignee_user_id).trim() !== '') return true
  if (row.assignee_id != null && String(row.assignee_id).trim() !== '') return true
  return false
}

export function rowAssigneeUserId(row: any, assigneeField: string): string | null {
  if (!row || typeof row !== 'object') return null
  const raw = row[assigneeField]
  if (raw == null || raw === '') return null
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim()
    return s.length > 0 ? s : null
  }
  if (typeof raw === 'object') {
    const uid = (raw as { userId?: unknown; id?: unknown }).userId ?? (raw as { id?: unknown }).id
    if (uid == null || uid === '') return null
    const s = String(uid).trim()
    return s.length > 0 ? s : null
  }
  return null
}

export const hasAssignmentData = (rows: any[], assigneeField?: string): boolean => {
  if (!Array.isArray(rows) || rows.length === 0) return false
  if (assigneeField) {
    return rows.some(r => r && rowAssigneeUserId(r, assigneeField) != null)
  }
  for (const field of ['assignee_user_id', 'assignee_id']) {
    if (rows.some(r => r && rowAssigneeUserId(r, field) != null)) return true
  }
  return rows.some(r => r && r.assignee_display_name)
}

export function resolveBindingAssigneeField(binding: {
  columns?: Array<{ field?: string }>
  tableName?: string
  data?: any[]
}): string | undefined {
  const assigneeField = resolveAssigneeFieldForBinding(binding.columns, binding.tableName)
  if (assigneeField && hasAssignmentData(binding.data || [], assigneeField)) {
    return assigneeField
  }
  return undefined
}

export function isSyntheticLookupField(fieldName?: string): boolean {
  return !fieldName || String(fieldName).startsWith('lookup:')
}

export function isAssigneeLikeLabel(label?: string): boolean {
  const normalized = String(label || '').trim().toLowerCase()
  return /assignee|处理人|負責人|经办人|經辦人/.test(normalized)
}

export const getHistoryStatus = (operationType: string): 'completed' | 'current' | 'pending' | 'rejected' => {
  const map: Record<string, 'completed' | 'current' | 'pending' | 'rejected'> = {
    'SUBMIT': 'completed',
    'APPROVE': 'completed',
    'REJECT': 'rejected',
    'DELEGATE': 'completed',
    'TRANSFER': 'completed',
    'CLAIM': 'completed',
    'PENDING': 'current'
  }
  return map[operationType] || 'completed'
}

export const getHistoryAction = (operationType: string): HistoryRecord['action'] => {
  const map: Record<string, NonNullable<HistoryRecord['action']>> = {
    'SUBMIT': 'submit',
    'APPROVE': 'approve',
    'REJECT': 'reject',
    'TRANSFER': 'transfer',
    'DELEGATE': 'delegate',
    'WITHDRAW': 'withdraw',
    'RETURN': 'return',
    'DRAFT': 'draft',
    'DRAFT_TASK': 'draft',
  }
  return map[operationType]
}
