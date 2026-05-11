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
                :label="`${field.description || field.fieldName} (${field.fieldName})`"
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
                  :label="`${field.description || field.fieldName} (${field.fieldName})`"
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
          <div
            v-if="formId"
            class="form-preview-link"
          >
            <el-button
              link
              type="primary"
              size="small"
            >
              {{ t('common.preview') }}
            </el-button>
          </div>
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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import type { FormDefinition, ActionDefinition, TableDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import { adminCenterApi, type BusinessUnitInfo, type RoleInfo } from '@/api/adminCenter'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty
} from '@/utils/bpmnExtensions'
import { countIncomingSequenceFlows } from '@/utils/bpmnAssigneeTopology'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'subTask', 'assignee', 'form', 'actions'])

// Basic properties
const taskName = ref('')
const taskDescription = ref('')

/** 与引擎 {@code AssigneeType.fromCode} / 旧版 BPMN 字符串对齐；BU_UNBOUNDED 仅历史加载 */
type AssigneeTypeEnum =
  | 'INITIATOR'
  | 'ENTITY_MANAGER'
  | 'FUNCTION_MANAGER'
  | 'HIERARCHY_ROLE'
  | 'BU_ROLE'
  | 'MANUAL_ASSIGN'
  | 'ASSIGNEE_FROM_VARIABLE'
  | 'ELEMENT_VARIABLE'
  | 'CURRENT_BU_ROLE'
  | 'CURRENT_PARENT_BU_ROLE'
  | 'INITIATOR_BU_ROLE'
  | 'INITIATOR_PARENT_BU_ROLE'
  | 'FIXED_BU_ROLE'
  | 'BU_UNBOUNDED_ROLE'

// Assignee config
const assigneeType = ref<AssigneeTypeEnum>('INITIATOR')
const lastLoadedAssigneeType = ref<AssigneeTypeEnum>('INITIATOR')
const roleId = ref('')
const businessUnitId = ref('')
const assigneeLabel = ref('')
const candidateUsers = ref('')
const candidateGroups = ref('')
const manualAssignVariable = ref('')
const manualAssignBuVariable = ref('')
const manualAssignRoleVariable = ref('')
const assigneeVariableName = ref('')
const elementSubTableId = ref<number | ''>('')
const elementSubTableName = ref('')
const assigneeField = ref('')
const rowIdVariable = ref('')
const subTables = ref<TableDefinition[]>([])
const loadingSubTables = ref(false)

// Multi-instance sub-process row progress columns (stored on parent SubProcess)
const miTaskStatusField = ref('task_status')
const miTaskCurrentNodeField = ref('task_current_node')
const FIELD_NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/
const miStatusFieldInvalid = computed(() => {
  return !!miTaskStatusField.value && !FIELD_NAME_RE.test(miTaskStatusField.value.trim())
})
const miCurrentNodeFieldInvalid = computed(() => {
  return !!miTaskCurrentNodeField.value && !FIELD_NAME_RE.test(miTaskCurrentNodeField.value.trim())
})

/** 顺序流变化时递增，驱动「单入线」校验刷新 */
const topologyTick = ref(0) // retained for backward compatibility; anchor UI removed

// Business unit and role data
const businessUnits = ref<BusinessUnitInfo[]>([])
const buBoundedRoles = ref<RoleInfo[]>([])
const buUnboundedRoles = ref<RoleInfo[]>([])
const eligibleRoles = ref<RoleInfo[]>([])
const loadingBusinessUnits = ref(false)
const loadingRoles = ref(false)

// Form binding
const formId = ref<number | null>(null)
const forms = ref<FormDefinition[]>([])

// Action binding
const actionIds = ref<number[]>([])
const actions = ref<ActionDefinition[]>([])

// Timeout config
const timeoutEnabled = ref(false)
const timeoutDuration = ref('')
const timeoutAction = ref<'remind' | 'approve' | 'reject'>('remind')

// Multi-instance config
const multiInstance = ref(false)
const sequential = ref(false)
const collection = ref('')
const completionCondition = ref('')

const basicProps = computed(() => getBasicProperties(props.element))

const ROLE_ID_ASSIGNEE_TYPES: AssigneeTypeEnum[] = [
  'HIERARCHY_ROLE',
  'BU_ROLE',
  'CURRENT_BU_ROLE',
  'CURRENT_PARENT_BU_ROLE',
  'INITIATOR_BU_ROLE',
  'INITIATOR_PARENT_BU_ROLE',
  'FIXED_BU_ROLE',
  'BU_UNBOUNDED_ROLE'
]

function assigneeTypeNeedsRoleId(t: AssigneeTypeEnum): boolean {
  return ROLE_ID_ASSIGNEE_TYPES.includes(t)
}

const needsBuForRole = computed(
  () => assigneeType.value === 'FIXED_BU_ROLE' || assigneeType.value === 'BU_ROLE'
)

// Whether role ID is needed
const needsRoleId = computed(() => assigneeTypeNeedsRoleId(assigneeType.value))

// Whether to show role selector
const showRoleSelector = computed(() => {
  return needsRoleId.value
})

// Role selector placeholder
const roleSelectPlaceholder = computed(() => {
  if (needsBuForRole.value && !businessUnitId.value) {
    return t('properties.selectBusinessUnitFirst')
  }
  return t('properties.selectRole')
})

// Whether claim is needed
const needsClaim = computed(() => {
  return [
    'HIERARCHY_ROLE',
    'BU_ROLE',
    'MANUAL_ASSIGN',
    'ASSIGNEE_FROM_VARIABLE',
    'ELEMENT_VARIABLE',
    'CURRENT_BU_ROLE',
    'CURRENT_PARENT_BU_ROLE',
    'INITIATOR_BU_ROLE',
    'INITIATOR_PARENT_BU_ROLE',
    'FIXED_BU_ROLE',
    'BU_UNBOUNDED_ROLE'
  ].includes(assigneeType.value)
})

// Filter roles by assignment type
const filteredRoles = computed(() => {
  if (assigneeType.value === 'BU_UNBOUNDED_ROLE') {
    return buUnboundedRoles.value
  }
  if ((assigneeType.value === 'FIXED_BU_ROLE' || assigneeType.value === 'BU_ROLE') && businessUnitId.value) {
    return eligibleRoles.value
  }
  return buBoundedRoles.value
})

// Role selection tip
const roleSelectTip = computed(() => {
  if (assigneeType.value === 'BU_UNBOUNDED_ROLE') {
    return t('properties.buUnboundedRoleTip')
  }
  if (assigneeType.value === 'FIXED_BU_ROLE' || assigneeType.value === 'BU_ROLE') {
    return t('properties.fixedBuRoleTip')
  }
  return t('properties.buBoundedRoleTip')
})

function loadProperties() {
  if (!props.element) return
  
  // Basic properties
  const basic = getBasicProperties(props.element)
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
    ensureSubTaskAssigneeMode()
    // Read progress columns from parent SubProcess extension properties
    const parent = getParentMiSubProcessElement()
    if (parent) {
      const pExt = getExtensionProperties(parent)
      const rawSt = pExt?.miTaskStatusField
      const rawNd = pExt?.miTaskCurrentNodeField
      miTaskStatusField.value =
        typeof rawSt === 'string' && rawSt.trim() && FIELD_NAME_RE.test(rawSt.trim())
          ? rawSt.trim()
          : 'task_status'
      miTaskCurrentNodeField.value =
        typeof rawNd === 'string' && rawNd.trim() && FIELD_NAME_RE.test(rawNd.trim())
          ? rawNd.trim()
          : 'task_current_node'
    }
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

function updateBasicProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setBasicProperties(props.modeler, props.element, { [name]: value })
}

function updateExtProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setExtensionProperty(props.modeler, props.element, name, value)
}

function ensureSubTaskAssigneeMode() {
  if (!isFirstMultiInstanceSubTask.value || assigneeType.value === 'ELEMENT_VARIABLE') {
    return
  }
  assigneeType.value = 'ELEMENT_VARIABLE'
  lastLoadedAssigneeType.value = 'ELEMENT_VARIABLE'
  updateExtProp('assigneeType', 'ELEMENT_VARIABLE')
  updateExtProp('assigneeLabel', t('properties.elementVariableType'))
}

function handleFormChange(id: number | null) {
  ensureSubTaskAssigneeMode()
  updateExtProp('formId', id)
  const form = forms.value.find(f => f.id === id)
  if (form) {
    updateExtProp('formName', form.formName)
  } else {
    updateExtProp('formName', '')
  }
}

function handleSubTableChange(id: number | '') {
  ensureSubTaskAssigneeMode()
  if (id === '' || id === null || id === undefined) {
    elementSubTableName.value = ''
    assigneeField.value = ''
    rowIdVariable.value = ''
    updateExtProp('subTableId', '')
    updateExtProp('subTableName', '')
    updateExtProp('assigneeField', '')
    updateExtProp('rowIdVariable', '')
    return
  }
  updateExtProp('subTableId', id)
  const table = subTables.value.find(tb => tb.id === id)
  if (table) {
    elementSubTableName.value = table.tableName
    updateExtProp('subTableName', table.tableName)

    // If current assigneeField not in this table's fields, reset it and try to auto-pick a plausible one
    const fieldNames = (table.fieldDefinitions || []).map(fd => fd.fieldName)
    if (!assigneeField.value || !fieldNames.includes(assigneeField.value)) {
      const preferred = (table.fieldDefinitions || []).find(fd =>
        /^(assignee|assignee_user_id|user_id|handler|owner_user_id|approver)$/i.test(fd.fieldName)
      )
      assigneeField.value = preferred?.fieldName || ''
      updateExtProp('assigneeField', assigneeField.value)
    }

    // Default rowIdVariable convention uses the element variable (currentItem) of the parent SubProcess
    if (!rowIdVariable.value) {
      rowIdVariable.value = 'currentItem.rowId'
      updateExtProp('rowIdVariable', rowIdVariable.value)
    }
  }
}

function handleAssigneeFieldChange(value: string) {
  ensureSubTaskAssigneeMode()
  updateExtProp('assigneeField', value || '')
}

const assigneeFieldOptions = computed(() => {
  const table = subTables.value.find(tb => tb.id === elementSubTableId.value)
  return table?.fieldDefinitions || []
})

const miProgressFieldOptions = computed(() => {
  const base = ['task_status', 'task_current_node']
  const fromTable = (assigneeFieldOptions.value || []).map((f: any) => f.fieldName).filter(Boolean)
  const seen = new Set<string>()
  const merged: string[] = []
  for (const n of [...base, ...fromTable]) {
    const key = String(n || '').trim()
    if (!key || seen.has(key)) continue
    seen.add(key)
    merged.push(key)
  }
  return merged
})

const assigneeFieldPlaceholder = computed(() => {
  if (!elementSubTableId.value) return t('properties.selectSubTableFirst')
  return t('properties.selectAssigneeField')
})

const parentIsMultiInstanceSubProcess = computed(() => {
  const parent = (props.element as any)?.parent
  const parentBo = parent?.businessObject
  if (!parentBo) return false
  if (parentBo.$type !== 'bpmn:SubProcess') return false
  return !!parentBo.loopCharacteristics
})

function getParentMiSubProcessElement(): any | null {
  const parent = (props.element as any)?.parent
  if (!parent) return null
  const bo = parent?.businessObject
  if (!bo || bo.$type !== 'bpmn:SubProcess' || !bo.loopCharacteristics) return null
  return parent
}

function persistMiProgressFieldProps() {
  if (!props.modeler || !props.element) return
  const parent = getParentMiSubProcessElement()
  if (!parent) return
  const st = (miTaskStatusField.value || 'task_status').trim()
  const nd = (miTaskCurrentNodeField.value || 'task_current_node').trim()
  if (FIELD_NAME_RE.test(st)) {
    setExtensionProperty(props.modeler, parent, 'miTaskStatusField', st)
  }
  if (FIELD_NAME_RE.test(nd)) {
    setExtensionProperty(props.modeler, parent, 'miTaskCurrentNodeField', nd)
  }
}

function handleMiTaskStatusFieldChange() {
  miTaskStatusField.value = miTaskStatusField.value.trim()
  if (miStatusFieldInvalid.value) return
  persistMiProgressFieldProps()
}

function handleMiTaskCurrentNodeFieldChange() {
  miTaskCurrentNodeField.value = miTaskCurrentNodeField.value.trim()
  if (miCurrentNodeFieldInvalid.value) return
  persistMiProgressFieldProps()
}

function getElementRefId(ref: any): string {
  return typeof ref === 'string' ? ref : (ref?.id || '')
}

function findFirstUserTaskInSubProcess(parentBo: any): any {
  const flowElements: any[] = parentBo?.flowElements || []
  const byId = new Map(flowElements.filter(fe => fe?.id).map(fe => [fe.id, fe]))
  const sequenceFlows = flowElements.filter(fe => fe?.$type === 'bpmn:SequenceFlow')
  const outgoingBySource = new Map<string, string[]>()

  for (const flow of sequenceFlows) {
    const sourceId = getElementRefId(flow.sourceRef)
    const targetId = getElementRefId(flow.targetRef)
    if (!sourceId || !targetId) continue
    const outgoing = outgoingBySource.get(sourceId) || []
    outgoing.push(targetId)
    outgoingBySource.set(sourceId, outgoing)
  }

  const startIds = flowElements
    .filter(fe => fe?.$type === 'bpmn:StartEvent')
    .map(fe => fe.id)
    .filter(Boolean)

  const queue = [...startIds]
  const visited = new Set<string>()
  while (queue.length > 0) {
    const id = queue.shift()
    if (!id || visited.has(id)) continue
    visited.add(id)

    for (const targetId of outgoingBySource.get(id) || []) {
      const target = byId.get(targetId)
      if (target?.$type === 'bpmn:UserTask') {
        return target
      }
      queue.push(targetId)
    }
  }

  return flowElements.find(fe => fe?.$type === 'bpmn:UserTask')
}

const isFirstMultiInstanceSubTask = computed(() => {
  if (!parentIsMultiInstanceSubProcess.value) return false
  const parentBo = (props.element as any)?.parent?.businessObject
  const firstUserTask = findFirstUserTaskInSubProcess(parentBo)
  const currentId = props.element?.businessObject?.id || props.element?.id
  return !!firstUserTask && firstUserTask.id === currentId
})

function handleAssigneeTypeChange(type: AssigneeTypeEnum) {
  const prev = lastLoadedAssigneeType.value
  lastLoadedAssigneeType.value = type
  updateExtProp('assigneeType', type)

  const labelMap: Record<AssigneeTypeEnum, string> = {
    INITIATOR: t('properties.initiator'),
    ENTITY_MANAGER: t('properties.entityManager'),
    FUNCTION_MANAGER: t('properties.functionManager'),
    HIERARCHY_ROLE: '',
    BU_ROLE: '',
    MANUAL_ASSIGN: '',
    ASSIGNEE_FROM_VARIABLE: '',
    ELEMENT_VARIABLE: '',
    CURRENT_BU_ROLE: '',
    CURRENT_PARENT_BU_ROLE: '',
    INITIATOR_BU_ROLE: '',
    INITIATOR_PARENT_BU_ROLE: '',
    FIXED_BU_ROLE: '',
    BU_UNBOUNDED_ROLE: ''
  }

  roleId.value = ''
  businessUnitId.value = ''
  updateExtProp('roleId', '')
  updateExtProp('businessUnitId', '')

  if (!assigneeTypeNeedsRoleId(type)) {
    assigneeLabel.value = labelMap[type] || ''
    updateExtProp('assigneeLabel', assigneeLabel.value)
  } else {
    assigneeLabel.value = ''
    updateExtProp('assigneeLabel', '')
  }

  if (assigneeTypeNeedsRoleId(type)) {
    loadRoles()
  }

  if (type === 'FIXED_BU_ROLE' || type === 'BU_ROLE') {
    loadBusinessUnits()
  }

  candidateUsers.value = ''
  candidateGroups.value = ''
  updateExtProp('candidateUsers', '')
  updateExtProp('candidateGroups', '')
}

function handleRoleChange(id: string) {
  updateExtProp('roleId', id)
  
  // Update label
  const role = filteredRoles.value.find(r => r.id === id)
  if (role) {
    const typeLabel = getAssigneeTypeLabel(assigneeType.value)
    assigneeLabel.value = `${typeLabel}: ${role.name}`
    updateExtProp('assigneeLabel', assigneeLabel.value)
  }
}

function handleBusinessUnitChange(id: string) {
  updateExtProp('businessUnitId', id)
  
  // Clear role selection
  roleId.value = ''
  updateExtProp('roleId', '')
  
  // Load eligible roles for the business unit
  if (id) {
    loadEligibleRoles(id)
  } else {
    eligibleRoles.value = []
  }
  
  // Update label
  const bu = findBusinessUnitById(businessUnits.value, id)
  if (bu) {
    assigneeLabel.value = bu.name
    updateExtProp('assigneeLabel', assigneeLabel.value)
  }
}

function getAssigneeTypeLabel(type: AssigneeTypeEnum): string {
  const labels: Record<AssigneeTypeEnum, string> = {
    INITIATOR: t('properties.initiator'),
    ENTITY_MANAGER: t('properties.entityManager'),
    FUNCTION_MANAGER: t('properties.functionManager'),
    HIERARCHY_ROLE: t('properties.hierarchyRole'),
    BU_ROLE: t('properties.buRoleConverged'),
    MANUAL_ASSIGN: t('properties.manualAssignType'),
    ASSIGNEE_FROM_VARIABLE: t('properties.assigneeFromVariableType'),
    ELEMENT_VARIABLE: t('properties.elementVariableType'),
    CURRENT_BU_ROLE: t('properties.currentBuRole'),
    CURRENT_PARENT_BU_ROLE: t('properties.currentParentBuRole'),
    INITIATOR_BU_ROLE: t('properties.initiatorBuRoleOption'),
    INITIATOR_PARENT_BU_ROLE: t('properties.initiatorParentBuRole'),
    FIXED_BU_ROLE: t('properties.fixedBuRole'),
    BU_UNBOUNDED_ROLE: t('properties.buUnboundedRole')
  }
  return labels[type] || type
}

// Recursively find business unit
function findBusinessUnitById(units: BusinessUnitInfo[], id: string): BusinessUnitInfo | null {
  for (const unit of units) {
    if (unit.id === id) return unit
    if (unit.children) {
      const found = findBusinessUnitById(unit.children, id)
      if (found) return found
    }
  }
  return null
}

// Load roles
async function loadRoles() {
  loadingRoles.value = true
  try {
    const [bounded, unbounded] = await Promise.all([
      adminCenterApi.getBuBoundedRoles(),
      adminCenterApi.getBuUnboundedRoles()
    ])
    buBoundedRoles.value = bounded || []
    buUnboundedRoles.value = unbounded || []
  } catch (e) {
    console.error('Failed to load roles:', e)
    buBoundedRoles.value = []
    buUnboundedRoles.value = []
  } finally {
    loadingRoles.value = false
  }
}

// Load business units
async function loadBusinessUnits() {
  if (businessUnits.value.length > 0) return
  loadingBusinessUnits.value = true
  try {
    const data = await adminCenterApi.getBusinessUnitTree()
    businessUnits.value = data || []
  } catch (e) {
    console.error('Failed to load business units:', e)
    businessUnits.value = []
  } finally {
    loadingBusinessUnits.value = false
  }
}

// Load eligible roles for business unit
async function loadEligibleRoles(unitId: string) {
  try {
    const data = await adminCenterApi.getBusinessUnitEligibleRoles(unitId)
    eligibleRoles.value = data || []
  } catch (e) {
    console.error('Failed to load eligible roles:', e)
    eligibleRoles.value = []
  }
}

function handleActionsChange(ids: number[]) {
  updateExtProp('actionIds', ids)
  const actionNames = ids.map(id => {
    const action = actions.value.find(a => a.id === id)
    return action?.actionName || ''
  }).filter(Boolean)
  updateExtProp('actionNames', actionNames)
}

const actionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    APPROVE: t('action.approve'),
    REJECT: t('action.reject'),
    TRANSFER: t('action.transfer'),
    DELEGATE: t('action.delegate'),
    ROLLBACK: t('action.rollback'),
    WITHDRAW: t('action.withdraw'),
    SAVE: t('action.saveDraft'),
    PROCESS_SUBMIT: t('action.processSubmit'),
    PROCESS_REJECT: t('action.processReject'),
    COMPOSITE: t('action.composite'),
    API_CALL: t('action.apiCall'),
    FORM_POPUP: t('action.formPopup'),
    CUSTOM_SCRIPT: t('action.customScript')
  }
  return map[type] || type
}

async function loadForms() {
  try {
    const res = await functionUnitApi.getForms(props.functionUnitId)
    forms.value = res.data || []
  } catch {
    forms.value = []
  }
}

async function loadActions() {
  try {
    const res = await functionUnitApi.getActions(props.functionUnitId)
    actions.value = res.data || []
  } catch {
    actions.value = []
  }
}

async function loadSubTables() {
  loadingSubTables.value = true
  try {
    const res = await functionUnitApi.getTables(props.functionUnitId)
    const all = res.data || []
    subTables.value = all.filter(tb => (tb.tableType || '').toUpperCase() === 'SUB')
  } catch {
    subTables.value = []
  } finally {
    loadingSubTables.value = false
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
  
  .form-preview-link {
    margin-top: -8px;
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
