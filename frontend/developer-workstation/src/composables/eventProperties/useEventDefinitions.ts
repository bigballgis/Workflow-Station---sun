/**
 * Event 属性面板的事件定义读写与配置处理逻辑。
 *
 * 涵盖属性加载（loadProperties）、事件定义探测、Timer/Message/Signal/Error
 * 定义的读取与写回、通知 / 服务 / 表单配置处理，以及表单列表加载。行为零变化。
 */
import { functionUnitApi } from '@/api/functionUnit'
import {
  getExtensionProperties,
  getElementType
} from '@/utils/bpmnExtensions'
import type { EventPropertyContext, EventPropsAccessor } from './types'

export function useEventDefinitions(
  props: EventPropsAccessor,
  ctx: EventPropertyContext
) {
  const {
    eventType,
    eventName,
    eventDefinitionType,
    startFormId,
    initiator,
    forms,
    endAction,
    notifyType,
    notifyRecipient,
    notifyContent,
    serviceUrl,
    serviceMethod,
    timerType,
    timerValue,
    messageName,
    correlationKey,
    signalName,
    signalScope,
    errorCode,
    errorMessage,
    updateExtProp
  } = ctx

  function loadProperties() {
    if (!props.element) return

    const currentType = getElementType(props.element)
    const ext = getExtensionProperties(props.element)

    if (currentType.includes('Event')) {
      eventType.value = currentType
    } else {
      eventType.value = ext.eventType || 'bpmn:StartEvent'
    }

    const basic = ctx.basicProps.value
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

  return {
    loadProperties,
    detectEventDefinition,
    loadTimerDefinition,
    loadMessageDefinition,
    loadSignalDefinition,
    loadErrorDefinition,
    handleStartFormChange,
    updateNotifyConfig,
    updateServiceConfig,
    setTimerValue,
    updateTimerDefinition,
    updateMessageDefinition,
    updateSignalDefinition,
    updateErrorDefinition,
    loadForms
  }
}
