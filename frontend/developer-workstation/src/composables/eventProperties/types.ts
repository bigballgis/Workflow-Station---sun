/**
 * Shared types for the Event properties panel composables.
 *
 * 行为零变化：这些类型仅描述原 SFC 中已有的局部类型/上下文，未引入新约束。
 */
import type { Ref, ComputedRef } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import type { FormDefinition } from '@/api/functionUnit'
import type { getBasicProperties } from '@/utils/bpmnExtensions'

/** 事件定义类型（与原 SFC 顶层 eventDefinitionType 联合类型逐字一致） */
export type EventDefinitionTypeEnum =
  | 'none'
  | 'timer'
  | 'message'
  | 'signal'
  | 'error'
  | 'terminate'

/** i18n 翻译函数（与 useI18n 的 t 等价签名） */
export type TFn = (key: string, ...args: any[]) => string

/** 组件 props 的只读访问器；composable 内统一通过它读取 element/modeler */
export interface EventPropsAccessor {
  readonly modeler: BpmnModeler
  readonly element: BpmnElement
  readonly functionUnitId: number
}

/**
 * 由状态 composable 暴露、供功能 composable 共享的上下文。
 * 字段与原 SFC 顶层 ref/computed/函数一一对应，名称逐字保留。
 */
export interface EventPropertyContext {
  // Basic info
  eventType: Ref<string>
  eventName: Ref<string>
  eventDefinitionType: Ref<EventDefinitionTypeEnum>

  // Start event config
  startFormId: Ref<number | null>
  initiator: Ref<string>
  forms: Ref<FormDefinition[]>

  // End event config
  endAction: Ref<'none' | 'notify' | 'service'>
  notifyType: Ref<string>
  notifyRecipient: Ref<string>
  notifyContent: Ref<string>
  serviceUrl: Ref<string>
  serviceMethod: Ref<string>

  // Timer event config
  timerType: Ref<'date' | 'duration' | 'cycle'>
  timerValue: Ref<string>

  // Message event config
  messageName: Ref<string>
  correlationKey: Ref<string>

  // Signal event config
  signalName: Ref<string>
  signalScope: Ref<'global' | 'processInstance'>

  // Error event config
  errorCode: Ref<string>
  errorMessage: Ref<string>

  // Computed
  basicProps: ComputedRef<ReturnType<typeof getBasicProperties>>
  isStart: ComputedRef<boolean>
  isEnd: ComputedRef<boolean>
  eventTypeLabel: ComputedRef<string>
  eventDefinitionLabel: ComputedRef<string>
  timerPlaceholder: ComputedRef<string>
  timerTip: ComputedRef<string>

  // Property writers
  updateBasicProp: (name: string, value: any) => void
  updateExtProp: (name: string, value: any) => void

  // i18n 翻译函数
  t: TFn
}
