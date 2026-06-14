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
              <div class="table-scroll-wrap">
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
              </div>
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
import { VideoPlay, VideoPause, Right, DArrowRight, Delete, FullScreen, ScaleToOriginal, Close } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import VariableMonitor from './VariableMonitor.vue'
import ExecutionLogViewer from './ExecutionLogViewer.vue'
import ProcessDebugNodeForm from './ProcessDebugNodeForm.vue'
import { useDebugState } from '@/composables/processDebug/useDebugState'
import { useDebugSteps, isFormCapableNode } from '@/composables/processDebug/useDebugSteps'
import { useDebugMiContext } from '@/composables/processDebug/useDebugMiContext'
import { useDebugGateway } from '@/composables/processDebug/useDebugGateway'
import { useDebugActions } from '@/composables/processDebug/useDebugActions'
import { useDebugBreakpoints } from '@/composables/processDebug/useDebugBreakpoints'
import { useDebugSession } from '@/composables/processDebug/useDebugSession'

const { t } = useI18n()

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

// Shared reactive state + logging, consumed by every feature composable below.
const state = useDebugState()
const {
  activeTab,
  isDebugging,
  isPaused,
  starting,
  currentNode,
  currentVariables,
  executionLogs,
  breakpoints,
  inputVariables,
  executionTime,
  simulationSteps,
  generatedCollectionsPreview,
  activeParallelMi,
  parallelInstancePicker,
  currentGatewayEval,
  currentNodeActions,
  runningActionId,
  actionRunResult,
  breakpointCandidate,
} = state

// Actions: node-action list sync + dry-run execution.
const { syncCurrentNodeActions, handleRunAction } = useDebugActions({
  state,
  functionUnitId: props.functionUnitId,
  t,
})

// MI / parallel-instance context. `hasCurrentNodeFormBinding` is a wrapper
// closure that reads `steps.currentNodeFormBinding` declared just below,
// breaking the cycle between MI and step application.
const mi = useDebugMiContext({
  state,
  t,
  hasCurrentNodeFormBinding: () => !!steps.currentNodeFormBinding.value,
})
const {
  effectiveMiContext,
  miExecutionModeText,
  miInstanceText,
  miAssigneeText,
  miCompletionConditionText,
  handleParallelInstanceChange,
  copyCollectionJson,
} = mi

// Step execution; depends on MI/actions sync functions injected above.
const steps = useDebugSteps({
  state,
  t,
  emit,
  syncParallelMiScope: mi.syncParallelMiScope,
  syncCurrentNodeActions,
})
const { currentNodeFormBinding, stepProgressText, applyStep, consumeNextStepIndex } = steps

// Gateway decision pane.
const { canSelectGatewayBranch, gatewaySelectableFlowIds, handleSelectGatewayBranch } = useDebugGateway({
  state,
  t,
})

// Breakpoints.
const {
  breakpointCandidates,
  hasBreakpoint,
  isBreakpointHit,
  handleBreakpointToggle,
  addBreakpoint,
  removeBreakpoint,
} = useDebugBreakpoints({ state, t })

// Session orchestration; depends on step/breakpoint functions injected above.
const {
  statusText,
  statusTagType,
  handleStartDebug,
  handleStepOver,
  handleContinue,
  handleStopDebug,
  handleVariableUpdate,
  handleLookupProbeLog,
  addInputVariable,
} = useDebugSession({
  state,
  functionUnitId: props.functionUnitId,
  getBpmnXml: props.getBpmnXml,
  t,
  emit,
  applyStep,
  consumeNextStepIndex,
  extractProcessNodes: steps.extractProcessNodes,
  extractProcessFlows: steps.extractProcessFlows,
  isBreakpointHit,
})
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
