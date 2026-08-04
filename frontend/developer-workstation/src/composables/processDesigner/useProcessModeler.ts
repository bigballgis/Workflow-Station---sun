import { ref, shallowRef } from 'vue'
import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  bpmnIoCustomModdleDescriptor,
  workflowPlatformModdleDescriptor,
  flowableModdleDescriptor
} from '@/utils/customModdle'
import { customTranslateModule } from '@/utils/customTranslate'
import { isEmptyBpmnDiagram } from '@/utils/bpmnDiagramContent'

// @ts-ignore - bpmn-js types
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { layoutProcess } from 'bpmn-auto-layout'

interface UseProcessModelerOptions {
  functionUnitId: number
  canvasRef: Ref<HTMLElement | undefined>
  store: {
    process: { bpmnXml?: string } | null
    fetchProcess: (functionUnitId: number) => Promise<unknown>
  }
  /** Called on commandStack.changed to trigger auto-save (wrapper closure breaks the cycle with useProcessActions). */
  onCommandStackChanged: () => void
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * BPMN modeler lifecycle for ProcessDesigner: owns the bpmn-js Modeler
 * instance, builds the default/visual-layout BPMN XML, initializes and
 * destroys the modeler, and exposes the live instance to other composables.
 */
export function useProcessModeler(options: UseProcessModelerOptions) {
  const { functionUnitId, canvasRef, store, onCommandStackChanged, t } = options

  const modelerReady = ref(false)
  const bpmnModelerRef = shallowRef<any>(null)
  /**
   * 画布装的是 import 失败后的兜底默认图，不是这个 FU 的流程。
   * useProcessActions 据此挡住自动保存 —— 兜底图有节点有 shape，空图护栏拦不住它。
   */
  const diagramIsFallback = ref(false)

  let bpmnModeler: any = null

  function getModeler(): any {
    return bpmnModeler
  }

  /** Fallback when no saved BPMN yet (legacy units): unique process id per function unit. */
  function defaultBpmnXml(processElementId: string) {
    const safeId = /^[a-zA-Z][a-zA-Z0-9_.-]*$/.test(processElementId)
      ? processElementId
      : `Process_${functionUnitId}`
    return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  id="Definitions_1"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="${safeId}" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_1</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${safeId}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="187" y="203" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="400" y="160" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="407" y="203" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="216" y="178" />
        <di:waypoint x="400" y="178" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
  }

  /** AI / backend may attach an empty BPMNDiagram (plane only). bpmn-js imports it but renders nothing. */
  async function ensureBpmnHasVisualLayout(bpmnXml: string): Promise<string> {
    if (!bpmnXml || /BPMNShape/i.test(bpmnXml)) {
      return bpmnXml
    }
    try {
      return await layoutProcess(bpmnXml)
    } catch (e) {
      console.warn('bpmn-auto-layout failed, using raw BPMN XML', e)
      return bpmnXml
    }
  }

  async function initModeler() {
    if (!canvasRef.value) return

    try {
      bpmnModeler = new BpmnModeler({
        container: canvasRef.value,
        // Scope diagram-js keyboard bindings to the canvas (it is tabindex="0", so
        // clicking the diagram focuses it). Binding to `document` — the bpmn-js
        // default suggestion — makes diagram-js swallow Cmd/Ctrl+C / V / A on the
        // WHOLE page: its KeyboardBindings preventDefault() every hit outside an
        // input, which killed text copying in the AI Generate panel and any other
        // overlay rendered while the designer is mounted.
        keyboard: {
          bindTo: canvasRef.value
        },
        moddleExtensions: {
          custom: workflowPlatformModdleDescriptor,
          custom_1: bpmnIoCustomModdleDescriptor,
          flowable: flowableModdleDescriptor
        },
        additionalModules: [
          customTranslateModule
        ]
      })

      // Load existing process or default
      await store.fetchProcess(functionUnitId)
      const fallbackProcessId = `Process_${functionUnitId}`
      let xml = store.process?.bpmnXml || defaultBpmnXml(fallbackProcessId)
      xml = await ensureBpmnHasVisualLayout(xml)

      console.log('Loading BPMN XML:', xml)

      try {
        const result = await bpmnModeler.importXML(xml)
        console.log('Import result:', result)
      } catch (importErr: any) {
        const importMessage = String(importErr?.message || '')
        const missingDiagram = importMessage.includes('no diagram to display')
        if (!missingDiagram) {
          throw importErr
        }

        // Some AI-generated BPMN XML may contain semantic nodes but no BPMN DI section,
        // which bpmn-js cannot render directly.
        console.warn('BPMN XML has no DI diagram info, falling back to default diagram')
        await bpmnModeler.importXML(defaultBpmnXml(fallbackProcessId))
        // 已存流程还在库里，画布上的只是占位默认图 —— 标记出来，别让自动保存覆盖它。
        if (!isEmptyBpmnDiagram(store.process?.bpmnXml)) {
          diagramIsFallback.value = true
        }
        ElMessage.warning(t('process.initializationFailed'))
      }

      // Check if connections exist
      const elementRegistry = bpmnModeler.get('elementRegistry')
      const connections = elementRegistry.filter((element: any) => element.type === 'bpmn:SequenceFlow')
      console.log('Connections found:', connections.length, connections)

      bpmnModelerRef.value = bpmnModeler
      modelerReady.value = true

      // Fit to viewport after import
      const canvas = bpmnModeler.get('canvas')
      canvas.zoom('fit-viewport')

      // Listen for changes and trigger auto-save
      bpmnModeler.on('commandStack.changed', () => {
        onCommandStackChanged()
      })

    } catch (err: any) {
      console.error('Failed to initialize BPMN modeler:', err)
      ElMessage.error(t('process.initializationFailed') + ': ' + (err.message || t('common.error')))
    }
  }

  function destroyModeler() {
    if (bpmnModeler) {
      bpmnModeler.destroy()
      bpmnModeler = null
    }
  }

  return {
    modelerReady,
    bpmnModelerRef,
    diagramIsFallback,
    getModeler,
    initModeler,
    destroyModeler,
  }
}
