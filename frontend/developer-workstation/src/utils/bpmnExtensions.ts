/**
 * BPMN Extension Elements 工具函数
 * 用于读写 BPMN 节点的自定义扩展属性
 */

import type { BpmnElement, BpmnModeler } from '@/types/bpmn'

const CUSTOM_PREFIX = 'custom'

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
  
  // 尝试从 values 获取扩展元素
  const values = extensionElements.values || []
  
  if (!values || values.length === 0) {
    return {}
  }
  
  // 查找 custom:Properties 元素
  let properties = null
  for (const ext of values) {
    const type = ext.$type || ''
    if (type === 'custom:Properties') {
      properties = ext
      break
    }
  }
  
  if (!properties) {
    return {}
  }
  
  // 获取属性值列表
  const propValues = properties.values || []
  
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
    properties = moddle.create(`${CUSTOM_PREFIX}:Properties`, { values: [] })
    extensionElements.values.push(properties)
  }
  
  if (!properties.values) {
    properties.values = []
  }
  
  // 更新或添加属性
  const stringValue = stringifyPropertyValue(value)
  const existingProp = properties.values.find((p: any) => p.name === name)
  
  if (existingProp) {
    existingProp.value = stringValue
  } else {
    const newProp = moddle.create(`${CUSTOM_PREFIX}:Property`, {
      name,
      value: stringValue
    })
    properties.values.push(newProp)
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
  const modeling = modeler.get('modeling')
  const businessObject = element.businessObject
  const extensionElements = businessObject?.extensionElements
  
  if (!extensionElements?.values) return
  
  const properties = extensionElements.values.find(
    (ext: any) => ext.$type === `${CUSTOM_PREFIX}:Properties`
  )
  
  if (!properties?.values) return
  
  const index = properties.values.findIndex((p: any) => p.name === name)
  if (index > -1) {
    properties.values.splice(index, 1)
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
  modeling.updateProperties(element, props)
}
