import { ref } from 'vue'
import { fitDiagramWithPadding } from './fitDiagramWithPadding'

interface UseProcessCanvasControlsOptions {
  /** Accessor for the live bpmn-js modeler instance (avoids holding a stale reference). */
  getModeler: () => any
}

/**
 * Canvas viewport controls for ProcessDesigner: zoom in/out, fit-to-viewport,
 * undo/redo, and the debug-node highlight marker that ProcessDebugPanel drives.
 */
export function useProcessCanvasControls(options: UseProcessCanvasControlsOptions) {
  const { getModeler } = options

  const currentZoom = ref(1)
  let highlightedDebugNodeId: string | null = null

  function handleZoomIn() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    const canvas = bpmnModeler.get('canvas')
    currentZoom.value = Math.min(currentZoom.value + 0.1, 3)
    canvas.zoom(currentZoom.value)
  }

  function handleZoomOut() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    const canvas = bpmnModeler.get('canvas')
    currentZoom.value = Math.max(currentZoom.value - 0.1, 0.3)
    canvas.zoom(currentZoom.value)
  }

  function handleFitViewport() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    fitDiagramWithPadding(bpmnModeler)
    currentZoom.value = 1
  }

  function handleUndo() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    const commandStack = bpmnModeler.get('commandStack')
    commandStack.undo()
  }

  function handleRedo() {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    const commandStack = bpmnModeler.get('commandStack')
    commandStack.redo()
  }

  function handleDebugNodeChange(nodeId: string | null) {
    const bpmnModeler = getModeler()
    if (!bpmnModeler) return
    const canvas = bpmnModeler.get('canvas')
    if (highlightedDebugNodeId) {
      canvas.removeMarker(highlightedDebugNodeId, 'debug-current')
      highlightedDebugNodeId = null
    }
    if (!nodeId) return
    const elementRegistry = bpmnModeler.get('elementRegistry')
    const element = elementRegistry.get(nodeId)
    if (!element) return
    canvas.addMarker(nodeId, 'debug-current')
    highlightedDebugNodeId = nodeId
    try {
      canvas.scrollToElement(element, { top: 80, bottom: 80, left: 80, right: 80 })
    } catch {
      // ignore scroll errors for unknown layout nodes
    }
  }

  return {
    currentZoom,
    handleZoomIn,
    handleZoomOut,
    handleFitViewport,
    handleUndo,
    handleRedo,
    handleDebugNodeChange,
  }
}
