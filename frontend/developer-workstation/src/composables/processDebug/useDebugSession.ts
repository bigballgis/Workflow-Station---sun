import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { functionUnitApi } from '@/api/functionUnit'
import { parseBpmnNodeFormBindings } from '@/utils/bpmnFormBindings'
import type { DebugState, ProcessFlow, ProcessNode } from './useDebugState'

interface UseDebugSessionOptions {
  state: DebugState
  functionUnitId: number
  getBpmnXml?: () => Promise<string>
  t: (key: string, params?: Record<string, unknown>) => string
  emit: (event: 'current-node-change', nodeId: string | null) => void
  // Injected cross-composable functions to keep wiring one-directional.
  applyStep: (index: number, options?: { log?: boolean }) => void
  consumeNextStepIndex: () => number
  extractProcessNodes: (processStructure: any) => ProcessNode[]
  extractProcessFlows: (processStructure: any) => Record<string, ProcessFlow>
  isBreakpointHit: (index: number) => boolean
}

/**
 * 调试会话编排：启动/单步/继续/停止/完成/重置会话、输入变量解析与编辑、
 * 状态文案以及变量更新与查找探针日志。行为与原 SFC 完全一致。
 */
export function useDebugSession(options: UseDebugSessionOptions) {
  const {
    state,
    functionUnitId,
    getBpmnXml,
    t,
    emit,
    applyStep,
    consumeNextStepIndex,
    extractProcessNodes,
    extractProcessFlows,
    isBreakpointHit,
  } = options
  const {
    isDebugging,
    isPaused,
    starting,
    currentNode,
    currentVariables,
    currentMiContext,
    executionLogs,
    inputVariables,
    executionTime,
    startTime,
    simulationSteps,
    stepIndex,
    processNodes,
    processFlows,
    breakpointCandidate,
    nodeFormBindings,
    availableActions,
    generatedCollectionsPreview,
    activeParallelMi,
    parallelInstancePicker,
    currentGatewayEval,
    currentNodeActions,
    runningActionId,
    actionRunResult,
    pendingGatewayTargetNodeId,
    addLog,
  } = state

  const statusText = computed(() => {
    if (!isDebugging.value) return t('debug.notStarted')
    if (isPaused.value) return t('debug.paused')
    return t('debug.running')
  })

  const statusTagType = computed(() => {
    if (!isDebugging.value) return 'info'
    if (isPaused.value) return 'warning'
    return 'success'
  })

  async function handleStartDebug() {
    starting.value = true
    resetDebugSession(false)
    try {
      const variables = parseInputVariables()
      const res = await functionUnitApi.simulateProcess(functionUnitId, variables)
      const data = res?.data
      if (!data) {
        throw new Error(t('debug.startDebugFailed'))
      }

      if (data.error) {
        ElMessage.error(String(data.error))
        addLog('error', `${t('debug.executionError')}: ${data.error}`, 'NODE_ENTER')
        return
      }

      simulationSteps.value = Array.isArray(data.steps) ? data.steps : []
      processNodes.value = extractProcessNodes(data.processStructure)
      processFlows.value = extractProcessFlows(data.processStructure)

      let bpmnXml = ''
      if (getBpmnXml) {
        try {
          bpmnXml = await getBpmnXml()
        } catch {
          bpmnXml = ''
        }
      }
      nodeFormBindings.value = parseBpmnNodeFormBindings(bpmnXml)
      try {
        const actionRes = await functionUnitApi.getActions(functionUnitId)
        availableActions.value = Array.isArray(actionRes.data) ? actionRes.data : []
      } catch {
        availableActions.value = []
      }

      generatedCollectionsPreview.value = []
      activeParallelMi.value = null
      parallelInstancePicker.value = 1
      currentGatewayEval.value = null
      currentNodeActions.value = []
      actionRunResult.value = null
      pendingGatewayTargetNodeId.value = null
      if (data.generatedCollections && typeof data.generatedCollections === 'object') {
        for (const [varName, meta] of Object.entries(data.generatedCollections as Record<string, any>)) {
          const count = meta?.instanceCount ?? '?'
          addLog('info', t('debug.miCollectionGenerated', { name: varName, count }), 'NODE_ENTER')
          const rows = Array.isArray(data.variables?.[varName]) ? data.variables[varName] : []
          generatedCollectionsPreview.value.push({
            variableName: varName,
            instanceCount: Number(meta?.instanceCount ?? rows.length ?? 0),
            rows: rows.slice(0, 5),
          })
        }
      }

      if (!simulationSteps.value.length) {
        ElMessage.warning(t('debug.noSimulationSteps'))
        return
      }

      isDebugging.value = true
      isPaused.value = true
      startTime.value = Date.now()
      executionTime.value = null
      stepIndex.value = 0

      addLog('info', t('debug.debugStarted'), 'NODE_ENTER', undefined, undefined, variables)
      applyStep(stepIndex.value, { log: true })

      if (data.completed && stepIndex.value === simulationSteps.value.length - 1) {
        finishDebug(true)
      }
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || e.message || t('debug.startDebugFailed'))
    } finally {
      starting.value = false
    }
  }

  function parseInputVariables(): Record<string, any> {
    const variables: Record<string, any> = {}
    for (const [key, rawValue] of Object.entries(inputVariables)) {
      if (!key.trim()) continue
      const trimmed = String(rawValue ?? '').trim()
      if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
        try {
          variables[key] = JSON.parse(trimmed)
          continue
        } catch {
          // fall through to scalar parsing
        }
      }
      if (trimmed === 'true') {
        variables[key] = true
      } else if (trimmed === 'false') {
        variables[key] = false
      } else if (trimmed !== '' && !Number.isNaN(Number(trimmed))) {
        variables[key] = Number(trimmed)
      } else {
        variables[key] = rawValue
      }
    }
    return variables
  }

  function handleStepOver() {
    if (!isDebugging.value || !isPaused.value) return
    if (stepIndex.value >= simulationSteps.value.length - 1) {
      finishDebug(true)
      return
    }

    stepIndex.value = consumeNextStepIndex()
    applyStep(stepIndex.value, { log: true })

    if (isBreakpointHit(stepIndex.value)) {
      isPaused.value = true
      addLog('warning', t('debug.hitBreakpoint'), 'NODE_ENTER', simulationSteps.value[stepIndex.value].nodeId,
        simulationSteps.value[stepIndex.value].nodeName)
      return
    }

    if (stepIndex.value >= simulationSteps.value.length - 1) {
      finishDebug(true)
      return
    }

    isPaused.value = true
  }

  function handleContinue() {
    if (!isDebugging.value || !isPaused.value) return

    isPaused.value = false
    addLog('info', t('debug.continuing'), 'NODE_ENTER')

    while (stepIndex.value < simulationSteps.value.length - 1) {
      stepIndex.value = consumeNextStepIndex()
      applyStep(stepIndex.value, { log: true })

      if (isBreakpointHit(stepIndex.value) && stepIndex.value < simulationSteps.value.length - 1) {
        isPaused.value = true
        addLog('warning', t('debug.hitBreakpoint'), 'NODE_ENTER', simulationSteps.value[stepIndex.value].nodeId,
          simulationSteps.value[stepIndex.value].nodeName)
        return
      }
    }

    finishDebug(true)
  }

  function finishDebug(completed: boolean) {
    if (completed) {
      executionTime.value = Date.now() - startTime.value
      addLog('success', t('debug.processCompleted'), 'NODE_ENTER')
    }
    isDebugging.value = false
    isPaused.value = false
    emit('current-node-change', null)
  }

  function handleStopDebug() {
    executionTime.value = Date.now() - startTime.value
    addLog('warning', t('debug.debugStopped'), 'NODE_ENTER')
    resetDebugSession(true)
  }

  function resetDebugSession(keepLogs: boolean) {
    isDebugging.value = false
    isPaused.value = false
    currentNode.value = null
    currentVariables.value = {}
    currentMiContext.value = null
    simulationSteps.value = []
    stepIndex.value = -1
    processNodes.value = []
    processFlows.value = {}
    nodeFormBindings.value = new Map()
    availableActions.value = []
    currentGatewayEval.value = null
    currentNodeActions.value = []
    actionRunResult.value = null
    runningActionId.value = ''
    pendingGatewayTargetNodeId.value = null
    generatedCollectionsPreview.value = []
    activeParallelMi.value = null
    parallelInstancePicker.value = 1
    breakpointCandidate.value = null
    if (!keepLogs) {
      executionLogs.value = []
      executionTime.value = null
    }
    emit('current-node-change', null)
  }

  function handleVariableUpdate(key: string, value: any) {
    currentVariables.value[key] = value
    addLog('info', t('debug.variableUpdatedLog', { key, value: JSON.stringify(value) }), 'VARIABLE_PATCH')
  }

  function handleLookupProbeLog(payload: { message: string; detail?: Record<string, any> }) {
    addLog('info', payload.message, 'LOOKUP_PROBE', currentNode.value?.id, currentNode.value?.name, payload.detail)
  }

  function addInputVariable() {
    ElMessageBox.prompt(t('debug.enterVariableName'), t('debug.addVariable'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel')
    }).then(({ value }) => {
      if (value) {
        inputVariables[value] = ''
      }
    }).catch(() => {})
  }

  return {
    statusText,
    statusTagType,
    handleStartDebug,
    parseInputVariables,
    handleStepOver,
    handleContinue,
    finishDebug,
    handleStopDebug,
    resetDebugSession,
    handleVariableUpdate,
    handleLookupProbeLog,
    addInputVariable,
  }
}
