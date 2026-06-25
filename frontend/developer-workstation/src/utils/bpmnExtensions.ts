/**
 * BPMN Extension Elements 工具函数
 * 用于读写 BPMN 节点的自定义扩展属性
 */

import { toRaw } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'

const CUSTOM_PREFIX = 'custom'

/**
 * bpmn-js 的 commandStack（modeling.updateProperties 等）会直接读写元素上的
 * `labels` 等只读/不可配置内部属性。当元素来自 Vue 的 reactive 代理时，命令栈对
 * 代理的写入会触发「'get' on proxy: property 'labels' is read-only…」TypeError，
 * 中断后续逻辑（例如切换子表后 Sub-table name 不再回填）。因此凡是要交给 bpmn-js
 * 改写的元素，先用 toRaw 脱壳成原始对象。
 */
function rawElement(element: BpmnElement): BpmnElement {
  return toRaw(element) as BpmnElement
}

/** custom:Properties / custom_1:Properties 在 moddle 中的子项字段名（见 customModdle.ts） */
function getCustomPropertyList(properties: any): any[] {
  const list = properties?.property ?? properties?.values
  return Array.isArray(list) ? list : []
}

/** 从单个 custom:Properties 或 custom_1:Properties 容器解析为键值表 */
function collectExtensionPropsFromContainer(properties: any): Record<string, any> {
  if (!properties) {
    return {}
  }
  const propValues = getCustomPropertyList(properties)
  const result: Record<string, any> = {}
  for (const prop of propValues) {
    const name = prop.name
    const value = prop.value
    if (name) {
      result[name] = parsePropertyValue(value)
    }
  }
  return result
}

/**
 * 解析属性值，支持 JSON 格式
 */
export function parsePropertyValue(value: string): any {
  if (value === undefined || value === null) return value
  if (value === 'true') return true
  if (value === 'false') return false
  if (value === '') return ''
  
  // 尝试解析数字
  const num = Number(value)
  if (!isNaN(num) && value.trim() !== '') return num
  
  // 尝试解析 JSON
  if ((value.startsWith('{') && value.endsWith('}')) || 
      (value.startsWith('[') && value.endsWith(']'))) {
    try {
      return JSON.parse(value)
    } catch {
      return value
    }
  }
  
  return value
}

/**
 * 序列化属性值为字符串
 */
export function stringifyPropertyValue(value: any): string {
  if (value === undefined || value === null) return ''
  if (typeof value === 'boolean') return value.toString()
  if (typeof value === 'number') return value.toString()
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/**
 * 获取元素的所有扩展属性
 *
 * 合并两处来源（见 `customModdle.ts` 中 custom / custom_1）：
 * - **custom_1:Properties**（http://custom.bpmn.io/schema）：表单设计器「绑定流程节点」写入的 formId / formName 等
 * - **custom:Properties**（http://workflow.platform/schema/custom）：流程属性面板写入的受理人、表单等
 *
 * 同名键以 **custom** 为准，避免面板修改被表单侧旧数据覆盖。
 */
export function getExtensionProperties(element: BpmnElement): Record<string, any> {
  const businessObject = element?.businessObject
  if (!businessObject) {
    return {}
  }

  const extensionElements = businessObject.extensionElements
  if (!extensionElements) {
    return {}
  }

  const values = extensionElements.values || []
  if (!values || values.length === 0) {
    return {}
  }

  let fromBpmnIo: Record<string, any> = {}
  let fromPlatform: Record<string, any> = {}

  for (const ext of values) {
    const type = ext.$type || ''
    if (type === 'custom_1:Properties') {
      fromBpmnIo = { ...fromBpmnIo, ...collectExtensionPropsFromContainer(ext) }
    } else if (type === 'custom:Properties') {
      fromPlatform = { ...fromPlatform, ...collectExtensionPropsFromContainer(ext) }
    }
  }

  return { ...fromBpmnIo, ...fromPlatform }
}

/**
 * 获取单个扩展属性
 */
export function getExtensionProperty(element: BpmnElement, name: string): any {
  const props = getExtensionProperties(element)
  return props[name]
}


/**
 * 设置扩展属性
 */
export function setExtensionProperty(
  modeler: BpmnModeler,
  element: BpmnElement,
  name: string,
  value: any
): void {
  element = rawElement(element)
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  const businessObject = element.businessObject

  // 获取或创建 extensionElements
  let extensionElements: any = businessObject.extensionElements
  if (!extensionElements) {
    extensionElements = moddle.create('bpmn:ExtensionElements', { values: [] })
    modeling.updateProperties(element, { extensionElements })
  }
  
  // 确保 values 数组存在
  if (!extensionElements.values) {
    extensionElements.values = []
  }
  
  // 获取或创建 custom:Properties
  let properties: any = extensionElements.values.find(
    (ext: any) => ext.$type === `${CUSTOM_PREFIX}:Properties`
  )
  
  if (!properties) {
    properties = moddle.create(`${CUSTOM_PREFIX}:Properties`, { property: [] })
    extensionElements.values.push(properties)
  }
  
  if (!properties.property) {
    // 兼容旧会话中误用的 values
    if (Array.isArray(properties.values) && properties.values.length) {
      properties.property = properties.values
    } else {
      properties.property = []
    }
  }
  
  // 更新或添加属性
  const stringValue = stringifyPropertyValue(value)
  const list = getCustomPropertyList(properties)
  const existingProp = list.find((p: any) => p.name === name)
  
  if (existingProp) {
    existingProp.value = stringValue
  } else {
    const newProp = moddle.create(`${CUSTOM_PREFIX}:Property`, {
      name,
      value: stringValue
    })
    properties.property.push(newProp)
  }
  
  // 触发更新
  modeling.updateProperties(element, { extensionElements })
}

/**
 * 批量设置扩展属性
 */
export function setExtensionProperties(
  modeler: BpmnModeler,
  element: BpmnElement,
  props: Record<string, any>
): void {
  Object.entries(props).forEach(([name, value]) => {
    setExtensionProperty(modeler, element, name, value)
  })
}

/**
 * 删除扩展属性
 */
export function removeExtensionProperty(
  modeler: BpmnModeler,
  element: BpmnElement,
  name: string
): void {
  element = rawElement(element)
  const modeling = modeler.get('modeling')
  const businessObject = element.businessObject
  const extensionElements = businessObject?.extensionElements

  if (!extensionElements?.values) return

  const properties = extensionElements.values.find(
    (ext: any) => ext.$type === `${CUSTOM_PREFIX}:Properties`
  )

  const list = getCustomPropertyList(properties)
  if (!list.length) return
  
  const index = list.findIndex((p: any) => p.name === name)
  if (index > -1) {
    list.splice(index, 1)
    modeling.updateProperties(element, { extensionElements })
  }
}

/**
 * 清除所有扩展属性
 */
export function clearExtensionProperties(
  modeler: BpmnModeler,
  element: BpmnElement
): void {
  element = rawElement(element)
  const modeling = modeler.get('modeling')
  const businessObject = element.businessObject
  const extensionElements = businessObject?.extensionElements

  if (!extensionElements?.values) return

  const propertiesIndex = extensionElements.values.findIndex(
    (ext: any) => ext.$type === `${CUSTOM_PREFIX}:Properties`
  )
  
  if (propertiesIndex > -1) {
    extensionElements.values.splice(propertiesIndex, 1)
    modeling.updateProperties(element, { extensionElements })
  }
}

/**
 * 获取元素类型
 */
export function getElementType(element: BpmnElement): string {
  return element?.businessObject?.$type || element?.type || ''
}

/**
 * 判断是否为用户任务
 */
export function isUserTask(element: BpmnElement): boolean {
  const type = getElementType(element)
  return type === 'bpmn:UserTask'
}

/**
 * 判断是否为服务任务
 */
export function isServiceTask(element: BpmnElement): boolean {
  const type = getElementType(element)
  return type === 'bpmn:ServiceTask'
}

/**
 * 判断是否为任务（包括 Task, UserTask, ServiceTask, ScriptTask 等）
 */
export function isTask(element: BpmnElement): boolean {
  const type = getElementType(element)
  return type.includes('Task')
}

/**
 * 判断是否为通用任务（不是 UserTask 或 ServiceTask）
 */
export function isGenericTask(element: BpmnElement): boolean {
  const type = getElementType(element)
  return type === 'bpmn:Task' || type === 'bpmn:Activity'
}

/**
 * 判断是否为网关
 */
export function isGateway(element: BpmnElement): boolean {
  const type = getElementType(element)
  return type.includes('Gateway')
}

/**
 * 判断是否为排他网关
 */
export function isExclusiveGateway(element: BpmnElement): boolean {
  return getElementType(element) === 'bpmn:ExclusiveGateway'
}

/**
 * 判断是否为并行网关
 */
export function isParallelGateway(element: BpmnElement): boolean {
  return getElementType(element) === 'bpmn:ParallelGateway'
}

/**
 * 判断是否为连接线
 */
export function isSequenceFlow(element: BpmnElement): boolean {
  return getElementType(element) === 'bpmn:SequenceFlow'
}

/**
 * 判断是否为开始事件
 */
export function isStartEvent(element: BpmnElement): boolean {
  return getElementType(element) === 'bpmn:StartEvent'
}

/**
 * 判断是否为结束事件
 */
export function isEndEvent(element: BpmnElement): boolean {
  return getElementType(element) === 'bpmn:EndEvent'
}

/**
 * 判断是否为事件
 */
export function isEvent(element: BpmnElement): boolean {
  const type = getElementType(element)
  return type.includes('Event')
}

/**
 * 判断是否为流程
 */
export function isProcess(element: BpmnElement): boolean {
  return getElementType(element) === 'bpmn:Process'
}

/**
 * 获取元素的基本属性
 */
export function getBasicProperties(element: BpmnElement): { id: string; name: string } {
  const bo = element?.businessObject
  return {
    id: bo?.id || '',
    name: bo?.name || ''
  }
}

/**
 * 设置元素的基本属性
 */
export function setBasicProperties(
  modeler: BpmnModeler,
  element: BpmnElement,
  props: { id?: string; name?: string }
): void {
  const modeling = modeler.get('modeling')
  modeling.updateProperties(rawElement(element), props)
}
