/**
 * Multi-instance sub-table row assignment: infer the assignee field name from column
 * definitions (aligns with BPMN assigneeField, e.g. assignee_user_id).
 */
import {
  isAssignmentConfigured,
  type AssignmentConfig,
} from './miAssignmentConfig'
import { getActiveMiFieldNames } from '@/composables/tasks/useMiConfig'
import {
  bindingDeclaresMiParticipantRow,
  type MiKindBindingLike,
} from '@/composables/tasks/miBindingKindFromConfig'

/**
 * 解析**这个 binding** 的 MI 分派列。
 *
 * <p><b>唯一判据是配置</b>：Sub-Task Config 的 `assigneeField`，且**只适用于 MI collection**
 * （`bindingLinkMode === 'miParticipantRow'` 声明的那张表）。其余 binding —— 共享附件、普通
 * 子表、关联表 —— 一律返回 `undefined`，表示「这张表与 MI 分派无关」。
 *
 * <p><b>为什么必须看 binding 而不能只读全局配置。</b>`assigneeField` 是**每 FU 一个**的
 * collection 列名，把它套到同一表单上的其它 binding 上，会让那些表也被当成「需要分派」：
 * 实测 Assign Task 表单上的 Attachment 子表因此恒定拦截 Approve，报
 * “Assign a user to every sub-table row” —— 而附件行本就没有分派人。
 * 返回值的 `undefined` 同时承担「跳过分派校验」的语义，调用方依赖它。
 *
 * <p><b>为什么不再按列名推断。</b>此前的兜底是一张写死的名字表
 * （`assignee_user_id` / `assignee_id` / 正则 `/assignee/i`）—— 与被删除的其它启发式同类：
 * 列改名即失效，且任何带 assignee 字样的普通列都会被误当成分派列。分派列由配置回答，
 * 配置回答不了就是「这张表不参与分派」，不猜。
 */
export function resolveAssigneeFieldForBinding(
  binding: MiKindBindingLike | null | undefined,
): string | undefined {
  if (!bindingDeclaresMiParticipantRow(binding)) return undefined
  return getActiveMiFieldNames().assigneeField ?? undefined
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
