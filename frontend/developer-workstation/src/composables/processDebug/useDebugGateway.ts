import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { DebugState } from './useDebugState'

interface UseDebugGatewayOptions {
  state: DebugState
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 网关决策：评估表展示、可选分支计算与手动选择分支
 * （含强制注入仿真步骤与网关选择变量补丁）。行为与原 SFC 完全一致。
 */
export function useDebugGateway(options: UseDebugGatewayOptions) {
  const { state, t } = options
  const {
    isPaused,
    currentNode,
    currentGatewayEval,
    processFlows,
    simulationSteps,
    stepIndex,
    processNodes,
    currentVariables,
    currentMiContext,
    pendingGatewayTargetNodeId,
    addLog,
  } = state

  const canSelectGatewayBranch = computed(() =>
    isPaused.value
    && currentNode.value?.type === 'exclusiveGateway'
    && !!currentGatewayEval.value,
  )

  const gatewaySelectableFlowIds = computed(() => {
    const evals = currentGatewayEval.value?.evaluations || []
    const ids = evals.map(item => item.flowId).filter(Boolean)
    if (currentGatewayEval.value?.defaultFlowId) {
      ids.push(currentGatewayEval.value.defaultFlowId)
    }
    return Array.from(new Set(ids))
  })

  function handleSelectGatewayBranch(flowId: string) {
    const flow = processFlows.value[flowId]
    if (!flow?.target) {
      ElMessage.warning(t('debug.gatewayBranchTargetMissing', { flowId }))
      return
    }
    const targetIdx = simulationSteps.value.findIndex((step, idx) => idx > stepIndex.value && step.nodeId === flow.target)
    if (targetIdx < 0) {
      const fallbackNode = processNodes.value.find(node => node.id === flow.target)
      simulationSteps.value.splice(stepIndex.value + 1, 0, {
        nodeId: flow.target,
        nodeName: fallbackNode?.name || flow.target,
        nodeType: fallbackNode?.type || 'userTask',
        message: t('debug.gatewayBranchForcedStep', { flowId, target: flow.target }),
        variables: { ...currentVariables.value },
        miContext: currentMiContext.value || undefined,
      })
      addLog(
        'warning',
        t('debug.gatewayBranchUnavailableInSimulation', { flowId, target: flow.target }),
        'GATEWAY_EVAL',
        currentNode.value?.id,
        currentNode.value?.name,
      )
    }
    const gatewayId = currentGatewayEval.value?.gatewayId || currentNode.value?.id || 'gateway'
    const selectionMap = {
      ...(currentVariables.value.__debugGatewaySelectionMap || {}),
      [gatewayId]: flowId,
    }
    currentVariables.value = {
      ...currentVariables.value,
      __debugGatewaySelectionMap: selectionMap,
      __debugLastGatewayId: gatewayId,
      __debugLastGatewayFlowId: flowId,
    }
    pendingGatewayTargetNodeId.value = flow.target
    if (currentGatewayEval.value) {
      currentGatewayEval.value = {
        ...currentGatewayEval.value,
        selectedFlowId: flowId,
      }
    }
    addLog(
      'info',
      t('debug.gatewayBranchSelected', { flowId, target: flow.target }),
      'GATEWAY_EVAL',
      currentNode.value?.id,
      currentNode.value?.name,
    )
    ElMessage.success(t('debug.gatewayBranchSelectionSaved', { flowId, target: flow.target }))
    if (targetIdx < 0) {
      ElMessage.info(t('debug.gatewayBranchForced', { flowId, target: flow.target }))
    }
    addLog(
      'info',
      t('debug.gatewayBranchSelectionPatched', { gatewayId, flowId }),
      'VARIABLE_PATCH',
      currentNode.value?.id,
      currentNode.value?.name,
      {
        __debugGatewaySelectionMap: selectionMap,
        __debugLastGatewayId: gatewayId,
        __debugLastGatewayFlowId: flowId,
      },
    )
  }

  return {
    canSelectGatewayBranch,
    gatewaySelectableFlowIds,
    handleSelectGatewayBranch,
  }
}
