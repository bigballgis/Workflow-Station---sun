import { watch, type Ref } from 'vue'
import type { ProcessNode } from '@/components/ProcessDiagram.vue'
import { processApi, type CalledFunctionUnitInstance } from '@/api/process'

/**
 * Colours a request's Function Unit call steps by how the calls they started are doing.
 *
 * The diagram parsers decide a node's status from the request's current step and its task
 * history, and neither says anything about a call step: while the call runs, the current step
 * is the *called* unit's task (a name not in this diagram), and history records only user
 * tasks. So call steps were never coloured — not current while running, not completed after.
 *
 * The called instances themselves are the reliable source (one request per page, only when
 * the diagram has a call step): a step whose call is running is current, one whose calls all
 * finished is completed, one with a rejected call is rejected. A step that has not been
 * reached is left pending, which keeps its own call-step styling.
 *
 * Runs after either parser has produced its nodes, so neither parser needs to know about
 * calls; applying it twice is a no-op.
 */
export function useCallActivityDiagramStatus(deps: {
  processInstanceId: Ref<string | undefined | null>
  bpmnXml: Ref<string>
  processNodes: Ref<ProcessNode[]>
  currentNodeId: Ref<string>
}) {
  let calls: CalledFunctionUnitInstance[] = []
  let loadedFor = ''

  const apply = () => {
    const merged = mergeCallActivityStatuses(deps.bpmnXml.value, deps.processNodes.value, calls)
    if (Array.isArray(merged)) return
    deps.processNodes.value = merged.nodes
    if (merged.currentId && !deps.currentNodeId.value) {
      deps.currentNodeId.value = merged.currentId
    }
  }

  const load = async () => {
    const id = deps.processInstanceId.value
    const xml = deps.bpmnXml.value
    if (!id || !hasCallActivity(xml) || loadedFor === id) return
    loadedFor = id
    try {
      const res = await processApi.getCalledFunctionUnits(id)
      calls = Array.isArray(res) ? res : ((res as { data?: CalledFunctionUnitInstance[] })?.data ?? [])
    } catch (error) {
      // The diagram is still correct without call colouring; never block it on this.
      console.warn('Could not load called Function Units for the diagram', error)
      calls = []
    }
    apply()
  }

  watch([deps.processInstanceId, deps.bpmnXml], () => { void load() }, { immediate: true })
  // The parsers replace the node list on every re-parse; re-apply onto the new list.
  watch(deps.processNodes, apply)

  return { reload: async () => { loadedFor = ''; await load() } }
}

export function hasCallActivity(xml: string | undefined | null): boolean {
  return typeof xml === 'string' && /<(?:\w+:)?callActivity\b/.test(xml)
}

type CallStatus = ProcessNode['status']

/** One call step's status from the instances it started; undefined when it has not been reached. */
export function callActivityStatus(instances: CalledFunctionUnitInstance[]): CallStatus | undefined {
  if (instances.length === 0) return undefined
  const statuses = instances.map(i => String(i.status || '').toUpperCase())
  if (statuses.some(s => s === 'RUNNING' || s === 'SUSPENDED')) return 'current'
  if (statuses.some(s => s === 'REJECTED')) return 'rejected'
  return 'completed'
}

/**
 * The node list with every call step present and carrying its call status. Returns the same
 * array when nothing changes, so callers can skip the write (and the watch it would trigger).
 */
export function mergeCallActivityStatuses(
  xml: string,
  nodes: ProcessNode[],
  calls: CalledFunctionUnitInstance[],
): ProcessNode[] | { nodes: ProcessNode[]; currentId: string } {
  if (!hasCallActivity(xml)) return nodes
  let doc: Document
  try {
    doc = new DOMParser().parseFromString(xml, 'text/xml')
  } catch {
    return nodes
  }

  const byStep = new Map<string, CalledFunctionUnitInstance[]>()
  for (const call of calls) {
    if (!call.callActivityId) continue
    const list = byStep.get(call.callActivityId) ?? []
    list.push(call)
    byStep.set(call.callActivityId, list)
  }

  const next = [...nodes]
  let changed = false
  let currentId = ''
  for (const el of Array.from(doc.getElementsByTagName('*'))) {
    if ((el.localName || el.nodeName.split(':').pop()) !== 'callActivity') continue
    const id = el.getAttribute('id')
    if (!id) continue
    const status: CallStatus = callActivityStatus(byStep.get(id) ?? []) ?? 'pending'
    if (status === 'current' && !currentId) currentId = id

    const index = next.findIndex(n => n.id === id)
    if (index === -1) {
      next.push({ id, name: el.getAttribute('name') || '', type: 'task', status })
      changed = true
    } else if (next[index].status !== status) {
      next[index] = { ...next[index], status }
      changed = true
    }
  }
  return changed ? { nodes: next, currentId } : nodes
}
