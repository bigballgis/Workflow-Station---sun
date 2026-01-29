<template>
  <div class="action-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="showCreateDialog = true">创建动作</el-button>
      <el-button @click="loadActions" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>
    
    <div class="action-list" v-if="!selectedAction">
      <el-table :data="store.actions" v-loading="loading" stripe @row-click="handleSelectAction">
        <el-table-column prop="actionName" :label="t('action.actionName')" width="120" />
        <el-table-column prop="actionType" :label="t('action.actionType')" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ actionTypeLabel(row.actionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="绑定节点" min-width="200">
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
              <span v-else class="no-binding">未绑定</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleSelectAction(row)">编辑</el-button>
            <el-button link type="success" @click.stop="handleTestAction(row)">测试</el-button>
            <el-button link type="danger" @click.stop="handleDeleteAction(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="action-editor" v-else>
      <div class="editor-header">
        <el-button @click="handleBackToList">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <span class="action-name">{{ selectedAction.actionName }}</span>
        <el-button type="success" @click="handleTestAction(selectedAction)">{{ t('action.test') }}</el-button>
        <el-button type="primary" @click="handleSaveAction">保存</el-button>
      </div>
      
      <el-form :model="selectedAction" label-width="100px" style="max-width: 600px;">
        <el-form-item :label="t('action.actionName')">
          <el-input v-model="selectedAction.actionName" />
        </el-form-item>
        <el-form-item :label="t('action.actionType')">
          <el-select v-model="selectedAction.actionType">
            <el-option-group label="审批操作">
              <el-option label="批准" value="APPROVE" />
              <el-option label="拒绝" value="REJECT" />
              <el-option label="转办" value="TRANSFER" />
              <el-option label="委托" value="DELEGATE" />
              <el-option label="回退" value="ROLLBACK" />
              <el-option label="撤回" value="WITHDRAW" />
            </el-option-group>
            <el-option-group label="流程操作">
              <el-option label="流程提交" value="PROCESS_SUBMIT" />
              <el-option label="流程驳回" value="PROCESS_REJECT" />
              <el-option label="组合动作" value="COMPOSITE" />
            </el-option-group>
            <el-option-group label="自定义操作">
              <el-option label="API调用" value="API_CALL" />
              <el-option label="表单弹出" value="FORM_POPUP" />
              <el-option label="自定义脚本" value="CUSTOM_SCRIPT" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="selectedAction.description" type="textarea" />
        </el-form-item>
        
        <!-- API Call Config -->
        <template v-if="selectedAction.actionType === 'API_CALL'">
          <el-divider>API配置</el-divider>
          <el-form-item label="请求URL">
            <el-input v-model="actionConfig.url" placeholder="https://api.example.com/endpoint" />
          </el-form-item>
          <el-form-item label="请求方法">
            <el-select v-model="actionConfig.method">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
          <el-form-item label="请求头">
            <el-input v-model="actionConfig.headers" type="textarea" :rows="3" 
                      placeholder='{"Content-Type": "application/json"}' />
          </el-form-item>
          <el-form-item label="请求体">
            <el-input v-model="actionConfig.body" type="textarea" :rows="5" 
                      placeholder='{"key": "value"}' />
          </el-form-item>
        </template>
        
        <!-- Form Popup Config -->
        <template v-if="selectedAction.actionType === 'FORM_POPUP'">
          <el-divider>表单配置</el-divider>
          <el-form-item label="关联表单">
            <el-select v-model="actionConfig.formId">
              <el-option v-for="form in store.forms" :key="form.id" 
                         :label="form.formName" :value="form.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="弹窗标题">
            <el-input v-model="actionConfig.dialogTitle" />
          </el-form-item>
          <el-form-item label="弹窗宽度">
            <el-input v-model="actionConfig.dialogWidth" placeholder="600px" />
          </el-form-item>
        </template>

        <!-- Process Submit/Reject Config -->
        <template v-if="selectedAction.actionType === 'PROCESS_SUBMIT' || selectedAction.actionType === 'PROCESS_REJECT'">
          <el-divider>流程配置</el-divider>
          <el-form-item label="需要审批意见">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
          <el-form-item label="确认提示">
            <el-input v-model="actionConfig.confirmMessage" placeholder="确定要提交吗？" />
          </el-form-item>
        </template>

        <!-- Custom Script Config -->
        <template v-if="selectedAction.actionType === 'CUSTOM_SCRIPT'">
          <el-divider>脚本配置</el-divider>
          <el-form-item label="脚本代码">
            <el-input v-model="actionConfig.script" type="textarea" :rows="10" 
                      placeholder="// JavaScript code here" />
          </el-form-item>
        </template>

        <!-- Approve/Reject Config -->
        <template v-if="selectedAction.actionType === 'APPROVE' || selectedAction.actionType === 'REJECT'">
          <el-divider>审批配置</el-divider>
          <el-form-item label="目标状态">
            <el-input v-model="actionConfig.targetStatus" placeholder="APPROVED / REJECTED" />
          </el-form-item>
          <el-form-item label="需要审批意见">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
          <el-form-item label="确认提示">
            <el-input v-model="actionConfig.confirmMessage" placeholder="确定要执行此操作吗？" />
          </el-form-item>
        </template>

        <!-- Transfer/Delegate Config -->
        <template v-if="selectedAction.actionType === 'TRANSFER' || selectedAction.actionType === 'DELEGATE'">
          <el-divider>转办/委托配置</el-divider>
          <el-form-item label="需要指定处理人">
            <el-switch v-model="actionConfig.requireAssignee" />
          </el-form-item>
          <el-form-item label="需要审批意见">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
        </template>

        <!-- Rollback Config -->
        <template v-if="selectedAction.actionType === 'ROLLBACK'">
          <el-divider>回退配置</el-divider>
          <el-form-item label="目标步骤">
            <el-select v-model="actionConfig.targetStep">
              <el-option label="上一步" value="previous" />
              <el-option label="指定步骤" value="specific" />
              <el-option label="发起人" value="initiator" />
            </el-select>
          </el-form-item>
          <el-form-item label="需要审批意见">
            <el-switch v-model="actionConfig.requireComment" />
          </el-form-item>
        </template>

        <!-- Withdraw Config -->
        <template v-if="selectedAction.actionType === 'WITHDRAW'">
          <el-divider>撤回配置</el-divider>
          <el-form-item label="目标状态">
            <el-input v-model="actionConfig.targetStatus" placeholder="CANCELLED" />
          </el-form-item>
          <el-form-item label="允许撤回的状态">
            <el-select v-model="actionConfig.allowedFromStatus" multiple placeholder="选择允许撤回的状态">
              <el-option label="待处理" value="PENDING" />
              <el-option label="审批中" value="IN_PROGRESS" />
            </el-select>
          </el-form-item>
        </template>

        <!-- Composite Config -->
        <template v-if="selectedAction.actionType === 'COMPOSITE'">
          <el-divider>组合动作配置</el-divider>
          <el-form-item label="子动作">
            <el-select v-model="actionConfig.subActions" multiple placeholder="选择要组合的动作">
              <el-option v-for="action in store.actions.filter(a => a.actionType !== 'COMPOSITE')" 
                         :key="action.id" :label="action.actionName" :value="action.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="执行顺序">
            <el-radio-group v-model="actionConfig.executionOrder">
              <el-radio label="sequential">顺序执行</el-radio>
              <el-radio label="parallel">并行执行</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 节点绑定配置 -->
        <el-divider>节点绑定</el-divider>
        <el-form-item label="绑定类型">
          <el-radio-group v-model="bindingType">
            <el-radio label="node">绑定到节点</el-radio>
            <el-radio label="global">流程全局</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="bindingType === 'node'" label="选择节点">
          <el-checkbox-group v-model="selectedNodeIds">
            <el-checkbox 
              v-for="node in availableNodes" 
              :key="node.id" 
              :label="node.id"
            >
              {{ node.name || node.id }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="availableNodes.length === 0" class="no-nodes-tip">
            暂无可绑定的用户任务节点，请先在流程设计器中创建
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSaveBinding" :loading="savingBinding">
            保存绑定
          </el-button>
          <span class="binding-tip">绑定会更新流程定义中的节点配置</span>
        </el-form-item>
      </el-form>
    </div>

    <!-- Create Action Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建动作" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="动作名" required>
          <el-input v-model="createForm.actionName" />
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select v-model="createForm.actionType">
            <el-option-group label="审批操作">
              <el-option label="批准" value="APPROVE" />
              <el-option label="拒绝" value="REJECT" />
              <el-option label="转办" value="TRANSFER" />
              <el-option label="委托" value="DELEGATE" />
              <el-option label="回退" value="ROLLBACK" />
              <el-option label="撤回" value="WITHDRAW" />
            </el-option-group>
            <el-option-group label="流程操作">
              <el-option label="流程提交" value="PROCESS_SUBMIT" />
              <el-option label="流程驳回" value="PROCESS_REJECT" />
              <el-option label="组合动作" value="COMPOSITE" />
            </el-option-group>
            <el-option-group label="自定义操作">
              <el-option label="API调用" value="API_CALL" />
              <el-option label="表单弹出" value="FORM_POPUP" />
              <el-option label="自定义脚本" value="CUSTOM_SCRIPT" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateAction">确定</el-button>
      </template>
    </el-dialog>

    <!-- Test Action Dialog -->
    <el-dialog v-model="showTestDialog" title="测试动作" width="600px">
      <el-form label-width="80px">
        <el-form-item label="测试数据">
          <el-input v-model="testData" type="textarea" :rows="5" placeholder='{"key": "value"}' />
        </el-form-item>
      </el-form>
      <el-divider>执行结果</el-divider>
      <pre class="test-result">{{ testResult }}</pre>
      <template #footer>
        <el-button @click="showTestDialog = false">关闭</el-button>
        <el-button type="primary" @click="executeTest" :loading="testing">执行测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi, type ActionDefinition } from '@/api/functionUnit'

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
const createForm = reactive({ actionName: '', actionType: 'APPROVE', description: '' })

// 存储从BPMN XML解析出的动作绑定信息
const actionNodeBindings = ref<Map<number, Array<{ id: string; name: string }>>>(new Map())

// 节点绑定相关
const bindingType = ref<'node' | 'global'>('node')
const selectedNodeIds = ref<string[]>([])
const availableNodes = ref<Array<{ id: string; name: string }>>([])
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
  targetStep: ''
})

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

watch(selectedAction, (action) => {
  if (action?.configJson) {
    Object.assign(actionConfig, action.configJson)
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
      targetStep: ''
    })
  }
  
  // 加载当前动作的绑定信息
  if (action) {
    loadActionBinding(action.id)
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
  const bindings = new Map<number, Array<{ id: string; name: string }>>()
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
                const actionIds = JSON.parse(value) as number[]
                actionIds.forEach(actionId => {
                  if (!bindings.has(actionId)) {
                    bindings.set(actionId, [])
                  }
                  bindings.get(actionId)!.push({ id: 'process', name: '流程全局' })
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
                const actionIds = JSON.parse(value) as number[]
                actionIds.forEach(actionId => {
                  if (!bindings.has(actionId)) {
                    bindings.set(actionId, [])
                  }
                  bindings.get(actionId)!.push({ id: taskId, name: taskName })
                })
              } catch (e) {
                console.warn('Failed to parse actionIds:', value)
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
}

/**
 * 获取动作绑定的节点列表
 */
function getActionBoundNodes(actionId: number): Array<{ id: string; name: string }> {
  return actionNodeBindings.value.get(actionId) || []
}

/**
 * 加载当前动作的绑定信息
 */
function loadActionBinding(actionId: number) {
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
    ElMessage.warning('请先保存流程定义')
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
    
    ElMessage.success('绑定保存成功')
    
    // 重新加载绑定信息
    await store.fetchProcess(props.functionUnitId)
    parseActionBindingsFromBpmn()
    loadActionBinding(actionId)
  } catch (e: any) {
    console.error('Save binding failed:', e)
    ElMessage.error(e.response?.data?.message || '保存绑定失败')
  } finally {
    savingBinding.value = false
  }
}

/**
 * 从所有节点中移除指定动作
 */
function removeActionFromAllNodes(xmlDoc: Document, actionId: number) {
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
            let actionIds = JSON.parse(value) as number[]
            actionIds = actionIds.filter(id => id !== actionId)
            prop.setAttribute('value', JSON.stringify(actionIds))
            
            // 同步更新actionNames
            const namesProp = Array.from(properties).find(p => p.getAttribute('name') === 'globalActionNames')
            if (namesProp) {
              const namesValue = namesProp.getAttribute('value')
              if (namesValue) {
                const names = JSON.parse(namesValue) as string[]
                // 找到对应索引并移除
                const originalIds = JSON.parse(value) as number[]
                const idx = originalIds.indexOf(actionId)
                if (idx > -1 && names.length > idx) {
                  names.splice(idx, 1)
                  namesProp.setAttribute('value', JSON.stringify(names))
                }
              }
            }
          } catch (e) {
            console.warn('Failed to parse globalActionIds:', e)
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
            const originalIds = JSON.parse(value) as number[]
            let actionIds = [...originalIds]
            const idx = actionIds.indexOf(actionId)
            if (idx > -1) {
              actionIds.splice(idx, 1)
              prop.setAttribute('value', JSON.stringify(actionIds))
              
              // 同步更新actionNames
              const namesProp = Array.from(properties).find(p => p.getAttribute('name') === 'actionNames')
              if (namesProp) {
                const namesValue = namesProp.getAttribute('value')
                if (namesValue) {
                  const names = JSON.parse(namesValue) as string[]
                  if (names.length > idx) {
                    names.splice(idx, 1)
                    namesProp.setAttribute('value', JSON.stringify(names))
                  }
                }
              }
            }
          } catch (e) {
            console.warn('Failed to parse actionIds:', e)
          }
        }
      }
    })
  })
}

/**
 * 添加动作到流程全局
 */
function addActionToProcess(xmlDoc: Document, actionId: number, actionName: string) {
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
    const actionIds = value ? JSON.parse(value) as number[] : []
    if (!actionIds.includes(actionId)) {
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
function addActionToNode(xmlDoc: Document, nodeId: string, actionId: number, actionName: string) {
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
    const actionIds = value ? JSON.parse(value) as number[] : []
    if (!actionIds.includes(actionId)) {
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
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    Object.assign(createForm, { actionName: '', actionType: 'APPROVE', description: '' })
    loadActions()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  }
}

async function handleSaveAction() {
  if (!selectedAction.value) return
  try {
    await store.updateAction(props.functionUnitId, selectedAction.value.id, {
      actionName: selectedAction.value.actionName,
      actionType: selectedAction.value.actionType,
      description: selectedAction.value.description,
      configJson: actionConfig
    })
    ElMessage.success('保存成功')
    loadActions()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function handleDeleteAction(row: ActionDefinition) {
  await ElMessageBox.confirm('确定要删除该动作吗？', '提示', { type: 'warning' })
  try {
    await store.deleteAction(props.functionUnitId, row.id)
    ElMessage.success('删除成功')
    loadActions()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

function handleTestAction(row: ActionDefinition) {
  selectedAction.value = row
  testData.value = '{}'
  testResult.value = ''
  showTestDialog.value = true
}

async function executeTest() {
  if (!selectedAction.value) return
  testing.value = true
  try {
    const data = JSON.parse(testData.value)
    const res = await functionUnitApi.testAction?.(props.functionUnitId, selectedAction.value.id, data)
    testResult.value = JSON.stringify(res?.data || {}, null, 2)
  } catch (e: any) {
    testResult.value = `Error: ${e.message || '测试失败'}`
  } finally {
    testing.value = false
  }
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

.test-result {
  background-color: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
  font-family: monospace;
  font-size: 12px;
  white-space: pre-wrap;
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
</style>
