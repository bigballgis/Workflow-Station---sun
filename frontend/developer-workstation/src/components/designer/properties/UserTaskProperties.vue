<template>
  <div class="user-task-properties">
    <el-collapse v-model="activeGroups">
      <!-- 基本信息 -->
      <el-collapse-item title="基本信息" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item label="任务ID">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item label="任务名称">
            <el-input v-model="taskName" @change="updateBasicProp('name', taskName)" placeholder="任务名称" />
          </el-form-item>
          <el-form-item label="任务描述">
            <el-input v-model="taskDescription" type="textarea" :rows="2" @change="updateExtProp('description', taskDescription)" placeholder="任务描述" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 处理人配置 -->
      <el-collapse-item title="处理人配置" name="assignee">
        <el-form label-position="top" size="small">
          <el-form-item label="分配方式">
            <el-select v-model="assigneeType" @change="handleAssigneeTypeChange">
              <el-option label="流程发起人" value="initiator" />
              <el-option label="发起人的直属上级" value="manager" />
              <el-option label="实体管理者" value="entityManager" />
              <el-option label="职能管理者" value="functionManager" />
              <el-option label="实体或职能管理者（或签）" value="eitherManager" />
              <el-option label="实体+职能管理者（会签）" value="bothManagers" />
              <el-option label="部门主经理" value="departmentManager" />
              <el-option label="部门副经理" value="departmentSecondaryManager" />
              <el-option label="指定用户" value="user" />
              <el-option label="指定部门/组" value="group" />
              <el-option label="表达式" value="expression" />
            </el-select>
          </el-form-item>
          
          <!-- 显示当前分配标签 -->
          <div v-if="assigneeLabel" class="assignee-label">
            <el-tag type="info" size="small">{{ assigneeLabel }}</el-tag>
          </div>
          
          <el-form-item v-if="assigneeType === 'user'" label="处理人">
            <el-input v-model="assigneeValue" @change="handleAssigneeValueChange" placeholder="用户ID或用户名" />
          </el-form-item>
          
          <el-form-item v-if="assigneeType === 'group'" label="部门/组">
            <el-input v-model="assigneeValue" @change="handleAssigneeValueChange" placeholder="如: dept_hr, role_manager" />
            <div class="form-tip">部门组以 dept_ 开头，角色组以 role_ 开头</div>
          </el-form-item>
          
          <el-form-item v-if="assigneeType === 'expression'" label="表达式">
            <el-input v-model="assigneeValue" @change="handleAssigneeValueChange" placeholder="${initiator}" />
            <div class="form-tip">支持 JUEL 表达式，如 ${initiator}、${initiatorManager}</div>
          </el-form-item>
          
          <el-form-item label="候选用户">
            <el-input v-model="candidateUsers" @change="updateExtProp('candidateUsers', candidateUsers)" placeholder="多个用户用逗号分隔" />
          </el-form-item>
          
          <el-form-item label="候选组">
            <el-input v-model="candidateGroups" @change="updateExtProp('candidateGroups', candidateGroups)" placeholder="多个组用逗号分隔" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 表单绑定 -->
      <el-collapse-item title="表单绑定" name="form">
        <el-form label-position="top" size="small">
          <el-form-item label="绑定表单">
            <el-select v-model="formId" @change="handleFormChange" placeholder="选择表单" clearable>
              <el-option v-for="form in forms" :key="form.id" :label="form.formName" :value="form.id" />
            </el-select>
          </el-form-item>
          <div v-if="formId" class="form-preview-link">
            <el-button link type="primary" size="small">预览表单</el-button>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- 动作绑定 -->
      <el-collapse-item title="动作绑定" name="actions">
        <el-form label-position="top" size="small">
          <el-form-item label="可用动作">
            <el-select v-model="actionIds" @change="handleActionsChange" placeholder="选择动作" multiple clearable>
              <el-option v-for="action in actions" :key="action.id" :label="action.actionName" :value="action.id">
                <span>{{ action.actionName }}</span>
                <el-tag size="small" style="margin-left: 8px;">{{ actionTypeLabel(action.actionType) }}</el-tag>
              </el-option>
            </el-select>
          </el-form-item>
          <div v-if="actionIds.length > 0" class="selected-actions">
            <div class="form-tip">已选择 {{ actionIds.length }} 个动作</div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- 超时配置 -->
      <el-collapse-item title="超时配置" name="timeout">
        <el-form label-position="top" size="small">
          <el-form-item label="启用超时">
            <el-switch v-model="timeoutEnabled" @change="updateExtProp('timeoutEnabled', timeoutEnabled)" />
          </el-form-item>
          
          <template v-if="timeoutEnabled">
            <el-form-item label="超时时间">
              <el-input v-model="timeoutDuration" @change="updateExtProp('timeoutDuration', timeoutDuration)" placeholder="PT24H (24小时)" />
              <div class="form-tip">ISO 8601 格式：PT1H (1小时)、P1D (1天)</div>
            </el-form-item>
            
            <el-form-item label="超时动作">
              <el-select v-model="timeoutAction" @change="updateExtProp('timeoutAction', timeoutAction)">
                <el-option label="发送提醒" value="remind" />
                <el-option label="自动通过" value="approve" />
                <el-option label="自动拒绝" value="reject" />
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
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import type { FormDefinition, ActionDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty
} from '@/utils/bpmnExtensions'

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'assignee', 'form', 'actions'])

// 基本属性
const taskName = ref('')
const taskDescription = ref('')

// 处理人配置
const assigneeType = ref<'initiator' | 'manager' | 'entityManager' | 'functionManager' | 'eitherManager' | 'bothManagers' | 'departmentManager' | 'departmentSecondaryManager' | 'user' | 'group' | 'expression'>('user')
const assigneeValue = ref('')
const assigneeLabel = ref('')
const candidateUsers = ref('')
const candidateGroups = ref('')

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

function loadProperties() {
  if (!props.element) return
  
  // 基本属性
  const basic = getBasicProperties(props.element)
  taskName.value = basic.name
  
  // 扩展属性
  const ext = getExtensionProperties(props.element)
  taskDescription.value = ext.description || ''
  assigneeType.value = ext.assigneeType || 'user'
  assigneeValue.value = ext.assigneeValue || ''
  assigneeLabel.value = ext.assigneeLabel || ''
  candidateUsers.value = ext.candidateUsers || ''
  candidateGroups.value = ext.candidateGroups || ''
  formId.value = ext.formId || null
  // 加载动作绑定
  actionIds.value = ext.actionIds || []
  timeoutEnabled.value = ext.timeoutEnabled || false
  timeoutDuration.value = ext.timeoutDuration || ''
  timeoutAction.value = ext.timeoutAction || 'remind'
  multiInstance.value = ext.multiInstance || false
  sequential.value = ext.sequential || false
  collection.value = ext.collection || ''
  completionCondition.value = ext.completionCondition || ''
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

function handleAssigneeTypeChange(type: string) {
  updateExtProp('assigneeType', type)
  
  // 根据类型设置默认值和标签
  const labelMap: Record<string, string> = {
    initiator: '流程发起人',
    manager: '发起人的直属上级',
    entityManager: '实体管理者',
    functionManager: '职能管理者',
    eitherManager: '实体或职能管理者（或签）',
    bothManagers: '实体+职能管理者（会签）',
    departmentManager: '部门主经理',
    departmentSecondaryManager: '部门副经理',
    user: '',
    group: '',
    expression: ''
  }
  
  assigneeLabel.value = labelMap[type] || ''
  updateExtProp('assigneeLabel', assigneeLabel.value)
  
  // 根据类型设置 assignee 或 candidateUsers
  const managerTypes = ['initiator', 'manager', 'entityManager', 'functionManager', 'departmentManager', 'departmentSecondaryManager']
  
  if (type === 'eitherManager') {
    // 或签模式：使用 candidateUsers，任一人审批即可
    assigneeValue.value = ''
    updateExtProp('assigneeValue', '')
    candidateUsers.value = '${entityManager},${functionManager}'
    updateExtProp('candidateUsers', candidateUsers.value)
  } else if (type === 'bothManagers') {
    // 会签模式：使用 candidateUsers + 多实例配置
    assigneeValue.value = ''
    updateExtProp('assigneeValue', '')
    candidateUsers.value = '${entityManager},${functionManager}'
    updateExtProp('candidateUsers', candidateUsers.value)
    // 启用多实例（会签需要所有人审批）
    multiInstance.value = true
    updateExtProp('multiInstance', true)
    sequential.value = false
    updateExtProp('sequential', false)
  } else if (managerTypes.includes(type)) {
    // 单管理者模式：清空值，流程引擎会根据类型解析
    assigneeValue.value = ''
    updateExtProp('assigneeValue', '')
    candidateUsers.value = ''
    updateExtProp('candidateUsers', '')
  }
}

function handleAssigneeValueChange(value: string) {
  updateExtProp('assigneeValue', value)
  
  // 更新标签
  if (assigneeType.value === 'group') {
    // 可以根据组ID查找组名称
    assigneeLabel.value = value
    updateExtProp('assigneeLabel', value)
  } else if (assigneeType.value === 'user') {
    assigneeLabel.value = value
    updateExtProp('assigneeLabel', value)
  }
}

function handleActionsChange(ids: number[]) {
  updateExtProp('actionIds', ids)
  // 保存动作名称列表
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
}
</style>
