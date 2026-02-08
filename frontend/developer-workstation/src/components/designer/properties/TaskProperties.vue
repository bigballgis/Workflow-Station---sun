<template>
  <div class="task-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item :title="t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.taskId')">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="t('common.type')">
            <el-input :model-value="taskTypeLabel" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.taskName')">
            <el-input v-model="taskName" @change="updateBasicProp('name', taskName)" :placeholder="t('properties.taskName')" />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- User task config -->
      <template v-if="taskType === 'bpmn:UserTask'">
        <el-collapse-item :title="t('properties.assigneeConfig')" name="assignee">
          <el-form label-position="top" size="small">
            <el-form-item :label="t('properties.assigneeType')">
              <el-select v-model="assigneeType" @change="updateExtProp('assigneeType', assigneeType)">
                <el-option :label="t('properties.user')" value="user" />
                <el-option :label="t('properties.role')" value="role" />
                <el-option :label="t('properties.expression')" value="expression" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="assigneeType === 'user'" :label="t('properties.assignee')">
              <el-input v-model="assigneeValue" @change="updateExtProp('assigneeValue', assigneeValue)" :placeholder="t('properties.userIdPlaceholder')" />
            </el-form-item>
            <el-form-item v-if="assigneeType === 'role'" :label="t('properties.role')">
              <el-input v-model="assigneeValue" @change="updateExtProp('assigneeValue', assigneeValue)" :placeholder="t('properties.roleIdPlaceholder')" />
            </el-form-item>
            <el-form-item v-if="assigneeType === 'expression'" :label="t('properties.expression')">
              <el-input v-model="assigneeValue" @change="updateExtProp('assigneeValue', assigneeValue)" placeholder="${initiator}" />
              <div class="form-tip">{{ t('properties.expressionTip') }}</div>
            </el-form-item>
            <el-form-item :label="t('properties.candidateUsers')">
              <el-input v-model="candidateUsers" @change="updateExtProp('candidateUsers', candidateUsers)" :placeholder="t('properties.candidateUsersPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('properties.candidateRoles')">
              <el-input v-model="candidateGroups" @change="updateExtProp('candidateGroups', candidateGroups)" :placeholder="t('properties.candidateRolesPlaceholder')" />
            </el-form-item>
          </el-form>
        </el-collapse-item>

        <el-collapse-item :title="t('properties.formBinding')" name="form">
          <el-form label-position="top" size="small">
            <el-form-item :label="t('properties.bindForm')">
              <el-select v-model="formId" @change="handleFormChange" :placeholder="t('properties.selectForm')" clearable style="width: 100%">
                <el-option v-for="form in forms" :key="form.id" :label="form.formName" :value="form.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-collapse-item>

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
                  <el-option :label="t('properties.sendReminder')" value="remind" />
                  <el-option :label="t('properties.autoApprove')" value="approve" />
                  <el-option :label="t('properties.autoReject')" value="reject" />
                </el-select>
              </el-form-item>
            </template>
          </el-form>
        </el-collapse-item>

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
              </el-form-item>
            </template>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Service task config -->
      <template v-if="taskType === 'bpmn:ServiceTask'">
        <el-collapse-item :title="t('properties.serviceConfig')" name="service">
          <el-form label-position="top" size="small">
            <el-form-item :label="t('properties.implementationType')">
              <el-select v-model="serviceType" @change="updateExtProp('serviceType', serviceType)">
                <el-option :label="t('properties.httpCall')" value="http" />
                <el-option :label="t('properties.javaClass')" value="class" />
                <el-option :label="t('properties.expression')" value="expression" />
                <el-option :label="t('properties.delegateExpression')" value="delegateExpression" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="serviceType === 'http'" :label="t('properties.requestUrl')">
              <el-input v-model="httpUrl" @change="updateExtProp('httpUrl', httpUrl)" placeholder="https://api.example.com/endpoint" />
            </el-form-item>
            <el-form-item v-if="serviceType === 'http'" :label="t('properties.requestMethod')">
              <el-select v-model="httpMethod" @change="updateExtProp('httpMethod', httpMethod)">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="serviceType === 'class'" :label="t('properties.javaClassName')">
              <el-input v-model="javaClass" @change="updateExtProp('javaClass', javaClass)" placeholder="com.example.MyDelegate" />
            </el-form-item>
            <el-form-item v-if="serviceType === 'expression'" :label="t('properties.expression')">
              <el-input v-model="serviceExpression" @change="updateExtProp('serviceExpression', serviceExpression)" placeholder="${myBean.execute()}" />
            </el-form-item>
            <el-form-item v-if="serviceType === 'delegateExpression'" :label="t('properties.delegateExpression')">
              <el-input v-model="delegateExpression" @change="updateExtProp('delegateExpression', delegateExpression)" placeholder="${myDelegate}" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Script task config -->
      <template v-if="taskType === 'bpmn:ScriptTask'">
        <el-collapse-item :title="t('properties.script')" name="script">
          <el-form label-position="top" size="small">
            <el-form-item :label="t('properties.scriptLanguage')">
              <el-select v-model="scriptFormat" @change="updateExtProp('scriptFormat', scriptFormat)">
                <el-option label="JavaScript" value="javascript" />
                <el-option label="Groovy" value="groovy" />
                <el-option label="Python" value="python" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('properties.scriptContent')">
              <el-input v-model="scriptBody" type="textarea" :rows="6" @change="updateExtProp('scriptBody', scriptBody)" :placeholder="t('properties.scriptBodyPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('properties.resultVariable')">
              <el-input v-model="resultVariable" @change="updateExtProp('resultVariable', resultVariable)" :placeholder="t('properties.resultVariablePlaceholder')" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Send/Receive task config -->
      <template v-if="taskType === 'bpmn:SendTask' || taskType === 'bpmn:ReceiveTask'">
        <el-collapse-item :title="t('properties.message')" name="message">
          <el-form label-position="top" size="small">
            <el-form-item :label="t('properties.messageName')">
              <el-input v-model="messageName" @change="updateExtProp('messageName', messageName)" :placeholder="t('properties.messageNamePlaceholder')" />
            </el-form-item>
            <el-form-item v-if="taskType === 'bpmn:SendTask'" :label="t('properties.messagePayload')">
              <el-input v-model="messagePayload" type="textarea" :rows="3" @change="updateExtProp('messagePayload', messagePayload)" :placeholder="t('properties.messagePayloadPlaceholder')" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </template>

      <!-- Business rule task config -->
      <template v-if="taskType === 'bpmn:BusinessRuleTask'">
        <el-collapse-item :title="t('properties.rule')" name="rule">
          <el-form label-position="top" size="small">
            <el-form-item :label="t('properties.ruleEngine')">
              <el-select v-model="ruleEngine" @change="updateExtProp('ruleEngine', ruleEngine)">
                <el-option label="DMN" value="dmn" />
                <el-option label="Drools" value="drools" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('properties.decisionRef')">
              <el-input v-model="decisionRef" @change="updateExtProp('decisionRef', decisionRef)" :placeholder="t('properties.decisionRefPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('properties.ruleResultVariable')">
              <el-input v-model="ruleResultVariable" @change="updateExtProp('ruleResultVariable', ruleResultVariable)" :placeholder="t('properties.ruleResultVariablePlaceholder')" />
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

// Task type
const taskType = ref('bpmn:UserTask')
const taskName = ref('')

// User task config
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

// Service task config
const serviceType = ref<'http' | 'class' | 'expression' | 'delegateExpression'>('http')
const httpUrl = ref('')
const httpMethod = ref('POST')
const javaClass = ref('')
const serviceExpression = ref('')
const delegateExpression = ref('')

// Script task config
const scriptFormat = ref('javascript')
const scriptBody = ref('')
const resultVariable = ref('')

// Message task config
const messageName = ref('')
const messagePayload = ref('')

// Business rule task config
const ruleEngine = ref('dmn')
const decisionRef = ref('')
const ruleResultVariable = ref('')

const basicProps = computed(() => getBasicProperties(props.element))

const taskTypeLabel = computed(() => {
  const names: Record<string, string> = {
    'bpmn:UserTask': t('properties.taskTypeUserTask'),
    'bpmn:ServiceTask': t('properties.taskTypeServiceTask'),
    'bpmn:ScriptTask': t('properties.taskTypeScriptTask'),
    'bpmn:SendTask': t('properties.taskTypeSendTask'),
    'bpmn:ReceiveTask': t('properties.taskTypeReceiveTask'),
    'bpmn:ManualTask': t('properties.taskTypeManualTask'),
    'bpmn:BusinessRuleTask': t('properties.taskTypeBusinessRuleTask'),
    'bpmn:Task': t('properties.taskTypeTask')
  }
  return names[taskType.value] || t('properties.taskTypeTask')
})

function loadProperties() {
  if (!props.element) return
  
  // Get current element type
  const currentType = getElementType(props.element)
  
  const ext = getExtensionProperties(props.element)
  if (currentType === 'bpmn:Task' || currentType === 'bpmn:Activity') {
    taskType.value = ext.taskType || 'bpmn:UserTask'
  } else if (currentType.includes('Task')) {
    taskType.value = currentType
  } else {
    taskType.value = ext.taskType || 'bpmn:UserTask'
  }
  
  // Basic properties
  const basic = getBasicProperties(props.element)
  taskName.value = basic.name
  
  // User task properties
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
  
  // Service task properties
  serviceType.value = ext.serviceType || 'http'
  httpUrl.value = ext.httpUrl || ''
  httpMethod.value = ext.httpMethod || 'POST'
  javaClass.value = ext.javaClass || ''
  serviceExpression.value = ext.serviceExpression || ''
  delegateExpression.value = ext.delegateExpression || ''
  
  // Script task properties
  scriptFormat.value = ext.scriptFormat || 'javascript'
  scriptBody.value = ext.scriptBody || ''
  resultVariable.value = ext.resultVariable || ''
  
  // Message task properties
  messageName.value = ext.messageName || ''
  messagePayload.value = ext.messagePayload || ''
  
  // Business rule task properties
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
