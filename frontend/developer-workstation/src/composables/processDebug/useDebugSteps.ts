import { computed } from 'vue'
import { lookupNodeFormBinding } from '@/utils/bpmnFormBindings'
import type { DebugState, MiContext, ProcessFlow, ProcessNode } from './useDebugState'

interface UseDebugStepsOptions {
  state: DebugState
  t: (key: string, params?: Record<string, unknown>) => string
  emit: (event: 'current-node-change', nodeId: string | null) => void
  /** Injected to break the cycle with the MI composable. */
  syncParallelMiScope: (miContext: MiContext | null) => void
  /** Injected to break the cycle with the actions composable. */
  syncCurrentNodeActions: (nodeId: string) => void
}

export function isFormCapableNode(nodeType?: string): boolean {
  return nodeType === 'userTask' || nodeType === 'serviceTask'
}

/**
 * 仿真步骤执行：解析流程结构、应用单个步骤（同步节点/变量/MI/网关/动作并写日志）、
 * 网关强制目标的下一步计算以及步骤进度文案。行为与原 SFC 完全一致。
 */
export function useDebugSteps(options: UseDebugStepsOptions) {
  const { state, t, emit, syncParallelMiScope, syncCurrentNodeActions } = options
  const {
    simulationSteps,
    stepIndex,
    currentNode,
    currentVariables,
    currentMiContext,
    currentGatewayEval,
    nodeFormBindings,
    activeTab,
    pendingGatewayTargetNodeId,
    addLog,
  } = state

  const currentNodeFormBinding = computed(() =>
    lookupNodeFormBinding(nodeFormBindings.value, currentNode.value?.id ?? null),
  )

  const stepProgressText = computed(() => {
    if (!simulationSteps.value.length || stepIndex.value < 0) return '-'
    return t('debug.stepProgressValue', {
      current: stepIndex.value + 1,
      total: simulationSteps.value.length
    })
  })

  function extractProcessNodes(processStructure: any): ProcessNode[] {
    const nodes = Array.isArray(processStructure?.nodes) ? processStructure.nodes : []
    return nodes
      .filter((node: any) => node?.id && node?.type && node.type !== 'process')
      .map((node: any) => ({
        id: String(node.id),
        type: String(node.type),
        name: node.name ? String(node.name) : String(node.id)
      }))
  }

  function extractProcessFlows(processStructure: any): Record<string, ProcessFlow> {
    const flows = Array.isArray(processStructure?.flows) ? processStructure.flows : []
    const mapped: Record<string, ProcessFlow> = {}
    for (const flow of flows) {
      const flowId = flow?.id
      const source = flow?.source ?? flow?.sourceId ?? flow?.sourceRef
      const target = flow?.target ?? flow?.targetId ?? flow?.targetRef
      if (!flowId || !source || !target) continue
      mapped[String(flowId)] = {
        id: String(flowId),
        source: String(source),
        target: String(target),
      }
    }
    return mapped
  }

  function consumeNextStepIndex(): number {
    const fallback = stepIndex.value + 1
    const targetNodeId = pendingGatewayTargetNodeId.value
    pendingGatewayTargetNodeId.value = null
    if (!targetNodeId) return fallback
    const targetIdx = simulationSteps.value.findIndex((step, idx) => idx > stepIndex.value && step.nodeId === targetNodeId)
    return targetIdx > -1 ? targetIdx : fallback
  }

  function applyStep(index: number, options: { log?: boolean } = {}) {
    const step = simulationSteps.value[index]
    if (!step) return

    currentNode.value = {
      id: step.nodeId,
      name: step.nodeName || step.nodeId,
      type: step.nodeType
    }
    currentVariables.value = { ...(step.variables || {}) }
    currentMiContext.value = step.miContext ?? null
    currentGatewayEval.value = step.gatewayEval ?? null
    syncParallelMiScope(step.miContext ?? null)
    syncCurrentNodeActions(step.nodeId)
    emit('current-node-change', step.nodeId)

    if (
      isFormCapableNode(step.nodeType)
      && lookupNodeFormBinding(nodeFormBindings.value, step.nodeId)
    ) {
      activeTab.value = 'nodeForm'
    }

    if (options.log) {
      addLog(
        step.nodeType === 'endEvent' ? 'success' : 'info',
        step.message || `${t('debug.executeNode')}: ${step.nodeName || step.nodeId}`,
        'NODE_ENTER',
        step.nodeId,
        step.nodeName,
        step.variables
      )
      if (step.gatewayEval) {
        addLog(
          'info',
          t('debug.gatewayEvaluated', { node: step.nodeName || step.nodeId }),
          'GATEWAY_EVAL',
          step.nodeId,
          step.nodeName,
          { gatewayEval: step.gatewayEval },
        )
      }
    }
  }

  return {
    currentNodeFormBinding,
    stepProgressText,
    extractProcessNodes,
    extractProcessFlows,
    consumeNextStepIndex,
    applyStep,
  }
}
