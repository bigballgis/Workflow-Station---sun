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
      class="action-list"
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
          width="200"
        >
          <template #default="{ row }">
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
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ActionCreateDialog from './action-designer/ActionCreateDialog.vue'
import ActionTestDialog from './action-designer/ActionTestDialog.vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi, type ActionDefinition } from '@/api/functionUnit'
import { n8nApi, type N8nConfig, type N8nWorkflow } from '@/api/n8n'
import ConditionBuilder from './ConditionBuilder.vue'
import type { ConditionExpression } from './formBusinessLogicTypes'

const { t } = useI18n()

const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const loading = ref(false)
const selectedAction = ref<ActionDefinition | null>(null)
const showCreateDialog = ref(false)
const showTestDialog = ref(false)
const testData = ref('{}')
const testResult = ref('')
const testing = ref(false)
const testRawJsonMode = ref(false)
const testActionType = ref('')
const testInputMapping = ref<Array<{ paramName: string; paramLabel: string; paramType: string; required: boolean }>>([])
const testStructuredData = ref<Record<string, any>>({})
const createForm = reactive({ actionName: '', actionType: 'APPROVE', description: '' })

// N8N Action 相关状态
const n8nConfigList = ref<N8nConfig[]>([])
const n8nWorkflowList = ref<N8nWorkflow[]>([])

// 存储从BPMN XML解析出的动作绑定信息
const actionNodeBindings = ref<Map<string | number, Array<{ id: string; name: string }>>>(new Map())

// 节点绑定相关
const bindingType = ref<'node' | 'global'>('node')
const selectedNodeIds = ref<string[]>([])
const availableNodes = ref<Array<{ id: string; name: string }>>([])

// FORM_POPUP action: only show ACTION type forms
const actionFormOptions = computed(() => store.forms.filter(f => f.formType === 'ACTION'))
const availableFormFields = computed(() => {
  const fields = new Set<string>()
  for (const form of store.forms) {
    const rule = form.configJson?.rule
    if (Array.isArray(rule)) {
      for (const r of rule) {
        if (r.field && r.type !== 'subTable') {
          fields.add(r.field)
        }
      }
    }
  }
  return Array.from(fields)
})
const availableRoles = ref<string[]>([])
const savingBinding = ref(false)

const actionConfig = reactive<Record<string, any>>({
  url: '',
  method: 'POST',
  headers: '',
  body: '',
  formId: null,
  dialogTitle: '',
  dialogWidth: '600px',
  requireComment: false,
  confirmMessage: '',
  script: '',
  targetStatus: '',
  requireAssignee: false,
  targetStep: '',
  // N8N Action fields
  n8nConfigId: '',
  n8nWorkflowId: '',
  webhookUrl: '',
  timeoutSeconds: 120,
  inputMapping: [] as Array<{ paramName: string; paramLabel: string; paramType: string; required: boolean }>,
  outputMapping: [] as Array<{ source: string; target: string }>,
  // Visibility, roles & sort order
  visibilityCondition: null as ConditionExpression[] | null,
  allowedRoles: [] as string[],
  sortOrder: 0
})

const actionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    APPROVE: t('action.approve'),
    REJECT: t('action.reject'),
    TRANSFER: t('action.transfer'),
    DELEGATE: t('action.delegate'),
    ROLLBACK: t('action.rollback'),
    WITHDRAW: t('action.withdraw'),
    DRAFT: t('action.draft'),
    SAVE: t('action.saveDraft'),
    PROCESS_SUBMIT: t('action.processSubmit'),
    PROCESS_REJECT: t('action.processReject'),
    COMPOSITE: t('action.composite'),
    API_CALL: t('action.apiCall'),
    FORM_POPUP: t('action.formPopup'),
    CUSTOM_SCRIPT: t('action.customScript'),
    N8N_ACTION: t('action.n8nAction')
  }
  return map[type] || type
}

watch(selectedAction, (action) => {
  if (action?.configJson) {
    Object.assign(actionConfig, action.configJson)
    // Ensure N8N arrays are initialized
    if (action.actionType === 'N8N_ACTION') {
      if (!Array.isArray(actionConfig.inputMapping)) actionConfig.inputMapping = []
      if (!Array.isArray(actionConfig.outputMapping)) actionConfig.outputMapping = []
    }
  } else {
    // Reset to defaults
    Object.assign(actionConfig, {
      url: '',
      method: 'POST',
      headers: '',
      body: '',
      formId: null,
      dialogTitle: '',
      dialogWidth: '600px',
      requireComment: false,
      confirmMessage: '',
      script: '',
      targetStatus: '',
      requireAssignee: false,
      targetStep: '',
      // N8N Action fields
      n8nConfigId: '',
      n8nWorkflowId: '',
      webhookUrl: '',
      timeoutSeconds: 120,
      inputMapping: [],
      outputMapping: [],
      // Visibility, roles & sort order
      visibilityCondition: null as ConditionExpression[] | null,
      allowedRoles: [] as string[],
      sortOrder: 0
    })
  }
  
  // 加载当前动作的绑定信息
  if (action) {
    loadActionBinding(action.id)
    // Load N8N configs if action type is N8N_ACTION
    if (action.actionType === 'N8N_ACTION') {
      loadN8nConfigs()
      if (actionConfig.n8nConfigId) {
        loadN8nWorkflows(actionConfig.n8nConfigId)
      }
    }
  }
})

async function loadActions() {
  loading.value = true
  try {
    await store.fetchActions(props.functionUnitId)
    await store.fetchForms(props.functionUnitId)
    await store.fetchProcess(props.functionUnitId)
    // 解析BPMN XML获取动作绑定信息
    parseActionBindingsFromBpmn()
  } finally {
    loading.value = false
  }
}

/**
 * 从BPMN XML解析动作与节点的绑定关系
 */
function parseActionBindingsFromBpmn() {
  const bindings = new Map<string | number, Array<{ id: string; name: string }>>()
  const nodes: Array<{ id: string; name: string }> = []
  
  const processDefinition = store.process
  if (!processDefinition?.bpmnXml) {
    actionNodeBindings.value = bindings
    availableNodes.value = nodes
    return
  }
  
  try {
    const parser = new DOMParser()
    const xmlDoc = parser.parseFromString(processDefinition.bpmnXml, 'text/xml')
    
    // 查找流程级别的全局动作 - 支持带命名空间
    const allElements = xmlDoc.getElementsByTagName('*')
    
    // 查找 process 元素
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'process') {
        // 查找 process 下的 property/values 元素
        const procProps = el.getElementsByTagName('*')
        for (let j = 0; j < procProps.length; j++) {
          const prop = procProps[j]
          const propLocalName = prop.localName || prop.nodeName.split(':').pop()
          
          if (propLocalName === 'property' || propLocalName === 'values') {
            const name = prop.getAttribute('name')
            const value = prop.getAttribute('value')
            
            if (name === 'globalActionIds' && value) {
              try {
                const actionIds = parseActionIds(value)
                actionIds.forEach(actionId => {
                  if (!bindings.has(actionId)) {
                    bindings.set(actionId, [])
                  }
                  bindings.get(actionId)!.push({ id: 'process', name: t('action.processGlobal') })
                })
              } catch (e) {
                console.warn('Failed to parse globalActionIds:', value)
              }
            }
          }
        }
      }
    }
    
    // 查找所有userTask节点 - 支持带命名空间
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'userTask') {
        const taskId = el.getAttribute('id') || ''
        const taskName = el.getAttribute('name') || taskId
        
        // 添加到可用节点列表
        nodes.push({ id: taskId, name: taskName })
        
        // 查找 property/values 中的 actionIds
        const taskProps = el.getElementsByTagName('*')
        for (let j = 0; j < taskProps.length; j++) {
          const prop = taskProps[j]
          const propLocalName = prop.localName || prop.nodeName.split(':').pop()
          
          if (propLocalName === 'property' || propLocalName === 'values') {
            const name = prop.getAttribute('name')
            const value = prop.getAttribute('value')
            
            if (name === 'actionIds' && value) {
              try {
                const actionIds = parseActionIds(value)
                actionIds.forEach(actionId => {
                  if (!bindings.has(actionId)) {
                    bindings.set(actionId, [])
                  }
                  bindings.get(actionId)!.push({ id: taskId, name: taskName })
                })
              } catch (e) {
                console.warn('Failed to parse actionIds:', value, e)
              }
            }
          }
        }
      }
    }
  } catch (e) {
    console.error('Failed to parse BPMN XML:', e)
  }
  
  actionNodeBindings.value = bindings
  availableNodes.value = nodes
  
  // 调试日志
  console.log('[ActionDesigner] Parsed bindings:', bindings)
  console.log('[ActionDesigner] Available nodes:', nodes)
}

/**
 * 解析actionIds - 支持数字ID和字符串ID
 * 格式: [12,22] 或 [action-dl-verify-docs,action-dl-approve-loan]
 */
function parseActionIds(value: string): Array<string | number> {
  if (!value) return []
  
  try {
    // 尝试作为JSON解析（数字ID格式）
    const result = JSON.parse(value) as Array<string | number>
    console.log('[ActionDesigner] Parsed as JSON:', value, '->', result)
    return result
  } catch (e) {
    // 如果JSON解析失败，尝试解析字符串ID格式
    // 移除括号和空格: "[id1,id2]" -> "id1,id2"
    const cleaned = value.replace(/[\[\]\s]/g, '')
    if (!cleaned) return []
    
    // 分割并返回字符串ID数组
    const stringIds = cleaned.split(',').map(s => s.trim()).filter(s => s)
    console.log('[ActionDesigner] Parsed as String IDs:', value, '->', stringIds)
    return stringIds
  }
}

function actionIdsListIncludes(list: Array<string | number>, actionId: string | number): boolean {
  return list.some(id => String(id) === String(actionId))
}

/**
 * 获取动作绑定的节点列表
 */
function getActionBoundNodes(actionId: string | number): Array<{ id: string; name: string }> {
  return actionNodeBindings.value.get(actionId) || []
}

/**
 * 加载当前动作的绑定信息
 */
function loadActionBinding(actionId: string | number) {
  const boundNodes = getActionBoundNodes(actionId)
  
  // 判断是否为全局绑定
  const isGlobal = boundNodes.some(n => n.id === 'process')
  bindingType.value = isGlobal ? 'global' : 'node'
  
  // 设置已选中的节点
  selectedNodeIds.value = boundNodes
    .filter(n => n.id !== 'process')
    .map(n => n.id)
}

/**
 * 保存动作绑定到流程节点
 */
async function handleSaveBinding() {
  if (!selectedAction.value || !store.process?.bpmnXml) {
    ElMessage.warning(t('action.saveProcessFirst'))
    return
  }
  
  savingBinding.value = true
  try {
    const actionId = selectedAction.value.id
    const actionName = selectedAction.value.actionName
    let bpmnXml = store.process.bpmnXml
    
    const parser = new DOMParser()
    const xmlDoc = parser.parseFromString(bpmnXml, 'text/xml')
    
    // 先从所有节点中移除当前动作
    removeActionFromAllNodes(xmlDoc, actionId)
    
    if (bindingType.value === 'global') {
      // 添加到流程全局
      addActionToProcess(xmlDoc, actionId, actionName)
    } else {
      // 添加到选中的节点
      selectedNodeIds.value.forEach(nodeId => {
        addActionToNode(xmlDoc, nodeId, actionId, actionName)
      })
    }
    
    // 序列化XML
    const serializer = new XMLSerializer()
    const newXml = serializer.serializeToString(xmlDoc)
    
    // 保存到后端
    await store.saveProcess(props.functionUnitId, {
      ...store.process,
      bpmnXml: newXml
    })
    
    ElMessage.success(t('action.bindingSaveSuccess'))
    
    // 重新加载绑定信息
    await store.fetchProcess(props.functionUnitId)
    parseActionBindingsFromBpmn()
    loadActionBinding(actionId)
  } catch (e: any) {
    console.error('Save binding failed:', e)
    ElMessage.error(e.response?.data?.message || t('action.saveFailed'))
  } finally {
    savingBinding.value = false
  }
}

/**
 * 从所有节点中移除指定动作
 */
function removeActionFromAllNodes(xmlDoc: Document, actionId: string | number) {
  // 从流程全局移除
  const processes = xmlDoc.querySelectorAll('process')
  processes.forEach(proc => {
    const properties = proc.querySelectorAll(':scope > extensionElements > properties > property')
    properties.forEach(prop => {
      const name = prop.getAttribute('name')
      if (name === 'globalActionIds') {
        const value = prop.getAttribute('value')
        if (value) {
          try {
            const currentIds = parseActionIds(value)
            const filteredIds = currentIds.filter(id => String(id) !== String(actionId))
            prop.setAttribute('value', JSON.stringify(filteredIds))
            
            // 同步更新actionNames
            const namesProp = Array.from(properties).find(p => p.getAttribute('name') === 'globalActionNames')
            if (namesProp) {
              const namesValue = namesProp.getAttribute('value')
              if (namesValue) {
                try {
                  const names = JSON.parse(namesValue) as string[]
                  const idx = currentIds.findIndex(id => String(id) === String(actionId))
                  if (idx > -1 && names.length > idx) {
                    names.splice(idx, 1)
                    namesProp.setAttribute('value', JSON.stringify(names))
                  }
                } catch (e) {
                  console.warn('Failed to parse globalActionNames:', namesValue, e)
                }
              }
            }
          } catch (e) {
            console.warn('Failed to parse globalActionIds, skipping node:', value, e)
          }
        }
      }
    })
  })
  
  // 从所有userTask节点移除
  const userTasks = xmlDoc.querySelectorAll('userTask')
  userTasks.forEach(task => {
    const properties = task.querySelectorAll('property')
    properties.forEach(prop => {
      const name = prop.getAttribute('name')
      if (name === 'actionIds') {
        const value = prop.getAttribute('value')
        if (value) {
          try {
            const currentIds = parseActionIds(value)
            const idx = currentIds.findIndex(id => String(id) === String(actionId))
            if (idx > -1) {
              const filteredIds = currentIds.filter(id => String(id) !== String(actionId))
              prop.setAttribute('value', JSON.stringify(filteredIds))
              
              // 同步更新actionNames
              const namesProp = Array.from(properties).find(p => p.getAttribute('name') === 'actionNames')
              if (namesProp) {
                const namesValue = namesProp.getAttribute('value')
                if (namesValue) {
                  try {
                    const names = JSON.parse(namesValue) as string[]
                    if (names.length > idx) {
                      names.splice(idx, 1)
                      namesProp.setAttribute('value', JSON.stringify(names))
                    }
                  } catch (e) {
                    console.warn('Failed to parse actionNames:', namesValue, e)
                  }
                }
              }
            }
          } catch (e) {
            console.warn('Failed to parse actionIds, skipping node:', value, e)
          }
        }
      }
    })
  })
}

/**
 * 添加动作到流程全局
 */
function addActionToProcess(xmlDoc: Document, actionId: string | number, actionName: string) {
  const process = xmlDoc.querySelector('process')
  if (!process) return
  
  let extensionElements = process.querySelector(':scope > extensionElements')
  if (!extensionElements) {
    extensionElements = xmlDoc.createElementNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'bpmn:extensionElements')
    process.insertBefore(extensionElements, process.firstChild)
  }
  
  let properties = extensionElements.querySelector('properties')
  if (!properties) {
    properties = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:properties')
    extensionElements.appendChild(properties)
  }
  
  // 查找或创建globalActionIds属性
  let actionIdsProp = Array.from(properties.querySelectorAll('property')).find(
    p => p.getAttribute('name') === 'globalActionIds'
  )
  let actionNamesProp = Array.from(properties.querySelectorAll('property')).find(
    p => p.getAttribute('name') === 'globalActionNames'
  )
  
  if (actionIdsProp) {
    const value = actionIdsProp.getAttribute('value')
    const actionIds = value ? (JSON.parse(value) as Array<string | number>) : []
    if (!actionIdsListIncludes(actionIds, actionId)) {
      actionIds.push(actionId)
      actionIdsProp.setAttribute('value', JSON.stringify(actionIds))
    }
  } else {
    actionIdsProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
    actionIdsProp.setAttribute('name', 'globalActionIds')
    actionIdsProp.setAttribute('value', JSON.stringify([actionId]))
    properties.appendChild(actionIdsProp)
  }
  
  if (actionNamesProp) {
    const value = actionNamesProp.getAttribute('value')
    const names = value ? JSON.parse(value) as string[] : []
    if (!names.includes(actionName)) {
      names.push(actionName)
      actionNamesProp.setAttribute('value', JSON.stringify(names))
    }
  } else {
    actionNamesProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
    actionNamesProp.setAttribute('name', 'globalActionNames')
    actionNamesProp.setAttribute('value', JSON.stringify([actionName]))
    properties.appendChild(actionNamesProp)
  }
}

/**
 * 添加动作到指定节点
 */
function addActionToNode(xmlDoc: Document, nodeId: string, actionId: string | number, actionName: string) {
  const task = xmlDoc.querySelector(`userTask[id="${nodeId}"]`)
  if (!task) return
  
  let extensionElements = task.querySelector(':scope > extensionElements')
  if (!extensionElements) {
    extensionElements = xmlDoc.createElementNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'bpmn:extensionElements')
    task.insertBefore(extensionElements, task.firstChild)
  }
  
  let properties = extensionElements.querySelector('properties')
  if (!properties) {
    properties = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:properties')
    extensionElements.appendChild(properties)
  }
  
  // 查找或创建actionIds属性
  let actionIdsProp = Array.from(properties.querySelectorAll('property')).find(
    p => p.getAttribute('name') === 'actionIds'
  )
  let actionNamesProp = Array.from(properties.querySelectorAll('property')).find(
    p => p.getAttribute('name') === 'actionNames'
  )
  
  if (actionIdsProp) {
    const value = actionIdsProp.getAttribute('value')
    const actionIds = value ? (JSON.parse(value) as Array<string | number>) : []
    if (!actionIdsListIncludes(actionIds, actionId)) {
      actionIds.push(actionId)
      actionIdsProp.setAttribute('value', JSON.stringify(actionIds))
    }
  } else {
    actionIdsProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
    actionIdsProp.setAttribute('name', 'actionIds')
    actionIdsProp.setAttribute('value', JSON.stringify([actionId]))
    properties.appendChild(actionIdsProp)
  }
  
  if (actionNamesProp) {
    const value = actionNamesProp.getAttribute('value')
    const names = value ? JSON.parse(value) as string[] : []
    if (!names.includes(actionName)) {
      names.push(actionName)
      actionNamesProp.setAttribute('value', JSON.stringify(names))
    }
  } else {
    actionNamesProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
    actionNamesProp.setAttribute('name', 'actionNames')
    actionNamesProp.setAttribute('value', JSON.stringify([actionName]))
    properties.appendChild(actionNamesProp)
  }
}

function handleSelectAction(row: ActionDefinition) {
  selectedAction.value = { ...row }
}

function handleBackToList() {
  selectedAction.value = null
}

async function handleCreateAction() {
  try {
    await store.createAction(props.functionUnitId, {
      actionName: createForm.actionName,
      actionType: createForm.actionType,
      description: createForm.description,
      configJson: {}
    })
    ElMessage.success(t('action.createSuccess'))
    showCreateDialog.value = false
    Object.assign(createForm, { actionName: '', actionType: 'APPROVE', description: '' })
    loadActions()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('action.createFailed'))
  }
}

async function handleSaveAction() {
  if (!selectedAction.value) return
  
  // Validate N8N_ACTION required fields
  if (selectedAction.value.actionType === 'N8N_ACTION') {
    if (!actionConfig.n8nConfigId) {
      ElMessage.error(t('action.n8nConfigRequired'))
      return
    }
    if (!actionConfig.webhookUrl) {
      ElMessage.error(t('action.n8nWebhookUrlRequired'))
      return
    }
  }
  
  try {
    await store.updateAction(props.functionUnitId, selectedAction.value.id, {
      actionName: selectedAction.value.actionName,
      actionType: selectedAction.value.actionType,
      description: selectedAction.value.description,
      configJson: actionConfig
    })
    ElMessage.success(t('action.saveSuccess'))
    loadActions()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('action.saveFailed'))
  }
}

async function handleDeleteAction(row: ActionDefinition) {
  await ElMessageBox.confirm(t('action.deleteConfirm'), t('action.confirmTitle'), { type: 'warning' })
  try {
    await store.deleteAction(props.functionUnitId, row.id)
    ElMessage.success(t('action.deleteSuccess'))
    loadActions()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('action.deleteFailed'))
  }
}

function handleTestAction(row: ActionDefinition) {
  selectedAction.value = row
  testData.value = '{}'
  testResult.value = ''
  testRawJsonMode.value = false
  testActionType.value = row.actionType
  // Auto-generate structured input fields for N8N_ACTION
  if (row.actionType === 'N8N_ACTION' && row.configJson?.inputMapping) {
    testInputMapping.value = row.configJson.inputMapping
    const initial: Record<string, any> = {}
    for (const param of row.configJson.inputMapping) {
      if (param.paramType === 'number') initial[param.paramName] = 0
      else if (param.paramType === 'boolean') initial[param.paramName] = false
      else initial[param.paramName] = ''
    }
    testStructuredData.value = initial
  } else {
    testInputMapping.value = []
    testStructuredData.value = {}
  }
  showTestDialog.value = true
}

async function executeTest() {
  if (!selectedAction.value) return
  testing.value = true
  try {
    let data: Record<string, unknown>
    if (testActionType.value === 'N8N_ACTION' && testInputMapping.value.length > 0 && !testRawJsonMode.value) {
      data = { ...testStructuredData.value }
    } else {
      data = JSON.parse(testData.value)
    }
    const res = await functionUnitApi.testAction?.(props.functionUnitId, selectedAction.value.id, data)
    testResult.value = JSON.stringify(res?.data || {}, null, 2)
  } catch (e: any) {
    testResult.value = `Error: ${e.message || t('action.testFailed')}`
  } finally {
    testing.value = false
  }
}

// ===== N8N Action helper methods =====

/** Load N8N connection configs from admin-center */
async function loadN8nConfigs() {
  try {
    n8nConfigList.value = await n8nApi.getConfigs()
  } catch {
    n8nConfigList.value = []
  }
}

/** Load N8N workflows for selected config */
async function loadN8nWorkflows(configId: string) {
  if (!configId) {
    n8nWorkflowList.value = []
    return
  }
  try {
    n8nWorkflowList.value = await n8nApi.getWorkflows(configId)
  } catch {
    n8nWorkflowList.value = []
  }
}

/** Handle N8N config selection change */
function onN8nConfigChange(configId: string) {
  actionConfig.n8nWorkflowId = ''
  actionConfig.webhookUrl = ''
  n8nWorkflowList.value = []
  loadN8nWorkflows(configId)
}

/** Handle N8N workflow selection change - auto-fill webhook URL */
function onN8nWorkflowChange(workflowId: string) {
  const selected = n8nWorkflowList.value.find(wf => wf.id === workflowId)
  if (selected?.webhookUrl) {
    actionConfig.webhookUrl = selected.webhookUrl
  }
}

/** Add input parameter mapping row */
function addN8nInputParam() {
  if (!actionConfig.inputMapping) actionConfig.inputMapping = []
  actionConfig.inputMapping.push({ paramName: '', paramLabel: '', paramType: 'string', required: false })
}

/** Remove input parameter mapping row */
function removeN8nInputParam(index: number) {
  actionConfig.inputMapping.splice(index, 1)
}

/** Add output result mapping row */
function addN8nOutputMapping() {
  if (!actionConfig.outputMapping) actionConfig.outputMapping = []
  actionConfig.outputMapping.push({ source: '', target: '' })
}

/** Remove output result mapping row */
function removeN8nOutputMapping(index: number) {
  actionConfig.outputMapping.splice(index, 1)
}

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

.bound-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
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
