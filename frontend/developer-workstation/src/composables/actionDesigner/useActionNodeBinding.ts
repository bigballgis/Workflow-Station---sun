import { ref } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { isAxiosError } from 'axios'
import type { ActionDefinition } from '@/api/functionUnit'
import { parseActionIds } from '@/utils/actionIds'
import { normalizeActionBpmnXml, readXmlActionProperties, writeXmlActionBinding } from '@/utils/actionBpmnProperties'

interface ActionBinding { scope: 'global' | 'node'; id: string; name: string }

interface UseActionNodeBindingOptions {
  functionUnitId: number
  selectedAction: Ref<ActionDefinition | null>
  store: {
    process: { bpmnXml?: string } | null
    actions: ActionDefinition[]
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
  const actionNodeBindings = ref<Map<string | number, ActionBinding[]>>(new Map())

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
    const bindings = new Map<string | number, ActionBinding[]>()
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

      if (xmlDoc.getElementsByTagName('parsererror').length) {
        throw new Error(t('action.bindingViewIncomplete'))
      }
      function addBinding(actionId: string | number, node: ActionBinding) {
        const key = String(actionId)
        const bound = bindings.get(key) ?? []
        if (!bound.some(existing => existing.scope === node.scope && existing.id === node.id)) bound.push(node)
        bindings.set(key, bound)
      }
      // Read each scope's own properties; never collect descendant task properties as globals.
      for (const process of Array.from(xmlDoc.getElementsByTagNameNS('*', 'process'))) {
        idsOn(process, 'globalActionIds').forEach(id => addBinding(id, { scope: 'global', id: 'process', name: t('action.processGlobal') }))
      }
      for (const task of Array.from(xmlDoc.getElementsByTagNameNS('*', 'userTask'))) {
        const node: ActionBinding = { scope: 'node', id: task.id, name: task.getAttribute('name') || task.id }
        nodes.push(node)
        idsOn(task, 'actionIds').forEach(id => addBinding(id, node))
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

  function actionIdsListIncludes(list: Array<string | number>, actionId: string | number): boolean {
    return list.some(id => String(id) === String(actionId))
  }

  /**
   * 获取动作绑定的节点列表
   */
  function getActionBoundNodes(actionId: string | number): ActionBinding[] {
    const matches = Array.from(actionNodeBindings.value)
      .filter(([id]) => String(id) === String(actionId))
      .flatMap(([, nodes]) => nodes)
    return Array.from(new Map(matches.map(node => [`${node.scope}:${node.id}`, node])).values())
  }

  /** Global scope supersedes legacy node bindings in the list's scope summary. */
  function getActionBindingSummary(actionId: string | number): ActionBinding[] {
    const nodes = getActionBoundNodes(actionId)
    const global = nodes.find(node => node.scope === 'global')
    return global ? [global] : nodes
  }

  /**
   * 加载当前动作的绑定信息
   */
  function loadActionBinding(actionId: string | number) {
    const boundNodes = getActionBoundNodes(actionId)

    // 判断是否为全局绑定
    const isGlobal = boundNodes.some(n => n.scope === 'global')
    bindingType.value = isGlobal ? 'global' : 'node'

    // 设置已选中的节点
    selectedNodeIds.value = boundNodes
      .filter(n => n.scope === 'node')
      .map(n => n.id)
  }

  /**
   * 保存动作绑定到流程节点
   */
  async function handleSaveBinding(): Promise<boolean> {
    if (!selectedAction.value || !store.process?.bpmnXml) {
      ElMessage.warning(t('action.saveProcessFirst'))
      return false
    }

    savingBinding.value = true
    try {
      const actionId = selectedAction.value.id
      const bpmnXml = normalizeActionBpmnXml(store.process.bpmnXml)

      const parser = new DOMParser()
      const xmlDoc = parser.parseFromString(bpmnXml, 'text/xml')

      // Only remove cancelled bindings: unchanged nodes retain their button position.
      if (xmlDoc.getElementsByTagName('parsererror').length) throw new Error(t('action.bindingViewIncomplete'))
      const wasGlobal = Array.from(xmlDoc.getElementsByTagNameNS('*', 'process'))
        .some(process => actionIdsListIncludes(idsOn(process, 'globalActionIds'), actionId))
      removeActionFromAllNodes(xmlDoc, actionId, wasGlobal)

      if (bindingType.value === 'global') {
        // 添加到流程全局
        addActionToProcess(xmlDoc, actionId)
      } else {
        // 添加到选中的节点
        selectedNodeIds.value.forEach(nodeId => {
          addActionToNode(xmlDoc, nodeId, actionId)
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
      return true
    } catch (e: unknown) {
      console.error('Save binding failed:', e)
      ElMessage.error(isAxiosError<{ message?: string }>(e) && e.response?.data?.message || t('action.saveFailed'))
      return false
    } finally {
      savingBinding.value = false
    }
  }

  function writeBinding(element: Element, key: 'actionIds' | 'globalActionIds', ids: Array<string | number>) {
    const names = ids.map(id => {
      const action = store.actions.find(a => String(a.id) === String(id))
      if (!action) throw new Error(t('action.bindingViewIncomplete'))
      return action.actionName
    })
    writeXmlActionBinding(element, key, ids, names)
  }

  function idsOn(element: Element, key: string): Array<string | number> {
    return parseActionIds(readXmlActionProperties(element)[key])
  }

  function removeActionFromAllNodes(xmlDoc: Document, actionId: string | number, wasGlobal: boolean) {
    for (const process of Array.from(xmlDoc.getElementsByTagNameNS('*', 'process'))) {
      if (bindingType.value === 'global') continue
      const ids = idsOn(process, 'globalActionIds')
      if (actionIdsListIncludes(ids, actionId)) {
        writeBinding(process, 'globalActionIds', ids.filter(id => String(id) !== String(actionId)))
      }
    }
    // Legacy duplicate bindings are inert under global scope. An unchanged save must not erase them.
    if (bindingType.value === 'global' && wasGlobal) return
    for (const task of Array.from(xmlDoc.getElementsByTagNameNS('*', 'userTask'))) {
      if (bindingType.value === 'node' && selectedNodeIds.value.includes(task.id)) continue
      const ids = idsOn(task, 'actionIds')
      if (actionIdsListIncludes(ids, actionId)) {
        writeBinding(task, 'actionIds', ids.filter(id => String(id) !== String(actionId)))
      }
    }
  }

  function addActionToProcess(xmlDoc: Document, actionId: string | number) {
    const process = xmlDoc.getElementsByTagNameNS('*', 'process')[0]
    if (!process) throw new Error(t('action.bindingViewIncomplete'))
    const ids = idsOn(process, 'globalActionIds')
    if (!actionIdsListIncludes(ids, actionId)) writeBinding(process, 'globalActionIds', [...ids, actionId])
  }

  function addActionToNode(xmlDoc: Document, nodeId: string, actionId: string | number) {
    const task = Array.from(xmlDoc.getElementsByTagNameNS('*', 'userTask')).find(node => node.id === nodeId)
    if (!task) throw new Error(t('action.bindingViewIncomplete'))
    const ids = idsOn(task, 'actionIds')
    if (!actionIdsListIncludes(ids, actionId)) writeBinding(task, 'actionIds', [...ids, actionId])
  }

  return {
    actionNodeBindings,
    bindingType,
    selectedNodeIds,
    availableNodes,
    savingBinding,
    parseActionBindingsFromBpmn,
    getActionBoundNodes,
    getActionBindingSummary,
    loadActionBinding,
    handleSaveBinding,
  }
}
