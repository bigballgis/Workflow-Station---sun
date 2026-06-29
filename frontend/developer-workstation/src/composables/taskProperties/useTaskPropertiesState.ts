/**
 * 通用 Task 节点属性面板的响应式状态与基础动作。
 *
 * 集中托管 SFC 拆分前的全部顶层 ref/computed，以及 updateBasicProp/updateExtProp
 * 与 loadProperties 编排函数。所有取值、默认值与赋值顺序均与拆分前逐字一致，
 * 行为零变化。
 */
import { ref, computed } from 'vue'
import type { Composer } from 'vue-i18n'
import {
  getBasicProperties,
  getExtensionProperties,
  setBasicProperties,
  setExtensionProperty,
  getElementType
} from '@/utils/bpmnExtensions'
import type { TaskPropertiesAccessor } from './useTaskPropertiesForms'
import type { AssigneeType, ServiceType, TimeoutAction } from './types'

export function useTaskPropertiesState(
  props: TaskPropertiesAccessor,
  t: Composer['t']
) {
  // Task type
  const taskType = ref('bpmn:UserTask')
  const taskName = ref('')

  // User task config
  const assigneeType = ref<AssigneeType>('user')
  const assigneeValue = ref('')
  const candidateUsers = ref('')
  const candidateGroups = ref('')
  const formId = ref<number | null>(null)
  const timeoutEnabled = ref(false)
  const timeoutDuration = ref('')
  const timeoutAction = ref<TimeoutAction>('remind')
  const multiInstance = ref(false)
  const sequential = ref(false)
  const collection = ref('')
  const completionCondition = ref('')

  // Service task config
  const serviceType = ref<ServiceType>('http')
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

  // Send task email config
  const connectionId = ref('')
  const emailFrom = ref('')
  const emailTo = ref('')
  const emailCc = ref('')
  const emailBcc = ref('')
  const emailReplyTo = ref('')
  const emailImportance = ref('normal')
  const emailSensitivity = ref('normal')
  const emailSubject = ref('')
  const emailBody = ref('')

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

  const isSendEmailTask = computed(() => {
    if (taskType.value === 'bpmn:SendTask') return true
    if (!props.element) return false
    return getElementType(props.element) === 'bpmn:SendTask'
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

    // Send task email properties
    connectionId.value = ext.connectionId || ''
    emailFrom.value = ext.emailFrom || ''
    emailTo.value = ext.emailTo || ''
    emailCc.value = ext.emailCc || ''
    emailBcc.value = ext.emailBcc || ''
    emailReplyTo.value = ext.emailReplyTo || ''
    emailImportance.value = ext.emailImportance || 'normal'
    emailSensitivity.value = ext.emailSensitivity || 'normal'
    emailSubject.value = ext.emailSubject || ''
    emailBody.value = ext.emailBody || ext.messagePayload || ''

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

  function onEmailConfigChange(name: string, value: unknown) {
    updateExtProp(name, value)
    if (name === 'connectionId' && value) {
      updateExtProp('sendMode', 'email')
    }
  }

  return {
    taskType,
    taskName,
    assigneeType,
    assigneeValue,
    candidateUsers,
    candidateGroups,
    formId,
    timeoutEnabled,
    timeoutDuration,
    timeoutAction,
    multiInstance,
    sequential,
    collection,
    completionCondition,
    serviceType,
    httpUrl,
    httpMethod,
    javaClass,
    serviceExpression,
    delegateExpression,
    scriptFormat,
    scriptBody,
    resultVariable,
    messageName,
    messagePayload,
    connectionId,
    emailFrom,
    emailTo,
    emailCc,
    emailBcc,
    emailReplyTo,
    emailImportance,
    emailSensitivity,
    emailSubject,
    emailBody,
    ruleEngine,
    decisionRef,
    ruleResultVariable,
    basicProps,
    taskTypeLabel,
    isSendEmailTask,
    loadProperties,
    updateBasicProp,
    updateExtProp,
    onEmailConfigChange
  }
}
