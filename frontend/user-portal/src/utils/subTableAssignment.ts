/**
 * 多实例子表行分配：从列定义推断「处理人」字段名（与 BPMN assigneeField 一致，如 assignee_user_id）。
 */
export function inferAssigneeFieldFromColumns(
  columns: Array<{ field?: string } | null | undefined>
): string | undefined {
  if (!columns?.length) return undefined
  const priority = ['assignee_user_id', 'assigneeUserId', 'assignee_id', 'assigneeId']
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
 * 列定义为空时仍可根据表名解析多实例处理人字段（与 BPMN subTableName=participants、assigneeField=assignee_user_id 对齐）。
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

/** 子表存在多行且配置了处理人列时，每一行都必须已分配（非空）才能完成任务。 */
export function allSubTableRowsHaveAssignee(rows: any[], assigneeField: string): boolean {
  if (!rows?.length) return true
  return rows.every(
    r =>
      r &&
      r[assigneeField] != null &&
      String(r[assigneeField]).trim() !== ''
  )
}
