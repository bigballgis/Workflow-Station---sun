/**
 * Sub-table binding / row classification: MI dashboard bindings, participant-scoped bindings,
 * attachment bindings, MI dashboard rows and runtime meta fields.
 */

import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'
import { SUB_TABLE_ROW_META_KEYS } from './internal'
import { normalizeSubTableName } from './subTableCore'

/** Runtime / MI dashboard keys that must not become inferred sub-table columns or leak into non-MI bindings. */
export function isSubTableRowMetaField(key: string): boolean {
  if (!key || key.startsWith('__')) return true
  return SUB_TABLE_ROW_META_KEYS.has(key)
}

/** True for a non-empty nested `__subTables__` payload (grandchild rows of a sub-table-in-sub-table). */
function hasNestedSubTableRows(value: unknown): boolean {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  return Object.values(value as Record<string, unknown>).some(v => Array.isArray(v) && v.length > 0)
}

/**
 * Remove MI / runtime meta fields from a sub-table row (non-MI bindings after hydration).
 *
 * A row's `__subTables__` is NOT meta: for sub-table-in-sub-table it holds the grandchild rows
 * (Add/Edit dialog reads them back via {@code pullNestedRowsForBindingFromParentRows}, and persist
 * carries them on the parent row). Stripping it detached the innermost level from its parent —
 * the nested grid rendered empty on the next task and the next save dropped the link. Empty maps
 * are still removed so they don't linger as noise.
 */
export function stripSubTableRowMetaFields(row: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(row)) {
    if (k === '__subTables__') {
      if (hasNestedSubTableRows(v)) out[k] = v
      continue
    }
    if (isSubTableRowMetaField(k)) continue
    out[k] = v
  }
  return out
}

const MI_DASHBOARD_STATUS_FIELDS = new Set([
  'task_status',
  'task_current_node',
  'sub_task_status',
  'sub_task_current_node',
])

/**
 * The sub-table columns a multi-instance sub-process mirrors its per-participant progress into,
 * as configured in Process Design → Sub-Task Config ("Sub-task status column" /
 * "Current node column"). They reach the portal as the BPMN sub-process extension properties
 * {@code miTaskStatusField} / {@code miTaskCurrentNodeField}
 * (see {@code MiSubProcessScopeConfig}).
 */
export interface MiDashboardFieldNames {
  statusField?: string | null
  currentNodeField?: string | null
}

/**
 * Column names the designer configured, falling back to the platform defaults ONLY when the BPMN
 * carries no configuration (older process definitions saved before Sub-Task Config exposed these,
 * and non-MI contexts that still merge these mirror columns). A configured name always wins, so a
 * Function Unit whose status column is called anything else is handled by config, not by adding
 * another literal here.
 */
export function resolveMiDashboardFieldNames(
  fields?: MiDashboardFieldNames | null,
): { statusField: string; currentNodeField: string } {
  const status = String(fields?.statusField ?? '').trim()
  const node = String(fields?.currentNodeField ?? '').trim()
  return {
    statusField: status || 'task_status',
    currentNodeField: node || 'task_current_node',
  }
}

/**
 * True when designer schema declares this binding as a multi-instance participant dashboard (not a plain related table).
 *
 * <p>Everything below is a <b>guess from column names</b>, so it misfires on tables that merely look
 * like a participant list — e.g. a Function Unit copied from the MI meeting demo keeps
 * {@code assignee_user_id} columns long after its MI sub-process is gone, and every row without the
 * sub-table PK then gets dropped as an MI ghost row (rows produced by an AP service task carry no PK,
 * so the whole grid rendered empty). {@code miCollection === false} is the escape hatch: callers that
 * KNOW the truth — the task detail loader reads the BPMN, which either has a multi-instance
 * sub-process or does not — stamp it on the binding and the guessing is skipped. Left undefined the
 * heuristic applies unchanged, so contexts without BPMN (My Requests, application detail) keep
 * today's behaviour.
 */
export function isMiDashboardSubTableBinding(binding: {
  columns?: Array<{ field?: string }> | null
  tableName?: string
  miCollection?: boolean | null
}, miFields?: MiDashboardFieldNames | null): boolean {
  if (binding.miCollection === false) return false
  const cols = binding.columns ?? []
  // Configured Sub-Task Config columns win over the name guesses below.
  const configured = [miFields?.statusField, miFields?.currentNodeField]
    .map(f => String(f ?? '').trim())
    .filter(Boolean)
  if (configured.length > 0 && cols.some(c => c?.field != null && configured.includes(String(c.field)))) {
    return true
  }
  if (cols.some(c => c?.field != null && MI_DASHBOARD_STATUS_FIELDS.has(String(c.field)))) return true
  const assigneeField = resolveAssigneeFieldForBinding(cols, binding.tableName)
  if (assigneeField && cols.some(c => c?.field === assigneeField)) return true
  const tn = (binding.tableName || '').toLowerCase()
  return tn === 'participants' || tn.endsWith('participants')
}

const SHARED_PROCESS_SUB_TABLE_FK = new Set([
  'main_id',
  'mainid',
  'process_id',
  'processid',
  'main_record_id',
])

const MI_PARTICIPANT_SUB_TABLE_FK = new Set([
  'id_idw',
  'row_id',
  'participant_id',
  'parent_id',
  'meeting_participant_id',
])

/**
 * True when sub-table rows are scoped to one MI participant (assignee dashboard, link-form child, etc.).
 * False for process-level tables keyed to the main form (e.g. attachment.main_id) — those rows are shared by every MI sub-task.
 */
export function isMiParticipantScopedSubTableBinding(binding: {
  columns?: Array<{ field?: string }> | null
  tableName?: string
  foreignKeyField?: string | null
}): boolean {
  if (isMiDashboardSubTableBinding(binding)) return true
  const fk = String(binding.foreignKeyField || '').trim().toLowerCase()
  if (!fk) return false
  if (SHARED_PROCESS_SUB_TABLE_FK.has(fk)) return false
  if (MI_PARTICIPANT_SUB_TABLE_FK.has(fk)) return true
  // Link-form child rows often FK via generic {@code id} to the parent MI row.
  if (fk === 'id') return true
  return false
}

/** Designer list columns are file-only (e.g. HMDC Attachment) — not {@link isSharedAttachmentFileBinding} (main_id). */
export function isFileOnlySubTableBinding(binding: {
  columns?: Array<{ field?: string }> | null
}): boolean {
  const fields = (binding.columns ?? [])
    .map(c => String(c?.field ?? '').trim())
    .filter(Boolean)
  return fields.length > 0 && fields.every(f => f === 'file')
}

export function isSharedAttachmentFileBinding(binding: {
  bindingId?: number
  tableId?: number | null
  tableName?: string
  physicalTableName?: string
  foreignKeyField?: string | null
  columns?: Array<{ field?: string }> | null
}): boolean {
  const tn = normalizeSubTableName(binding.tableName ?? binding.physicalTableName ?? '')
  if (tn === 'attachment') return true
  if (binding.tableId != null && Number(binding.tableId) === 74) return true
  const fk = String(binding.foreignKeyField ?? '').trim().toLowerCase()
  if (fk !== 'main_id') return false
  const cols = binding.columns ?? []
  return cols.some(c => String(c?.field ?? '').trim() === 'file')
}

const MI_ASSIGNEE_FIELD_KEYS = ['assignee', 'assignee_user_id', 'assignee_id'] as const

function rowHasMiAssigneeField(row: Record<string, unknown>): boolean {
  for (const key of MI_ASSIGNEE_FIELD_KEYS) {
    const raw = row[key]
    if (raw == null) continue
    if (typeof raw === 'string' && raw.trim() === '') continue
    if (typeof raw === 'object' && !Array.isArray(raw)) return true
    if (typeof raw === 'string' && raw.startsWith('user-')) return true
  }
  return false
}

/**
 * True when a row carries MI dashboard columns (assignee / per-row task status).
 *
 * <p>{@code miFields} carries the Sub-Task Config column names when the caller knows them; the
 * literals below are only the platform defaults for callers with no BPMN in hand.
 */
export function isSubTableMiDashboardRow(
  row: Record<string, unknown> | null | undefined,
  miFields?: MiDashboardFieldNames | null,
): boolean {
  if (!row) return false
  const { statusField, currentNodeField } = resolveMiDashboardFieldNames(miFields)
  if (row[statusField] !== undefined && row[statusField] !== null) return true
  if (row[currentNodeField] !== undefined && row[currentNodeField] !== null) return true
  if (row.task_status !== undefined && row.task_status !== null) return true
  if (row.sub_task_status !== undefined && row.sub_task_status !== null) return true
  if (row.task_id != null && String(row.task_id).trim() !== '') return true
  if (row.task_definition_key != null && String(row.task_definition_key).trim() !== '') return true
  if (row.assignee_user_id != null && String(row.assignee_user_id).trim() !== '') return true
  if (row.assignee_id != null && String(row.assignee_id).trim() !== '') return true
  if (rowHasMiAssigneeField(row)) return true
  return false
}
