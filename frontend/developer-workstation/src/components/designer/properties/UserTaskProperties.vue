<template>
  <div class="user-task-properties">
    <el-collapse v-model="activeGroups">
      <!-- 基本信息 -->
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
      
      <!-- 处理人配置 -->
      <el-collapse-item :title="t('properties.assignee')" name="assignee">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.assigneeType')">
            <el-select v-model="assigneeType" @change="handleAssigneeTypeChange">
              <el-option-group :label="t('properties.directAssignment')">
                <el-option :label="t('properties.initiator')" value="INITIATOR" />
                <el-option :label="t('properties.entityManager')" value="ENTITY_MANAGER" />
                <el-option :label="t('properties.functionManager')" value="FUNCTION_MANAGER" />
              </el-option-group>
              <el-option-group :label="t('properties.currentUserBuRole')">
                <el-option :label="t('properties.currentBuRole')" value="CURRENT_BU_ROLE" />
                <el-option :label="t('properties.currentParentBuRole')" value="CURRENT_PARENT_BU_ROLE" />
              </el-option-group>
              <el-option-group :label="t('properties.initiatorBuRole')">
                <el-option :label="t('properties.initiatorBuRoleOption')" value="INITIATOR_BU_ROLE" />
                <el-option :label="t('properties.initiatorParentBuRole')" value="INITIATOR_PARENT_BU_ROLE" />
              </el-option-group>
              <el-option-group :label="t('properties.otherRoleTypes')">
                <el-option :label="t('properties.fixedBuRole')" value="FIXED_BU_ROLE" />
                <el-option :label="t('properties.buUnboundedRole')" value="BU_UNBOUNDED_ROLE" />
              </el-option-group>
            </el-select>
          </el-form-item>
          
          <!-- 显示当前分配标签 -->
          <div v-if="assigneeLabel" class="assignee-label">
            <el-tag type="info" size="small">{{ assigneeLabel }}</el-tag>
          </div>
          
          <!-- 业务单元选择器（FIXED_BU_ROLE需要，放在角色选择器上面） -->
          <el-form-item v-if="assigneeType === 'FIXED_BU_ROLE'" :label="t('properties.selectBusinessUnit')">
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
          
          <!-- 角色选择器（6种角色类型需要） -->
          <!-- FIXED_BU_ROLE 需要先选择业务单元才能选择角色 -->
          <el-form-item v-if="showRoleSelector" :label="t('properties.selectRole')">
            <el-select
              v-model="roleId"
              :loading="loadingRoles"
              :placeholder="roleSelectPlaceholder"
              :disabled="assigneeType === 'FIXED_BU_ROLE' && !businessUnitId"
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
          
          <!-- 认领类型提示 -->
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
      
      <!-- 表单绑定 -->
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
      
      <!-- 动作绑定 -->
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
      
      <!-- 超时配置 -->
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
      
      <!-- 多实例配置 -->
      <el-collapse-item title="多实例配置" name="multiInstance">
        <el-form label-position="top" size="small">
          <el-form-item label="启用多实例">
            <el-switch v-model="multiInstance" @change="updateExtProp('multiInstance', multiInstance)" />
          </el-form-item>
          
          <template v-if="multiInstance">
            <el-form-item label="执行方式">
              <el-radio-group v-model="sequential" @change="updateExtProp('sequential', sequential)">
                <el-radio :value="false">并行</el-radio>
                <el-radio :value="true">串行</el-radio>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="集合变量">
              <el-input v-model="collection" @change="updateExtProp('collection', collection)" placeholder="assigneeList" />
              <div class="form-tip">包含处理人列表的流程变量名</div>
            </el-form-item>
            
            <el-form-item label="完成条件">
              <el-input v-model="completionCondition" @change="updateExtProp('completionCondition', completionCondition)" placeholder="${nrOfCompletedInstances/nrOfInstances >= 0.5}" />
              <div class="form-tip">JUEL 表达式，满足条件时结束多实例</div>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>


<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'assignee', 'form', 'actions'])

// 基本属性
const taskName = ref('')
const taskDescription = ref('')

// 9种标准分配类型
type AssigneeTypeEnum = 
  | 'FUNCTION_MANAGER' 
  | 'ENTITY_MANAGER' 
  | 'INITIATOR' 
  | 'CURRENT_BU_ROLE' 
  | 'CURRENT_PARENT_BU_ROLE' 
  | 'INITIATOR_BU_ROLE' 
  | 'INITIATOR_PARENT_BU_ROLE' 
  | 'FIXED_BU_ROLE' 
  | 'BU_UNBOUNDED_ROLE'

// 处理人配置
const assigneeType = ref<AssigneeTypeEnum>('INITIATOR')
const roleId = ref('')
const businessUnitId = ref('')
const assigneeLabel = ref('')
const candidateUsers = ref('')
const candidateGroups = ref('')

// 业务单元和角色数据
const businessUnits = ref<BusinessUnitInfo[]>([])
const buBoundedRoles = ref<RoleInfo[]>([])
const buUnboundedRoles = ref<RoleInfo[]>([])
const eligibleRoles = ref<RoleInfo[]>([])
const loadingBusinessUnits = ref(false)
const loadingRoles = ref(false)

// 表单绑定
const formId = ref<number | null>(null)
const forms = ref<FormDefinition[]>([])

// 动作绑定
const actionIds = ref<number[]>([])
const actions = ref<ActionDefinition[]>([])

// 超时配置
const timeoutEnabled = ref(false)
const timeoutDuration = ref('')
const timeoutAction = ref<'remind' | 'approve' | 'reject'>('remind')

// 多实例配置
const multiInstance = ref(false)
const sequential = ref(false)
const collection = ref('')
const completionCondition = ref('')

const basicProps = computed(() => getBasicProperties(props.element))

// 是否需要角色ID
const needsRoleId = computed(() => {
  return ['CURRENT_BU_ROLE', 'CURRENT_PARENT_BU_ROLE', 'INITIATOR_BU_ROLE', 
          'INITIATOR_PARENT_BU_ROLE', 'FIXED_BU_ROLE', 'BU_UNBOUNDED_ROLE'].includes(assigneeType.value)
})

// 是否显示角色选择器
const showRoleSelector = computed(() => {
  return needsRoleId.value
})

// 角色选择器占位符
const roleSelectPlaceholder = computed(() => {
  if (assigneeType.value === 'FIXED_BU_ROLE' && !businessUnitId.value) {
    return t('properties.selectBusinessUnitFirst')
  }
  return t('properties.selectRole')
})

// 是否需要认领
const needsClaim = computed(() => {
  return ['CURRENT_BU_ROLE', 'CURRENT_PARENT_BU_ROLE', 'INITIATOR_BU_ROLE', 
          'INITIATOR_PARENT_BU_ROLE', 'FIXED_BU_ROLE', 'BU_UNBOUNDED_ROLE'].includes(assigneeType.value)
})

// 根据分配类型过滤角色
const filteredRoles = computed(() => {
  if (assigneeType.value === 'BU_UNBOUNDED_ROLE') {
    return buUnboundedRoles.value
  } else if (assigneeType.value === 'FIXED_BU_ROLE' && businessUnitId.value) {
    // FIXED_BU_ROLE 只显示业务单元的准入角色
    return eligibleRoles.value
  } else {
    // 其他BU角色类型显示所有BU绑定型角色
    return buBoundedRoles.value
  }
})

// 角色选择提示
const roleSelectTip = computed(() => {
  if (assigneeType.value === 'BU_UNBOUNDED_ROLE') {
    return t('properties.buUnboundedRoleTip')
  } else if (assigneeType.value === 'FIXED_BU_ROLE') {
    return t('properties.fixedBuRoleTip')
  } else {
    return t('properties.buBoundedRoleTip')
  }
})

function loadProperties() {
  if (!props.element) return
  
  // 基本属性
  const basic = getBasicProperties(props.element)
  taskName.value = basic.name
  
  // 扩展属性
  const ext = getExtensionProperties(props.element)
  taskDescription.value = ext.description || ''
  assigneeType.value = ext.assigneeType || 'INITIATOR'
  roleId.value = ext.roleId || ''
  businessUnitId.value = ext.businessUnitId || ''
  assigneeLabel.value = ext.assigneeLabel || ''
  candidateUsers.value = ext.candidateUsers || ''
  candidateGroups.value = ext.candidateGroups || ''
  formId.value = ext.formId || null
  actionIds.value = ext.actionIds || []
  timeoutEnabled.value = ext.timeoutEnabled || false
  timeoutDuration.value = ext.timeoutDuration || ''
  timeoutAction.value = ext.timeoutAction || 'remind'
  multiInstance.value = ext.multiInstance || false
  sequential.value = ext.sequential || false
  collection.value = ext.collection || ''
  completionCondition.value = ext.completionCondition || ''
  
  // 根据分配类型加载数据
  if (needsRoleId.value) {
    loadRoles()
  }
  if (assigneeType.value === 'FIXED_BU_ROLE') {
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

function handleAssigneeTypeChange(type: AssigneeTypeEnum) {
  updateExtProp('assigneeType', type)
  
  // 根据类型设置默认标签
  const labelMap: Record<AssigneeTypeEnum, string> = {
    INITIATOR: t('properties.initiator'),
    ENTITY_MANAGER: t('properties.entityManager'),
    FUNCTION_MANAGER: t('properties.functionManager'),
    CURRENT_BU_ROLE: '',
    CURRENT_PARENT_BU_ROLE: '',
    INITIATOR_BU_ROLE: '',
    INITIATOR_PARENT_BU_ROLE: '',
    FIXED_BU_ROLE: '',
    BU_UNBOUNDED_ROLE: ''
  }
  
  // 清空角色和业务单元
  roleId.value = ''
  businessUnitId.value = ''
  updateExtProp('roleId', '')
  updateExtProp('businessUnitId', '')
  
  // 设置默认标签
  if (!needsRoleId.value) {
    assigneeLabel.value = labelMap[type] || ''
    updateExtProp('assigneeLabel', assigneeLabel.value)
  } else {
    assigneeLabel.value = ''
    updateExtProp('assigneeLabel', '')
  }
  
  // 加载角色数据
  if (needsRoleId.value) {
    loadRoles()
  }
  
  // 加载业务单元数据
  if (type === 'FIXED_BU_ROLE') {
    loadBusinessUnits()
  }
  
  // 清空候选用户/组
  candidateUsers.value = ''
  candidateGroups.value = ''
  updateExtProp('candidateUsers', '')
  updateExtProp('candidateGroups', '')
}

function handleRoleChange(id: string) {
  updateExtProp('roleId', id)
  
  // 更新标签
  const role = filteredRoles.value.find(r => r.id === id)
  if (role) {
    const typeLabel = getAssigneeTypeLabel(assigneeType.value)
    assigneeLabel.value = `${typeLabel}: ${role.name}`
    updateExtProp('assigneeLabel', assigneeLabel.value)
  }
}

function handleBusinessUnitChange(id: string) {
  updateExtProp('businessUnitId', id)
  
  // 清空角色选择
  roleId.value = ''
  updateExtProp('roleId', '')
  
  // 加载业务单元的准入角色
  if (id) {
    loadEligibleRoles(id)
  } else {
    eligibleRoles.value = []
  }
  
  // 更新标签
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
    CURRENT_BU_ROLE: t('properties.currentBuRole'),
    CURRENT_PARENT_BU_ROLE: t('properties.currentParentBuRole'),
    INITIATOR_BU_ROLE: t('properties.initiatorBuRoleOption'),
    INITIATOR_PARENT_BU_ROLE: t('properties.initiatorParentBuRole'),
    FIXED_BU_ROLE: t('properties.fixedBuRole'),
    BU_UNBOUNDED_ROLE: t('properties.buUnboundedRole')
  }
  return labels[type] || type
}

// 递归查找业务单元
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

// 加载角色
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

// 加载业务单元
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

// 加载业务单元的准入角色
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
    APPROVE: '批准',
    REJECT: '拒绝',
    TRANSFER: '转办',
    DELEGATE: '委托',
    ROLLBACK: '回退',
    WITHDRAW: '撤回',
    PROCESS_SUBMIT: '流程提交',
    PROCESS_REJECT: '流程驳回',
    COMPOSITE: '组合动作',
    API_CALL: 'API调用',
    FORM_POPUP: '表单弹出',
    CUSTOM_SCRIPT: '自定义脚本'
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

onMounted(() => {
  loadProperties()
  loadForms()
  loadActions()
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
