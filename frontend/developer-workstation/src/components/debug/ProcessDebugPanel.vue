<template>
  <div class="process-debug-panel">
    <div class="debug-header">
      <h3>{{ t('process.processDebug') }}</h3>
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
            v-if="executionTime !== null"
            class="status-item"
          >
            <span class="label">{{ t('debug.executionTime') }}:</span>
            <span class="value">{{ executionTime }}ms</span>
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
              v-for="(value, key) in inputVariables"
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
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { VideoPlay, VideoPause, Right, DArrowRight, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { functionUnitApi } from '@/api/functionUnit'
import VariableMonitor from './VariableMonitor.vue'
import ExecutionLogViewer from './ExecutionLogViewer.vue'

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

interface SimulationStep {
  nodeId: string
  nodeName: string
  nodeType: string
  message?: string
  variables?: Record<string, any>
}

interface ExecutionLog {
  timestamp: string
  level: string
  nodeId?: string
  nodeName?: string
  message: string
  variables?: Record<string, any>
}

const props = defineProps<{ functionUnitId: number }>()

const emit = defineEmits<{
  (e: 'current-node-change', nodeId: string | null): void
}>()

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
const executionLogs = ref<ExecutionLog[]>([])
const breakpoints = ref<Breakpoint[]>([])
const inputVariables = reactive<Record<string, string>>({ initiator: 'admin' })
const executionTime = ref<number | null>(null)
const startTime = ref<number>(0)
const simulationSteps = ref<SimulationStep[]>([])
const stepIndex = ref(-1)
const processNodes = ref<ProcessNode[]>([])
const breakpointCandidate = ref<string | null>(null)

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

const breakpointCandidates = computed(() =>
  processNodes.value.filter(node => BREAKPOINT_NODE_TYPES.has(node.type))
)

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
      addLog('error', `${t('debug.executionError')}: ${data.error}`)
      return
    }

    simulationSteps.value = Array.isArray(data.steps) ? data.steps : []
    processNodes.value = extractProcessNodes(data.processStructure)

    if (!simulationSteps.value.length) {
      ElMessage.warning(t('debug.noSimulationSteps'))
      return
    }

    isDebugging.value = true
    isPaused.value = true
    startTime.value = Date.now()
    executionTime.value = null
    stepIndex.value = 0

    addLog('info', t('debug.debugStarted'), undefined, variables)
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

function applyStep(index: number, options: { log?: boolean } = {}) {
  const step = simulationSteps.value[index]
  if (!step) return

  currentNode.value = {
    id: step.nodeId,
    name: step.nodeName || step.nodeId,
    type: step.nodeType
  }
  currentVariables.value = { ...(step.variables || {}) }
  emit('current-node-change', step.nodeId)

  if (options.log) {
    addLog(
      step.nodeType === 'endEvent' ? 'success' : 'info',
      step.message || `${t('debug.executeNode')}: ${step.nodeName || step.nodeId}`,
      step.nodeId,
      step.nodeName,
      step.variables
    )
  }
}

function handleStepOver() {
  if (!isDebugging.value || !isPaused.value) return
  if (stepIndex.value >= simulationSteps.value.length - 1) {
    finishDebug(true)
    return
  }

  stepIndex.value += 1
  applyStep(stepIndex.value, { log: true })

  if (isBreakpointHit(stepIndex.value)) {
    isPaused.value = true
    addLog('warning', t('debug.hitBreakpoint'), simulationSteps.value[stepIndex.value].nodeId,
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
  addLog('info', t('debug.continuing'))

  while (stepIndex.value < simulationSteps.value.length - 1) {
    stepIndex.value += 1
    applyStep(stepIndex.value, { log: true })

    if (isBreakpointHit(stepIndex.value) && stepIndex.value < simulationSteps.value.length - 1) {
      isPaused.value = true
      addLog('warning', t('debug.hitBreakpoint'), simulationSteps.value[stepIndex.value].nodeId,
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
    addLog('success', t('debug.processCompleted'))
  }
  isDebugging.value = false
  isPaused.value = false
  emit('current-node-change', null)
}

function handleStopDebug() {
  executionTime.value = Date.now() - startTime.value
  addLog('warning', t('debug.debugStopped'))
  resetDebugSession(true)
}

function resetDebugSession(keepLogs: boolean) {
  isDebugging.value = false
  isPaused.value = false
  currentNode.value = null
  currentVariables.value = {}
  simulationSteps.value = []
  stepIndex.value = -1
  processNodes.value = []
  breakpointCandidate.value = null
  if (!keepLogs) {
    executionLogs.value = []
    executionTime.value = null
  }
  emit('current-node-change', null)
}

function handleVariableUpdate(key: string, value: any) {
  currentVariables.value[key] = value
  addLog('info', t('debug.variableUpdatedLog', { key, value: JSON.stringify(value) }))
}

function handleBreakpointToggle(bp: Breakpoint) {
  addLog('info', t('debug.breakpointToggled', {
    name: bp.nodeName,
    state: bp.enabled ? t('debug.enabled') : t('debug.disabled')
  }))
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
  addLog('info', t('debug.breakpointAdded', { name: node.name }))
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
  nodeId?: string,
  nodeName?: string,
  variables?: Record<string, any>
) {
  executionLogs.value.push({
    timestamp: new Date().toISOString(),
    level,
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

  h3 { margin: 0; }

  .debug-actions {
    display: flex;
    gap: 8px;
  }
}

.debug-content {
  flex: 1;
  display: flex;
  overflow: hidden;
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
  }
}

.input-variables {
  h4 {
    margin: 0 0 12px;
    font-size: 14px;
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
</style>
