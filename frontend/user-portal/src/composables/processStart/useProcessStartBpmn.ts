import type { Ref } from 'vue'
import type { ProcessNode, ProcessFlow } from '@/components/ProcessDiagram.vue'

// 解析 BPMN XML 并获取开始节点后第一个用户任务的 formId（纯函数，无副作用）
export function parseBpmnXmlAndGetStartFormId(
  xml: string,
): { formId: string | null; formName: string | null; actionIds: string[] | null } {
  if (!xml) return { formId: null, formName: null, actionIds: null }

  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')

    // 查找开始事件
    const allElements = doc.getElementsByTagName('*')
    let startEventId: string | null = null

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()

      if (localName === 'startEvent') {
        startEventId = el.getAttribute('id')
        break
      }
    }

    if (!startEventId) return { formId: null, formName: null, actionIds: null }

    // 查找从开始事件出发的顺序流
    let firstTaskId: string | null = null
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()

      if (localName === 'sequenceFlow') {
        const sourceRef = el.getAttribute('sourceRef')
        if (sourceRef === startEventId) {
          firstTaskId = el.getAttribute('targetRef')
          break
        }
      }
    }

    if (!firstTaskId) return { formId: null, formName: null, actionIds: null }

    // 查找第一个用户任务的 formId、formName 和 actionIds
    let formId: string | null = null
    let formName: string | null = null
    let actionIds: string[] | null = null

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()

      if (localName === 'userTask') {
        const taskId = el.getAttribute('id')

        if (taskId === firstTaskId) {
          // 查找 formId、formName 和 actionIds 属性
          const taskProps = el.getElementsByTagName('*')
          for (let j = 0; j < taskProps.length; j++) {
            const prop = taskProps[j]
            const propLocalName = prop.localName || prop.nodeName.split(':').pop()

            if (propLocalName === 'property' || propLocalName === 'values') {
              const name = prop.getAttribute('name')
              const value = prop.getAttribute('value')

              if (name === 'formId' && value) {
                formId = value
              }
              if (name === 'formName' && value) {
                formName = value
              }
              if (name === 'actionIds' && value) {
                try {
                  // actionIds 格式: "[46,47]" 或 "46,47"
                  const cleaned = value.replace(/[\[\]\s]/g, '')
                  actionIds = cleaned.split(',').filter(Boolean)
                } catch (e) {
                  console.error('Failed to parse actionIds:', value, e)
                }
              }
            }
          }
          break
        }
      }
    }

    return { formId, formName, actionIds }
  } catch (error) {
    console.error('Failed to parse BPMN for start formId:', error)
  }

  return { formId: null, formName: null, actionIds: null }
}

/**
 * 解析 BPMN XML 为流程图节点/连线。写入传入的响应式 refs，
 * 行为与原 start.vue 内联实现逐行一致。
 */
export function createBpmnDiagramParser(deps: {
  t: (key: string, named?: Record<string, unknown>) => string
  processNodes: Ref<ProcessNode[]>
  processFlows: Ref<ProcessFlow[]>
  currentNodeId: Ref<string>
  completedNodeIds: Ref<string[]>
}) {
  const { t, processNodes, processFlows, currentNodeId, completedNodeIds } = deps

  const parseBpmnXml = (xml: string) => {
    if (!xml) return

    try {
      const parser = new DOMParser()
      const doc = parser.parseFromString(xml, 'text/xml')

      const nodes: ProcessNode[] = []
      const flows: ProcessFlow[] = []
      const completed: string[] = []

      // Parse position info from BPMN DI
      const positionMap = new Map<string, { x: number; y: number; width: number; height: number }>()
      doc.querySelectorAll('BPMNShape, bpmndi\\:BPMNShape').forEach(shape => {
        const bpmnElement = shape.getAttribute('bpmnElement')
        if (bpmnElement) {
          const bounds = shape.querySelector('Bounds, dc\\:Bounds')
          if (bounds) {
            positionMap.set(bpmnElement, {
              x: parseFloat(bounds.getAttribute('x') || '0'),
              y: parseFloat(bounds.getAttribute('y') || '0'),
              width: parseFloat(bounds.getAttribute('width') || '100'),
              height: parseFloat(bounds.getAttribute('height') || '80')
            })
          }
        }
      })

      const getParentSubProcessId = (element: Element): string | null => {
        let node: Node | null = element.parentNode
        while (node && node.nodeType === 1) {
          const el = node as Element
          const localName = el.localName || el.nodeName.split(':').pop()
          if (localName === 'subProcess') return el.getAttribute('id')
          if (localName === 'process' || localName === 'definitions') return null
          node = el.parentNode
        }
        return null
      }

      // Pre-parse sequence flows for BFS to find the first userTask
      const seqFlows: Array<{sourceRef: string, targetRef: string}> = []
      doc.querySelectorAll('sequenceFlow').forEach(flow => {
        seqFlows.push({
          sourceRef: flow.getAttribute('sourceRef') || '',
          targetRef: flow.getAttribute('targetRef') || ''
        })
      })

      // Collect element types by ID for BFS traversal
      const allElements = doc.getElementsByTagName('*')
      const elementTypeById = new Map<string, string>()
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const id = el.getAttribute('id')
        const localName = el.localName || el.nodeName.split(':').pop() || ''
        if (id) elementTypeById.set(id, localName)
      }

      // BFS from main-process startEvents to find the first userTask
      let firstUserTaskId = ''
      const mainStartIds: string[] = []
      doc.querySelectorAll('startEvent').forEach(event => {
        if (!getParentSubProcessId(event)) {
          mainStartIds.push(event.getAttribute('id') || '')
        }
      })
      const visited = new Set<string>(mainStartIds)
      const queue = [...mainStartIds]
      while (queue.length > 0 && !firstUserTaskId) {
        const currentId = queue.shift()!
        const elType = elementTypeById.get(currentId)
        if (elType === 'userTask') {
          firstUserTaskId = currentId
          break
        }
        for (const f of seqFlows) {
          if (f.sourceRef === currentId && !visited.has(f.targetRef)) {
            visited.add(f.targetRef)
            queue.push(f.targetRef)
          }
        }
      }

      // Parse start events: main-process starts are completed, subprocess starts are pending
      doc.querySelectorAll('startEvent').forEach((event, index) => {
        const id = event.getAttribute('id') || `start_${index}`
        const name = event.getAttribute('name') || t('task.startNode')
        const pos = positionMap.get(id)
        const parentSpId = getParentSubProcessId(event)
        const status = parentSpId ? 'pending' : 'completed'
        nodes.push({ id, name, type: 'start', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
        if (status === 'completed') completed.push(id)
      })

      currentNodeId.value = firstUserTaskId

      // Parse user tasks: the first userTask is current, rest are pending
      doc.querySelectorAll('userTask').forEach((task, index) => {
        const id = task.getAttribute('id') || `task_${index}`
        const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
        const pos = positionMap.get(id)
        const status = (id === firstUserTaskId) ? 'current' : 'pending'
        nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      })

      // Parse service / send tasks (designer keeps sendTask; deploy converts email sendTask → serviceTask)
      doc.querySelectorAll('serviceTask, sendTask').forEach((task, index) => {
        const id = task.getAttribute('id') || `service_${index}`
        const name = task.getAttribute('name') || t('processStart.serviceFallbackName', { index: index + 1 })
        const pos = positionMap.get(id)
        nodes.push({ id, name, type: 'task', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      })

      // Parse subProcess elements
      const subProcessMap = new Map<string, Element>()
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const localName = el.localName || el.nodeName.split(':').pop()
        if (localName === 'subProcess') {
          const spId = el.getAttribute('id')
          if (spId) subProcessMap.set(spId, el)
        }
      }
      for (const [spId] of subProcessMap) {
        const pos = positionMap.get(spId)
        const sp = subProcessMap.get(spId)!
        const name = sp.getAttribute('name') || ''
        nodes.push({ id: spId, name, type: 'subprocess', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      }

      // Parse gateways
      doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
        const id = gateway.getAttribute('id') || `gateway_${index}`
        const name = gateway.getAttribute('name') || ''
        const pos = positionMap.get(id)
        nodes.push({ id, name, type: 'gateway', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      })

      // Parse end events
      doc.querySelectorAll('endEvent').forEach((event, index) => {
        const id = event.getAttribute('id') || `end_${index}`
        const name = event.getAttribute('name') || t('task.endNode')
        const pos = positionMap.get(id)
        nodes.push({ id, name, type: 'end', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      })

      completedNodeIds.value = completed


      // 解析连线的路径点（waypoints）
      const waypointsMap = new Map<string, Array<{ x: number; y: number }>>()
      const bpmnEdges = doc.querySelectorAll('BPMNEdge, bpmndi\\:BPMNEdge')
      bpmnEdges.forEach(edge => {
        const bpmnElement = edge.getAttribute('bpmnElement')
        if (bpmnElement) {
          const waypoints: Array<{ x: number; y: number }> = []
          const waypointElements = edge.querySelectorAll('waypoint, di\\:waypoint')
          waypointElements.forEach(wp => {
            const x = parseFloat(wp.getAttribute('x') || '0')
            const y = parseFloat(wp.getAttribute('y') || '0')
            waypoints.push({ x, y })
          })
          if (waypoints.length > 0) {
            waypointsMap.set(bpmnElement, waypoints)
          }
        }
      })

      // 解析顺序流
      const sequenceFlows = doc.querySelectorAll('sequenceFlow')
      sequenceFlows.forEach((flow, index) => {
        const id = flow.getAttribute('id') || `flow_${index}`
        const sourceRef = flow.getAttribute('sourceRef') || ''
        const targetRef = flow.getAttribute('targetRef') || ''
        const name = flow.getAttribute('name') || ''
        const waypoints = waypointsMap.get(id)
        flows.push({ id, sourceRef, targetRef, name, waypoints })
      })

      processNodes.value = nodes
      processFlows.value = flows

    } catch (error) {
      console.error('Failed to parse BPMN XML:', error)
    }
  }

  return { parseBpmnXml }
}
