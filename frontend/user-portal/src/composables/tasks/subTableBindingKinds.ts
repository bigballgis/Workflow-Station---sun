/**
 * Sub-table binding / row classification: MI dashboard bindings, participant-scoped bindings,
 * attachment bindings, MI dashboard rows and runtime meta fields.
 */

import {
  bindingDeclaresMiParticipantRow,
  resolveMiBindingKindFromConfig,
  type MiKindContext,
  type MiKindFieldDef,
} from './miBindingKindFromConfig'
import { SUB_TABLE_ROW_META_KEYS } from './internal'
import { normalizeSubTableName } from './subTableCore'
import { getActiveMiFieldNames } from './useMiConfig'

/** Runtime / MI dashboard keys that must not become inferred sub-table columns or leak into non-MI bindings. */
export function isSubTableRowMetaField(key: string): boolean {
  if (!key || key.startsWith('__')) return true
  if (SUB_TABLE_ROW_META_KEYS.has(key)) return true
  // 上面的集合是跨 FU 的已知名字并集；当前 FU 在 Sub-Task Config 里自定义的状态/节点列
  // 同样是运行时元数据，漏判会让它被当成用户业务数据处理。
  const { statusField, currentNodeField } = getActiveMiFieldNames()
  return key === statusField || key === currentNodeField
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
 * 设计器配置的 MI 进度列名。**没有平台默认值**（2026-09-02 删除，见 `useMiConfig.ts` 顶部说明）：
 * 未配置时返回 `null`，调用方按「这个 FU 没有这一列」处理，不要再兜底一个字面量。
 */
export function resolveMiDashboardFieldNames(
  fields?: MiDashboardFieldNames | null,
): { statusField: string | null; currentNodeField: string | null } {
  // 显式传入 > 当前 FU 的 Sub-Task Config（useMiConfig 注册表）。
  const { statusField, currentNodeField } = getActiveMiFieldNames({
    statusField: fields?.statusField,
    currentNodeField: fields?.currentNodeField,
  })
  return { statusField, currentNodeField }
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
  tableId?: number | null
  bindingLinkMode?: string | null
  fieldDefinitions?: MiKindFieldDef[] | null
  miCollection?: boolean | null
}, miFields?: MiDashboardFieldNames | null): boolean {
  if (binding.miCollection === false) return false
  // 设计器显式声明 Link Mode = MI Participant Row —— **唯一**判据。
  if (bindingDeclaresMiParticipantRow(binding)) return true
  // 同一张 collection 表的其它 binding（同 tableId）也是 collection。
  if (resolveMiBindingKindFromConfig(binding, null) === 'collection') return true
  // Sub-Task Config 显式配置的状态/节点列：这也是配置，不是猜 —— 但只在调用方**传了**
  // miFields 时才算数，不再退回 task_status / assignee_* / 'participants' 这些字面量。
  const cols = binding.columns ?? []
  const configured = [miFields?.statusField, miFields?.currentNodeField]
    .map(f => String(f ?? '').trim())
    .filter(Boolean)
  return (
    configured.length > 0
    && cols.some(c => c?.field != null && configured.includes(String(c.field)))
  )
}

/**
 * True when sub-table rows are scoped to one MI participant (assignee dashboard, link-form child, etc.).
 * False for process-level tables keyed to the main form (e.g. attachment.main_id) — those rows are shared by every MI sub-task.
 */
export function isMiParticipantScopedSubTableBinding(
  binding: {
    columns?: Array<{ field?: string }> | null
    tableName?: string
    tableId?: number | null
    foreignKeyField?: string | null
    bindingLinkMode?: string | null
    fieldDefinitions?: MiKindFieldDef[] | null
  },
  ctx?: MiKindContext | null,
): boolean {
  if (isMiDashboardSubTableBinding(binding)) return true
  // 配置判据是**唯一**判据：FK 指向 collection = participant-child；FK 指向主表 = shared。
  // 二者的 bindingLinkMode 都是 structuralFk，只有 refTableId 能区分（实测 attachment vs people）。
  //
  // 判不出来时返回 false（= 不按参与者分片），**不再回退猜列名**。猜列名曾经两个方向都答错：
  // demo FU 把 sub_task_id 改名成 sub_task_idq 后，别人的行被当成无归属放行给当前用户，
  // 自己的行又被判成别人的而在保存时丢弃。返回 false 是安全的一侧 —— 最多是不做参与者过滤
  // （显示全量，用户看得见），而猜错会**跨参与者写数据**。
  return resolveMiBindingKindFromConfig(binding, ctx) === 'participant-child'
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
