import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailBpmnCurrentFormFns {
  parseBpmnXmlAndGetFormId: (xml: string) => { formId: string | null, formName: string | null }
  parseBpmnXmlAndGetPreviousFormIds: (xml: string) => Array<{ formId: string | null, formName: string | null, taskName: string | null }>
  getSubProcessUserTaskIds: (subProcessId: string) => string[]
}

export function createApplicationDetailBpmnCurrentForm(ctx: ApplicationDetailCtx): ApplicationDetailBpmnCurrentFormFns {
  const { snapshotTaskName, snapshotTaskDefinitionKey, snapshotActivityId, processInfo, bpmnXml } = ctx

  // Parse BPMN XML and get the current node formId and formName
  const parseBpmnXmlAndGetFormId = (xml: string): { formId: string | null, formName: string | null } => {
    if (!xml) return { formId: null, formName: null }

    try {
      const doc = getCachedBpmnDocument(xml)
      if (!doc) return { formId: null, formName: null }
      // Snapshot mode (from Completed Tasks): use snapshotTaskName; otherwise use currentNode
      const currentNodeName = snapshotActivityId.value || snapshotTaskDefinitionKey || snapshotTaskName || processInfo.value.currentNode || ''

      const allElements = doc.getElementsByTagName('*')

      // Collect all userTasks and sequenceFlows
      const tasks = new Map<string, { name: string; formId: string | null; formName: string | null }>()
      const flows: Array<{ source: string; target: string }> = []

      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const localName = el.localName || el.nodeName.split(':').pop()
        if (localName === 'userTask') {
          const id = el.getAttribute('id') || ''
          const name = el.getAttribute('name') || ''
          let formId: string | null = null, formName: string | null = null
          const props = el.getElementsByTagName('*')
          for (let j = 0; j < props.length; j++) {
            const p = props[j]
            const ln = p.localName || p.nodeName.split(':').pop()
            if (ln === 'property' || ln === 'values') {
              const n = p.getAttribute('name'), v = p.getAttribute('value')
              if (n === 'formId' && v) formId = v
              if (n === 'formName' && v) formName = v
            }
          }
          tasks.set(id, { name, formId, formName })
          // Direct match on current node (whitespace-normalized for robustness)
          const normName = currentNodeName.trim().replace(/\s+/g, ' ')
          const normBpmnName = name.trim().replace(/\s+/g, ' ')
          if (normBpmnName === normName || id === currentNodeName) {
            return { formId, formName }
          }
        } else if (localName === 'sequenceFlow') {
          flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
        }
      }

      // Current node is not a userTask (e.g. process completed, currentNode = "End")
      // Find the last userTask: node whose outgoing edges do not point to any other userTask
      const taskIds = new Set(tasks.keys())
      for (const [id, info] of tasks) {
        const outTargets = flows.filter(f => f.source === id).map(f => f.target)
        const hasUserTaskSuccessor = outTargets.some(t => taskIds.has(t))
        if (!hasUserTaskSuccessor) {
          return { formId: info.formId, formName: info.formName }
        }
      }
      // Final fallback: take the last one
      const last = [...tasks.values()].pop()
      if (last) return { formId: last.formId, formName: last.formName }
    } catch (error) {
      console.error('Failed to parse BPMN for formId:', error)
    }

    return { formId: null, formName: null }
  }

  // Parse BPMN XML: return form info bound to all nodes before the current node, in topological order (deduplicated)
  const parseBpmnXmlAndGetPreviousFormIds = (xml: string): Array<{ formId: string | null, formName: string | null, taskName: string | null }> => {
    if (!xml) return []
    try {
      const doc = getCachedBpmnDocument(xml)
      if (!doc) return []
      const allElements = doc.getElementsByTagName('*')
      const currentNodeName = snapshotTaskName || processInfo.value.currentNode || ''

      const tasks = new Map<string, { name: string; formId: string | null; formName: string | null }>()
      const flows: Array<{ source: string; target: string }> = []

      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const localName = el.localName || el.nodeName.split(':').pop()
        if (localName === 'userTask') {
          const id = el.getAttribute('id') || ''
          const name = el.getAttribute('name') || ''
          let formId: string | null = null, formName: string | null = null
          const props = el.getElementsByTagName('*')
          for (let j = 0; j < props.length; j++) {
            const p = props[j]
            const ln = p.localName || p.nodeName.split(':').pop()
            if (ln === 'property' || ln === 'values') {
              const n = p.getAttribute('name'), v = p.getAttribute('value')
              if (n === 'formId' && v) formId = v
              if (n === 'formName' && v) formName = v
            }
          }
          tasks.set(id, { name, formId, formName })
        } else if (localName === 'sequenceFlow') {
          flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
        }
      }
      const taskIds = new Set(tasks.keys())
      let currentId = ''
      const normNodeName = currentNodeName.trim().replace(/\s+/g, ' ')
      for (const [id, info] of tasks) {
        const normInfoName = info.name.trim().replace(/\s+/g, ' ')
        if (normInfoName === normNodeName || id === currentNodeName) { currentId = id; break }
      }
      // If no match (process completed, currentNode = "End"), find the last userTask (no outgoing edges to other userTasks)
      if (!currentId) {
        // Find node with no outgoing edges to other userTasks (the last userTask in the process)
        for (const [id] of tasks) {
          const outTargets = flows.filter(f => f.source === id).map(f => f.target)
          const hasUserTaskSuccessor = outTargets.some(t => taskIds.has(t))
          if (!hasUserTaskSuccessor) { currentId = id; break }
        }
        // Still not found, take the last one
        if (!currentId) currentId = [...tasks.keys()].pop() || ''
      }
      if (!currentId) return []

      // Find startEvent
      let startId = ''
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        if ((el.localName || el.nodeName.split(':').pop()) === 'startEvent') {
          startId = el.getAttribute('id') || ''; break
        }
      }

      const forwardAdj = new Map<string, string[]>()
      for (const f of flows) {
        if (!forwardAdj.has(f.source)) forwardAdj.set(f.source, [])
        forwardAdj.get(f.source)!.push(f.target)
      }

      // BFS from start, collecting userTasks encountered before reaching currentId
      const visited = new Set<string>()
      const queue: string[] = [startId]
      const orderedPrevTaskIds: string[] = []
      visited.add(startId)

      while (queue.length > 0) {
        const node = queue.shift()!
        if (node === currentId) break
        if (tasks.has(node)) orderedPrevTaskIds.push(node)
        for (const next of (forwardAdj.get(node) || [])) {
          if (!visited.has(next)) { visited.add(next); queue.push(next) }
        }
      }

      const result: Array<{ formId: string | null, formName: string | null, taskName: string | null }> = []
      const seenKeys = new Set<string>()
      for (const taskId of orderedPrevTaskIds) {
        const info = tasks.get(taskId)
        if (!info) continue
        // Prefer formId, then formName, finally taskName as fallback key
        const key = info.formId || info.formName || info.name || ''
        if (!key || seenKeys.has(key)) continue
        seenKeys.add(key)
        result.push({ formId: info.formId, formName: info.formName, taskName: info.name || null })
      }
      return result
    } catch (e) {
      console.error('Failed to parse BPMN for previous formIds:', e)
    }
    return []
  }

  const getSubProcessUserTaskIds = (subProcessId: string): string[] => {
    if (!bpmnXml.value || !subProcessId) return []
    try {
      const doc = getCachedBpmnDocument(bpmnXml.value)
      if (!doc) return []
      const allElements = doc.getElementsByTagName('*')
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const localName = el.localName || el.nodeName.split(':').pop()
        if (localName !== 'subProcess' || el.getAttribute('id') !== subProcessId) continue
        const childIds: string[] = []
        const children = el.getElementsByTagName('*')
        for (let j = 0; j < children.length; j++) {
          const childLocalName = children[j].localName || children[j].nodeName.split(':').pop()
          if (childLocalName === 'userTask') {
            const childId = children[j].getAttribute('id')
            if (childId) childIds.push(childId)
          }
        }
        return childIds
      }
    } catch (error) {
      console.warn('Failed to resolve subprocess user tasks:', error)
    }
    return []
  }

  return {
    parseBpmnXmlAndGetFormId,
    parseBpmnXmlAndGetPreviousFormIds,
    getSubProcessUserTaskIds,
  }
}
