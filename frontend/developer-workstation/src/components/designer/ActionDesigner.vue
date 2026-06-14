<template>
  <div class="action-designer">
    <div class="designer-toolbar">
      <el-button
        type="primary"
        @click="showCreateDialog = true"
      >
        {{ t('action.createAction') }}
      </el-button>
      <el-button
        :loading="loading"
        @click="loadActions"
      >
        <el-icon><Refresh /></el-icon> {{ t('action.refresh') }}
      </el-button>
    </div>
    
    <div
      v-if="!selectedAction"
      class="action-list table-scroll-wrap"
    >
      <el-table
        v-loading="loading"
        :data="store.actions"
        stripe
        @row-click="handleSelectAction"
      >
        <el-table-column
          prop="actionName"
          :label="t('action.actionName')"
          width="120"
        />
        <el-table-column
          prop="actionType"
          :label="t('action.actionType')"
          width="100"
        >
          <template #default="{ row }">
            <el-tag size="small">
              {{ actionTypeLabel(row.actionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('action.boundNodes')"
          min-width="200"
        >
          <template #default="{ row }">
            <div class="bound-nodes">
              <template v-if="getActionBoundNodes(row.id).length > 0">
                <el-tag 
                  v-for="node in getActionBoundNodes(row.id)" 
                  :key="node.id"
                  size="small"
                  type="info"
                  class="node-tag"
                >
                  {{ node.name || node.id }}
                </el-tag>
              </template>
              <span
                v-else
                class="no-binding"
              >{{ t('action.notBound') }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          :label="t('action.description')"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('action.operation')"
          min-width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-row-actions">
              <el-button
                link
                type="primary"
                @click.stop="handleSelectAction(row)"
              >
                {{ t('action.edit') }}
              </el-button>
              <el-button
                link
                type="success"
                @click.stop="handleTestAction(row)"
              >
                {{ t('action.test') }}
              </el-button>
              <el-button
                link
                type="danger"
                @click.stop="handleDeleteAction(row)"
              >
                {{ t('action.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div
      v-else
      class="action-editor"
    >
      <div class="editor-header">
        <el-button @click="handleBackToList">
          <el-icon><ArrowLeft /></el-icon> {{ t('action.backToList') }}
        </el-button>
        <span class="action-name">{{ selectedAction.actionName }}</span>
        <el-button
          type="success"
          @click="handleTestAction(selectedAction)"
        >
          {{ t('action.test') }}
        </el-button>
        <el-button
          type="primary"
          @click="handleSaveAction"
        >
          {{ t('action.save') }}
        </el-button>
      </div>
      
      <el-form
        :model="selectedAction"
        label-width="100px"
        label-position="left"
        style="max-width: 600px;"
      >
        <el-form-item :label="t('action.actionName')">
          <el-input v-model="selectedAction.actionName" />
        </el-form-item>
        <el-form-item :label="t('action.actionType')">
          <el-select v-model="selectedAction.actionType">
            <el-option-group :label="t('action.approvalOperations')">
              <el-option
                :label="t('action.approve')"
                value="APPROVE"
              />
              <el-option
                :label="t('action.reject')"
                value="REJECT"
              />
              <el-option
                :label="t('action.transfer')"
                value="TRANSFER"
              />
              <el-option
                :label="t('action.delegate')"
                value="DELEGATE"
              />
              <el-option
                :label="t('action.rollback')"
                value="ROLLBACK"
              />
              <el-option
                :label="t('action.withdraw')"
                value="WITHDRAW"
              />
              <el-option
                :label="t('action.draft')"
                value="DRAFT"
              />
            </el-option-group>
            <el-option-group :label="t('action.processOperations')">
              <el-option
                :label="t('action.saveDraft')"
                value="SAVE"
              />
              <el-option
                :label="t('action.processSubmit')"
                value="PROCESS_SUBMIT"
              />
              <el-option
                :label="t('action.processReject')"
                value="PROCESS_REJECT"
              />
              <el-option
                :label="t('action.composite')"
                value="COMPOSITE"
              />
            </el-option-group>
            <el-option-group :label="t('action.customOperations')">
              <el-option
                :label="t('action.apiCall')"
                value="API_CALL"
              />
              <el-option
                :label="t('action.formPopup')"
                value="FORM_POPUP"
              />
              <el-option
                :label="t('action.customScript')"
                value="CUSTOM_SCRIPT"
              />
              <el-option
                :label="t('action.n8nAction')"
                value="N8N_ACTION"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item :label="t('action.description')">
          <el-input
            v-model="selectedAction.description"
            type="textarea"
          />
        </el-form-item>
        
        <!-- API Call Config -->
        <template v-if="selectedAction.actionType === 'API_CALL'">
          <el-divider>{{ t('action.apiConfig') }}</el-divider>
          <el-form-item :label="t('action.requestUrl')">
            <el-input
              v-model="actionConfig.url"
              :placeholder="t('action.requestUrlPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('action.requestMethod')">
            <el-select v-model="actionConfig.method">
              <el-option
                label="GET"
                value="GET"
              />
              <el-option
                label="POST"
                value="POST"
              />
              <el-option
                label="PUT"
                value="PUT"
              />
              <el-option
                label="DELETE"
                value="DELETE"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('action.requestHeaders')">
            <el-input
              v-model="actionConfig.headers"
              type="textarea"
              :rows="3" 
              :placeholder="t('action.requestHeadersPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('action.requestBody')">
            <el-input
              v-model="actionConfig.body"
              type="textarea"
              :rows="5" 
              :placeholder="t('action.requestBodyPlaceholder')"
            />
          </el-form-item>
        </template>
        
        <!-- Form Popup Config -->
        <template v-if="selectedAction.actionType === 'FORM_POPUP'">
          <el-divider>{{ t('action.formConfig') }}</el-divider>
          <el-form-item :label="t('action.relatedForm')">
            <el-select v-model="actionConfig.formId">
              <el-option
                v-for="form in actionFormOptions"
                :key="form.id" 
                :label="form.formName"
                :value="form.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('action.dialogTitle')">
            <el-input v-model="actionConfig.dialogTitle" />
          </el-form-item>
          <el-form-item :label="t('action.dialogWidth')">
            <el-input
              v-model="actionConfig.dialogWidth"
              :placeholder="t('action.dialogWidthPlaceholder')"
            />
          </el-form-item>
        </template>

        <!-- Process Submit/Reject Config -->
        <template v-if="selectedAction.actionType === 'PROCESS_SUBMIT' || selectedAction.actionType === 'PROCESS_REJECT'">
          <el-divider>{{ t('action.processConfig') }}</el-divider>
          <el-form-item :label="t('action.requireComment')">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
          <el-form-item :label="t('action.confirmMessage')">
            <el-input
              v-model="actionConfig.confirmMessage"
              :placeholder="t('action.confirmMessagePlaceholder')"
            />
          </el-form-item>
        </template>

        <!-- Custom Script Config -->
        <template v-if="selectedAction.actionType === 'CUSTOM_SCRIPT'">
          <el-divider>{{ t('action.scriptConfig') }}</el-divider>
          <el-form-item :label="t('action.scriptCode')">
            <el-input
              v-model="actionConfig.script"
              type="textarea"
              :rows="10" 
              :placeholder="t('action.scriptCodePlaceholder')"
            />
          </el-form-item>
        </template>

        <!-- N8N Action Config -->
        <template v-if="selectedAction.actionType === 'N8N_ACTION'">
          <el-divider>{{ t('action.n8nConfig') }}</el-divider>
          <el-form-item :label="t('action.n8nConfigId')">
            <el-select
              v-model="actionConfig.n8nConfigId"
              :placeholder="t('action.n8nConfigPlaceholder')"
              filterable
              @change="onN8nConfigChange"
            >
              <el-option
                v-for="config in n8nConfigList"
                :key="config.id"
                :label="config.name"
                :value="config.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('action.n8nWorkflowId')">
            <el-select
              v-model="actionConfig.n8nWorkflowId"
              :placeholder="t('action.n8nWorkflowPlaceholder')"
              filterable
              :disabled="!actionConfig.n8nConfigId"
              @change="onN8nWorkflowChange"
            >
              <el-option
                v-for="wf in n8nWorkflowList"
                :key="wf.id"
                :label="wf.name"
                :value="wf.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('action.n8nWebhookUrl')">
            <el-input
              v-model="actionConfig.webhookUrl"
              :placeholder="t('action.n8nWebhookUrlPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('action.n8nTimeout')">
            <el-input-number
              v-model="actionConfig.timeoutSeconds"
              :min="1"
              :max="3600"
            />
            <span style="font-size: 11px; color: #909399; margin-left: 8px;">{{ t('action.n8nTimeoutUnit') }}</span>
          </el-form-item>

          <!-- Input Parameter Mapping -->
          <div class="mapping-section">
            <div class="mapping-header">
              <span>{{ t('action.n8nInputMapping') }}</span>
              <el-button
                type="primary"
                link
                size="small"
                @click="addN8nInputParam"
              >
                + {{ t('common.add') }}
              </el-button>
            </div>
            <div class="table-scroll-wrap">
            <el-table
              v-if="actionConfig.inputMapping && actionConfig.inputMapping.length > 0"
              :data="actionConfig.inputMapping"
              size="small"
              border
            >
              <el-table-column
                :label="t('action.n8nParamName')"
                min-width="100"
              >
                <template #default="{ row }">
                  <el-input
                    v-model="row.paramName"
                    size="small"
                    :placeholder="t('action.n8nParamNamePlaceholder')"
                  />
                </template>
              </el-table-column>
              <el-table-column
                :label="t('action.n8nParamLabel')"
                min-width="100"
              >
                <template #default="{ row }">
                  <el-input
                    v-model="row.paramLabel"
                    size="small"
                    :placeholder="t('action.n8nParamLabelPlaceholder')"
                  />
                </template>
              </el-table-column>
              <el-table-column
                :label="t('action.n8nParamType')"
                width="110"
              >
                <template #default="{ row }">
                  <el-select
                    v-model="row.paramType"
                    size="small"
                  >
                    <el-option
                      label="string"
                      value="string"
                    />
                    <el-option
                      label="number"
                      value="number"
                    />
                    <el-option
                      label="boolean"
                      value="boolean"
                    />
                    <el-option
                      label="select"
                      value="select"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column
                :label="t('action.n8nParamRequired')"
                width="70"
                align="center"
              >
                <template #default="{ row }">
                  <el-checkbox v-model="row.required" />
                </template>
              </el-table-column>
              <el-table-column
                width="50"
                align="center"
              >
                <template #default="{ $index }">
                  <el-button
                    type="danger"
                    link
                    size="small"
                    @click="removeN8nInputParam($index)"
                  >
                    ✕
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            </div>
          </div>

          <!-- Output Result Mapping -->
          <div class="mapping-section">
            <div class="mapping-header">
              <span>{{ t('action.n8nOutputMapping') }}</span>
              <el-button
                type="primary"
                link
                size="small"
                @click="addN8nOutputMapping"
              >
                + {{ t('common.add') }}
              </el-button>
            </div>
            <div class="table-scroll-wrap">
            <el-table
              v-if="actionConfig.outputMapping && actionConfig.outputMapping.length > 0"
              :data="actionConfig.outputMapping"
              size="small"
              border
            >
              <el-table-column
                :label="t('action.n8nOutputSource')"
                min-width="120"
              >
                <template #default="{ row }">
                  <el-input
                    v-model="row.source"
                    size="small"
                    :placeholder="t('action.n8nOutputSourcePlaceholder')"
                  />
                </template>
              </el-table-column>
              <el-table-column
                :label="t('action.n8nOutputTarget')"
                min-width="120"
              >
                <template #default="{ row }">
                  <el-input
                    v-model="row.target"
                    size="small"
                    :placeholder="t('action.n8nOutputTargetPlaceholder')"
                  />
                </template>
              </el-table-column>
              <el-table-column
                width="50"
                align="center"
              >
                <template #default="{ $index }">
                  <el-button
                    type="danger"
                    link
                    size="small"
                    @click="removeN8nOutputMapping($index)"
                  >
                    ✕
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            </div>
          </div>
        </template>

        <!-- Approve/Reject Config -->
        <template v-if="selectedAction.actionType === 'APPROVE' || selectedAction.actionType === 'REJECT'">
          <el-divider>{{ t('action.approvalConfig') }}</el-divider>
          <el-form-item :label="t('action.targetStatus')">
            <el-input
              v-model="actionConfig.targetStatus"
              :placeholder="t('action.targetStatusPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('action.requireComment')">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
          <el-form-item :label="t('action.confirmMessage')">
            <el-input
              v-model="actionConfig.confirmMessage"
              :placeholder="t('action.confirmMessageApprovalPlaceholder')"
            />
          </el-form-item>
        </template>

        <!-- Transfer/Delegate Config -->
        <template v-if="selectedAction.actionType === 'TRANSFER' || selectedAction.actionType === 'DELEGATE'">
          <el-divider>{{ t('action.transferDelegateConfig') }}</el-divider>
          <el-form-item :label="t('action.requireAssignee')">
            <el-switch v-model="actionConfig.requireAssignee" />
          </el-form-item>
          <el-form-item :label="t('action.requireComment')">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
        </template>

        <!-- Rollback Config -->
        <template v-if="selectedAction.actionType === 'ROLLBACK'">
          <el-divider>{{ t('action.rollbackConfig') }}</el-divider>
          <el-form-item :label="t('action.targetStep')">
            <el-select v-model="actionConfig.targetStep">
              <el-option
                :label="t('action.previousStep')"
                value="previous"
              />
              <el-option
                :label="t('action.specificStep')"
                value="specific"
              />
              <el-option
                :label="t('action.initiator')"
                value="initiator"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('action.requireComment')">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
        </template>

        <!-- Draft Config (return to first user task) -->
        <template v-if="selectedAction.actionType === 'DRAFT'">
          <el-divider>{{ t('action.draftConfig') }}</el-divider>
          <el-form-item :label="t('action.requireComment')">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
          <el-form-item :label="t('action.confirmMessage')">
            <el-input
              v-model="actionConfig.confirmMessage"
              :placeholder="t('action.draftConfirmPlaceholder')"
            />
          </el-form-item>
        </template>

        <!-- Withdraw Config -->
        <template v-if="selectedAction.actionType === 'WITHDRAW'">
          <el-divider>{{ t('action.withdrawConfig') }}</el-divider>
          <el-form-item :label="t('action.targetStatus')">
            <el-input
              v-model="actionConfig.targetStatus"
              :placeholder="t('action.targetStatusCancelledPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="t('action.allowedFromStatus')">
            <el-select
              v-model="actionConfig.allowedFromStatus"
              multiple
              :placeholder="t('action.selectAllowedStatus')"
            >
              <el-option
                :label="t('action.pending')"
                value="PENDING"
              />
              <el-option
                :label="t('action.inProgress')"
                value="IN_PROGRESS"
              />
            </el-select>
          </el-form-item>
        </template>

        <!-- Composite Config -->
        <template v-if="selectedAction.actionType === 'COMPOSITE'">
          <el-divider>{{ t('action.compositeConfig') }}</el-divider>
          <el-form-item :label="t('action.subActions')">
            <el-select
              v-model="actionConfig.subActions"
              multiple
              :placeholder="t('action.selectSubActions')"
            >
              <el-option
                v-for="action in store.actions.filter(a => a.actionType !== 'COMPOSITE')" 
                :key="action.id"
                :label="action.actionName"
                :value="action.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('action.executionOrder')">
            <el-radio-group v-model="actionConfig.executionOrder">
              <el-radio label="sequential">
                {{ t('action.sequential') }}
              </el-radio>
              <el-radio label="parallel">
                {{ t('action.parallel') }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 节点绑定配置 -->
        <el-divider>{{ t('action.nodeBinding') }}</el-divider>
        <el-form-item :label="t('action.bindingType')">
          <el-radio-group v-model="bindingType">
            <el-radio label="node">
              {{ t('action.bindToNode') }}
            </el-radio>
            <el-radio label="global">
              {{ t('action.processGlobal') }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="bindingType === 'node'"
          :label="t('action.selectNodes')"
        >
          <el-checkbox-group v-model="selectedNodeIds">
            <el-checkbox 
              v-for="node in availableNodes" 
              :key="node.id" 
              :label="node.id"
            >
              {{ node.name || node.id }}
            </el-checkbox>
          </el-checkbox-group>
          <div
            v-if="availableNodes.length === 0"
            class="no-nodes-tip"
          >
            {{ t('action.noNodesAvailable') }}
          </div>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="savingBinding"
            @click="handleSaveBinding"
          >
            {{ t('action.saveBinding') }}
          </el-button>
          <span class="binding-tip">{{ t('action.bindingWillUpdateProcess') }}</span>
        </el-form-item>

        <!-- Visibility, Roles & Sort Order -->
        <el-divider>{{ t('action.visibilityAndPermissions') }}</el-divider>
        <el-form-item :label="t('action.visibilityCondition')">
          <ConditionBuilder
            :model-value="actionConfig.visibilityCondition ?? []"
            :fields="availableFormFields"
            :placeholder="t('action.visibilityConditionPlaceholder')"
            @update:model-value="(val: ConditionExpression[]) => actionConfig.visibilityCondition = val.length > 0 ? val : null"
          />
        </el-form-item>
        <el-form-item :label="t('action.allowedRoles')">
          <el-select
            v-model="actionConfig.allowedRoles"
            multiple
            filterable
            allow-create
            :placeholder="t('action.allowedRolesPlaceholder')"
            style="width: 100%"
          >
            <el-option
              v-for="role in availableRoles"
              :key="role"
              :label="role"
              :value="role"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('action.sortOrder')">
          <el-input-number
            v-model="actionConfig.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
    </div>

    <!-- Create Action Dialog -->
    <ActionCreateDialog
      v-model="showCreateDialog"
      :create-form="createForm"
      @confirm="handleCreateAction"
    />

    <!-- Test Action Dialog -->
    <ActionTestDialog
      v-model="showTestDialog"
      v-model:test-raw-json-mode="testRawJsonMode"
      v-model:test-data="testData"
      :test-action-type="testActionType"
      :test-input-mapping="testInputMapping"
      :test-structured-data="testStructuredData"
      :test-result="testResult"
      :testing="testing"
      @execute-test="executeTest"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import ActionCreateDialog from './action-designer/ActionCreateDialog.vue'
import ActionTestDialog from './action-designer/ActionTestDialog.vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { type ActionDefinition } from '@/api/functionUnit'
import ConditionBuilder from './ConditionBuilder.vue'
import type { ConditionExpression } from './formBusinessLogicTypes'
import { useActionNodeBinding } from '@/composables/actionDesigner/useActionNodeBinding'
import { useActionConfig } from '@/composables/actionDesigner/useActionConfig'
import { useN8nAction } from '@/composables/actionDesigner/useN8nAction'
import { useActionList } from '@/composables/actionDesigner/useActionList'
import { useActionTest } from '@/composables/actionDesigner/useActionTest'

const { t } = useI18n()

const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()

// 核心共享状态
const selectedAction = ref<ActionDefinition | null>(null)
// availableRoles：模板的 allowedRoles 下拉用，始终为空（保持原行为）
const availableRoles = ref<string[]>([])

// 节点绑定（独立，不依赖 config/n8n）
const {
  bindingType,
  selectedNodeIds,
  availableNodes,
  savingBinding,
  parseActionBindingsFromBpmn,
  getActionBoundNodes,
  loadActionBinding,
  handleSaveBinding,
} = useActionNodeBinding({ functionUnitId: props.functionUnitId, selectedAction, store, t })

// 动作配置（watch 通过 wrapper 闭包延迟引用 n8n composable，破除 config↔n8n 循环依赖）
const {
  actionConfig,
  actionFormOptions,
  availableFormFields,
} = useActionConfig({
  selectedAction,
  store,
  loadActionBinding,
  loadN8nConfigs: () => loadN8nConfigs(),
  loadN8nWorkflows: (configId: string) => loadN8nWorkflows(configId),
})

// N8N Action（依赖 actionConfig）
const {
  n8nConfigList,
  n8nWorkflowList,
  loadN8nConfigs,
  loadN8nWorkflows,
  onN8nConfigChange,
  onN8nWorkflowChange,
  addN8nInputParam,
  removeN8nInputParam,
  addN8nOutputMapping,
  removeN8nOutputMapping,
} = useN8nAction({ actionConfig })

// 列表与编辑器生命周期（loadActions 通过 wrapper 调用 binding 的解析）
const {
  loading,
  showCreateDialog,
  createForm,
  actionTypeLabel,
  loadActions,
  handleSelectAction,
  handleBackToList,
  handleCreateAction,
  handleSaveAction,
  handleDeleteAction,
} = useActionList({
  functionUnitId: props.functionUnitId,
  selectedAction,
  actionConfig,
  store,
  t,
  parseActionBindingsFromBpmn,
})

// 测试对话框
const {
  showTestDialog,
  testData,
  testResult,
  testing,
  testRawJsonMode,
  testActionType,
  testInputMapping,
  testStructuredData,
  handleTestAction,
  executeTest,
} = useActionTest({ functionUnitId: props.functionUnitId, selectedAction, t })

onMounted(loadActions)
</script>

<style lang="scss" scoped>
.action-designer {
  min-height: 400px;
}

.designer-toolbar {
  margin-bottom: 16px;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.action-name {
  flex: 1;
  font-size: 18px;
  font-weight: bold;
}

.node-tag {
  margin: 0;
}

.no-binding {
  color: #909399;
  font-size: 12px;
}

.no-nodes-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}

.binding-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 12px;
}

.mapping-section {
  margin-bottom: 16px;
  padding: 0 100px 0 0;

  .mapping-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    font-size: 12px;
    font-weight: 600;
    color: #606266;
  }
}
</style>
