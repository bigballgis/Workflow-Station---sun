/**
 * Event 属性面板的共享状态与基础读写。
 *
 * 持有原 SFC 顶层的全部 ref/computed，以及 updateBasicProp/updateExtProp；
 * 由功能 composable（事件定义读写）共享。行为零变化。
 */
import { ref, computed } from 'vue'
import type { FormDefinition } from '@/api/functionUnit'
import {
  getBasicProperties,
  setBasicProperties,
  setExtensionProperty
} from '@/utils/bpmnExtensions'
import type {
  EventDefinitionTypeEnum,
  EventPropertyContext,
  EventPropsAccessor,
  TFn
} from './types'

export function useEventState(
  props: EventPropsAccessor,
  t: TFn
): EventPropertyContext {
  const eventType = ref('bpmn:StartEvent')
  const eventName = ref('')
  const eventDefinitionType = ref<EventDefinitionTypeEnum>('none')

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
      date: '2026-12-31T23:59:59',
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

  function updateBasicProp(name: string, value: any) {
    if (!props.element || !props.modeler) return
    setBasicProperties(props.modeler, props.element, { [name]: value })
  }

  function updateExtProp(name: string, value: any) {
    if (!props.element || !props.modeler) return
    setExtensionProperty(props.modeler, props.element, name, value)
  }

  return {
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
    basicProps,
    isStart,
    isEnd,
    eventTypeLabel,
    eventDefinitionLabel,
    timerPlaceholder,
    timerTip,
    updateBasicProp,
    updateExtProp,
    t
  }
}
