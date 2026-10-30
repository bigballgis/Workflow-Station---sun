import type { Ref } from 'vue'
import type { BpmnModeler } from '@/types/bpmn'
import { ElMessage } from 'element-plus'
import { findInvalidAssignmentExclusionNodes } from '@/utils/assignmentExclusionBpmn'

export function createTaskAssignmentSaveGuard(getModeler: () => BpmnModeler | null,
  topologyViolations: () => string, blocked: Ref<boolean>, t: (key: string, params?: Record<string, unknown>) => string) {
  return (isAutoSave: boolean): boolean => {
    const modeler = getModeler()
    if (!modeler) return false
    const invalid = findInvalidAssignmentExclusionNodes(modeler)
    if (invalid.length) {
      blocked.value = true
      if (!isAutoSave) ElMessage.error(t('assignmentExclusion.saveBlocked', { nodes: invalid.join(', ') }))
      return false
    }
    const detail = topologyViolations()
    if (detail && !isAutoSave) ElMessage.error(t('process.lastTaskAnchorBlocked', { detail }))
    return !detail
  }
}
