<template>
  <div class="event-properties">
    <el-collapse v-model="activeGroups">
      <!-- 基本信息 -->
      <el-collapse-item title="基本信息" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item label="事件ID">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item label="事件类型">
            <el-input :model-value="eventTypeLabel" disabled />
          </el-form-item>
          <el-form-item label="触发类型">
            <el-input :model-value="eventDefinitionLabel" disabled />
          </el-form-item>
          <el-form-item label="事件名称">
            <el-input v-model="eventName" @change="updateBasicProp('name', eventName)" placeholder="事件名称" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 开始事件配置 -->
      <el-collapse-item v-if="isStart" title="启动配置" name="start">
        <el-form label-position="top" size="small">
          <el-form-item label="启动表单">
            <el-select v-model="startFormId" @change="handleStartFormChange" placeholder="选择启动表单" clearable>
              <el-option v-for="form in forms" :key="form.id" :label="form.formName" :value="form.id" />
            </el-select>
            <div class="form-tip">流程启动时显示的表单</div>
          </el-form-item>
          <el-form-item label="发起人变量">
            <el-input v-model="initiator" @change="updateExtProp('initiator', initiator)" placeholder="initiator" />
            <div class="form-tip">存储流程发起人的变量名</div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 结束事件配置 -->
      <el-collapse-item v-if="isEnd" title="结束配置" name="end">
        <el-form label-position="top" size="small">
          <el-form-item label="结束动作">
            <el-select v-model="endAction" @change="updateExtProp('endAction', endAction)">
              <el-option label="无动作" value="none" />
              <el-option label="发送通知" value="notify" />
              <el-option label="调用服务" value="service" />
            </el-select>
          </el-form-item>
          <template v-if="endAction === 'notify'">
            <el-form-item label="通知类型">
              <el-select v-model="notifyType" @change="updateNotifyConfig">
                <el-option label="邮件" value="email" />
                <el-option label="短信" value="sms" />
                <el-option label="站内信" value="message" />
              </el-select>
            </el-form-item>
            <el-form-item label="通知接收人">
              <el-input v-model="notifyRecipient" @change="updateNotifyConfig" placeholder="${initiator}" />
            </el-form-item>
            <el-form-item label="通知内容">
              <el-input v-model="notifyContent" type="textarea" :rows="3" @change="updateNotifyConfig" placeholder="流程已完成" />
            </el-form-item>
          </template>
          <template v-if="endAction === 'service'">
            <el-form-item label="服务URL">
              <el-input v-model="serviceUrl" @change="updateServiceConfig" placeholder="https://api.example.com/callback" />
            </el-form-item>
            <el-form-item label="请求方法">
              <el-select v-model="serviceMethod" @change="updateServiceConfig">
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
              </el-select>
            </el-form-item>
          </template>
        </el-form>
      </el-collapse-item>
      
      <!-- 定时事件配置 -->
      <el-collapse-item v-if="eventDefinitionType === 'timer'" title="定时配置" name="timer">
        <el-form label-position="top" size="small">
          <el-form-item label="定时类型">
            <el-select v-model="timerType" @change="updateTimerDefinition">
              <el-option label="指定日期" value="date" />
              <el-option label="持续时间" value="duration" />
              <el-option label="周期执行" value="cycle" />
            </el-select>
          </el-form-item>
          <el-form-item label="定时表达式">
            <el-input v-model="timerValue" @change="updateTimerDefinition" :placeholder="timerPlaceholder" />
            <div class="form-tip">{{ timerTip }}</div>
          </el-form-item>
          <div class="timer-examples">
            <div class="examples-title">表达式示例</div>
            <div v-if="timerType === 'date'" class="example-item" @click="setTimerValue('2024-12-31T23:59:59')">
              <code>2024-12-31T23:59:59</code>
              <span>指定日期时间</span>
            </div>
            <div v-if="timerType === 'duration'" class="example-item" @click="setTimerValue('PT1H')">
              <code>PT1H</code>
              <span>1小时后</span>
            </div>
            <div v-if="timerType === 'duration'" class="example-item" @click="setTimerValue('P1D')">
              <code>P1D</code>
              <span>1天后</span>
            </div>
            <div v-if="timerType === 'cycle'" class="example-item" @click="setTimerValue('R3/PT10M')">
              <code>R3/PT10M</code>
              <span>每10分钟执行，共3次</span>
            </div>
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- 消息事件配置 -->
      <el-collapse-item v-if="eventDefinitionType === 'message'" title="消息配置" name="message">
        <el-form label-position="top" size="small">
          <el-form-item label="消息名称">
            <el-input v-model="messageName" @change="updateMessageDefinition" placeholder="order.created" />
          </el-form-item>
          <el-form-item label="关联键">
            <el-input v-model="correlationKey" @change="updateExtProp('correlationKey', correlationKey)" placeholder="${orderId}" />
            <div class="form-tip">用于关联消息和流程实例的表达式</div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 信号事件配置 -->
      <el-collapse-item v-if="eventDefinitionType === 'signal'" title="信号配置" name="signal">
        <el-form label-position="top" size="small">
          <el-form-item label="信号名称">
            <el-input v-model="signalName" @change="updateSignalDefinition" placeholder="approval.completed" />
          </el-form-item>
          <el-form-item label="信号范围">
            <el-select v-model="signalScope" @change="updateExtProp('signalScope', signalScope)">
              <el-option label="全局" value="global" />
              <el-option label="流程实例" value="processInstance" />
            </el-select>
            <div class="form-tip">全局信号可跨流程实例传播</div>
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 错误事件配置 -->
      <el-collapse-item v-if="eventDefinitionType === 'error'" title="错误配置" name="error">
        <el-form label-position="top" size="small">
          <el-form-item label="错误代码">
            <el-input v-model="errorCode" @change="updateErrorDefinition" placeholder="ERR_001" />
          </el-form-item>
          <el-form-item label="错误消息">
            <el-input v-model="errorMessage" @change="updateExtProp('errorMessage', errorMessage)" placeholder="处理失败" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
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

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}>()

const activeGroups = ref(['basic', 'start', 'end', 'timer', 'message', 'signal', 'error'])

// 事件类型
const eventType = ref('bpmn:StartEvent')
const eventName = ref('')
const eventDefinitionType = ref<'none' | 'timer' | 'message' | 'signal' | 'error' | 'terminate'>('none')

// 开始事件
const startFormId = ref<number | null>(null)
const initiator = ref('initiator')
const forms = ref<FormDefinition[]>([])

// 结束事件
const endAction = ref<'none' | 'notify' | 'service'>('none')
const notifyType = ref('email')
const notifyRecipient = ref('')
const notifyContent = ref('')
const serviceUrl = ref('')
const serviceMethod = ref('POST')

// 定时事件
const timerType = ref<'date' | 'duration' | 'cycle'>('duration')
const timerValue = ref('')

// 消息事件
const messageName = ref('')
const correlationKey = ref('')

// 信号事件
const signalName = ref('')
const signalScope = ref<'global' | 'processInstance'>('global')

// 错误事件
const errorCode = ref('')
const errorMessage = ref('')

const basicProps = computed(() => getBasicProperties(props.element))

const isStart = computed(() => eventType.value === 'bpmn:StartEvent')
const isEnd = computed(() => eventType.value === 'bpmn:EndEvent')

const eventTypeLabel = computed(() => {
  const names: Record<string, string> = {
    'bpmn:StartEvent': '开始事件',
    'bpmn:EndEvent': '结束事件',
    'bpmn:IntermediateCatchEvent': '中间捕获事件',
    'bpmn:IntermediateThrowEvent': '中间抛出事件',
    'bpmn:BoundaryEvent': '边界事件'
  }
  return names[eventType.value] || '事件'
})

const eventDefinitionLabel = computed(() => {
  const names: Record<string, string> = {
    'none': '无',
    'timer': '定时器',
    'message': '消息',
    'signal': '信号',
    'error': '错误',
    'terminate': '终止'
  }
  return names[eventDefinitionType.value] || '无'
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
    date: 'ISO 8601 日期格式',
    duration: 'ISO 8601 持续时间格式 (PT1H = 1小时, P1D = 1天)',
    cycle: 'ISO 8601 重复格式'
  }
  return tips[timerType.value]
})

function loadProperties() {
  if (!props.element) return
  
  // 获取当前事件类型
  const currentType = getElementType(props.element)
  const ext = getExtensionProperties(props.element)
  
  // 直接使用元素的实际类型
  if (currentType.includes('Event')) {
    eventType.value = currentType
  } else {
    eventType.value = ext.eventType || 'bpmn:StartEvent'
  }
  
  // 基本属性
  const basic = getBasicProperties(props.element)
  eventName.value = basic.name
  
  // 检测事件定义类型
  detectEventDefinition()
  
  // 开始事件属性
  startFormId.value = ext.formId || null
  initiator.value = ext.initiator || 'initiator'
  
  // 结束事件属性
  endAction.value = ext.endAction || 'none'
  const notifyConfig = ext.notifyConfig || {}
  notifyType.value = notifyConfig.type || 'email'
  notifyRecipient.value = notifyConfig.recipient || ''
  notifyContent.value = notifyConfig.content || ''
  const serviceConfig = ext.serviceConfig || {}
  serviceUrl.value = serviceConfig.url || ''
  serviceMethod.value = serviceConfig.method || 'POST'
  
  // 定时事件属性
  timerType.value = ext.timerType || 'duration'
  loadTimerDefinition()
  
  // 消息事件属性
  correlationKey.value = ext.correlationKey || ''
  loadMessageDefinition()
  
  // 信号事件属性
  signalScope.value = ext.signalScope || 'global'
  loadSignalDefinition()
  
  // 错误事件属性
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