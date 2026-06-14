import { ref, reactive } from 'vue'
import type {
  ActionDefinition,
  DebugActionRunResult,
  GatewayEvaluation,
} from '@/api/functionUnit'
import type { BpmnNodeFormBinding } from '@/utils/bpmnFormBindings'

export interface Breakpoint {
  nodeId: string
  nodeName: string
  nodeType: string
  enabled: boolean
}

export interface ProcessNode {
  id: string
  name: string
  type: string
}

export interface ProcessFlow {
  id: string
  source: string
  target: string
}

export interface SimulationStep {
  nodeId: string
  nodeName: string
  nodeType: string
  message?: string
  variables?: Record<string, any>
  miContext?: MiContext
  gatewayEval?: GatewayEvaluation
}

export interface MiContext {
  subProcessId?: string
  subProcessName?: string
  collectionVariable?: string
  elementVariable?: string
  sequential?: boolean
  parallelMode?: boolean
  completionCondition?: string
  instanceIndex?: number
  totalInstances?: number
  currentItem?: Record<string, any>
  subTableId?: number
  phase?: string
}

export interface ActiveParallelMi {
  collectionVariable: string
  elementVariable: string
  totalInstances: number
  subProcessId?: string
}

export interface ExecutionLog {
  timestamp: string
  level: string
  eventType?: 'NODE_ENTER' | 'GATEWAY_EVAL' | 'LOOKUP_PROBE' | 'ACTION_RUN' | 'VARIABLE_PATCH'
  nodeId?: string
  nodeName?: string
  message: string
  variables?: Record<string, any>
}

export interface GeneratedCollectionPreview {
  variableName: string
  instanceCount: number
  rows: Array<Record<string, any>>
}

/**
 * 调试面板共享状态与执行日志：集中持有所有跨职责的响应式状态，
 * 供会话编排 / 步骤执行 / MI / 网关 / 动作 / 断点等 composable 共用，
 * 避免循环依赖。行为与原 SFC 完全一致。
 */
export function useDebugState() {
  const activeTab = ref('variables')
  const isDebugging = ref(false)
  const isPaused = ref(false)
  const starting = ref(false)
  const currentNode = ref<{ id: string; name: string; type?: string } | null>(null)
  const currentVariables = ref<Record<string, any>>({})
  const currentMiContext = ref<MiContext | null>(null)
  const executionLogs = ref<ExecutionLog[]>([])
  const breakpoints = ref<Breakpoint[]>([])
  const inputVariables = reactive<Record<string, string>>({ initiator: 'admin' })
  const executionTime = ref<number | null>(null)
  const startTime = ref<number>(0)
  const simulationSteps = ref<SimulationStep[]>([])
  const stepIndex = ref(-1)
  const processNodes = ref<ProcessNode[]>([])
  const processFlows = ref<Record<string, ProcessFlow>>({})
  const breakpointCandidate = ref<string | null>(null)
  const nodeFormBindings = ref<Map<string, BpmnNodeFormBinding>>(new Map())
  const availableActions = ref<ActionDefinition[]>([])
  const generatedCollectionsPreview = ref<GeneratedCollectionPreview[]>([])
  const activeParallelMi = ref<ActiveParallelMi | null>(null)
  const parallelInstancePicker = ref(1)
  const currentGatewayEval = ref<GatewayEvaluation | null>(null)
  const currentNodeActions = ref<ActionDefinition[]>([])
  const runningActionId = ref('')
  const actionRunResult = ref<DebugActionRunResult | null>(null)
  const pendingGatewayTargetNodeId = ref<string | null>(null)

  function addLog(
    level: string,
    message: string,
    eventType?: ExecutionLog['eventType'],
    nodeId?: string,
    nodeName?: string,
    variables?: Record<string, any>
  ) {
    executionLogs.value.push({
      timestamp: new Date().toISOString(),
      level,
      eventType,
      nodeId,
      nodeName: nodeName || (nodeId ? nodeId : undefined),
      message,
      variables
    })
  }

  return {
    activeTab,
    isDebugging,
    isPaused,
    starting,
    currentNode,
    currentVariables,
    currentMiContext,
    executionLogs,
    breakpoints,
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
  }
}

export type DebugState = ReturnType<typeof useDebugState>
