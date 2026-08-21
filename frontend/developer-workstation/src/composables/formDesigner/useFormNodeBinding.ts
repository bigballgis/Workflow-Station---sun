import { ref, nextTick } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormDefinition } from '@/api/functionUnit'
import { parseBpmnNodeFormBindings } from '@/utils/bpmnFormBindings'
import {
  buildUpdatedBpmnFormBindingsXml,
  parseProcessNodesFromBpmnXml,
  type BpmnBoundNode,
  type BpmnProcessNode,
} from '@/utils/bpmnFormBindingUpdate'

interface UseFormNodeBindingOptions {
  functionUnitId: number
  store: {
    process: { bpmnXml?: string } | null
    fetchProcess: (functionUnitId: number) => Promise<unknown>
    saveProcess: (functionUnitId: number, payload: Record<string, unknown>) => Promise<unknown>
  }
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Form ↔ BPMN process-node binding state and actions for FormDesigner:
 * parses bindings out of the process BPMN XML, drives the bind-node dialog,
 * and persists binding changes back into the BPMN.
 */
export function useFormNodeBinding(options: UseFormNodeBindingOptions) {
  const { functionUnitId, store, t } = options

  // Store form-node bindings parsed from BPMN XML (supports multiple nodes)
  const formNodeBindings = ref<Map<number, BpmnBoundNode[]>>(new Map())
  // Selected nodes in bind dialog
  const selectedBindNodes = ref<BpmnBoundNode[]>([])
  // Key to force checkbox re-render
  const bindDialogKey = ref(0)
  const processNodes = ref<BpmnProcessNode[]>([])
  const bindingForm: Ref<FormDefinition | null> = ref(null)
  const showBindDialog = ref(false)

  /**
   * 从BPMN XML解析表单与节点的绑定关系（支持多节点）
   */
  function parseFormBindingsFromBpmn() {
    const byNode = parseBpmnNodeFormBindings(store.process?.bpmnXml)
    const bindings = new Map<number, BpmnBoundNode[]>()
    for (const b of byNode.values()) {
      if (!bindings.has(b.formId)) {
        bindings.set(b.formId, [])
      }
      bindings.get(b.formId)!.push({ nodeId: b.nodeId, nodeName: b.nodeName, readOnly: b.readOnly })
    }
    formNodeBindings.value = bindings
  }

  /**
   * 获取表单绑定的所有节点信息
   */
  function getFormBoundNodes(formId: number): BpmnBoundNode[] {
    return formNodeBindings.value.get(formId) || []
  }

  /**
   * 检查节点是否被选中
   */
  function isNodeSelected(nodeId: string): boolean {
    return selectedBindNodes.value.some(n => n.nodeId === nodeId)
  }

  /**
   * 检查节点是否为只读
   */
  function isNodeReadOnly(nodeId: string): boolean {
    const node = selectedBindNodes.value.find(n => n.nodeId === nodeId)
    return node?.readOnly || false
  }

  /**
   * 切换节点选中状态
   */
  function toggleNodeSelection(nodeId: string, nodeName: string, selected: boolean) {
    if (selected) {
      if (!isNodeSelected(nodeId)) {
        selectedBindNodes.value.push({ nodeId, nodeName, readOnly: false })
      }
    } else {
      selectedBindNodes.value = selectedBindNodes.value.filter(n => n.nodeId !== nodeId)
    }
  }

  /**
   * 设置节点只读状态
   */
  function setNodeReadOnly(nodeId: string, readOnly: boolean) {
    const node = selectedBindNodes.value.find(n => n.nodeId === nodeId)
    if (node) {
      node.readOnly = readOnly
    }
  }

  async function loadProcessNodes() {
    try {
      await store.fetchProcess(functionUnitId)
      if (store.process?.bpmnXml) {
        processNodes.value = parseProcessNodesFromBpmnXml(store.process.bpmnXml, ['userTask', 'serviceTask'])
      } else {
        processNodes.value = []
      }
    } catch {
      processNodes.value = []
    }
  }

  async function handleBindNode(form: FormDefinition) {
    bindingForm.value = form
    // 确保流程数据是最新的
    await store.fetchProcess(functionUnitId)
    parseFormBindingsFromBpmn()
    // 从BPMN中获取当前绑定信息
    const boundNodes = getFormBoundNodes(form.id)
    selectedBindNodes.value = boundNodes.map(n => ({ ...n }))
    await loadProcessNodes()
    showBindDialog.value = true
  }

  async function handleConfirmBind() {
    if (!bindingForm.value) return
    try {
      console.log('[FormDesigner] Saving bindings for form:', bindingForm.value.id, 'Selected nodes:', selectedBindNodes.value)
      // 更新BPMN XML中的节点formId属性
      if (store.process?.bpmnXml) {
        await updateBpmnFormBindings(
          bindingForm.value.id,
          bindingForm.value.formName,
          selectedBindNodes.value,
          (bindingForm.value.scene ?? 'TASK') as 'TASK' | 'REQUEST',
        )
      }

      // 重新加载流程数据，确保获取最新的 BPMN XML
      await store.fetchProcess(functionUnitId)
      // 重新解析绑定信息，让列表页的「已绑定节点」列立即反映本次保存
      parseFormBindingsFromBpmn()

      // 回写选中状态：对话框重新打开时应显示落库后的绑定，而不是本次的临时勾选。
      const boundNodes = getFormBoundNodes(bindingForm.value.id)
      selectedBindNodes.value.splice(0, selectedBindNodes.value.length, ...boundNodes.map(n => ({ ...n })))
      bindDialogKey.value++
      await nextTick()

      ElMessage.success(t('form.bindSuccess'))
      // Confirm is a commit: close on success. Leaving it open made the toast the only
      // signal that anything happened, and users clicked Confirm again thinking it had not
      // saved. Cancel/close still discards nothing, since the write already happened here.
      showBindDialog.value = false
    } catch (e: any) {
      console.error('[FormDesigner] Save binding failed:', e)
      ElMessage.error(e.response?.data?.message || t('form.bindFailed'))
    }
  }

  /**
   * 更新BPMN XML中多个节点的表单绑定并保存流程
   */
  async function updateBpmnFormBindings(
    formId: number,
    formName: string,
    nodes: BpmnBoundNode[],
    scene: 'TASK' | 'REQUEST' = 'TASK',
  ) {
    if (!store.process?.bpmnXml) return

    const newXml = buildUpdatedBpmnFormBindingsXml(store.process.bpmnXml, formId, formName, nodes, scene)

    console.log('[FormDesigner] Serialized XML length:', newXml.length)
    console.log('[FormDesigner] Saving process with updated BPMN XML')

    await store.saveProcess(functionUnitId, {
      ...store.process,
      bpmnXml: newXml
    })

    console.log('[FormDesigner] Process saved successfully')
  }

  return {
    formNodeBindings,
    selectedBindNodes,
    bindDialogKey,
    processNodes,
    bindingForm,
    showBindDialog,
    parseFormBindingsFromBpmn,
    getFormBoundNodes,
    isNodeSelected,
    isNodeReadOnly,
    toggleNodeSelection,
    setNodeReadOnly,
    loadProcessNodes,
    handleBindNode,
    handleConfirmBind,
  }
}
