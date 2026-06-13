import { computed, type ComputedRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { processApi } from '@/api/process'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailActionsFns {
  displayCurrentStepLabel: ComputedRef<string>
  workflowDiagramBadgeLabel: ComputedRef<string>
  getCurrentAssigneeDisplay: () => string
  getStatusType: (status?: string) => 'success' | 'warning' | 'info' | 'danger'
  getStatusLabel: (status?: string) => string
  getNodeStatusType: (status?: string) => 'success' | 'warning' | 'info'
  findNextNodeName: (taskName: string) => string | null
  handleUrge: () => Promise<void>
  handleWithdraw: () => Promise<void>
}

export function createApplicationDetailActions(ctx: ApplicationDetailCtx): ApplicationDetailActionsFns {
  const { t, router, processId, processInfo, urging, withdrawing, processNodes, processFlows } = ctx

  /** 已完成等终态下无「当前步骤」；兼容库内仍残留最后一笔活动名的历史数据 */
  const displayCurrentStepLabel = computed(() => {
    const st = processInfo.value.status
    if (st === 'COMPLETED') return '-'
    return processInfo.value.currentNode || '-'
  })

  /** 流程图区块角标：终态显示状态文案，运行中显示当前节点或待处理 */
  const workflowDiagramBadgeLabel = computed(() => {
    const st = processInfo.value.status || ''
    if (st === 'COMPLETED') return t('applicationDetail.completed')
    if (st === 'WITHDRAWN') return t('applicationDetail.withdrawn')
    if (st === 'REJECTED') return t('applicationDetail.rejected')
    return processInfo.value.currentNode || t('applicationDetail.pending')
  })

  const getCurrentAssigneeDisplay = () => {
    // Direct assignee
    if (processInfo.value.currentAssignee) {
      return processInfo.value.currentAssignee
    }
    // Candidate users (counter-sign scenario)
    if (processInfo.value.candidateUsers) {
      const candidates = processInfo.value.candidateUsers.split(',')
      if (candidates.length === 1) {
        return candidates[0]
      }
      return `${candidates.join(' / ')} (${t('applicationDetail.anyApprove')})`
    }
    return '-'
  }

  const getStatusType = (status?: string): 'success' | 'warning' | 'info' | 'danger' => {
    const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = { RUNNING: 'warning', COMPLETED: 'success', WITHDRAWN: 'info', REJECTED: 'danger' }
    return map[status || ''] || 'info'
  }

  const getStatusLabel = (status?: string) => {
    const map: Record<string, string> = { RUNNING: t('applicationDetail.running'), COMPLETED: t('applicationDetail.completed'), WITHDRAWN: t('applicationDetail.withdrawn'), REJECTED: t('applicationDetail.rejected') }
    return map[status || ''] || status || '-'
  }

  const getNodeStatusType = (status?: string): 'success' | 'warning' | 'info' => {
    if (status === 'COMPLETED') return 'success'
    if (status === 'RUNNING') return 'warning'
    return 'info'
  }

  // Find the next node name after a completed task, using flow history and the BPMN diagram
  const findNextNodeName = (taskName: string): string | null => {
    const taskNode = processNodes.value.find(n => n.name === taskName)
    if (!taskNode) return null

    // For direct connections to the next userTask (e.g. "Submit Request"), return immediately.
    // For gateway paths, use processInfo.currentNode (the DB-recorded final node) to resolve the branch.
    const originalCurrentNode = processInfo.value.currentNode || ''

    const visited = new Set<string>()
    const queue = [taskNode.id]
    const candidates: string[] = []

    while (queue.length > 0) {
      const nodeId = queue.shift()!
      if (visited.has(nodeId)) continue
      visited.add(nodeId)

      const outFlows = processFlows.value.filter(f => f.sourceRef === nodeId)
      for (const flow of outFlows) {
        const target = processNodes.value.find(n => n.id === flow.targetRef)
        if (!target) continue
        if (target.type === 'gateway') {
          queue.push(target.id)
        } else {
          // Non-gateway node: if there's only one outgoing path, return directly
          if (outFlows.length === 1 && candidates.length === 0) return target.name
          candidates.push(target.name)
        }
      }
    }

    if (candidates.length === 0) return null
    if (candidates.length === 1) return candidates[0]

    // Multiple candidates (gateway branches): prefer the DB-recorded final node
    if (originalCurrentNode && candidates.includes(originalCurrentNode)) {
      return originalCurrentNode
    }
    // Otherwise return the first candidate
    return candidates[0]
  }

  // Urge
  const handleUrge = async () => {
    urging.value = true
    try { await processApi.urgeProcess(processId); ElMessage.success(t('applicationDetail.urgeSuccess')) }
    catch { ElMessage.error(t('applicationDetail.urgeFailed')) }
    finally { urging.value = false }
  }

  // Withdraw
  const handleWithdraw = async () => {
    try {
      await ElMessageBox.confirm(t('applicationDetail.withdrawConfirm'), t('applicationDetail.withdrawConfirmTitle'), { type: 'warning' })
      withdrawing.value = true
      await processApi.withdrawProcess(processId, t('applicationDetail.userWithdraw'))
      ElMessage.success(t('applicationDetail.withdrawSuccess'))
      router.push('/my-applications')
    } catch (error: any) { if (error !== 'cancel') ElMessage.error(t('applicationDetail.withdrawFailed')) }
    finally { withdrawing.value = false }
  }

  return {
    displayCurrentStepLabel,
    workflowDiagramBadgeLabel,
    getCurrentAssigneeDisplay,
    getStatusType,
    getStatusLabel,
    getNodeStatusType,
    findNextNodeName,
    handleUrge,
    handleWithdraw,
  }
}
