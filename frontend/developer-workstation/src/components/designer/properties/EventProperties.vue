<template>
  <div class="event-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item :title="t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.eventId')">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="t('common.type')">
            <el-input :model-value="eventTypeLabel" disabled />
          </el-form-item>
          <el-form-item :label="t('common.type')">
            <el-input :model-value="eventDefinitionLabel" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.eventName')">
            <el-input v-model="eventName" @change="updateBasicProp('name', eventName)" :placeholder="t('properties.eventName')" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Start event config -->
      <el-collapse-item v-if="isStart" :title="t('properties.startConfig')" name="start">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.startForm')">
            <el-select v-model="startFormId" @change="handleStartFormChange" :placeholder="t('properties.selectStartForm')" clearable>
              <el-option v-for="form in forms" :key="form.id" :label="form.formName" :value="form.id" />
            </el-select>
            <div class="form-tip">{{ t('properties.startFormTip') }}</div>
          </el-form-item>
          <el-form-item :label="t('properties.initiatorVariable')">
            <el-input v-model="initiator" @change="updateExtProp('initiator', initiator)" placeholder="initiator" />
            <div class="form-tip">{{ t('properties.initiatorVariableTip') }}</div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- End event config -->
      <el-collapse-item v-if="isEnd" :title="t('properties.endConfig')" name="end">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.endAction')">
            <el-select v-model="endAction" @change="updateExtProp('endAction', endAction)">
              <el-option :label="t('properties.noAction')" value="none" />
              <el-option :label="t('properties.notify')" value="notify" />
              <el-option :label="t('properties.callService')" value="service" />
            </el-select>
          </el-form-item>
          <template v-if="endAction === 'notify'">
            <el-form-item :label="t('properties.notifyType')">
              <el-select v-model="notifyType" @change="updateNotifyConfig">
                <el-option :label="t('properties.email')" value="email" />
                <el-option :label="t('properties.sms')" value="sms" />
                <el-option :label="t('properties.inApp')" value="message" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('properties.notifyRecipient')">
              <el-input v-model="notifyRecipient" @change="updateNotifyConfig" placeholder="${initiator}" />
            </el-form-item>
            <el-form-item :label="t('properties.notifyContent')">
              <el-input v-model="notifyContent" type="textarea" :rows="3" @change="updateNotifyConfig" :placeholder="t('properties.notifyContentPlaceholder')" />
            </el-form-item>
          </template>
          <template v-if="endAction === 'service'">
            <el-form-item :label="t('properties.serviceUrl')">
              <el-input v-model="serviceUrl" @change="updateServiceConfig" placeholder="https://api.example.com/callback" />
            </el-form-item>
            <el-form-item :label="t('properties.requestMethod')">
              <el-select v-model="serviceMethod" @change="updateServiceConfig">
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
      
      <!-- Timer event config -->
      <el-collapse-item v-if="eventDefinitionType === 'timer'" :title="t('properties.timerConfig')" name="timer">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.timerType')">
            <el-select v-model="timerType" @change="updateTimerDefinition">
              <el-option :label="t('properties.timerTypeDate')" value="date" />
              <el-option :label="t('properties.timerTypeDuration')" value="duration" />
              <el-option :label="t('properties.timerTypeCycle')" value="cycle" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('properties.timerExpression')">
            <el-input v-model="timerValue" @change="updateTimerDefinition" :placeholder="timerPlaceholder" />
            <div class="form-tip">{{ timerTip }}</div>
          </el-form-item>
          <div class="timer-examples">
            <div class="examples-title">{{ t('properties.expressionExamples') }}</div>
            <div v-if="timerType === 'date'" class="example-item" @click="setTimerValue('2024-12-31T23:59:59')">
              <code>2024-12-31T23:59:59</code>
              <span>{{ t('properties.specificDateTime') }}</span>
            </div>
            <div v-if="timerType === 'duration'" class="example-item" @click="setTimerValue('PT1H')">
              <code>PT1H</code>
              <span>{{ t('properties.afterOneHour') }}</span>
            </div>
            <div v-if="timerType === 'duration'" class="example-item" @click="setTimerValue('P1D')">
              <code>P1D</code>
              <span>{{ t('properties.afterOneDay') }}</span>
            </div>
            <div v-if="timerType === 'cycle'" class="example-item" @click="setTimerValue('R3/PT10M')">
              <code>R3/PT10M</code>
              <span>{{ t('properties.every10MinTimes3') }}</span>
            </div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Message event config -->
      <el-collapse-item v-if="eventDefinitionType === 'message'" :title="t('properties.message')" name="message">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.messageName')">
            <el-input v-model="messageName" @change="updateMessageDefinition" placeholder="order.created" />
          </el-form-item>
          <el-form-item :label="t('properties.correlationKey')">
            <el-input v-model="correlationKey" @change="updateExtProp('correlationKey', correlationKey)" placeholder="${orderId}" />
            <div class="form-tip">{{ t('properties.correlationKeyTip') }}</div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Signal event config -->
      <el-collapse-item v-if="eventDefinitionType === 'signal'" :title="t('properties.signalConfig')" name="signal">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.signalName')">
            <el-input v-model="signalName" @change="updateSignalDefinition" placeholder="approval.completed" />
          </el-form-item>
          <el-form-item :label="t('properties.signalScope')">
            <el-select v-model="signalScope" @change="updateExtProp('signalScope', signalScope)">
              <el-option :label="t('properties.signalScopeGlobal')" value="global" />
              <el-option :label="t('properties.signalScopeProcessInstance')" value="processInstance" />
            </el-select>
            <div class="form-tip">{{ t('properties.signalScopeTip') }}</div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Error event config -->
      <el-collapse-item v-if="eventDefinitionType === 'error'" :title="t('properties.errorConfig')" name="error">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.errorCode')">
            <el-input v-model="errorCode" @change="updateErrorDefinition" placeholder="ERR_001" />
          </el-form-item>
          <el-form-item :label="t('properties.errorMessage')">
            <el-input v-model="errorMessage" @change="updateExtProp('errorMessage', errorMessage)" :placeholder="t('properties.errorMessagePlaceholder')" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
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

const activeGroups = ref(['basic', 'start', 'end', 'timer', 'message', 'signal', 'error'])

const eventType = ref('bpmn:StartEvent')
const eventName = ref('')
const eventDefinitionType = ref<'none' | 'timer' | 'message' | 'signal' | 'error' | 'terminate'>('none')

const startFormId = ref<number | null>(null)
const initiator = ref('initiator')
const forms = ref<FormDefinition[]>([])

const endAction = ref<'none' | 'notify' | 'service'>('none')
const notifyType = ref('email')
const notifyRecipient = ref('')
const notifyContent = ref('')
const serviceUrl = ref('')
const serviceMethod = ref('POST')

const timerType = ref<'date' | 'duration' | 'cycle'>('duration')
const timerValue = ref('')

const messageName = ref('')
const correlationKey = ref('')

const signalName = ref('')
const signalScope = ref<'global' | 'processInstance'>('global')

const errorCode = ref('')
const errorMessage = ref('')

const basicProps = computed(() => getBasicProperties(props.element))
const isStart = computed(() => eventType.value === 'bpmn:StartEvent')
const isEnd = computed(() => eventType.value === 'bpmn:EndEvent')

const eventTypeLabel = computed(() => {
  const names: Record<string, string> = {
    'bpmn:StartEvent': t('properties.eventTypeStartEvent'),
    'bpmn:EndEvent': t('properties.eventTypeEndEvent'),
    'bpmn:IntermediateCatchEvent': t('properties.eventTypeIntermediateCatchEvent'),
    'bpmn:IntermediateThrowEvent': t('properties.eventTypeIntermediateThrowEvent'),
    'bpmn:BoundaryEvent': t('properties.eventTypeBoundaryEvent')
  }
  return names[eventType.value] || t('properties.eventTypeEvent')
})

const eventDefinitionLabel = computed(() => {
  const names: Record<string, string> = {
    'none': t('properties.eventDefNone'),
    'timer': t('properties.eventDefTimer'),
    'message': t('properties.eventDefMessage'),
    'signal': t('properties.eventDefSignal'),
    'error': t('properties.eventDefError'),
    'terminate': t('properties.eventDefTerminate')
  }
  return names[eventDefinitionType.value] || t('properties.eventDefNone')
})

const timerPlaceholder = computed(() => {
  const placeholders: Record<string, string> = {
    date: '2024-12-31T23:59:59',
    duration: 'PT1H',
    cycle: 'R3/PT10M'
  }
  return placeholders[timerType.value]
})

const timerTip = computed(() => {
  const tips: Record<string, string> = {
    date: t('properties.timerTipDate'),
    duration: t('properties.timerTipDuration'),
    cycle: t('properties.timerTipCycle')
  }
  return tips[timerType.value]
})

function loadProperties() {
  if (!props.element) return
  
  const currentType = getElementType(props.element)
  const ext = getExtensionProperties(props.element)
  
  if (currentType.includes('Event')) {
    eventType.value = currentType
  } else {
    eventType.value = ext.eventType || 'bpmn:StartEvent'
  }
  
  const basic = getBasicProperties(props.element)
  eventName.value = basic.name
  
  detectEventDefinition()
  
  startFormId.value = ext.formId || null
  initiator.value = ext.initiator || 'initiator'
  
  endAction.value = ext.endAction || 'none'
  const notifyConfig = ext.notifyConfig || {}
  notifyType.value = notifyConfig.type || 'email'
  notifyRecipient.value = notifyConfig.recipient || ''
  notifyContent.value = notifyConfig.content || ''
  const serviceConfig = ext.serviceConfig || {}
  serviceUrl.value = serviceConfig.url || ''
  serviceMethod.value = serviceConfig.method || 'POST'
  
  timerType.value = ext.timerType || 'duration'
  loadTimerDefinition()
  
  correlationKey.value = ext.correlationKey || ''
  loadMessageDefinition()
  
  signalScope.value = ext.signalScope || 'global'
  loadSignalDefinition()
  
  errorMessage.value = ext.errorMessage || ''
  loadErrorDefinition()
}

function detectEventDefinition() {
  const bo = props.element?.businessObject
  const eventDefs = bo?.eventDefinitions || []
  
  if (eventDefs.length === 0) {
    eventDefinitionType.value = 'none'
    return
  }
  
  const def = eventDefs[0]
  const defType = def.$type
  
  if (defType === 'bpmn:TimerEventDefinition') {
    eventDefinitionType.value = 'timer'
  } else if (defType === 'bpmn:MessageEventDefinition') {
    eventDefinitionType.value = 'message'
  } else if (defType === 'bpmn:SignalEventDefinition') {
    eventDefinitionType.value = 'signal'
  } else if (defType === 'bpmn:ErrorEventDefinition') {
    eventDefinitionType.value = 'error'
  } else if (defType === 'bpmn:TerminateEventDefinition') {
    eventDefinitionType.value = 'terminate'
  } else {
    eventDefinitionType.value = 'none'
  }
}

function loadTimerDefinition() {
  const bo = props.element?.businessObject
  const timerDef = bo?.eventDefinitions?.find((def: any) => def.$type === 'bpmn:TimerEventDefinition')
  if (timerDef) {
    timerValue.value = timerDef.timeDuration?.body || timerDef.timeDate?.body || timerDef.timeCycle?.body || ''
  }
}

function loadMessageDefinition() {
  const bo = props.element?.businessObject
  const msgDef = bo?.eventDefinitions?.find((def: any) => def.$type === 'bpmn:MessageEventDefinition')
  if (msgDef?.messageRef) {
    messageName.value = msgDef.messageRef.name || ''
  }
}

function loadSignalDefinition() {
  const bo = props.element?.businessObject
  const sigDef = bo?.eventDefinitions?.find((def: any) => def.$type === 'bpmn:SignalEventDefinition')
  if (sigDef?.signalRef) {
    signalName.value = sigDef.signalRef.name || ''
  }
}

function loadErrorDefinition() {
  const bo = props.element?.businessObject
  const errDef = bo?.eventDefinitions?.find((def: any) => def.$type === 'bpmn:ErrorEventDefinition')
  if (errDef?.errorRef) {
    errorCode.value = errDef.errorRef.errorCode || ''
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

function handleStartFormChange(id: number | null) {
  updateExtProp('formId', id)
  const form = forms.value.find(f => f.id === id)
  if (form) {
    updateExtProp('formName', form.formName)
  }
}

function updateNotifyConfig() {
  updateExtProp('notifyConfig', {
    type: notifyType.value,
    recipient: notifyRecipient.value,
    content: notifyContent.value
  })
}

function updateServiceConfig() {
  updateExtProp('serviceConfig', {
    url: serviceUrl.value,
    method: serviceMethod.value
  })
}

function setTimerValue(value: string) {
  timerValue.value = value
  updateTimerDefinition()
}

function updateTimerDefinition() {
  if (!props.element || !props.modeler) return
  
  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')
  const bo = props.element.businessObject
  
  const timerDef = bo.eventDefinitions?.find((def: any) => def.$type === 'bpmn:TimerEventDefinition')
  if (!timerDef) return
  
  timerDef.timeDuration = undefined
  timerDef.timeDate = undefined
  timerDef.timeCycle = undefined
  
  if (timerValue.value) {
    const expression = moddle.create('bpmn:FormalExpression', { body: timerValue.value })
    
    if (timerType.value === 'date') {
      timerDef.timeDate = expression
    } else if (timerType.value === 'duration') {
      timerDef.timeDuration = expression
    } else {
      timerDef.timeCycle = expression
    }
  }
  
  modeling.updateProperties(props.element, { eventDefinitions: bo.eventDefinitions })
  updateExtProp('timerType', timerType.value)
}

function updateMessageDefinition() {
  if (!props.element || !props.modeler) return
  
  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')
  const bo = props.element.businessObject
  
  const msgDef = bo.eventDefinitions?.find((def: any) => def.$type === 'bpmn:MessageEventDefinition')
  if (!msgDef) return
  
  if (messageName.value) {
    const message = moddle.create('bpmn:Message', { name: messageName.value })
    msgDef.messageRef = message
  } else {
    msgDef.messageRef = undefined
  }
  
  modeling.updateProperties(props.element, { eventDefinitions: bo.eventDefinitions })
}

function updateSignalDefinition() {
  if (!props.element || !props.modeler) return
  
  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')
  const bo = props.element.businessObject
  
  const sigDef = bo.eventDefinitions?.find((def: any) => def.$type === 'bpmn:SignalEventDefinition')
  if (!sigDef) return
  
  if (signalName.value) {
    const signal = moddle.create('bpmn:Signal', { name: signalName.value })
    sigDef.signalRef = signal
  } else {
    sigDef.signalRef = undefined
  }
  
  modeling.updateProperties(props.element, { eventDefinitions: bo.eventDefinitions })
}

function updateErrorDefinition() {
  if (!props.element || !props.modeler) return
  
  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')
  const bo = props.element.businessObject
  
  const errDef = bo.eventDefinitions?.find((def: any) => def.$type === 'bpmn:ErrorEventDefinition')
  if (!errDef) return
  
  if (errorCode.value) {
    const error = moddle.create('bpmn:Error', { errorCode: errorCode.value })
    errDef.errorRef = error
  } else {
    errDef.errorRef = undefined
  }
  
  modeling.updateProperties(props.element, { eventDefinitions: bo.eventDefinitions })
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
.event-properties {
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
