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
              <el-option label="流程发起人" value="INITIATOR" />
              <el-option label="实体经理" value="ENTITY_MANAGER" />
              <el-option label="职能经理" value="FUNCTION_MANAGER" />
              <el-option label="本部门其他人" value="DEPT_OTHERS" />
              <el-option label="上级部门" value="PARENT_DEPT" />
              <el-option label="指定部门" value="FIXED_DEPT" />
              <el-option label="虚拟组" value="VIRTUAL_GROUP" />
            </el-select>
          </el-form-item>
          
          <!-- 显示当前分配标签 -->
          <div v-if="assigneeLabel" class="assignee-label">
            <el-tag type="info" size="small">{{ assigneeLabel }}</el-tag>
          </div>
          
          <!-- 指定部门选择器 -->
          <el-form-item v-if="assigneeType === 'FIXED_DEPT'" label="选择部门">
            <el-tree-select
              v-model="assigneeValue"
              :data="departments"
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              :loading="loadingDepartments"
              placeholder="请选择部门"
              check-strictly
              filterable
              @change="handleAssigneeValueChange"
            />
            <div class="form-tip">选择一个部门，该部门的所有成员都可以认领此任务</div>
          </el-form-item>
          
          <!-- 虚拟组选择器 -->
          <el-form-item v-if="assigneeType === 'VIRTUAL_GROUP'" label="选择虚拟组">
            <el-select
              v-model="assigneeValue"
              :loading="loadingVirtualGroups"
              placeholder="请选择虚拟组"
              filterable
              @change="handleAssigneeValueChange"
            >
              <el-option
                v-for="group in virtualGroups"
                :key="group.id"
                :label="group.name"
                :value="group.id"
              >
                <span>{{ group.name }}</span>
                <span v-if="group.memberCount" style="color: #909399; margin-left: 8px;">
                  ({{ group.memberCount }}人)
                </span>
              </el-option>
            </el-select>
            <div class="form-tip">选择一个虚拟组，该组的所有成员都可以认领此任务</div>
          </el-form-item>
          
          <!-- 认领类型提示 -->
          <div v-if="needsClaim" class="claim-tip">
            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                此分配方式需要用户认领任务
              </template>
            </el-alert>
          </div>
          
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
import { adminCenterApi, type DepartmentTree, type VirtualGroupInfo } from '@/api/adminCenter'
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

// 7种标准分配类型
type AssigneeTypeEnum = 'FUNCTION_MANAGER' | 'ENTITY_MANAGER' | 'INITIATOR' | 'DEPT_OTHERS' | 'PARENT_DEPT' | 'FIXED_DEPT' | 'VIRTUAL_GROUP'

// 处理人配置
const assigneeType = ref<AssigneeTypeEnum>('INITIATOR')
const assigneeValue = ref('')
const assigneeLabel = ref('')
const candidateUsers = ref('')
const candidateGroups = ref('')

// 部门和虚拟组数据
const departments = ref<DepartmentTree[]>([])
const virtualGroups = ref<VirtualGroupInfo[]>([])
const loadingDepartments = ref(false)
const loadingVirtualGroups = ref(false)

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
  assigneeType.value = ext.assigneeType || 'INITIATOR'
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
  
  // 如果是 FIXED_DEPT 或 VIRTUAL_GROUP，加载对应数据
  if (assigneeType.value === 'FIXED_DEPT') {
    loadDepartments()
  } else if (assigneeType.value === 'VIRTUAL_GROUP') {
    loadVirtualGroups()
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
    INITIATOR: '流程发起人',
    ENTITY_MANAGER: '实体经理',
    FUNCTION_MANAGER: '职能经理',
    DEPT_OTHERS: '本部门其他人',
    PARENT_DEPT: '上级部门',
    FIXED_DEPT: '',
    VIRTUAL_GROUP: ''
  }
  
  // 清空 assigneeValue（除非是 FIXED_DEPT 或 VIRTUAL_GROUP）
  if (type !== 'FIXED_DEPT' && type !== 'VIRTUAL_GROUP') {
    assigneeValue.value = ''
    updateExtProp('assigneeValue', '')
    assigneeLabel.value = labelMap[type] || ''
    updateExtProp('assigneeLabel', assigneeLabel.value)
  } else {
    // 加载部门或虚拟组数据
    if (type === 'FIXED_DEPT') {
      loadDepartments()
    } else if (type === 'VIRTUAL_GROUP') {
      loadVirtualGroups()
    }
  }
  
  // 清空候选用户/组
  candidateUsers.value = ''
  candidateGroups.value = ''
  updateExtProp('candidateUsers', '')
  updateExtProp('candidateGroups', '')
}

function handleAssigneeValueChange(value: string) {
  updateExtProp('assigneeValue', value)
  
  // 更新标签
  if (assigneeType.value === 'FIXED_DEPT') {
    // 查找部门名称
    const dept = findDepartmentById(departments.value, value)
    assigneeLabel.value = dept?.name || value
    updateExtProp('assigneeLabel', assigneeLabel.value)
  } else if (assigneeType.value === 'VIRTUAL_GROUP') {
    // 查找虚拟组名称
    const group = virtualGroups.value.find(g => g.id === value)
    assigneeLabel.value = group?.name || value
    updateExtProp('assigneeLabel', assigneeLabel.value)
  }
}

// 递归查找部门
function findDepartmentById(depts: DepartmentTree[], id: string): DepartmentTree | null {
  for (const dept of depts) {
    if (dept.id === id) return dept
    if (dept.children) {
      const found = findDepartmentById(dept.children, id)
      if (found) return found
    }
  }
  return null
}

// 是否需要认领
const needsClaim = computed(() => {
  return ['DEPT_OTHERS', 'PARENT_DEPT', 'FIXED_DEPT', 'VIRTUAL_GROUP'].includes(assigneeType.value)
})

// 加载部门树
async function loadDepartments() {
  if (departments.value.length > 0) return
  loadingDepartments.value = true
  try {
    const data = await adminCenterApi.getDepartmentTree()
    departments.value = data || []
  } catch (e) {
    console.error('Failed to load departments:', e)
    departments.value = []
  } finally {
    loadingDepartments.value = false
  }
}

// 加载虚拟组
async function loadVirtualGroups() {
  if (virtualGroups.value.length > 0) return
  loadingVirtualGroups.value = true
  try {
    const data = await adminCenterApi.getVirtualGroups()
    virtualGroups.value = data || []
  } catch (e) {
    console.error('Failed to load virtual groups:', e)
    virtualGroups.value = []
  } finally {
    loadingVirtualGroups.value = false
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
