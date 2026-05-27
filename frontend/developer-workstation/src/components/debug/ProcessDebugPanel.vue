<template>
  <div
    class="process-debug-panel"
    :class="{ 'process-debug-panel--expanded': expanded }"
  >
    <div class="debug-header">
      <div class="debug-header-title">
        <h3>{{ t('process.processDebug') }}</h3>
        <div class="debug-header-tools">
          <el-tooltip :content="expanded ? t('debug.panelHalfScreen') : t('debug.panelFullScreen')">
            <el-button
              :icon="expanded ? ScaleToOriginal : FullScreen"
              circle
              size="small"
              @click="toggleExpanded"
            />
          </el-tooltip>
          <el-tooltip :content="t('common.close')">
            <el-button
              :icon="Close"
              circle
              size="small"
              @click="emit('close')"
            />
          </el-tooltip>
        </div>
      </div>
      <div class="debug-actions">
        <el-button
          type="primary"
          :loading="starting"
          :disabled="isDebugging"
          @click="handleStartDebug"
        >
          <el-icon><VideoPlay /></el-icon> {{ t('debug.startDebug') }}
        </el-button>
        <el-button
          :disabled="!isDebugging || !isPaused"
          @click="handleStepOver"
        >
          <el-icon><Right /></el-icon> {{ t('debug.stepOver') }}
        </el-button>
        <el-button
          :disabled="!isDebugging || !isPaused"
          @click="handleContinue"
        >
          <el-icon><DArrowRight /></el-icon> {{ t('debug.continue') }}
        </el-button>
        <el-button
          type="danger"
          :disabled="!isDebugging"
          @click="handleStopDebug"
        >
          <el-icon><VideoPause /></el-icon> {{ t('debug.stop') }}
        </el-button>
      </div>
    </div>

    <div class="debug-content">
      <div class="debug-left">
        <el-tabs v-model="activeTab">
          <el-tab-pane
            :label="t('debug.variableMonitor')"
            name="variables"
          >
            <VariableMonitor
              :variables="currentVariables"
              :editable="isPaused"
              @update="handleVariableUpdate"
            />
          </el-tab-pane>
          <el-tab-pane
            :label="t('debug.executionLog')"
            name="logs"
          >
            <ExecutionLogViewer
              :logs="executionLogs"
              @clear="executionLogs = []"
            />
          </el-tab-pane>
          <el-tab-pane
            :label="t('debug.decision')"
            name="decision"
          >
            <div
              v-if="currentGatewayEval"
              class="decision-pane"
            >
              <div class="decision-meta">
                <el-tag size="small" type="info">
                  {{ currentGatewayEval.gatewayType || currentNode?.type || 'gateway' }}
                </el-tag>
                <span class="decision-selected">
                  {{ t('debug.gatewaySelectedFlow', { flowId: currentGatewayEval.selectedFlowId || '-' }) }}
                </span>
              </div>
              <el-table
                :data="currentGatewayEval.evaluations || []"
                size="small"
                border
              >
                <el-table-column prop="flowId" :label="t('debug.gatewayFlowId')" min-width="120" />
                <el-table-column prop="condition" :label="t('debug.gatewayCondition')" min-width="180" />
                <el-table-column :label="t('debug.gatewayResult')" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.result ? 'success' : 'info'" size="small">
                      {{ row.result ? t('common.yes') : t('common.no') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="reason" :label="t('debug.gatewayReason')" min-width="220" />
              </el-table>
              <p
                v-if="currentGatewayEval.defaultFlowId"
                class="decision-default"
              >
                {{ t('debug.gatewayDefaultFlow', { flowId: currentGatewayEval.defaultFlowId }) }}
              </p>
              <div
                v-if="canSelectGatewayBranch && gatewaySelectableFlowIds.length"
                class="decision-selector"
              >
                <div class="decision-selector-title">{{ t('debug.gatewayBranchSelectionTitle') }}</div>
                <el-button
                  v-for="flowId in gatewaySelectableFlowIds"
                  :key="flowId"
                  size="small"
                  :type="currentGatewayEval.selectedFlowId === flowId ? 'primary' : 'default'"
                  @click="handleSelectGatewayBranch(flowId)"
                >
                  {{ t('debug.gatewayBranchApply', { flowId }) }}
                </el-button>
              </div>
            </div>
            <el-empty
              v-else
              :description="t('debug.noGatewayExplain')"
            />
          </el-tab-pane>
          <el-tab-pane
            :label="t('debug.actionsTab')"
            name="actions"
          >
            <div class="actions-pane">
              <el-empty
                v-if="!currentNodeActions.length"
                :description="t('debug.noNodeActions')"
              />
              <el-card
                v-for="action in currentNodeActions"
                :key="action.id"
                class="action-card"
                shadow="never"
              >
                <div class="action-header">
                  <div class="action-title">
                    <span>{{ action.actionName || action.id }}</span>
                    <el-tag size="small" type="info">{{ action.id }}</el-tag>
                  </div>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="runningActionId === String(action.id)"
                    :disabled="!isDebugging || !isPaused"
                    @click="handleRunAction(action.id)"
                  >
                    {{ t('debug.runAction') }}
                  </el-button>
                </div>
                <p v-if="action.description" class="action-description">{{ action.description }}</p>
              </el-card>
              <el-alert
                v-if="actionRunResult"
                class="action-result"
                :type="actionRunResult.success ? 'success' : 'warning'"
                :title="t('debug.actionRunResult')"
                :closable="false"
                show-icon
              >
                <template #default>
                  <pre class="action-result-json">{{ JSON.stringify(actionRunResult, null, 2) }}</pre>
                </template>
              </el-alert>
            </div>
          </el-tab-pane>
          <el-tab-pane
            :label="t('debug.nodeForm')"
            name="nodeForm"
          >
            <ProcessDebugNodeForm
              :function-unit-id="functionUnitId"
              :binding="currentNodeFormBinding"
              :mi-context="effectiveMiContext"
              :expanded="expanded"
              @lookup-probe-log="handleLookupProbeLog"
            />
          </el-tab-pane>
          <el-tab-pane
            :label="t('debug.breakpoints')"
            name="breakpoints"
          >
            <div class="breakpoint-section">
              <div class="breakpoint-toolbar">
                <el-select
                  v-model="breakpointCandidate"
                  :placeholder="t('debug.selectNodeForBreakpoint')"
                  size="small"
                  filterable
                  clearable
                  style="flex: 1;"
                >
                  <el-option
                    v-for="node in breakpointCandidates"
                    :key="node.id"
                    :label="`${node.name} (${node.type})`"
                    :value="node.id"
                    :disabled="hasBreakpoint(node.id)"
                  />
                </el-select>
                <el-button
                  size="small"
                  type="primary"
                  :disabled="!breakpointCandidate"
                  @click="addBreakpoint(breakpointCandidate!)"
                >
                  {{ t('debug.addBreakpoint') }}
                </el-button>
              </div>

              <div class="breakpoint-list">
                <div
                  v-for="bp in breakpoints"
                  :key="bp.nodeId"
                  class="breakpoint-item"
                >
                  <el-checkbox
                    v-model="bp.enabled"
                    @change="handleBreakpointToggle(bp)"
                  />
                  <span class="node-name">{{ bp.nodeName }}</span>
                  <el-tag
                    size="small"
                    type="info"
                  >
                    {{ bp.nodeType }}
                  </el-tag>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    @click="removeBreakpoint(bp.nodeId)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-empty
                  v-if="!breakpoints.length"
                  :description="t('debug.noBreakpoints')"
                />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <div class="debug-right">
        <div class="execution-status">
          <div class="status-item">
            <span class="label">{{ t('debug.statusLabel') }}:</span>
            <el-tag :type="statusTagType">
              {{ statusText }}
            </el-tag>
          </div>
          <div
            v-if="isDebugging && simulationSteps.length"
            class="status-item"
          >
            <span class="label">{{ t('debug.stepProgress') }}:</span>
            <span class="value">{{ stepProgressText }}</span>
          </div>
          <div
            v-if="currentNode"
            class="status-item"
          >
            <span class="label">{{ t('debug.currentNode') }}:</span>
            <span class="value">{{ currentNode.name }}</span>
          </div>
          <div
            v-if="miExecutionModeText"
            class="status-item"
          >
            <span class="label">{{ t('debug.miExecutionMode') }}:</span>
            <el-tag
              size="small"
              :type="activeParallelMi ? 'warning' : 'info'"
            >
              {{ miExecutionModeText }}
            </el-tag>
          </div>
          <div
            v-if="miInstanceText"
            class="status-item"
          >
            <span class="label">{{ t('debug.miInstance') }}:</span>
            <span class="value">{{ miInstanceText }}</span>
          </div>
          <div
            v-if="activeParallelMi && isDebugging"
            class="status-item mi-instance-switcher"
          >
            <span class="label">{{ t('debug.miInstanceSwitcher') }}:</span>
            <el-radio-group
              v-model="parallelInstancePicker"
              size="small"
              @change="handleParallelInstanceChange"
            >
              <el-radio-button
                v-for="n in activeParallelMi.totalInstances"
                :key="n"
                :value="n"
              >
                {{ n }}
              </el-radio-button>
            </el-radio-group>
          </div>
          <div
            v-if="miAssigneeText"
            class="status-item"
          >
            <span class="label">{{ t('debug.miAssignee') }}:</span>
            <span class="value">{{ miAssigneeText }}</span>
          </div>
          <div
            v-if="miCompletionConditionText"
            class="status-item"
          >
            <span class="label">{{ t('debug.miCompletionCondition') }}:</span>
            <span class="value mi-completion-expr">{{ miCompletionConditionText }}</span>
          </div>
          <div
            v-if="currentNodeFormBinding"
            class="status-item"
          >
            <span class="label">{{ t('debug.boundForm') }}:</span>
            <span class="value form-link" @click="activeTab = 'nodeForm'">
              {{ currentNodeFormBinding.formName || `#${currentNodeFormBinding.formId}` }}
            </span>
          </div>
          <div
            v-else-if="currentNode && isFormCapableNode(currentNode.type)"
            class="status-item"
          >
            <span class="label">{{ t('debug.boundForm') }}:</span>
            <span class="value muted">{{ t('debug.noFormBound') }}</span>
          </div>
          <div
            v-if="executionTime !== null"
            class="status-item"
          >
            <span class="label">{{ t('debug.executionTime') }}:</span>
            <span class="value">{{ executionTime }}ms</span>
          </div>
        </div>

        <div
          v-if="generatedCollectionsPreview.length"
          class="generated-collections"
        >
          <h4>{{ t('debug.generatedCollectionsTitle') }}</h4>
          <div
            v-for="collection in generatedCollectionsPreview"
            :key="collection.variableName"
            class="collection-card"
          >
            <div class="collection-meta">
              <span class="collection-name">{{ collection.variableName }}</span>
              <el-tag size="small" type="success">
                {{ t('debug.generatedCollectionCount', { count: collection.instanceCount }) }}
              </el-tag>
            </div>
            <pre class="collection-json">{{ JSON.stringify(collection.rows, null, 2) }}</pre>
            <el-button
              size="small"
              text
              type="primary"
              @click="copyCollectionJson(collection)"
            >
              {{ t('debug.copyCollectionJson') }}
            </el-button>
          </div>
        </div>

        <div
          v-if="!isDebugging"
          class="input-variables"
        >
          <h4>{{ t('debug.inputVariables') }}</h4>
          <el-form
            label-position="top"
            size="small"
          >
            <el-form-item
              v-for="(_value, key) in inputVariables"
              :key="key"
              :label="String(key)"
            >
              <el-input v-model="inputVariables[key]" />
            </el-form-item>
          </el-form>
          <el-button
            size="small"
            @click="addInputVariable"
          >
            {{ t('debug.addVariable') }}
          </el-button>
          <p class="input-hint">
            {{ t('debug.miCollectionHint') }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { VideoPlay, VideoPause, Right, DArrowRight, Delete, FullScreen, ScaleToOriginal, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  functionUnitApi,
  type ActionDefinition,
  type DebugActionRunResult,
  type GatewayEvaluation,
} from '@/api/functionUnit'
import VariableMonitor from './VariableMonitor.vue'
import ExecutionLogViewer from './ExecutionLogViewer.vue'
import ProcessDebugNodeForm from './ProcessDebugNodeForm.vue'
import {
  parseBpmnNodeFormBindings,
  lookupNodeFormBinding,
  type BpmnNodeFormBinding,
} from '@/utils/bpmnFormBindings'

const { t } = useI18n()

interface Breakpoint {
  nodeId: string
  nodeName: string
  nodeType: string
  enabled: boolean
}

interface ProcessNode {
  id: string
  name: string
  type: string
}

interface ProcessFlow {
  id: string
  source: string
  target: string
}

interface SimulationStep {
  nodeId: string
  nodeName: string
  nodeType: string
  message?: string
  variables?: Record<string, any>
  miContext?: MiContext
  gatewayEval?: GatewayEvaluation
}

interface MiContext {
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

interface ActiveParallelMi {
  collectionVariable: string
  elementVariable: string
  totalInstances: number
  subProcessId?: string
}

interface ExecutionLog {
  timestamp: string
  level: string
  eventType?: 'NODE_ENTER' | 'GATEWAY_EVAL' | 'LOOKUP_PROBE' | 'ACTION_RUN' | 'VARIABLE_PATCH'
  nodeId?: string
  nodeName?: string
  message: string
  variables?: Record<string, any>
}

interface GeneratedCollectionPreview {
  variableName: string
  instanceCount: number
  rows: Array<Record<string, any>>
}

const props = defineProps<{
  functionUnitId: number
  /** Live BPMN XML from the designer canvas (includes unsaved form bindings). */
  getBpmnXml?: () => Promise<string>
  /** Drawer uses ~92% viewport height when true, ~50% when false. */
  expanded?: boolean
}>()

const emit = defineEmits<{
  (e: 'current-node-change', nodeId: string | null): void
  (e: 'close'): void
  (e: 'update:expanded', value: boolean): void
}>()

function toggleExpanded() {
  emit('update:expanded', !props.expanded)
}

const BREAKPOINT_NODE_TYPES = new Set([
  'startEvent',
  'endEvent',
  'userTask',
  'serviceTask',
  'scriptTask',
  'businessRuleTask',
  'exclusiveGateway',
  'parallelGateway',
  'inclusiveGateway',
  'subProcess',
  'callActivity'
])

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

const currentNodeFormBinding = computed(() =>
  lookupNodeFormBinding(nodeFormBindings.value, currentNode.value?.id ?? null),
)

function isFormCapableNode(nodeType?: string): boolean {
  return nodeType === 'userTask' || nodeType === 'serviceTask'
}

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

const stepProgressText = computed(() => {
  if (!simulationSteps.value.length || stepIndex.value < 0) return '-'
  return t('debug.stepProgressValue', {
    current: stepIndex.value + 1,
    total: simulationSteps.value.length
  })
})

const effectiveMiContext = computed(() => {
  const ctx = currentMiContext.value
  const scope = activeParallelMi.value
  if (!ctx || !scope?.collectionVariable) return ctx

  const collection = currentVariables.value[scope.collectionVariable]
  if (!Array.isArray(collection) || collection.length === 0) return ctx

  const index = Math.min(
    Math.max(parallelInstancePicker.value, 1),
    collection.length,
  ) - 1
  const currentItem = collection[index]
  if (!currentItem || typeof currentItem !== 'object') return ctx

  return {
    ...ctx,
    instanceIndex: index + 1,
    totalInstances: scope.totalInstances,
    currentItem: currentItem as Record<string, any>,
    parallelMode: true,
  }
})

const miExecutionModeText = computed(() => {
  if (activeParallelMi.value) return t('debug.miParallelMode')
  const ctx = currentMiContext.value
  if (ctx?.sequential === true && ctx.phase) return t('debug.miSequentialMode')
  return ''
})

const miInstanceText = computed(() => {
  const ctx = effectiveMiContext.value
  if (!ctx?.instanceIndex || !ctx.totalInstances || ctx.phase !== 'instance') return ''
  return t('debug.miInstanceProgress', {
    current: ctx.instanceIndex,
    total: ctx.totalInstances
  })
})

const miAssigneeText = computed(() => {
  const item = effectiveMiContext.value?.currentItem
  if (!item || typeof item !== 'object') return ''
  const assignee = item.assignee_id ?? item.assignee ?? item.user_id
  if (assignee == null || assignee === '') return ''
  return t('debug.miAssigneeValue', { assignee: String(assignee) })
})

const miCompletionConditionText = computed(() => {
  const expr = currentMiContext.value?.completionCondition
  return expr ? String(expr) : ''
})

const breakpointCandidates = computed(() =>
  processNodes.value.filter(node => BREAKPOINT_NODE_TYPES.has(node.type))
)

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

function hasBreakpoint(nodeId: string): boolean {
  return breakpoints.value.some(bp => bp.nodeId === nodeId)
}

async function handleStartDebug() {
  starting.value = true
  resetDebugSession(false)
  try {
    const variables = parseInputVariables()
    const res = await functionUnitApi.simulateProcess(props.functionUnitId, variables)
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
    if (props.getBpmnXml) {
      try {
        bpmnXml = await props.getBpmnXml()
      } catch {
        bpmnXml = ''
      }
    }
    nodeFormBindings.value = parseBpmnNodeFormBindings(bpmnXml)
    try {
      const actionRes = await functionUnitApi.getActions(props.functionUnitId)
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

function syncParallelMiScope(miContext: MiContext | null) {
  if (!miContext) {
    activeParallelMi.value = null
    return
  }
  if (!miContext.parallelMode || !miContext.collectionVariable) {
    return
  }
  activeParallelMi.value = {
    collectionVariable: miContext.collectionVariable,
    elementVariable: miContext.elementVariable || 'currentItem',
    totalInstances: miContext.totalInstances ?? 0,
    subProcessId: miContext.subProcessId,
  }
  if (miContext.instanceIndex && miContext.instanceIndex > 0) {
    parallelInstancePicker.value = miContext.instanceIndex
  }
}

function handleParallelInstanceChange() {
  if (!activeParallelMi.value) return
  const scope = activeParallelMi.value
  const collection = currentVariables.value[scope.collectionVariable]
  if (!Array.isArray(collection)) return
  const index = parallelInstancePicker.value - 1
  const item = collection[index]
  if (!item || typeof item !== 'object') return
  currentVariables.value = {
    ...currentVariables.value,
    [scope.elementVariable]: item,
  }
  if (currentNodeFormBinding.value) {
    activeTab.value = 'nodeForm'
  }
}

async function copyCollectionJson(collection: GeneratedCollectionPreview) {
  try {
    await navigator.clipboard.writeText(JSON.stringify(collection.rows, null, 2))
    ElMessage.success(t('debug.copyCollectionSuccess'))
  } catch {
    ElMessage.error(t('debug.copyCollectionFailed'))
  }
}

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
    const res = await functionUnitApi.debugRunAction(props.functionUnitId, {
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

function handleLookupProbeLog(payload: { message: string; detail?: Record<string, any> }) {
  addLog('info', payload.message, 'LOOKUP_PROBE', currentNode.value?.id, currentNode.value?.name, payload.detail)
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

function isBreakpointHit(index: number): boolean {
  const step = simulationSteps.value[index]
  if (!step) return false
  return breakpoints.value.some(bp => bp.enabled && bp.nodeId === step.nodeId)
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

function handleBreakpointToggle(bp: Breakpoint) {
  addLog('info', t('debug.breakpointToggled', {
    name: bp.nodeName,
    state: bp.enabled ? t('debug.enabled') : t('debug.disabled')
  }), 'NODE_ENTER')
}

function addBreakpoint(nodeId: string) {
  const node = processNodes.value.find(item => item.id === nodeId)
  if (!node || hasBreakpoint(nodeId)) return

  breakpoints.value.push({
    nodeId: node.id,
    nodeName: node.name,
    nodeType: node.type,
    enabled: true
  })
  breakpointCandidate.value = null
  addLog('info', t('debug.breakpointAdded', { name: node.name }), 'NODE_ENTER')
}

function removeBreakpoint(nodeId: string) {
  const index = breakpoints.value.findIndex(bp => bp.nodeId === nodeId)
  if (index > -1) {
    breakpoints.value.splice(index, 1)
  }
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
</script>

<style lang="scss" scoped>
.process-debug-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.debug-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e6e6e6;
  flex-shrink: 0;
  gap: 12px;

  .debug-header-title {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;

    h3 { margin: 0; }
  }

  .debug-header-tools {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .debug-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}

.debug-content {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

.process-debug-panel--expanded {
  .debug-left {
    display: flex;
    flex-direction: column;

    :deep(.el-tabs) {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }

    :deep(.el-tabs__content) {
      flex: 1;
      overflow-y: auto;
    }

    :deep(.el-tab-pane) {
      height: 100%;
    }
  }

  .debug-right {
    width: 340px;
  }
}

.debug-left {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  border-right: 1px solid #e6e6e6;
}

.debug-right {
  width: 300px;
  padding: 16px;
  overflow-y: auto;
}

.execution-status {
  margin-bottom: 20px;

  .status-item {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .label {
      color: #909399;
      min-width: 70px;
    }

    .value.muted {
      color: #c0c4cc;
      font-size: 12px;
    }

    .form-link {
      color: var(--el-color-primary);
      cursor: pointer;

      &:hover {
        text-decoration: underline;
      }
    }

    .mi-completion-expr {
      font-size: 11px;
      word-break: break-all;
      color: #606266;
    }
  }
}

.input-variables {
  h4 {
    margin: 0 0 12px;
    font-size: 14px;
  }

  .input-hint {
    margin: 10px 0 0;
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
  }
}

.generated-collections {
  margin-bottom: 16px;

  h4 {
    margin: 0 0 10px;
    font-size: 14px;
  }

  .collection-card {
    border: 1px solid #ebeef5;
    border-radius: 6px;
    padding: 8px;
    margin-bottom: 10px;
    background: #fafafa;
  }

  .collection-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
  }

  .collection-name {
    font-size: 12px;
    color: #606266;
    word-break: break-all;
  }

  .mi-instance-switcher {
    flex-wrap: wrap;

    .label {
      min-width: 100%;
      margin-bottom: 4px;
    }
  }

  .collection-json {
    margin: 0;
    max-height: 180px;
    overflow: auto;
    font-size: 12px;
    line-height: 1.4;
    background: #fff;
    padding: 8px;
    border-radius: 4px;
    border: 1px solid #f0f0f0;
  }
}

.breakpoint-section {
  .breakpoint-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }
}

.breakpoint-list {
  .breakpoint-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px;
    border-bottom: 1px solid #f0f0f0;

    .node-name {
      flex: 1;
    }
  }
}

.decision-pane {
  .decision-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }

  .decision-selected,
  .decision-default {
    font-size: 12px;
    color: #606266;
  }

  .decision-default {
    margin-top: 8px;
  }

  .decision-selector {
    margin-top: 10px;
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .decision-selector-title {
    width: 100%;
    font-size: 12px;
    color: #606266;
  }
}

.actions-pane {
  .action-card {
    margin-bottom: 10px;
  }

  .action-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .action-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #303133;
  }

  .action-description {
    margin: 8px 0 0;
    color: #606266;
    font-size: 12px;
  }

  .action-result {
    margin-top: 10px;
  }

  .action-result-json {
    margin: 0;
    max-height: 220px;
    overflow: auto;
    font-size: 12px;
    line-height: 1.4;
  }
}
</style>
