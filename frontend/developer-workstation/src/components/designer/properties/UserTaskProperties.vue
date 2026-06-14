<template>
  <div class="user-task-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item
        :title="t('properties.basic')"
        name="basic"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.taskId')">
            <el-input
              :model-value="basicProps.id"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('properties.taskName')">
            <el-input
              v-model="taskName"
              :placeholder="t('properties.taskName')"
              @change="updateBasicProp('name', taskName)"
            />
          </el-form-item>
          <el-form-item :label="t('properties.taskDescription')">
            <el-input
              v-model="taskDescription"
              type="textarea"
              :rows="2"
              :placeholder="t('properties.taskDescription')"
              @change="updateExtProp('description', taskDescription)"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- Multi-instance sub-task config -->
      <el-collapse-item
        v-if="isFirstMultiInstanceSubTask"
        :title="t('properties.subTaskConfig')"
        name="subTask"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 8px;"
          >
            <template #title>
              {{ t('properties.subTaskConfigHint') }}
            </template>
          </el-alert>

          <el-form-item
            :label="t('properties.subTableIdField')"
            required
          >
            <el-select
              v-model="elementSubTableId"
              :placeholder="t('properties.selectSubTable')"
              :loading="loadingSubTables"
              clearable
              filterable
              style="width: 100%"
              @change="handleSubTableChange"
            >
              <el-option
                v-for="table in subTables"
                :key="table.id"
                :label="`${table.tableDisplayName || table.tableName} (${table.tableName})`"
                :value="table.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item :label="t('properties.subTableNameField')">
            <el-input
              v-model="elementSubTableName"
              disabled
            />
            <div class="form-tip">
              {{ t('properties.subTableNameAutoFilledTip') }}
            </div>
          </el-form-item>

          <el-form-item
            :label="t('properties.assigneeFieldLabel')"
            required
          >
            <el-select
              v-model="assigneeField"
              :placeholder="assigneeFieldPlaceholder"
              :loading="loadingSubTables"
              :disabled="!elementSubTableId"
              clearable
              filterable
              style="width: 100%"
              @change="handleAssigneeFieldChange"
            >
              <el-option
                v-for="field in assigneeFieldOptions"
                :key="field.fieldName"
                :label="`${field.displayName || field.fieldName} (${field.fieldName})`"
                :value="field.fieldName"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.subTaskAssigneeFieldTip') }}
            </div>
          </el-form-item>

          <el-form-item
            :label="t('properties.subTaskForm')"
            required
          >
            <el-select
              v-model="formId"
              :placeholder="t('properties.selectSubTaskForm')"
              clearable
              filterable
              style="width: 100%"
              @change="handleFormChange"
            >
              <el-option
                v-for="form in forms"
                :key="form.id"
                :label="form.formName"
                :value="form.id"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.subTaskFormTip') }}
            </div>
          </el-form-item>

          <el-form-item :label="t('properties.rowIdVariableLabel')">
            <el-input
              v-model="rowIdVariable"
              placeholder="currentItem.rowId"
              @change="updateExtProp('rowIdVariable', rowIdVariable)"
            />
            <div class="form-tip">
              {{ t('properties.rowIdVariableTip') }}
            </div>
          </el-form-item>

          <el-divider content-position="left">
            {{ t('properties.miProgressFieldsDivider') }}
          </el-divider>
          <el-form-item :label="t('properties.miTaskStatusField')">
            <el-select
              v-model="miTaskStatusField"
              filterable
              allow-create
              default-first-option
              clearable
              :placeholder="t('properties.miProgressFieldSelectPlaceholder')"
              style="width: 100%"
              @change="handleMiTaskStatusFieldChange"
            >
              <el-option
                v-for="f in miProgressFieldOptions"
                :key="f"
                :label="f"
                :value="f"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.miTaskStatusFieldTip') }}
            </div>
            <div
              v-if="miStatusFieldInvalid"
              class="form-error"
            >
              {{ t('properties.miProgressFieldInvalid') }}
            </div>
          </el-form-item>
          <el-form-item :label="t('properties.miTaskCurrentNodeField')">
            <el-select
              v-model="miTaskCurrentNodeField"
              filterable
              allow-create
              default-first-option
              clearable
              :placeholder="t('properties.miProgressFieldSelectPlaceholder')"
              style="width: 100%"
              @change="handleMiTaskCurrentNodeFieldChange"
            >
              <el-option
                v-for="f in miProgressFieldOptions"
                :key="f"
                :label="f"
                :value="f"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.miTaskCurrentNodeFieldTip') }}
            </div>
            <div
              v-if="miCurrentNodeFieldInvalid"
              class="form-error"
            >
              {{ t('properties.miProgressFieldInvalid') }}
            </div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Assignee config -->
      <el-collapse-item
        v-if="!isFirstMultiInstanceSubTask"
        :title="t('properties.assigneeConfig')"
        name="assignee"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.assigneeType')">
            <el-select
              v-model="assigneeType"
              @change="handleAssigneeTypeChange"
            >
              <el-option-group :label="t('properties.directAssignment')">
                <el-option
                  :label="t('properties.initiator')"
                  value="INITIATOR"
                />
                <el-option
                  :label="t('properties.entityManager')"
                  value="ENTITY_MANAGER"
                />
                <el-option
                  :label="t('properties.functionManager')"
                  value="FUNCTION_MANAGER"
                />
              </el-option-group>
              <el-option-group :label="t('properties.convergedAssignee')">
                <el-option
                  :label="t('properties.hierarchyRole')"
                  value="HIERARCHY_ROLE"
                />
                <el-option
                  :label="t('properties.buRoleConverged')"
                  value="BU_ROLE"
                />
                <el-option
                  :label="t('properties.manualAssignType')"
                  value="MANUAL_ASSIGN"
                />
                <el-option
                  :label="t('properties.assigneeFromVariableType')"
                  value="ASSIGNEE_FROM_VARIABLE"
                />
                <el-option
                  :label="t('properties.elementVariableType')"
                  value="ELEMENT_VARIABLE"
                />
              </el-option-group>
              <el-option-group :label="t('properties.legacyBpmnAssignee')">
                <el-option
                  :label="t('properties.currentBuRole')"
                  value="CURRENT_BU_ROLE"
                />
                <el-option
                  :label="t('properties.currentParentBuRole')"
                  value="CURRENT_PARENT_BU_ROLE"
                />
                <el-option
                  :label="t('properties.initiatorBuRoleOption')"
                  value="INITIATOR_BU_ROLE"
                />
                <el-option
                  :label="t('properties.initiatorParentBuRole')"
                  value="INITIATOR_PARENT_BU_ROLE"
                />
                <el-option
                  :label="t('properties.fixedBuRole')"
                  value="FIXED_BU_ROLE"
                />
              </el-option-group>
            </el-select>
          </el-form-item>

          <div
            v-if="assigneeType === 'BU_UNBOUNDED_ROLE'"
            class="claim-tip"
          >
            <el-alert
              type="warning"
              :closable="false"
              show-icon
            >
              <template #title>
                {{ t('properties.buUnboundedDeprecated') }}
              </template>
            </el-alert>
          </div>
          
          <!-- Display current assignment label -->
          <div
            v-if="assigneeLabel"
            class="assignee-label"
          >
            <el-tag
              type="info"
              size="small"
            >
              {{ assigneeLabel }}
            </el-tag>
          </div>
          
          <!-- Business unit selector (FIXED_BU_ROLE / BU_ROLE) -->
          <el-form-item
            v-if="needsBuForRole"
            :label="t('properties.selectBusinessUnit')"
          >
            <el-tree-select
              v-model="businessUnitId"
              :data="businessUnits"
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              :loading="loadingBusinessUnits"
              :placeholder="t('properties.selectBusinessUnit')"
              check-strictly
              filterable
              @change="handleBusinessUnitChange"
            />
            <div class="form-tip">
              {{ t('properties.selectBusinessUnitTip') }}
            </div>
          </el-form-item>
          
          <!-- Role selector (required for 6 role types) -->
          <!-- FIXED_BU_ROLE requires selecting business unit first before selecting role -->
          <el-form-item
            v-if="showRoleSelector"
            :label="t('properties.selectRole')"
          >
            <el-select
              v-model="roleId"
              :loading="loadingRoles"
              :placeholder="roleSelectPlaceholder"
              :disabled="needsBuForRole && !businessUnitId"
              filterable
              @change="handleRoleChange"
            >
              <el-option
                v-for="role in filteredRoles"
                :key="role.id"
                :label="role.name"
                :value="role.id"
              >
                <span>{{ role.name }}</span>
                <span style="color: #909399; margin-left: 8px;">({{ role.code }})</span>
              </el-option>
            </el-select>
            <div class="form-tip">
              {{ roleSelectTip }}
            </div>
          </el-form-item>

          <template v-if="assigneeType === 'MANUAL_ASSIGN'">
            <el-form-item :label="t('properties.manualAssignVariable')">
              <el-input
                v-model="manualAssignVariable"
                :placeholder="t('properties.manualAssignVariableHint')"
                @change="updateExtProp('manualAssignVariable', manualAssignVariable)"
              />
            </el-form-item>
            <el-form-item :label="t('properties.manualAssignBuVariable')">
              <el-input
                v-model="manualAssignBuVariable"
                @change="updateExtProp('manualAssignBuVariable', manualAssignBuVariable)"
              />
            </el-form-item>
            <el-form-item :label="t('properties.manualAssignRoleVariable')">
              <el-input
                v-model="manualAssignRoleVariable"
                @change="updateExtProp('manualAssignRoleVariable', manualAssignRoleVariable)"
              />
            </el-form-item>
          </template>

          <el-form-item
            v-if="assigneeType === 'ASSIGNEE_FROM_VARIABLE'"
            :label="t('properties.assigneeVariableField')"
          >
            <el-input
              v-model="assigneeVariableName"
              :placeholder="t('properties.assigneeVariableHint')"
              @change="updateExtProp('assigneeVariable', assigneeVariableName)"
            />
          </el-form-item>

          <template v-if="assigneeType === 'ELEMENT_VARIABLE'">
            <el-form-item :label="t('properties.subTableIdField')">
              <el-select
                v-model="elementSubTableId"
                :placeholder="t('properties.selectSubTable')"
                :loading="loadingSubTables"
                clearable
                filterable
                style="width: 100%"
                @change="handleSubTableChange"
              >
                <el-option
                  v-for="table in subTables"
                  :key="table.id"
                  :label="`${table.tableDisplayName || table.tableName} (${table.tableName})`"
                  :value="table.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('properties.subTableNameField')">
              <el-input
                v-model="elementSubTableName"
                disabled
              />
              <div class="form-tip">
                {{ t('properties.subTableNameAutoFilledTip') }}
              </div>
            </el-form-item>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 8px;"
            >
              <template #title>
                {{ t('properties.elementVariableRuntimeHint') }}
              </template>
            </el-alert>
            <el-form-item
              :label="t('properties.assigneeFieldLabel')"
              required
            >
              <el-select
                v-model="assigneeField"
                :placeholder="assigneeFieldPlaceholder"
                :loading="loadingSubTables"
                :disabled="!elementSubTableId"
                clearable
                filterable
                style="width: 100%"
                @change="handleAssigneeFieldChange"
              >
                <el-option
                  v-for="field in assigneeFieldOptions"
                  :key="field.fieldName"
                  :label="`${field.displayName || field.fieldName} (${field.fieldName})`"
                  :value="field.fieldName"
                />
              </el-select>
              <div class="form-tip">
                {{ t('properties.assigneeFieldTip') }}
              </div>
            </el-form-item>
            <el-form-item :label="t('properties.rowIdVariableLabel')">
              <el-input
                v-model="rowIdVariable"
                placeholder="currentItem.rowId"
                @change="updateExtProp('rowIdVariable', rowIdVariable)"
              />
              <div class="form-tip">
                {{ t('properties.rowIdVariableTip') }}
              </div>
            </el-form-item>
          </template>
          
          <!-- Claim type tip -->
          <div
            v-if="needsClaim"
            class="claim-tip"
          >
            <el-alert
              type="info"
              :closable="false"
              show-icon
            >
              <template #title>
                {{ t('properties.claimRequired') }}
              </template>
            </el-alert>
          </div>
          
          <el-form-item :label="t('properties.candidateUsers')">
            <el-input
              v-model="candidateUsers"
              :placeholder="t('properties.candidateUsersPlaceholder')"
              @change="updateExtProp('candidateUsers', candidateUsers)"
            />
          </el-form-item>
          
          <el-form-item :label="t('properties.candidateGroups')">
            <el-input
              v-model="candidateGroups"
              :placeholder="t('properties.candidateGroupsPlaceholder')"
              @change="updateExtProp('candidateGroups', candidateGroups)"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Form binding -->
      <el-collapse-item
        v-if="!isFirstMultiInstanceSubTask"
        :title="t('properties.form')"
        name="form"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.bindForm')">
            <el-select
              v-model="formId"
              :placeholder="t('properties.selectForm')"
              clearable
              @change="handleFormChange"
            >
              <el-option
                v-for="form in forms"
                :key="form.id"
                :label="form.formName"
                :value="form.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Action binding -->
      <el-collapse-item
        :title="t('properties.actions')"
        name="actions"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.availableActions')">
            <el-select
              v-model="actionIds"
              :placeholder="t('properties.selectActions')"
              multiple
              clearable
              @change="handleActionsChange"
            >
              <el-option
                v-for="action in actions"
                :key="action.id"
                :label="action.actionName"
                :value="action.id"
              >
                <span>{{ action.actionName }}</span>
                <el-tag
                  size="small"
                  style="margin-left: 8px;"
                >
                  {{ actionTypeLabel(action.actionType) }}
                </el-tag>
              </el-option>
            </el-select>
          </el-form-item>
          <div
            v-if="actionIds.length > 0"
            class="selected-actions"
          >
            <div class="form-tip">
              {{ actionIds.length }}
            </div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Timeout config -->
      <el-collapse-item
        :title="t('properties.timeout')"
        name="timeout"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.enableTimeout')">
            <el-switch
              v-model="timeoutEnabled"
              @change="updateExtProp('timeoutEnabled', timeoutEnabled)"
            />
          </el-form-item>
          
          <template v-if="timeoutEnabled">
            <el-form-item :label="t('properties.timeoutDuration')">
              <el-input
                v-model="timeoutDuration"
                :placeholder="t('properties.timeoutDurationPlaceholder')"
                @change="updateExtProp('timeoutDuration', timeoutDuration)"
              />
              <div class="form-tip">
                {{ t('properties.timeoutDurationHint') }}
              </div>
            </el-form-item>
            
            <el-form-item :label="t('properties.timeoutAction')">
              <el-select
                v-model="timeoutAction"
                @change="updateExtProp('timeoutAction', timeoutAction)"
              >
                <el-option
                  :label="t('properties.notify')"
                  value="remind"
                />
                <el-option
                  :label="t('properties.autoComplete')"
                  value="approve"
                />
                <el-option
                  :label="t('properties.autoComplete')"
                  value="reject"
                />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
      
      <!-- Multi-instance config -->
      <el-collapse-item
        v-if="!parentIsMultiInstanceSubProcess"
        :title="t('properties.multiInstanceConfig')"
        name="multiInstance"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-alert
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>
              {{ t('properties.userTaskMultiInstanceHint') }}
            </template>
          </el-alert>
          <el-form-item
            :label="t('properties.enableMultiInstance')"
            style="margin-top: 8px;"
          >
            <el-switch
              v-model="multiInstance"
              @change="updateExtProp('multiInstance', multiInstance)"
            />
          </el-form-item>
          
          <template v-if="multiInstance">
            <el-form-item :label="t('properties.executionMode')">
              <el-radio-group
                v-model="sequential"
                @change="updateExtProp('sequential', sequential)"
              >
                <el-radio :value="false">
                  {{ t('properties.parallelMode') }}
                </el-radio>
                <el-radio :value="true">
                  {{ t('properties.sequentialMode') }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item :label="t('properties.collectionVariable')">
              <el-input
                v-model="collection"
                :placeholder="t('properties.collectionVariablePlaceholder')"
                @change="updateExtProp('collection', collection)"
              />
              <div class="form-tip">
                {{ t('properties.collectionVariableTip') }}
              </div>
            </el-form-item>
            
            <el-form-item :label="t('properties.completionCondition')">
              <el-input
                v-model="completionCondition"
                placeholder="${nrOfCompletedInstances/nrOfInstances >= 0.5}"
                @change="updateExtProp('completionCondition', completionCondition)"
              />
              <div class="form-tip">
                {{ t('properties.completionConditionTip') }}
              </div>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>


<script setup lang="ts">
/**
 * UserTask 节点属性面板。
 *
 * 本 SFC 为精简编排器：响应式状态与各职责逻辑已抽到
 * `@/composables/userTaskProperties/*`。此处仅做组装、加载编排与生命周期绑定，
 * 模板/样式与拆分前逐字节一致，emit/props/i18n key/行为均零变化。
 */
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import type { AssigneeTypeEnum } from '@/composables/userTaskProperties/types'
import { useUserTaskState } from '@/composables/userTaskProperties/useUserTaskState'
import { useUserTaskAssignee } from '@/composables/userTaskProperties/useUserTaskAssignee'
import { useUserTaskMultiInstance } from '@/composables/userTaskProperties/useUserTaskMultiInstance'
import { useUserTaskActions } from '@/composables/userTaskProperties/useUserTaskActions'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'subTask', 'assignee', 'form', 'actions'])

// 以 reactive 适配器透传 props，使 composable 读取 props.element/modeler 时保持响应性
const propsAccessor = reactive({
  get modeler() {
    return props.modeler
  },
  get element() {
    return props.element
  },
  get functionUnitId() {
    return props.functionUnitId
  }
})

// 共享状态（全部顶层 ref/computed + updateBasicProp/updateExtProp）
const ctx = useUserTaskState(propsAccessor, t)
const {
  taskName,
  taskDescription,
  assigneeType,
  lastLoadedAssigneeType,
  roleId,
  businessUnitId,
  assigneeLabel,
  candidateUsers,
  candidateGroups,
  manualAssignVariable,
  manualAssignBuVariable,
  manualAssignRoleVariable,
  assigneeVariableName,
  elementSubTableId,
  elementSubTableName,
  assigneeField,
  rowIdVariable,
  subTables,
  loadingSubTables,
  miTaskStatusField,
  miTaskCurrentNodeField,
  miStatusFieldInvalid,
  miCurrentNodeFieldInvalid,
  businessUnits,
  loadingBusinessUnits,
  loadingRoles,
  formId,
  forms,
  actionIds,
  actions,
  timeoutEnabled,
  timeoutDuration,
  timeoutAction,
  multiInstance,
  sequential,
  collection,
  completionCondition,
  basicProps,
  updateBasicProp,
  updateExtProp
} = ctx

// Assignee config 逻辑
const {
  needsBuForRole,
  needsRoleId,
  showRoleSelector,
  roleSelectPlaceholder,
  needsClaim,
  filteredRoles,
  roleSelectTip,
  handleAssigneeTypeChange,
  handleRoleChange,
  handleBusinessUnitChange,
  loadRoles,
  loadBusinessUnits,
  loadEligibleRoles
} = useUserTaskAssignee(ctx)

// 多实例子任务 / element-variable 逻辑
const {
  handleFormChange,
  handleSubTableChange,
  handleAssigneeFieldChange,
  assigneeFieldOptions,
  miProgressFieldOptions,
  assigneeFieldPlaceholder,
  parentIsMultiInstanceSubProcess,
  handleMiTaskStatusFieldChange,
  handleMiTaskCurrentNodeFieldChange,
  isFirstMultiInstanceSubTask,
  loadSubTables,
  loadSubTaskMiProgressFields
} = useUserTaskMultiInstance(propsAccessor, ctx)

// 动作 / 表单加载逻辑
const {
  handleActionsChange,
  actionTypeLabel,
  loadForms,
  loadActions
} = useUserTaskActions(propsAccessor, ctx)

/** 顺序流变化时递增，驱动「单入线」校验刷新 */
const topologyTick = ctx.topologyTick // retained for backward compatibility; anchor UI removed

function loadProperties() {
  if (!props.element) return
  
  // Basic properties
  const basic = basicProps.value
  taskName.value = basic.name
  
  // Extension properties
  const ext = getExtensionProperties(props.element)
  taskDescription.value = ext.description || ''
  let rawType = (ext.assigneeType || 'INITIATOR') as string
  if (rawType === 'PROCESS_INITIATOR') {
    rawType = 'INITIATOR'
  }
  assigneeType.value = (rawType as AssigneeTypeEnum) || 'INITIATOR'
  lastLoadedAssigneeType.value = assigneeType.value
  if (!ext.assigneeType) {
    updateExtProp('assigneeType', assigneeType.value)
    if (assigneeType.value === 'INITIATOR') {
      updateExtProp('assigneeLabel', t('properties.initiator'))
    }
  }
  roleId.value = ext.roleId || ''
  businessUnitId.value = ext.businessUnitId || ''
  assigneeLabel.value = ext.assigneeLabel || ''
  candidateUsers.value = ext.candidateUsers || ''
  candidateGroups.value = ext.candidateGroups || ''
  manualAssignVariable.value = ext.manualAssignVariable || ''
  manualAssignBuVariable.value = ext.manualAssignBuVariable || ''
  manualAssignRoleVariable.value = ext.manualAssignRoleVariable || ''
  assigneeVariableName.value = ext.assigneeVariable || ''
  const rawSubTableId = ext.subTableId
  elementSubTableId.value =
    typeof rawSubTableId === 'number'
      ? rawSubTableId
      : (rawSubTableId ? Number(rawSubTableId) || '' : '')
  elementSubTableName.value = ext.subTableName || ''
  assigneeField.value = ext.assigneeField || ''
  rowIdVariable.value = ext.rowIdVariable || ''
  formId.value = ext.formId || null
  actionIds.value = ext.actionIds || []
  timeoutEnabled.value = ext.timeoutEnabled || false
  timeoutDuration.value = ext.timeoutDuration || ''
  timeoutAction.value = ext.timeoutAction || 'remind'
  multiInstance.value = ext.multiInstance || false
  sequential.value = ext.sequential || false
  collection.value = ext.collection || ''
  completionCondition.value = ext.completionCondition || ''

  if (isFirstMultiInstanceSubTask.value) {
    loadSubTaskMiProgressFields()
  }

  // Load data based on assignment type
  if (needsRoleId.value) {
    loadRoles()
  }
  if (needsBuForRole.value) {
    loadBusinessUnits()
    if (businessUnitId.value) {
      loadEligibleRoles(businessUnitId.value)
    }
  }
}

watch(() => props.element, loadProperties, { immediate: true })

function bumpTopologyTick() {
  topologyTick.value++
}

onMounted(() => {
  props.modeler.on('commandStack.changed', bumpTopologyTick)
  loadProperties()
  loadForms()
  loadActions()
  loadSubTables()
})

onUnmounted(() => {
  props.modeler.off('commandStack.changed', bumpTopologyTick)
})
</script>

<style lang="scss" scoped>
.user-task-properties {
  :deep(.el-collapse) {
    border: none;
    
    .el-collapse-item__header {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      background: #fafafa;
      padding: 0 12px;
      height: 36px;
      line-height: 36px;
      border-radius: 4px;
      margin-bottom: 8px;
      
      &:hover {
        background: #f0f0f0;
      }
    }
    
    .el-collapse-item__wrap {
      border: none;
    }
    
    .el-collapse-item__content {
      padding: 0 4px 12px;
    }
  }
  
  :deep(.el-form-item) {
    margin-bottom: 12px;
    
    .el-form-item__label {
      font-size: 12px;
      color: #606266;
      padding-bottom: 4px;
    }
  }
  
  .form-tip {
    font-size: 11px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }
  
  .assignee-label {
    margin-bottom: 12px;
  }
  
  .selected-actions {
    margin-top: -8px;
  }
  
  .claim-tip {
    margin-bottom: 12px;
    
    :deep(.el-alert) {
      padding: 8px 12px;
      
      .el-alert__title {
        font-size: 12px;
      }
    }
  }
}
</style>
