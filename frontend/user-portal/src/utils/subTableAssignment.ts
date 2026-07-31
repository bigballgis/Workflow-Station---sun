/**
 * Multi-instance sub-table row assignment: infer the assignee field name from column
 * definitions (aligns with BPMN assigneeField, e.g. assignee_user_id).
 */
import {
  isAssignmentConfigured,
  type AssignmentConfig,
} from './miAssignmentConfig'

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
 * Resolve multi-instance assignee field even when columns are empty, by falling back
 * to table name convention (aligns with BPMN subTableName=participants, assigneeField=assignee_user_id).
 */
export function resolveAssigneeFieldForBinding(
  columns: Array<{ field?: string } | null | undefined> | undefined,
  tableName?: string
): string | undefined {
  const fromCols = inferAssigneeFieldFromColumns(columns || [])
  if (fromCols) return fromCols
  const tn = (tableName || '').toLowerCase()
  if (tn === 'participants' || tn.endsWith('participants')) {
    return 'assignee_user_id'
  }
  return undefined
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
