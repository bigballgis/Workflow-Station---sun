import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'

// Parse all userTask-bound forms in BPMN graph order (BFS from startEvent through sequenceFlows
// then descend into entered subProcesses). Ensures correct topological order so My Request shows
// y → subform → subform_copy regardless of XML element ordering.
export const parseBpmnXmlAndGetAllFormIds = (xml: string): Array<{ formId: string | null, formName: string | null, taskName: string | null }> => {
  if (!xml) return []
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return []
    const allElements = doc.getElementsByTagName('*')

    type FlowNode = { id: string; localName: string; el: Element; parentSubProc: string | null }
    const nodes = new Map<string, FlowNode>()
    const flows: Array<{ source: string; target: string }> = []

    const localNameOf = (el: Element) => el.localName || el.nodeName.split(':').pop() || ''
    const getDirectParentSubProcessId = (element: Element): string | null => {
      let node: Node | null = element.parentNode
      while (node && node.nodeType === 1) {
        const el = node as Element
        const ln = localNameOf(el)
        if (ln === 'subProcess') return el.getAttribute('id')
        if (ln === 'process' || ln === 'definitions') return null
        node = el.parentNode
      }
      return null
    }

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const ln = localNameOf(el)
      if (ln === 'sequenceFlow') {
        flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
        continue
      }
      const id = el.getAttribute('id')
      if (!id) continue
      if (ln === 'userTask' || ln === 'startEvent' || ln === 'endEvent' || ln === 'subProcess'
          || ln === 'serviceTask' || ln === 'exclusiveGateway' || ln === 'parallelGateway'
          || ln === 'inclusiveGateway' || ln === 'task' || ln === 'eventBasedGateway'
          || ln === 'intermediateCatchEvent' || ln === 'intermediateThrowEvent') {
        nodes.set(id, { id, localName: ln, el, parentSubProc: getDirectParentSubProcessId(el) })
      }
    }

    const adj = new Map<string, string[]>()
    for (const f of flows) {
      if (!f.source || !f.target) continue
      if (!adj.has(f.source)) adj.set(f.source, [])
      adj.get(f.source)!.push(f.target)
    }

    const startIds: string[] = []
    for (const [id, n] of nodes) {
      if (n.localName === 'startEvent' && !n.parentSubProc) startIds.push(id)
    }

    const collectUserTaskInfo = (el: Element): { formId: string | null; formName: string | null; taskName: string | null } => {
      const taskName = el.getAttribute('name') || null
      let formId: string | null = null
      let formName: string | null = null
      const props = el.getElementsByTagName('*')
      for (let j = 0; j < props.length; j++) {
        const p = props[j]
        const ln = localNameOf(p)
        if (ln === 'property' || ln === 'values') {
          const n = p.getAttribute('name')
          const v = p.getAttribute('value')
          if (n === 'formId' && v) formId = v
          if (n === 'formName' && v) formName = v
        }
      }
      return { formId, formName, taskName }
    }

    const result: Array<{ formId: string | null, formName: string | null, taskName: string | null }> = []
    const seenKeys = new Set<string>()
    const visited = new Set<string>()
    const queue: string[] = [...startIds]
    for (const s of startIds) visited.add(s)

    const orderedSubProcessVisits: string[] = []
    while (queue.length > 0) {
      const id = queue.shift()!
      const n = nodes.get(id)
      if (!n) continue
      if (n.localName === 'userTask') {
        const info = collectUserTaskInfo(n.el)
        const key = info.formId || info.formName || info.taskName || ''
        if (key && !seenKeys.has(key)) {
          seenKeys.add(key)
          result.push(info)
        }
      } else if (n.localName === 'subProcess') {
        orderedSubProcessVisits.push(id)
      }
      for (const next of (adj.get(id) || [])) {
        if (!visited.has(next)) { visited.add(next); queue.push(next) }
      }
    }

    // After top-level traversal, descend into each entered subProcess in encounter order
    // so its inner userTasks (e.g. subform_copy in MI) come after the subProcess's siblings.
    for (const spId of orderedSubProcessVisits) {
      const sp = nodes.get(spId)
      if (!sp) continue
      const innerStartIds: string[] = []
      const innerVisited = new Set<string>()
      for (const [id, n] of nodes) {
        if (n.parentSubProc === spId && n.localName === 'startEvent') {
          innerStartIds.push(id)
          innerVisited.add(id)
        }
      }
      const innerQueue = [...innerStartIds]
      while (innerQueue.length > 0) {
        const id = innerQueue.shift()!
        const n = nodes.get(id)
        if (!n) continue
        if (n.localName === 'userTask') {
          const info = collectUserTaskInfo(n.el)
          const key = info.formId || info.formName || info.taskName || ''
          if (key && !seenKeys.has(key)) {
            seenKeys.add(key)
            result.push(info)
          }
        }
        for (const next of (adj.get(id) || [])) {
          if (!innerVisited.has(next)) { innerVisited.add(next); innerQueue.push(next) }
        }
      }
    }

    return result
  } catch (e) {
    console.error('Failed to parse BPMN for all formIds:', e)
    return []
  }
}

/**
 * Map processInfo.currentNode to an index in parseBpmnXmlAndGetAllFormIds order.
 * Flowable often exposes taskDefinitionKey (BPMN userTask id) while the UI shows the task "name";
 * a plain string compare against taskName/formName then misses and subform_copy never appears.
 */
export function findInitiatorCurrentStepIndexInAllOrdered(
  xml: string,
  curRaw: string,
  allOrdered: Array<{ formId: string | null; formName: string | null; taskName: string | null }>
): number | null {
  const norm = (s: string | null | undefined) => (s || '').trim().replace(/\s+/g, ' ')
  const curTrim = String(curRaw || '').trim()
  const curN = norm(curRaw)
  if (!curTrim && !curN) return null

  let idx = allOrdered.findIndex(
    info =>
      norm(info.taskName) === curN ||
      norm(info.formName) === curN ||
      (info.formId != null && String(info.formId) === curTrim)
  )
  if (idx >= 0) return idx

  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return null
    const allElements = doc.getElementsByTagName('*')
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName !== 'userTask') continue
      const taskDefKey = el.getAttribute('id') || ''
      const tname = el.getAttribute('name') || ''
      if (taskDefKey !== curTrim && norm(tname) !== curN) continue
      let formId: string | null = null
      let formName: string | null = null
      const props = el.getElementsByTagName('*')
      for (let j = 0; j < props.length; j++) {
        const p = props[j]
        const ln = p.localName || p.nodeName.split(':').pop()
        if (ln === 'property' || ln === 'values') {
          const n = p.getAttribute('name')
          const v = p.getAttribute('value')
          if (n === 'formId' && v) formId = v
          if (n === 'formName' && v) formName = v
        }
      }
      const hit = allOrdered.findIndex(
        info =>
          (formId != null && info.formId === formId) ||
          (formName != null && norm(info.formName) === norm(formName)) ||
          (norm(info.taskName) === norm(tname) && norm(tname).length > 0)
      )
      if (hit >= 0) return hit
    }
  } catch {
    /* ignore */
  }

  return null
}

/** Find the formId (sourceId) of the MI subtask's userTask from BPMN XML. */
export const findMiSubTaskFormIdFromBpmn = (xml: string): string | null => {
  if (!xml) return null
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return null
    const allElements = doc.getElementsByTagName('*')

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName === 'subProcess') {
        const children = el.getElementsByTagName('*')
        const isMultiInstanceSubProcess = Array.from(children).some(child => {
          const childLocal = child.localName || child.nodeName.split(':').pop()
          return childLocal === 'multiInstanceLoopCharacteristics'
        })
        if (!isMultiInstanceSubProcess) continue

        for (let j = 0; j < children.length; j++) {
          const child = children[j]
          const childLocal = child.localName || child.nodeName.split(':').pop()
          if (childLocal !== 'userTask') continue
          const props = child.getElementsByTagName('*')
          for (let k = 0; k < props.length; k++) {
            const p = props[k]
            const propLocal = p.localName || p.nodeName.split(':').pop()
            if ((propLocal === 'property' || propLocal === 'values') && p.getAttribute('name') === 'formId') {
              return p.getAttribute('value')
            }
          }
        }
      }
      if (localName !== 'userTask') continue

      const children = el.getElementsByTagName('*')
      let isMultiInstance = false
      for (let j = 0; j < children.length; j++) {
        const childLocal = children[j].localName || children[j].nodeName.split(':').pop()
        if (childLocal === 'multiInstanceLoopCharacteristics') {
          isMultiInstance = true
          break
        }
      }
      // Fallback: developer-workstation uses "MI_" prefix convention for multi-instance tasks
      if (!isMultiInstance) {
        const taskId = el.getAttribute('id') || ''
        if (taskId.startsWith('MI_')) {
          isMultiInstance = true
        }
      }
      if (!isMultiInstance) continue

      for (let j = 0; j < children.length; j++) {
        const p = children[j]
        const ln = p.localName || p.nodeName.split(':').pop()
        if ((ln === 'property' || ln === 'values') && p.getAttribute('name') === 'formId') {
          return p.getAttribute('value')
        }
      }
    }
  } catch (e) {
    console.error('Failed to find MI subtask formId from BPMN:', e)
  }
  return null
}
