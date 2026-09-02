/**
 * Multi-instance sub-table row assignment: infer the assignee field name from column
 * definitions (aligns with BPMN assigneeField, e.g. assignee_user_id).
 */
import {
  isAssignmentConfigured,
  type AssignmentConfig,
} from './miAssignmentConfig'
import { getActiveMiFieldNames } from '@/composables/tasks/useMiConfig'

export function inferAssigneeFieldFromColumns(
  columns: Array<{ field?: string } | null | undefined>
): string | undefined {
  if (!columns?.length) return undefined
  const priority = ['assignee_user_id', 'assigneeUserId', 'assignee_id', 'assigneeId', 'assignee']
  for (const name of priority) {
    if (columns.some(c => c && (c as { field?: string }).field === name)) {
      return name
    }
  }
  const hit = columns.find(c => {
    const f = (c as { field?: string } | undefined)?.field
    return typeof f === 'string' && /assignee/i.test(f) && (/user/i.test(f) || /_id$/i.test(f))
  })
  return hit ? (hit as { field: string }).field : undefined
}

/**
 * 解析 MI 分派列。
 *
 * <p>权威来源是 Sub-Task Config 的 `assigneeField`（每个 FU 各不相同，只能由配置回答）；
 * 列名推断只在配置未注册时兜底。**不再按表名猜** —— 曾经"表名含 participants 就返回
 * `assignee_user_id`"，表名一改即失效，且任何名字带 participants 的普通子表都会被安上一个
 * 并不存在的分派列。
 */
export function resolveAssigneeFieldForBinding(
  columns: Array<{ field?: string } | null | undefined> | undefined,
  tableName?: string
): string | undefined {
  const configured = getActiveMiFieldNames().assigneeField
  if (configured) return configured
  return inferAssigneeFieldFromColumns(columns || [])
}

/** When a sub-table has multiple rows with an assignee column, every row must be assigned (non-empty) before the task can be completed. */
export function allSubTableRowsHaveAssignee(
  rows: any[],
  assigneeField: string,
  assignmentConfig?: AssignmentConfig,
): boolean {
  if (!rows?.length) return true
  const hasVal = (v: unknown) => v != null && String(v).trim() !== ''
  if (isAssignmentConfigured(assignmentConfig)) {
    const config = assignmentConfig!
    return rows.every(row => {
      if (!row) return false
      const hasUser = config.allowUser && !!config.assigneeField && hasVal(row[config.assigneeField])
      const hasRole = config.allowRole && !!config.roleField && hasVal(row[config.roleField])
      return hasUser || hasRole
    })
  }
  return rows.every(
    r =>
      r &&
      hasVal(r[assigneeField])
  )
}
