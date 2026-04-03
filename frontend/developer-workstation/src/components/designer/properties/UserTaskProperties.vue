<template>
  <div class="user-task-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item :title="t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.taskId')">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.taskName')">
            <el-input v-model="taskName" @change="updateBasicProp('name', taskName)" :placeholder="t('properties.taskName')" />
          </el-form-item>
          <el-form-item :label="t('properties.taskDescription')">
            <el-input v-model="taskDescription" type="textarea" :rows="2" @change="updateExtProp('description', taskDescription)" :placeholder="t('properties.taskDescription')" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Assignee config -->
      <el-collapse-item :title="t('properties.assigneeConfig')" name="assignee">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.assigneeType')">
            <el-select v-model="assigneeType" @change="handleAssigneeTypeChange">
              <el-option-group :label="t('properties.directAssignment')">
                <el-option :label="t('properties.initiator')" value="INITIATOR" />
                <el-option :label="t('properties.entityManager')" value="ENTITY_MANAGER" />
                <el-option :label="t('properties.functionManager')" value="FUNCTION_MANAGER" />
              </el-option-group>
              <el-option-group :label="t('properties.convergedAssignee')">
                <el-option :label="t('properties.hierarchyRole')" value="HIERARCHY_ROLE" />
                <el-option :label="t('properties.buRoleConverged')" value="BU_ROLE" />
                <el-option :label="t('properties.manualAssignType')" value="MANUAL_ASSIGN" />
                <el-option :label="t('properties.assigneeFromVariableType')" value="ASSIGNEE_FROM_VARIABLE" />
                <el-option :label="t('properties.elementVariableType')" value="ELEMENT_VARIABLE" />
              </el-option-group>
              <el-option-group :label="t('properties.legacyBpmnAssignee')">
                <el-option :label="t('properties.currentBuRole')" value="CURRENT_BU_ROLE" />
                <el-option :label="t('properties.currentParentBuRole')" value="CURRENT_PARENT_BU_ROLE" />
                <el-option :label="t('properties.initiatorBuRoleOption')" value="INITIATOR_BU_ROLE" />
                <el-option :label="t('properties.initiatorParentBuRole')" value="INITIATOR_PARENT_BU_ROLE" />
                <el-option :label="t('properties.fixedBuRole')" value="FIXED_BU_ROLE" />
              </el-option-group>
            </el-select>
          </el-form-item>

          <div v-if="assigneeType === 'BU_UNBOUNDED_ROLE'" class="claim-tip">
            <el-alert type="warning" :closable="false" show-icon>
              <template #title>{{ t('properties.buUnboundedDeprecated') }}</template>
            </el-alert>
          </div>

          <el-form-item v-if="showAssigneeAnchor" :label="t('properties.assigneeAnchor')">
            <el-select v-model="assigneeAnchor" @change="onAssigneeAnchorChange">
              <el-option :label="t('properties.anchorInitiator')" value="INITIATOR" />
              <el-option :label="t('properties.anchorLastCompleter')" value="LAST_TASK_ASSIGNEE" />
            </el-select>
            <div v-if="assigneeAnchor === 'LAST_TASK_ASSIGNEE'" class="form-tip">{{ t('properties.hierarchyRoleAnchorTip') }}</div>
          </el-form-item>

          <div v-if="lastTaskAnchorTopologyInvalid" class="claim-tip">
            <el-alert type="error" :closable="false" show-icon>
              <template #title>{{ t('properties.lastTaskAnchorIncomingBad', { count: incomingSequenceFlowCount }) }}</template>
            </el-alert>
          </div>
          
          <!-- Display current assignment label -->
          <div v-if="assigneeLabel" class="assignee-label">
            <el-tag type="info" size="small">{{ assigneeLabel }}</el-tag>
          </div>
          
          <!-- Business unit selector (FIXED_BU_ROLE / BU_ROLE) -->
          <el-form-item v-if="needsBuForRole" :label="t('properties.selectBusinessUnit')">
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
            <div class="form-tip">{{ t('properties.selectBusinessUnitTip') }}</div>
          </el-form-item>
          
          <!-- Role selector (required for 6 role types) -->
          <!-- FIXED_BU_ROLE requires selecting business unit first before selecting role -->
          <el-form-item v-if="showRoleSelector" :label="t('properties.selectRole')">
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
            <div class="form-tip">{{ roleSelectTip }}</div>
          </el-form-item>

          <template v-if="assigneeType === 'MANUAL_ASSIGN'">
            <el-form-item :label="t('properties.manualAssignVariable')">
              <el-input
                v-model="manualAssignVariable"
                @change="updateExtProp('manualAssignVariable', manualAssignVariable)"
                :placeholder="t('properties.manualAssignVariableHint')"
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

          <el-form-item v-if="assigneeType === 'ASSIGNEE_FROM_VARIABLE'" :label="t('properties.assigneeVariableField')">
            <el-input
              v-model="assigneeVariableName"
              @change="updateExtProp('assigneeVariable', assigneeVariableName)"
              :placeholder="t('properties.assigneeVariableHint')"
            />
          </el-form-item>

          <template v-if="assigneeType === 'ELEMENT_VARIABLE'">
            <el-form-item :label="t('properties.subTableIdField')">
              <el-input v-model="elementSubTableId" @change="updateExtProp('subTableId', elementSubTableId)" />
            </el-form-item>
            <el-form-item :label="t('properties.subTableNameField')">
              <el-input v-model="elementSubTableName" @change="updateExtProp('subTableName', elementSubTableName)" />
            </el-form-item>
          </template>
          
          <!-- Claim type tip -->
          <div v-if="needsClaim" class="claim-tip">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                {{ t('properties.claimRequired') }}
              </template>
            </el-alert>
          </div>
          
          <el-form-item :label="t('properties.candidateUsers')">
            <el-input v-model="candidateUsers" @change="updateExtProp('candidateUsers', candidateUsers)" :placeholder="t('properties.candidateUsersPlaceholder')" />
          </el-form-item>
          
          <el-form-item :label="t('properties.candidateGroups')">
            <el-input v-model="candidateGroups" @change="updateExtProp('candidateGroups', candidateGroups)" :placeholder="t('properties.candidateGroupsPlaceholder')" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Form binding -->
      <el-collapse-item :title="t('properties.form')" name="form">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.bindForm')">
            <el-select v-model="formId" @change="handleFormChange" :placeholder="t('properties.selectForm')" clearable>
              <el-option v-for="form in forms" :key="form.id" :label="form.formName" :value="form.id" />
            </el-select>
          </el-form-item>
          <div v-if="formId" class="form-preview-link">
            <el-button link type="primary" size="small">{{ t('common.preview') }}</el-button>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Action binding -->
      <el-collapse-item :title="t('properties.actions')" name="actions">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.availableActions')">
            <el-select v-model="actionIds" @change="handleActionsChange" :placeholder="t('properties.selectActions')" multiple clearable>
              <el-option v-for="action in actions" :key="action.id" :label="action.actionName" :value="action.id">
                <span>{{ action.actionName }}</span>
                <el-tag size="small" style="margin-left: 8px;">{{ actionTypeLabel(action.actionType) }}</el-tag>
              </el-option>
            </el-select>
          </el-form-item>
          <div v-if="actionIds.length > 0" class="selected-actions">
            <div class="form-tip">{{ actionIds.length }}</div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Timeout config -->
      <el-collapse-item :title="t('properties.timeout')" name="timeout">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.enableTimeout')">
            <el-switch v-model="timeoutEnabled" @change="updateExtProp('timeoutEnabled', timeoutEnabled)" />
          </el-form-item>
          
          <template v-if="timeoutEnabled">
            <el-form-item :label="t('properties.timeoutDuration')">
              <el-input v-model="timeoutDuration" @change="updateExtProp('timeoutDuration', timeoutDuration)" :placeholder="t('properties.timeoutDurationPlaceholder')" />
              <div class="form-tip">{{ t('properties.timeoutDurationHint') }}</div>
            </el-form-item>
            
            <el-form-item :label="t('properties.timeoutAction')">
              <el-select v-model="timeoutAction" @change="updateExtProp('timeoutAction', timeoutAction)">
                <el-option :label="t('properties.notify')" value="remind" />
                <el-option :label="t('properties.autoComplete')" value="approve" />
                <el-option :label="t('properties.autoComplete')" value="reject" />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
      
      <!-- Multi-instance config -->
      <el-collapse-item :title="t('properties.multiInstanceConfig')" name="multiInstance">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.enableMultiInstance')">
            <el-switch v-model="multiInstance" @change="updateExtProp('multiInstance', multiInstance)" />
          </el-form-item>
          
          <template v-if="multiInstance">
            <el-form-item :label="t('properties.executionMode')">
              <el-radio-group v-model="sequential" @change="updateExtProp('sequential', sequential)">
                <el-radio :value="false">{{ t('properties.parallelMode') }}</el-radio>
                <el-radio :value="true">{{ t('properties.sequentialMode') }}</el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item :label="t('properties.collectionVariable')">
              <el-input v-model="collection" @change="updateExtProp('collection', collection)" :placeholder="t('properties.collectionVariablePlaceholder')" />
              <div class="form-tip">{{ t('properties.collectionVariableTip') }}</div>
            </el-form-item>
            
            <el-form-item :label="t('properties.completionCondition')">
              <el-input v-model="completionCondition" @change="updateExtProp('completionCondition', completionCondition)" placeholder="${nrOfCompletedInstances/nrOfInstances >= 0.5}" />
              <div class="form-tip">{{ t('properties.completionConditionTip') }}</div>
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
import type { FormDefinition, ActionDefinition } from '@/api/functionUnit'
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

const activeGroups = ref(['basic', 'assignee', 'form', 'actions'])

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
const assigneeAnchor = ref<'INITIATOR' | 'LAST_TASK_ASSIGNEE'>('INITIATOR')
const roleId = ref('')
const businessUnitId = ref('')
const assigneeLabel = ref('')
const candidateUsers = ref('')
const candidateGroups = ref('')
const manualAssignVariable = ref('')
const manualAssignBuVariable = ref('')
const manualAssignRoleVariable = ref('')
const assigneeVariableName = ref('')
const elementSubTableId = ref('')
const elementSubTableName = ref('')

/** 顺序流变化时递增，驱动「单入线」校验刷新 */
const topologyTick = ref(0)

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

const ANCHORED_ASSIGNEE_TYPES: AssigneeTypeEnum[] = ['HIERARCHY_ROLE', 'ENTITY_MANAGER', 'FUNCTION_MANAGER']

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

const showAssigneeAnchor = computed(() => ANCHORED_ASSIGNEE_TYPES.includes(assigneeType.value))

const incomingSequenceFlowCount = computed(() => {
  topologyTick.value
  return countIncomingSequenceFlows(props.element as any)
})

const lastTaskAnchorTopologyInvalid = computed(() => {
  if (!showAssigneeAnchor.value || assigneeAnchor.value !== 'LAST_TASK_ASSIGNEE') {
    return false
  }
  return incomingSequenceFlowCount.value !== 1
})

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
  const anchorRaw = (ext.assigneeAnchor as string) || 'INITIATOR'
  assigneeAnchor.value =
    anchorRaw.toUpperCase() === 'LAST_TASK_ASSIGNEE' ? 'LAST_TASK_ASSIGNEE' : 'INITIATOR'
  roleId.value = ext.roleId || ''
  businessUnitId.value = ext.businessUnitId || ''
  assigneeLabel.value = ext.assigneeLabel || ''
  candidateUsers.value = ext.candidateUsers || ''
  candidateGroups.value = ext.candidateGroups || ''
  manualAssignVariable.value = ext.manualAssignVariable || ''
  manualAssignBuVariable.value = ext.manualAssignBuVariable || ''
  manualAssignRoleVariable.value = ext.manualAssignRoleVariable || ''
  assigneeVariableName.value = ext.assigneeVariable || ''
  elementSubTableId.value = ext.subTableId || ''
  elementSubTableName.value = ext.subTableName || ''
  formId.value = ext.formId || null
  actionIds.value = ext.actionIds || []
  timeoutEnabled.value = ext.timeoutEnabled || false
  timeoutDuration.value = ext.timeoutDuration || ''
  timeoutAction.value = ext.timeoutAction || 'remind'
  multiInstance.value = ext.multiInstance || false
  sequential.value = ext.sequential || false
  collection.value = ext.collection || ''
  completionCondition.value = ext.completionCondition || ''
  
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

function handleFormChange(id: number | null) {
  updateExtProp('formId', id)
  const form = forms.value.find(f => f.id === id)
  if (form) {
    updateExtProp('formName', form.formName)
  }
}

function onAssigneeAnchorChange(v: 'INITIATOR' | 'LAST_TASK_ASSIGNEE') {
  if (v === 'LAST_TASK_ASSIGNEE') {
    const n = countIncomingSequenceFlows(props.element as any)
    if (n !== 1) {
      ElMessage.error(t('properties.lastTaskAnchorSelectBlocked', { count: n }))
      assigneeAnchor.value = 'INITIATOR'
      updateExtProp('assigneeAnchor', 'INITIATOR')
      return
    }
  }
  updateExtProp('assigneeAnchor', v)
}

function handleAssigneeTypeChange(type: AssigneeTypeEnum) {
  const prev = lastLoadedAssigneeType.value
  lastLoadedAssigneeType.value = type
  updateExtProp('assigneeType', type)

  const wasAnchored = ANCHORED_ASSIGNEE_TYPES.includes(prev)
  const nowAnchored = ANCHORED_ASSIGNEE_TYPES.includes(type)
  if (nowAnchored && !wasAnchored) {
    assigneeAnchor.value = 'INITIATOR'
    updateExtProp('assigneeAnchor', 'INITIATOR')
  } else if (!nowAnchored) {
    assigneeAnchor.value = 'INITIATOR'
    updateExtProp('assigneeAnchor', '')
  }

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

watch(() => props.element, loadProperties, { immediate: true })

function bumpTopologyTick() {
  topologyTick.value++
}

onMounted(() => {
  props.modeler.on('commandStack.changed', bumpTopologyTick)
  loadProperties()
  loadForms()
  loadActions()
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
