import type { ProcessNode } from '@/components/ProcessDiagram.vue'
import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'
import {
  findHumanWorkflowTaskById,
  isHumanWorkflowTask,
} from '@/utils/bpmnHumanWorkflowTasks'

const ck = (s: unknown) => String(s ?? '').trim()
const normLabel = (s: unknown) => ck(s).replace(/\s+/g, ' ')

export type DiagramStatusSuppressMode = 'none' | 'full' | 'draft-return'

export type DiagramHistoryRecord = {
  status?: string
  action?: string
  nodeName?: string
}

function getParentSubProcessIdFromElement(element: Element): string | null {
  let node: Node | null = element.parentNode
  while (node && node.nodeType === 1) {
    const el = node as Element
    const ln = el.localName || el.nodeName.split(':').pop()
    if (ln === 'subProcess') return el.getAttribute('id')
    if (ln === 'process' || ln === 'definitions') return null
    node = el.parentNode
  }
  return null
}

/** BFS from main-process startEvents — first userTask in flow order. */
export function getFirstUserTaskId(xml: string): string {
  const doc = getCachedBpmnDocument(xml)
  if (!doc) return ''
  const seqFlows: Array<{ sourceRef: string; targetRef: string }> = []
  doc.querySelectorAll('sequenceFlow').forEach(flow => {
    seqFlows.push({
      sourceRef: flow.getAttribute('sourceRef') || '',
      targetRef: flow.getAttribute('targetRef') || '',
    })
  })
  const elementTypeById = new Map<string, string>()
  const allElements = doc.getElementsByTagName('*')
  for (let i = 0; i < allElements.length; i++) {
    const el = allElements[i]
    const id = el.getAttribute('id')
    const localName = el.localName || el.nodeName.split(':').pop() || ''
    if (id) elementTypeById.set(id, localName)
  }
  const mainStartIds: string[] = []
  doc.querySelectorAll('startEvent').forEach(event => {
    if (!getParentSubProcessIdFromElement(event)) {
      mainStartIds.push(event.getAttribute('id') || '')
    }
  })
  const visited = new Set<string>(mainStartIds)
  const queue = [...mainStartIds]
  while (queue.length > 0) {
    const currentId = queue.shift()!
    if (elementTypeById.get(currentId) && isHumanWorkflowTask(elementTypeById.get(currentId))) return currentId
    for (const f of seqFlows) {
      if (f.sourceRef === currentId && !visited.has(f.targetRef)) {
        visited.add(f.targetRef)
        queue.push(f.targetRef)
      }
    }
  }
  return ''
}

export function isCurrentNodeFirstUserTask(
  xml: string,
  currentTaskName: string,
  currentTaskDefinitionKey?: string,
): boolean {
  const firstUserTaskId = getFirstUserTaskId(xml)
  if (!firstUserTaskId) return false
  const defKey = ck(currentTaskDefinitionKey)
  const taskName = normLabel(currentTaskName)
  if (defKey && ck(firstUserTaskId) === defKey) return true
  const doc = getCachedBpmnDocument(xml)
  if (!doc) return false
  const firstTaskEl = findHumanWorkflowTaskById(doc, firstUserTaskId)
  const firstTaskName = normLabel(firstTaskEl?.getAttribute('name'))
  return !!taskName && firstTaskName === taskName
}

export function isCompletedHistoryRecord(r: { status?: string; action?: string }): boolean {
  return r.status === 'completed' || r.action === 'approve' || r.action === 'submit'
}

/**
 * After DRAFT (return-to-first-step), strip downstream completed colors but keep current + upstream.
 * RETURN_TO_REQUESTER uses a fully neutral diagram.
 */
export function resolveDiagramStatusSuppressMode(
  xml: string,
  opts: {
    processState?: string
    currentTaskName: string
    currentTaskDefinitionKey?: string
    historyRecords: DiagramHistoryRecord[]
  },
): DiagramStatusSuppressMode {
  if (ck(opts.processState).toUpperCase() === 'RETURN_TO_REQUESTER') return 'full'
  if (!isCurrentNodeFirstUserTask(xml, opts.currentTaskName, opts.currentTaskDefinitionKey)) return 'none'

  const currentTaskName = normLabel(opts.currentTaskName)
  const hasCompletedOtherStep = opts.historyRecords.some((r) => {
    if (!isCompletedHistoryRecord(r)) return false
    const nodeName = normLabel(r.nodeName)
    return !!nodeName && nodeName !== currentTaskName
  })
  if (!hasCompletedOtherStep) return 'none'

  const hasDraftReturn = opts.historyRecords.some((r) => r.action === 'draft')
  if (hasDraftReturn) return 'draft-return'

  const hadPriorFirstStepCompletion = opts.historyRecords.some((r) =>
    normLabel(r.nodeName) === currentTaskName && isCompletedHistoryRecord(r),
  )
  return hadPriorFirstStepCompletion ? 'draft-return' : 'none'
}

function forEachSequenceFlow(doc: Document, fn: (sourceRef: string, targetRef: string) => void) {
  const all = doc.getElementsByTagName('*')
  for (let i = 0; i < all.length; i++) {
    const el = all[i]
    const ln = el.localName || el.nodeName.split(':').pop() || ''
    if (ln !== 'sequenceFlow') continue
    const sourceRef = ck(el.getAttribute('sourceRef'))
    const targetRef = ck(el.getAttribute('targetRef'))
    if (!sourceRef || !targetRef) continue
    fn(sourceRef, targetRef)
  }
}

/** All BPMN elements strictly upstream of {@code anchorId} along sequenceFlow predecessors. */
export function getUpstreamElementIds(xml: string, anchorId: string): Set<string> {
  const doc = getCachedBpmnDocument(xml)
  const anchor = ck(anchorId)
  if (!doc || !anchor) return new Set()
  const reverseAdj = new Map<string, string[]>()
  forEachSequenceFlow(doc, (sourceRef, targetRef) => {
    if (!reverseAdj.has(targetRef)) reverseAdj.set(targetRef, [])
    reverseAdj.get(targetRef)!.push(sourceRef)
  })
  const upstream = new Set<string>()
  const queue = [...(reverseAdj.get(anchor) || [])]
  const visited = new Set<string>(queue)
  while (queue.length > 0) {
    const id = queue.shift()!
    upstream.add(id)
    for (const pred of reverseAdj.get(id) || []) {
      if (!visited.has(pred)) {
        visited.add(pred)
        queue.push(pred)
      }
    }
  }
  return upstream
}

export function applyFullNeutralDiagramStatus(nodes: ProcessNode[]): {
  nodes: ProcessNode[]
  completedNodeIds: string[]
  currentNodeId: string
} {
  return {
    nodes: nodes.map(node => ({ ...node, status: 'pending' as const })),
    completedNodeIds: [],
    currentNodeId: '',
  }
}

/** Resolve BPMN anchor when {@code currentNodeId} ref was not set (e.g. history marked the open task completed). */
export function resolveDraftReturnAnchorId(
  nodes: ProcessNode[],
  preservedCurrentId: string,
): string {
  const preserved = ck(preservedCurrentId)
  if (preserved) return preserved
  const currentNode = nodes.find(n => n.status === 'current')
  return currentNode ? ck(currentNode.id) : ''
}

/** Draft return: current orange; upstream green; downstream prior-pass nodes pending. */
export function applyDraftReturnDiagramStatus(
  nodes: ProcessNode[],
  xml: string,
  currentNodeId: string,
): { nodes: ProcessNode[]; completedNodeIds: string[] } {
  const preservedCurrentId = resolveDraftReturnAnchorId(nodes, currentNodeId)
  const upstreamIds = getUpstreamElementIds(xml, preservedCurrentId)
  const completedUpstream: string[] = []
  const nextNodes = nodes.map(node => {
    const isCurrent = node.status === 'current' || ck(node.id) === ck(preservedCurrentId)
    if (isCurrent) return { ...node, status: 'current' as const }
    const isUpstream = upstreamIds.has(ck(node.id))
    // Upstream nodes (Start, gateways, …) were traversed before the open step — always green.
    if (isUpstream) {
      completedUpstream.push(node.id)
      return { ...node, status: 'completed' as const }
    }
    return { ...node, status: 'pending' as const }
  })
  return { nodes: nextNodes, completedNodeIds: completedUpstream }
}
