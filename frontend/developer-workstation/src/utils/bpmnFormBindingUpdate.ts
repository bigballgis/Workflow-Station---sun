/**
 * Pure BPMN XML mutation helpers for form ↔ process-node bindings,
 * extracted from FormDesigner.vue. No Vue/store dependencies.
 * Parsing counterpart lives in ./bpmnFormBindings.ts.
 */

import { isTaskElement } from './bpmnFormBindings'

export interface BpmnBoundNode {
  nodeId: string
  nodeName: string
  readOnly: boolean
}

export interface BpmnProcessNode {
  id: string
  name: string
  type: string
}

/**
 * Parse task nodes from BPMN XML (same behaviour as the original
 * loadProcessNodes / loadCreateDialogProcessNodes inline parsing).
 */
export function parseProcessNodesFromBpmnXml(bpmnXml: string, types: string[]): BpmnProcessNode[] {
  const parser = new DOMParser()
  const doc = parser.parseFromString(bpmnXml, 'text/xml')
  const nodes: BpmnProcessNode[] = []
  for (const type of types) {
    const tasks = doc.querySelectorAll(type)
    tasks.forEach(task => {
      nodes.push({
        id: task.getAttribute('id') || '',
        name: task.getAttribute('name') || task.getAttribute('id') || '',
        type
      })
    })
  }
  return nodes
}

/**
 * 更新BPMN XML中多个节点的表单绑定，返回新的 XML 字符串。
 * 先从所有节点移除此表单的绑定属性（formId/formName/formReadOnly），
 * 再为选中节点写入新的绑定。
 */
export function buildUpdatedBpmnFormBindingsXml(
  bpmnXml: string,
  formId: number,
  formName: string,
  nodes: BpmnBoundNode[]
): string {
  console.log('[FormDesigner] updateBpmnFormBindings called with:', { formId, formName, nodesCount: nodes.length, nodes })

  const parser = new DOMParser()
  const xmlDoc = parser.parseFromString(bpmnXml, 'text/xml')

  // 先从所有节点中移除此表单的绑定
  // 使用更通用的方式查找任务节点，支持命名空间
  const allElements = xmlDoc.getElementsByTagName('*')
  const allTasks: Element[] = []
  for (let i = 0; i < allElements.length; i++) {
    const el = allElements[i]
    const localName = el.localName || el.nodeName.split(':').pop()
    if (isTaskElement(localName)) {
      allTasks.push(el)
    }
  }

  let removedCount = 0
  allTasks.forEach(task => {
    const taskId = task.getAttribute('id') || ''
    console.log(`[FormDesigner] Processing task ${taskId} for formId ${formId}`)

    // 使用与 parseFormBindingsFromBpmn 相同的方法查找属性
    // 查找所有后代元素中的 property，而不是只查找直接子元素
    const allProps = task.getElementsByTagName('*')
    const formIdProps: Element[] = []
    const allPropertyElements: Element[] = []

    // 第一步：找到所有 property 或 values 元素并记录
    // 注意：parseFormBindingsFromBpmn 检查的是 localName === 'property' || localName === 'values'
    for (let i = 0; i < allProps.length; i++) {
      const prop = allProps[i]
      const localName = prop.localName || prop.nodeName.split(':').pop()

      // 与 parseFormBindingsFromBpmn 保持一致：检查 property 或 values
      if (localName === 'property' || localName === 'values') {
        allPropertyElements.push(prop)
        const name = prop.getAttribute('name')
        const value = prop.getAttribute('value')
        console.log(`[FormDesigner] Found property/values in task ${taskId}: name=${name}, value=${value}, nodeName=${prop.nodeName}, localName=${localName}`)

        if (name === 'formId' && value === String(formId)) {
          formIdProps.push(prop)
          console.log(`[FormDesigner] ✓ Matched formId property in task ${taskId}: name=${name}, value=${value}`)
        }
      }
    }

    console.log(`[FormDesigner] Task ${taskId}: found ${allPropertyElements.length} total properties, ${formIdProps.length} matching formId=${formId}`)

    if (formIdProps.length > 0) {
      console.log(`[FormDesigner] Found ${formIdProps.length} formId properties in task ${taskId}, removing all related properties...`)

      // 对于每个找到的 formId property，找到它的父 properties 元素
      const processedProperties = new Set<Element>()

      formIdProps.forEach(formIdProp => {
        // 找到包含这个 property 的 properties 元素
        let properties: Element | null = null
        let current: Element | null = formIdProp as Element

        // 向上查找 properties 元素
        while (current && current.parentElement) {
          current = current.parentElement as Element
          const localName = current.localName || current.nodeName.split(':').pop()
          if (localName === 'properties') {
            properties = current
            break
          }
        }

        if (!properties || processedProperties.has(properties)) {
          return
        }

        processedProperties.add(properties)

        // 查找这个 properties 元素下的所有 property 或 values 元素
        // 注意：需要同时查找 property 和 values，因为 XML 中可能使用 values
        const allPropertyElements = properties.getElementsByTagName('*')
        const propsToRemove: Element[] = []

        for (let i = 0; i < allPropertyElements.length; i++) {
          const prop = allPropertyElements[i]
          const propLocalName = prop.localName || prop.nodeName.split(':').pop()

          // 检查是否是 property 或 values 元素
          if (propLocalName === 'property' || propLocalName === 'values') {
            const name = prop.getAttribute('name')
            if (name && ['formId', 'formName', 'formReadOnly'].includes(name)) {
              // 检查这个 property/values 是否在当前 properties 元素下（直接或间接）
              let parent: Element | null = prop.parentElement as Element
              while (parent && parent !== properties) {
                parent = parent.parentElement as Element
              }
              if (parent === properties) {
                propsToRemove.push(prop)
              }
            }
          }
        }

        console.log(`[FormDesigner] Removing ${propsToRemove.length} properties from task ${taskId}`)
        propsToRemove.forEach(p => {
          p.remove()
          removedCount++
        })

        // 如果 properties 为空，移除它
        if (properties.children.length === 0) {
          properties.remove()
          console.log(`[FormDesigner] Removed empty properties from task ${taskId}`)

          // 检查 extensionElements 是否为空
          let extensionElements: Element | null = null
          let current: Element | null = properties.parentElement as Element
          while (current) {
            const localName = current.localName || current.nodeName.split(':').pop()
            if (localName === 'extensionElements') {
              extensionElements = current
              break
            }
            current = current.parentElement as Element
          }

          if (extensionElements && extensionElements.children.length === 0) {
            extensionElements.remove()
            console.log(`[FormDesigner] Removed empty extensionElements from task ${taskId}`)
          }
        }
      })
    } else {
      console.log(`[FormDesigner] No formId=${formId} property found in task ${taskId}`)
    }
  })

  console.log(`[FormDesigner] Total removed ${removedCount} form binding properties`)

  // 为选中的节点添加绑定
  for (const node of nodes) {
    // 使用更通用的方式查找任务节点，支持命名空间
    const allElements = xmlDoc.getElementsByTagName('*')
    let task: Element | null = null
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (isTaskElement(localName) && el.getAttribute('id') === node.nodeId) {
        task = el
        break
      }
    }
    if (!task) {
      console.warn(`[FormDesigner] Task node not found: ${node.nodeId}`)
      continue
    }

    // 获取或创建extensionElements
    let extensionElements: Element | null = null
    const taskChildren = Array.from(task.children)
    for (const child of taskChildren) {
      const localName = child.localName || child.nodeName.split(':').pop()
      if (localName === 'extensionElements') {
        extensionElements = child
        break
      }
    }

    if (!extensionElements) {
      // 创建 extensionElements，使用与现有元素相同的命名空间
      const bpmnNamespace = task.namespaceURI || 'http://www.omg.org/spec/BPMN/20100524/MODEL'
      extensionElements = xmlDoc.createElementNS(bpmnNamespace, 'extensionElements')
      task.insertBefore(extensionElements, task.firstChild)
    }

    // 获取或创建properties
    let properties: Element | null = null
    const extChildren = Array.from(extensionElements.children)
    for (const child of extChildren) {
      const localName = child.localName || child.nodeName.split(':').pop()
      if (localName === 'properties') {
        properties = child
        break
      }
    }

    if (!properties) {
      // 创建 properties，使用自定义命名空间
      const customNamespace = 'http://custom.bpmn.io/schema'
      properties = xmlDoc.createElementNS(customNamespace, 'properties')
      extensionElements.appendChild(properties)
    }

    // 检查是否已存在 formId 属性，如果存在则先移除
    const existingProps = Array.from(properties.children)
    const existingFormId = existingProps.find(p => {
      const localName = p.localName || p.nodeName.split(':').pop()
      if (localName === 'property') {
        return p.getAttribute('name') === 'formId' && p.getAttribute('value') === String(formId)
      }
      return false
    })

    if (existingFormId) {
      // 移除现有的 formId, formName, formReadOnly
      const propsToRemove = existingProps.filter(p => {
        const localName = p.localName || p.nodeName.split(':').pop()
        if (localName === 'property') {
          const name = p.getAttribute('name')
          return name && ['formId', 'formName', 'formReadOnly'].includes(name)
        }
        return false
      })
      propsToRemove.forEach(p => p.remove())
    }

    // 添加formId
    const customNamespace = 'http://custom.bpmn.io/schema'
    const formIdProp = xmlDoc.createElementNS(customNamespace, 'property')
    formIdProp.setAttribute('name', 'formId')
    formIdProp.setAttribute('value', String(formId))
    properties.appendChild(formIdProp)

    // 添加formName
    const formNameProp = xmlDoc.createElementNS(customNamespace, 'property')
    formNameProp.setAttribute('name', 'formName')
    formNameProp.setAttribute('value', formName)
    properties.appendChild(formNameProp)

    // 如果是只读，添加formReadOnly
    if (node.readOnly) {
      const readOnlyProp = xmlDoc.createElementNS(customNamespace, 'property')
      readOnlyProp.setAttribute('name', 'formReadOnly')
      readOnlyProp.setAttribute('value', 'true')
      properties.appendChild(readOnlyProp)
    }
  }

  // 序列化
  const serializer = new XMLSerializer()
  return serializer.serializeToString(xmlDoc)
}
