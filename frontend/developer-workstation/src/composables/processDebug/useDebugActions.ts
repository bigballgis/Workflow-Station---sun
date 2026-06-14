import { functionUnitApi } from '@/api/functionUnit'
import { lookupNodeFormBinding } from '@/utils/bpmnFormBindings'
import type { DebugState } from './useDebugState'

interface UseDebugActionsOptions {
  state: DebugState
  functionUnitId: number
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 节点动作：根据节点绑定同步可运行动作列表，并以 dryRun 方式
 * 试运行动作、应用变量补丁并写日志。行为与原 SFC 完全一致。
 */
export function useDebugActions(options: UseDebugActionsOptions) {
  const { state, functionUnitId, t } = options
  const {
    nodeFormBindings,
    availableActions,
    currentNodeActions,
    currentNode,
    currentVariables,
    runningActionId,
    actionRunResult,
    addLog,
  } = state

  function syncCurrentNodeActions(nodeId: string) {
    const binding = lookupNodeFormBinding(nodeFormBindings.value, nodeId)
    const ids = binding?.actionIds || []
    if (!ids.length) {
      currentNodeActions.value = []
      return
    }
    currentNodeActions.value = ids.map((id) => {
      const found = availableActions.value.find(action => String(action.id) === String(id))
      return found || {
        id,
        actionName: String(id),
        actionType: 'UNKNOWN',
        description: '',
        configJson: {},
      }
    })
  }

  async function handleRunAction(actionId: string | number) {
    if (!currentNode.value) return
    runningActionId.value = String(actionId)
    try {
      const res = await functionUnitApi.debugRunAction(functionUnitId, {
        nodeId: currentNode.value.id,
        actionId,
        runtimeVariables: currentVariables.value,
        formData: currentVariables.value,
        dryRun: true,
      })
      actionRunResult.value = res.data
      addLog(
        res.data?.success ? 'success' : 'warning',
        t('debug.actionRunFinished', { actionId: String(actionId) }),
        'ACTION_RUN',
        currentNode.value.id,
        currentNode.value.name,
        res.data || {},
      )
      if (res.data?.variablePatches) {
        currentVariables.value = {
          ...currentVariables.value,
          ...res.data.variablePatches,
        }
        addLog(
          'info',
          t('debug.variablePatchApplied'),
          'VARIABLE_PATCH',
          currentNode.value.id,
          currentNode.value.name,
          res.data.variablePatches,
        )
      }
    } catch (e: any) {
      actionRunResult.value = null
      addLog(
        'error',
        t('debug.actionRunFailed', { actionId: String(actionId) }),
        'ACTION_RUN',
        currentNode.value.id,
        currentNode.value.name,
        { error: e?.response?.data?.error?.message || e?.message || 'unknown_error' },
      )
    } finally {
      runningActionId.value = ''
    }
  }

  return {
    syncCurrentNodeActions,
    handleRunAction,
  }
}
