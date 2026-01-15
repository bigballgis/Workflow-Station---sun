<template>
  <div class="task-properties">
    <el-collapse v-model="activeGroups">
      <!-- 基本信息 -->
      <el-collapse-item :title="$t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="$t('properties.taskId')">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="$t('common.type')">
            <el-input :model-value="taskTypeLabel" disabled />
          </el-form-item>
          <el-form-item :label="$t('properties.taskName')">
            <el-input v-model="taskName" @change="updateBasicProp('name', taskName)" :placeholder="$t('properties.taskName')" />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- 用户任务配置 -->
      <template v-if="taskType === 'bpmn:UserTask'">
        <el-collapse-item title="处理人配置" name="assignee">
          <el-form label-position="top" size="small">
            <el-form-item label="分配方式">
              <el-select v-model="assigneeType" @change="updateExtProp('assigneeType', assigneeType)">
                <el-option label="指定用户" value="user" />
                <el-option label="指定角色" value="role" />
                <el-option label="表达式" value="expression" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="assigneeType === 'user'" label="处理人">
              <el-input v-model="assigneeValue" @change="updateExtProp('assigneeValue', assigneeValue)" placeholder="用户ID或用户名" />
            </el-form-item>
            <el-form-item v-if="assigneeType === 'role'" label="角色">
              <el-input v-model="assigneeValue" @change="updateExtProp('assigneeValue', assigneeValue)" placeholder="角色ID或角色名" />
            </el-form-item>
            <el-form-item v-if="assigneeType === 'expression'" label="表达式">
              <el-input v-model="assigneeValue" @change="updateExtProp('assigneeValue', assigneeValue)" placeholder="${initiator}" />
              <div class="form-tip">支持 JUEL 表达式，如 ${initiator}、${manager}</div>
            </el-form-item>
            <el-form-item label="候选用户">
              <el-input v-model="candidateUsers" @change="updateExtProp('candidateUsers', candidateUsers)" placeholder="多个用户用逗号分隔" />
            </el-form-item>
            <el-form-item label="候选角色">
              <el-input v-model="candidateGroups" @change="updateExtProp('candidateGroups', candidateGroups)" placeholder="多个角色用逗号分隔" />
            </el-form-item>
          </el-form>
        </el-collapse-item>

        <el-collapse-item title="表单绑定" name="form">
          <el-form label-position="top" size="small">
            <el-form-item label="绑定表单">
              <el-select v-model="formId" @change="handleFormChange" placeholder="选择表单" clearable style="width: 100%">
                <el-option v-for="form in forms" :key="form.id" :label="form.formName" :value="form.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-collapse-item>

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
              </el-form-item>
            </template>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- 服务任务配置 -->
      <template v-if="taskType === 'bpmn:ServiceTask'">
        <el-collapse-item title="服务配置" name="service">
          <el-form label-position="top" size="small">
            <el-form-item label="实现方式">
              <el-select v-model="serviceType" @change="updateExtProp('serviceType', serviceType)">
                <el-option label="HTTP 调用" value="http" />
                <el-option label="Java 类" value="class" />
                <el-option label="表达式" value="expression" />
                <el-option label="委托表达式" value="delegateExpression" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="serviceType === 'http'" label="请求URL">
              <el-input v-model="httpUrl" @change="updateExtProp('httpUrl', httpUrl)" placeholder="https://api.example.com/endpoint" />
            </el-form-item>
            <el-form-item v-if="serviceType === 'http'" label="请求方法">
              <el-select v-model="httpMethod" @change="updateExtProp('httpMethod', httpMethod)">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="serviceType === 'class'" label="Java 类名">
              <el-input v-model="javaClass" @change="updateExtProp('javaClass', javaClass)" placeholder="com.example.MyDelegate" />
            </el-form-item>
            <el-form-item v-if="serviceType === 'expression'" label="表达式">
              <el-input v-model="serviceExpression" @change="updateExtProp('serviceExpression', serviceExpression)" placeholder="${myBean.execute()}" />
            </el-form-item>
            <el-form-item v-if="serviceType === 'delegateExpression'" label="委托表达式">
              <el-input v-model="delegateExpression" @change="updateExtProp('delegateExpression', delegateExpression)" placeholder="${myDelegate}" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- 脚本任务配置 -->
      <template v-if="taskType === 'bpmn:ScriptTask'">
        <el-collapse-item title="脚本配置" name="script">
          <el-form label-position="top" size="small">
            <el-form-item label="脚本语言">
              <el-select v-model="scriptFormat" @change="updateExtProp('scriptFormat', scriptFormat)">
                <el-option label="JavaScript" value="javascript" />
                <el-option label="Groovy" value="groovy" />
                <el-option label="Python" value="python" />
              </el-select>
            </el-form-item>
            <el-form-item label="脚本内容">
              <el-input v-model="scriptBody" type="textarea" :rows="6" @change="updateExtProp('scriptBody', scriptBody)" placeholder="// 输入脚本代码" />
            </el-form-item>
            <el-form-item label="结果变量">
              <el-input v-model="resultVariable" @change="updateExtProp('resultVariable', resultVariable)" placeholder="存储脚本执行结果的变量名" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- 发送/接收任务配置 -->
      <template v-if="taskType === 'bpmn:SendTask' || taskType === 'bpmn:ReceiveTask'">
        <el-collapse-item title="消息配置" name="message">
          <el-form label-position="top" size="small">
            <el-form-item label="消息名称">
              <el-input v-model="messageName" @change="updateExtProp('messageName', messageName)" placeholder="消息名称" />
            </el-form-item>
            <el-form-item v-if="taskType === 'bpmn:SendTask'" label="消息内容">
              <el-input v-model="messagePayload" type="textarea" :rows="3" @change="updateExtProp('messagePayload', messagePayload)" placeholder="消息内容（支持表达式）" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- 业务规则任务配置 -->
      <template v-if="taskType === 'bpmn:BusinessRuleTask'">
        <el-collapse-item title="规则配置" name="rule">
          <el-form label-position="top" size="small">
            <el-form-item label="规则引擎">
              <el-select v-model="ruleEngine" @change="updateExtProp('ruleEngine', ruleEngine)">
                <el-option label="DMN" value="dmn" />
                <el-option label="Drools" value="drools" />
              </el-select>
            </el-form-item>
            <el-form-item label="决策表Key">
              <el-input v-model="decisionRef" @change="updateExtProp('decisionRef', decisionRef)" placeholder="决策表的Key" />
            </el-form-item>
            <el-form-item label="结果变量">
              <el-input v-model="ruleResultVariable" @change="updateExtProp('ruleResultVariable', ruleResultVariable)" placeholder="存储规则执行结果的变量名" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import type { FormDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty,
  getElementType
} from '@/utils/bpmnExtensions'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'assignee', 'service', 'script'])

// 任务类型
const taskType = ref('bpmn:UserTask')
const taskName = ref('')

// 用户任务配置
const assigneeType = ref<'user' | 'role' | 'expression'>('user')
const assigneeValue = ref('')
const candidateUsers = ref('')
const candidateGroups = ref('')
const formId = ref<number | null>(null)
const forms = ref<FormDefinition[]>([])
const timeoutEnabled = ref(false)
const timeoutDuration = ref('')
const timeoutAction = ref<'remind' | 'approve' | 'reject'>('remind')
const multiInstance = ref(false)
const sequential = ref(false)
const collection = ref('')
const completionCondition = ref('')

// 服务任务配置
const serviceType = ref<'http' | 'class' | 'expression' | 'delegateExpression'>('http')
const httpUrl = ref('')
const httpMethod = ref('POST')
const javaClass = ref('')
const serviceExpression = ref('')
const delegateExpression = ref('')

// 脚本任务配置
const scriptFormat = ref('javascript')
const scriptBody = ref('')
const resultVariable = ref('')

// 消息任务配置
const messageName = ref('')
const messagePayload = ref('')

// 业务规则任务配置
const ruleEngine = ref('dmn')
const decisionRef = ref('')
const ruleResultVariable = ref('')

const basicProps = computed(() => getBasicProperties(props.element))

const taskTypeLabel = computed(() => {
  const names: Record<string, string> = {
    'bpmn:UserTask': '用户任务',
    'bpmn:ServiceTask': '服务任务',
    'bpmn:ScriptTask': '脚本任务',
    'bpmn:SendTask': '发送任务',
    'bpmn:ReceiveTask': '接收任务',
    'bpmn:ManualTask': '手动任务',
    'bpmn:BusinessRuleTask': '业务规则任务',
    'bpmn:Task': '任务'
  }
  return names[taskType.value] || '任务'
})

function loadProperties() {
  if (!props.element) return
  
  // 获取当前元素类型
  const currentType = getElementType(props.element)
  
  // 直接使用元素的实际类型
  const ext = getExtensionProperties(props.element)
  if (currentType === 'bpmn:Task' || currentType === 'bpmn:Activity') {
    taskType.value = ext.taskType || 'bpmn:UserTask'
  } else if (currentType.includes('Task')) {
    taskType.value = currentType
  } else {
    taskType.value = ext.taskType || 'bpmn:UserTask'
  }
  
  // 基本属性
  const basic = getBasicProperties(props.element)
  taskName.value = basic.name
  
  // 用户任务属性
  assigneeType.value = ext.assigneeType || 'user'
  assigneeValue.value = ext.assigneeValue || ''
  candidateUsers.value = ext.candidateUsers || ''
  candidateGroups.value = ext.candidateGroups || ''
  formId.value = ext.formId || null
  timeoutEnabled.value = ext.timeoutEnabled || false
  timeoutDuration.value = ext.timeoutDuration || ''
  timeoutAction.value = ext.timeoutAction || 'remind'
  multiInstance.value = ext.multiInstance || false
  sequential.value = ext.sequential || false
  collection.value = ext.collection || ''
  completionCondition.value = ext.completionCondition || ''
  
  // 服务任务属性
  serviceType.value = ext.serviceType || 'http'
  httpUrl.value = ext.httpUrl || ''
  httpMethod.value = ext.httpMethod || 'POST'
  javaClass.value = ext.javaClass || ''
  serviceExpression.value = ext.serviceExpression || ''
  delegateExpression.value = ext.delegateExpression || ''
  
  // 脚本任务属性
  scriptFormat.value = ext.scriptFormat || 'javascript'
  scriptBody.value = ext.scriptBody || ''
  resultVariable.value = ext.resultVariable || ''
  
  // 消息任务属性
  messageName.value = ext.messageName || ''
  messagePayload.value = ext.messagePayload || ''
  
  // 业务规则任务属性
  ruleEngine.value = ext.ruleEngine || 'dmn'
  decisionRef.value = ext.decisionRef || ''
  ruleResultVariable.value = ext.ruleResultVariable || ''
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

async function loadForms() {
  try {
    const res = await functionUnitApi.getForms(props.functionUnitId)
    forms.value = res.data || []
  } catch {
    forms.value = []
  }
}

watch(() => props.element, loadProperties, { immediate: true })

onMounted(() => {
  loadProperties()
  loadForms()
})
</script>

<style lang="scss" scoped>
.task-properties {
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
}
</style>
