import { ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ActionDefinition } from '@/api/functionUnit'

interface UseActionNodeBindingOptions {
  functionUnitId: number
  selectedAction: Ref<ActionDefinition | null>
  store: {
    process: { bpmnXml?: string } | null
    fetchProcess: (functionUnitId: number) => Promise<unknown>
    saveProcess: (functionUnitId: number, payload: Record<string, unknown>) => Promise<unknown>
  }
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 动作与流程节点的绑定状态及方法：从 BPMN XML 解析绑定、
 * 节点选择以及把绑定写回 BPMN 并保存流程。
 */
export function useActionNodeBinding(options: UseActionNodeBindingOptions) {
  const { functionUnitId, selectedAction, store, t } = options

  // 存储从BPMN XML解析出的动作绑定信息
  const actionNodeBindings = ref<Map<string | number, Array<{ id: string; name: string }>>>(new Map())

  // 节点绑定相关
  const bindingType = ref<'node' | 'global'>('node')
  const selectedNodeIds = ref<string[]>([])
  const availableNodes = ref<Array<{ id: string; name: string }>>([])
  const savingBinding = ref(false)

  /** Warn once per parse pass — a broken bindings view must be visible, not console-only. */
  let bindingViewWarningShown = false
  function notifyBindingViewIncomplete() {
    if (bindingViewWarningShown) return
    bindingViewWarningShown = true
    ElMessage.warning(t('action.bindingViewIncomplete'))
  }

  /**
   * 从BPMN XML解析动作与节点的绑定关系
   */
  function parseActionBindingsFromBpmn() {
    bindingViewWarningShown = false
    const bindings = new Map<string | number, Array<{ id: string; name: string }>>()
    const nodes: Array<{ id: string; name: string }> = []

    const processDefinition = store.process
    if (!processDefinition?.bpmnXml) {
      actionNodeBindings.value = bindings
      availableNodes.value = nodes
      return
    }

    try {
      const parser = new DOMParser()
      const xmlDoc = parser.parseFromString(processDefinition.bpmnXml, 'text/xml')

      // 查找流程级别的全局动作 - 支持带命名空间
      const allElements = xmlDoc.getElementsByTagName('*')

      // 查找 process 元素
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const localName = el.localName || el.nodeName.split(':').pop()

        if (localName === 'process') {
          // 查找 process 下的 property/values 元素
          const procProps = el.getElementsByTagName('*')
          for (let j = 0; j < procProps.length; j++) {
            const prop = procProps[j]
            const propLocalName = prop.localName || prop.nodeName.split(':').pop()

            if (propLocalName === 'property' || propLocalName === 'values') {
              const name = prop.getAttribute('name')
              const value = prop.getAttribute('value')

              if (name === 'globalActionIds' && value) {
                try {
                  const actionIds = parseActionIds(value)
                  actionIds.forEach(actionId => {
                    if (!bindings.has(actionId)) {
                      bindings.set(actionId, [])
                    }
                    bindings.get(actionId)!.push({ id: 'process', name: t('action.processGlobal') })
                  })
                } catch (e) {
                  // FALLBACK(ux): display-path parse failure — the bindings PANEL shows this
                  // action as unbound while the BPMN itself is untouched. Surface it so the
                  // designer doesn't trust an incomplete view. (parseActionIds has an internal
                  // format fallback and normally never throws; this is a defensive guard.)
                  console.warn('Failed to parse globalActionIds:', value)
                  notifyBindingViewIncomplete()
                }
              }
            }
          }
        }
      }

      // 查找所有userTask节点 - 支持带命名空间
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const localName = el.localName || el.nodeName.split(':').pop()

        if (localName === 'userTask') {
          const taskId = el.getAttribute('id') || ''
          const taskName = el.getAttribute('name') || taskId

          // 添加到可用节点列表
          nodes.push({ id: taskId, name: taskName })

          // 查找 property/values 中的 actionIds
          const taskProps = el.getElementsByTagName('*')
          for (let j = 0; j < taskProps.length; j++) {
            const prop = taskProps[j]
            const propLocalName = prop.localName || prop.nodeName.split(':').pop()

            if (propLocalName === 'property' || propLocalName === 'values') {
              const name = prop.getAttribute('name')
              const value = prop.getAttribute('value')

              if (name === 'actionIds' && value) {
                try {
                  const actionIds = parseActionIds(value)
                  actionIds.forEach(actionId => {
                    if (!bindings.has(actionId)) {
                      bindings.set(actionId, [])
                    }
                    bindings.get(actionId)!.push({ id: taskId, name: taskName })
                  })
                } catch (e) {
                  // FALLBACK(ux): same as globalActionIds above — incomplete panel, BPMN untouched.
                  console.warn('Failed to parse actionIds:', value, e)
                  notifyBindingViewIncomplete()
                }
              }
            }
          }
        }
      }
    } catch (e) {
      // FALLBACK(ux): whole-XML parse failure — the panel would silently show ZERO nodes and
      // bindings, which reads as "nothing is bound". Tell the designer the view is broken.
      console.error('Failed to parse BPMN XML:', e)
      notifyBindingViewIncomplete()
    }

    actionNodeBindings.value = bindings
    availableNodes.value = nodes

    // 调试日志
    console.log('[ActionDesigner] Parsed bindings:', bindings)
    console.log('[ActionDesigner] Available nodes:', nodes)
  }

  /**
   * 解析actionIds - 支持数字ID和字符串ID
   * 格式: [12,22] 或 [action-dl-verify-docs,action-dl-approve-loan]
   */
  function parseActionIds(value: string): Array<string | number> {
    if (!value) return []

    try {
      // 尝试作为JSON解析（数字ID格式）
      const result = JSON.parse(value) as Array<string | number>
      console.log('[ActionDesigner] Parsed as JSON:', value, '->', result)
      return result
    } catch (e) {
      // 如果JSON解析失败，尝试解析字符串ID格式
      // 移除括号和空格: "[id1,id2]" -> "id1,id2"
      const cleaned = value.replace(/[\[\]\s]/g, '')
      if (!cleaned) return []

      // 分割并返回字符串ID数组
      const stringIds = cleaned.split(',').map(s => s.trim()).filter(s => s)
      console.log('[ActionDesigner] Parsed as String IDs:', value, '->', stringIds)
      return stringIds
    }
  }

  function actionIdsListIncludes(list: Array<string | number>, actionId: string | number): boolean {
    return list.some(id => String(id) === String(actionId))
  }

  /**
   * 获取动作绑定的节点列表
   */
  function getActionBoundNodes(actionId: string | number): Array<{ id: string; name: string }> {
    return actionNodeBindings.value.get(actionId) || []
  }

  /**
   * 加载当前动作的绑定信息
   */
  function loadActionBinding(actionId: string | number) {
    const boundNodes = getActionBoundNodes(actionId)

    // 判断是否为全局绑定
    const isGlobal = boundNodes.some(n => n.id === 'process')
    bindingType.value = isGlobal ? 'global' : 'node'

    // 设置已选中的节点
    selectedNodeIds.value = boundNodes
      .filter(n => n.id !== 'process')
      .map(n => n.id)
  }

  /**
   * 保存动作绑定到流程节点
   */
  async function handleSaveBinding() {
    if (!selectedAction.value || !store.process?.bpmnXml) {
      ElMessage.warning(t('action.saveProcessFirst'))
      return
    }

    savingBinding.value = true
    try {
      const actionId = selectedAction.value.id
      const actionName = selectedAction.value.actionName
      let bpmnXml = store.process.bpmnXml

      const parser = new DOMParser()
      const xmlDoc = parser.parseFromString(bpmnXml, 'text/xml')

      // 先从所有节点中移除当前动作
      removeActionFromAllNodes(xmlDoc, actionId)

      if (bindingType.value === 'global') {
        // 添加到流程全局
        addActionToProcess(xmlDoc, actionId, actionName)
      } else {
        // 添加到选中的节点
        selectedNodeIds.value.forEach(nodeId => {
          addActionToNode(xmlDoc, nodeId, actionId, actionName)
        })
      }

      // 序列化XML
      const serializer = new XMLSerializer()
      const newXml = serializer.serializeToString(xmlDoc)

      // 保存到后端
      await store.saveProcess(functionUnitId, {
        ...store.process,
        bpmnXml: newXml
      })

      ElMessage.success(t('action.bindingSaveSuccess'))

      // 重新加载绑定信息
      await store.fetchProcess(functionUnitId)
      parseActionBindingsFromBpmn()
      loadActionBinding(actionId)
    } catch (e: any) {
      console.error('Save binding failed:', e)
      ElMessage.error(e.response?.data?.message || t('action.saveFailed'))
    } finally {
      savingBinding.value = false
    }
  }

  /**
   * 从所有节点中移除指定动作
   */
  function removeActionFromAllNodes(xmlDoc: Document, actionId: string | number) {
    // 从流程全局移除
    const processes = xmlDoc.querySelectorAll('process')
    processes.forEach(proc => {
      const properties = proc.querySelectorAll(':scope > extensionElements > properties > property')
      properties.forEach(prop => {
        const name = prop.getAttribute('name')
        if (name === 'globalActionIds') {
          const value = prop.getAttribute('value')
          if (value) {
            try {
              const currentIds = parseActionIds(value)
              const filteredIds = currentIds.filter(id => String(id) !== String(actionId))
              prop.setAttribute('value', JSON.stringify(filteredIds))

              // 同步更新actionNames
              const namesProp = Array.from(properties).find(p => p.getAttribute('name') === 'globalActionNames')
              if (namesProp) {
                const namesValue = namesProp.getAttribute('value')
                if (namesValue) {
                  try {
                    const names = JSON.parse(namesValue) as string[]
                    const idx = currentIds.findIndex(id => String(id) === String(actionId))
                    if (idx > -1 && names.length > idx) {
                      names.splice(idx, 1)
                      namesProp.setAttribute('value', JSON.stringify(names))
                    }
                  } catch (e) {
                    // The ids array WAS just rewritten above — persisting the old names alongside
                    // new ids desyncs names[i]/ids[i] and nodes then display the WRONG action
                    // names. Clear the names (display falls back to ids, regenerated on rebind)
                    // rather than persist a misaligned pair.
                    console.warn('Failed to parse globalActionNames; clearing to avoid ids/names desync:', namesValue, e)
                    namesProp.setAttribute('value', '[]')
                  }
                }
              }
            } catch (e) {
              console.warn('Failed to parse globalActionIds, skipping node:', value, e)
            }
          }
        }
      })
    })

    // 从所有userTask节点移除
    const userTasks = xmlDoc.querySelectorAll('userTask')
    userTasks.forEach(task => {
      const properties = task.querySelectorAll('property')
      properties.forEach(prop => {
        const name = prop.getAttribute('name')
        if (name === 'actionIds') {
          const value = prop.getAttribute('value')
          if (value) {
            try {
              const currentIds = parseActionIds(value)
              const idx = currentIds.findIndex(id => String(id) === String(actionId))
              if (idx > -1) {
                const filteredIds = currentIds.filter(id => String(id) !== String(actionId))
                prop.setAttribute('value', JSON.stringify(filteredIds))

                // 同步更新actionNames
                const namesProp = Array.from(properties).find(p => p.getAttribute('name') === 'actionNames')
                if (namesProp) {
                  const namesValue = namesProp.getAttribute('value')
                  if (namesValue) {
                    try {
                      const names = JSON.parse(namesValue) as string[]
                      if (names.length > idx) {
                        names.splice(idx, 1)
                        namesProp.setAttribute('value', JSON.stringify(names))
                      }
                    } catch (e) {
                      // Same ids/names desync hazard as globalActionNames above — clear, never
                      // persist a misaligned pair.
                      console.warn('Failed to parse actionNames; clearing to avoid ids/names desync:', namesValue, e)
                      namesProp.setAttribute('value', '[]')
                    }
                  }
                }
              }
            } catch (e) {
              console.warn('Failed to parse actionIds, skipping node:', value, e)
            }
          }
        }
      })
    })
  }

  /**
   * 添加动作到流程全局
   */
  function addActionToProcess(xmlDoc: Document, actionId: string | number, actionName: string) {
    const process = xmlDoc.querySelector('process')
    if (!process) return

    let extensionElements = process.querySelector(':scope > extensionElements')
    if (!extensionElements) {
      extensionElements = xmlDoc.createElementNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'bpmn:extensionElements')
      process.insertBefore(extensionElements, process.firstChild)
    }

    let properties = extensionElements.querySelector('properties')
    if (!properties) {
      properties = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:properties')
      extensionElements.appendChild(properties)
    }

    // 查找或创建globalActionIds属性
    let actionIdsProp = Array.from(properties.querySelectorAll('property')).find(
      p => p.getAttribute('name') === 'globalActionIds'
    )
    let actionNamesProp = Array.from(properties.querySelectorAll('property')).find(
      p => p.getAttribute('name') === 'globalActionNames'
    )

    if (actionIdsProp) {
      const value = actionIdsProp.getAttribute('value')
      const actionIds = value ? (JSON.parse(value) as Array<string | number>) : []
      if (!actionIdsListIncludes(actionIds, actionId)) {
        actionIds.push(actionId)
        actionIdsProp.setAttribute('value', JSON.stringify(actionIds))
      }
    } else {
      actionIdsProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
      actionIdsProp.setAttribute('name', 'globalActionIds')
      actionIdsProp.setAttribute('value', JSON.stringify([actionId]))
      properties.appendChild(actionIdsProp)
    }

    if (actionNamesProp) {
      const value = actionNamesProp.getAttribute('value')
      const names = value ? JSON.parse(value) as string[] : []
      if (!names.includes(actionName)) {
        names.push(actionName)
        actionNamesProp.setAttribute('value', JSON.stringify(names))
      }
    } else {
      actionNamesProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
      actionNamesProp.setAttribute('name', 'globalActionNames')
      actionNamesProp.setAttribute('value', JSON.stringify([actionName]))
      properties.appendChild(actionNamesProp)
    }
  }

  /**
   * 添加动作到指定节点
   */
  function addActionToNode(xmlDoc: Document, nodeId: string, actionId: string | number, actionName: string) {
    const task = xmlDoc.querySelector(`userTask[id="${nodeId}"]`)
    if (!task) return

    let extensionElements = task.querySelector(':scope > extensionElements')
    if (!extensionElements) {
      extensionElements = xmlDoc.createElementNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'bpmn:extensionElements')
      task.insertBefore(extensionElements, task.firstChild)
    }

    let properties = extensionElements.querySelector('properties')
    if (!properties) {
      properties = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:properties')
      extensionElements.appendChild(properties)
    }

    // 查找或创建actionIds属性
    let actionIdsProp = Array.from(properties.querySelectorAll('property')).find(
      p => p.getAttribute('name') === 'actionIds'
    )
    let actionNamesProp = Array.from(properties.querySelectorAll('property')).find(
      p => p.getAttribute('name') === 'actionNames'
    )

    if (actionIdsProp) {
      const value = actionIdsProp.getAttribute('value')
      const actionIds = value ? (JSON.parse(value) as Array<string | number>) : []
      if (!actionIdsListIncludes(actionIds, actionId)) {
        actionIds.push(actionId)
        actionIdsProp.setAttribute('value', JSON.stringify(actionIds))
      }
    } else {
      actionIdsProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
      actionIdsProp.setAttribute('name', 'actionIds')
      actionIdsProp.setAttribute('value', JSON.stringify([actionId]))
      properties.appendChild(actionIdsProp)
    }

    if (actionNamesProp) {
      const value = actionNamesProp.getAttribute('value')
      const names = value ? JSON.parse(value) as string[] : []
      if (!names.includes(actionName)) {
        names.push(actionName)
        actionNamesProp.setAttribute('value', JSON.stringify(names))
      }
    } else {
      actionNamesProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
      actionNamesProp.setAttribute('name', 'actionNames')
      actionNamesProp.setAttribute('value', JSON.stringify([actionName]))
      properties.appendChild(actionNamesProp)
    }
  }

  return {
    actionNodeBindings,
    bindingType,
    selectedNodeIds,
    availableNodes,
    savingBinding,
    parseActionBindingsFromBpmn,
    getActionBoundNodes,
    loadActionBinding,
    handleSaveBinding,
  }
}
