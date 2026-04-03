/**
 * 用户任务分配拓扑：与 assignee-type-convergence.md 中「LAST_TASK_ASSIGNEE 仅单入线」一致。
 */

import type { BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties } from '@/utils/bpmnExtensions'

export interface LastTaskTopologyViolation {
  taskId: string
  taskName?: string
  incomingCount: number
}

/** 画布上指向该节点的顺序流数量（bpmn-js shape.incoming） */
export function countIncomingSequenceFlows(
  shape: { incoming?: Array<{ type?: string }> } | null | undefined
): number {
  if (!shape?.incoming?.length) {
    return 0
  }
  return shape.incoming.filter((c) => c.type === 'bpmn:SequenceFlow').length
}

/** 扩展属性 assigneeAnchor 是否表示「上一完成任务办理人」（与引擎 AssigneeAnchor.fromCode 对齐） */
export function isExtensionLastTaskAssigneeAnchor(anchorRaw: unknown): boolean {
  if (anchorRaw == null || typeof anchorRaw !== 'string') {
    return false
  }
  const u = anchorRaw.trim().toUpperCase()
  return u === 'LAST_TASK_ASSIGNEE' || u === 'LAST' || u === 'CURRENT'
}

/**
 * 扫描全部用户任务：assigneeAnchor 为上一完成任务时，要求恰好一条顺序流入线。
 */
export function findLastTaskAssigneeTopologyViolations(modeler: BpmnModeler): LastTaskTopologyViolation[] {
  const elementRegistry = modeler.get('elementRegistry')
  const all = elementRegistry.getAll() as Array<{
    type?: string
    id?: string
    incoming?: Array<{ type?: string }>
    businessObject?: { name?: string }
  }>
  const out: LastTaskTopologyViolation[] = []
  for (const el of all) {
    if (el.type !== 'bpmn:UserTask') {
      continue
    }
    const ext = getExtensionProperties(el as any)
    if (!isExtensionLastTaskAssigneeAnchor(ext.assigneeAnchor)) {
      continue
    }
    const n = countIncomingSequenceFlows(el)
    if (n !== 1) {
      out.push({
        taskId: el.id || '',
        taskName: el.businessObject?.name,
        incomingCount: n
      })
    }
  }
  return out
}
