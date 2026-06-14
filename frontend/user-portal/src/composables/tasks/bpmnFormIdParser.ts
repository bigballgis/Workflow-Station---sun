import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'

/**
 * Pure BPMN form-id extraction helpers. Behaviour is identical to the inline implementations that
 * previously lived in `useBpmnParser`; the composable now delegates to these, passing the current
 * task identity explicitly instead of closing over `options.taskInfo`.
 */

export function parseBpmnFormId(
  xml: string,
  currentTaskDefinitionKey: string,
  currentTaskName: string,
): { formId: string | null; formName: string | null; readOnly: boolean } {
  if (!xml) return { formId: null, formName: null, readOnly: false }
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return { formId: null, formName: null, readOnly: false }
    const allElements = doc.getElementsByTagName('*')
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if ((el.localName || el.nodeName.split(':').pop()) !== 'userTask') continue
      const bpmnId = el.getAttribute('id') || ''
      const bpmnName = el.getAttribute('name') || ''
      const isMatch = (currentTaskDefinitionKey && bpmnId === currentTaskDefinitionKey)
        || (!currentTaskDefinitionKey && bpmnName === currentTaskName)
      if (!isMatch) continue
      let formId: string | null = null
      let formName: string | null = null
      let readOnly = false
      const taskProps = el.getElementsByTagName('*')
      for (let j = 0; j < taskProps.length; j++) {
        const prop = taskProps[j]
        const ln = prop.localName || prop.nodeName.split(':').pop()
        if (ln !== 'property' && ln !== 'values') continue
        const n = prop.getAttribute('name'), v = prop.getAttribute('value')
        if (n === 'formId' && v) formId = v
        if (n === 'formName' && v) formName = v
        if (n === 'formReadOnly' && v === 'true') readOnly = true
      }
      return { formId, formName, readOnly }
    }
  } catch (error) {
    console.error('Failed to parse BPMN for formId:', error)
  }
  return { formId: null, formName: null, readOnly: false }
}

export function parsePreviousFormIds(
  xml: string,
  currentTaskDefinitionKey: string,
  currentTaskName: string,
): Array<{ formId: string | null; formName: string | null; taskName: string | null }> {
  if (!xml) return []
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return []
    const allElements = doc.getElementsByTagName('*')

    /**
     * Track each node's nearest ancestor subProcess so we can:
     *   - skip startEvents nested inside MI subProcess bodies (only main-process starts seed BFS),
     *   - stop BFS when entering the subProcess that owns the current MI sub-task — otherwise siblings
     *     after the MI subProcess would leak in as "previous" forms.
     */
    const parentSubProcOf = new Map<string, string | null>()
    const tasks = new Map<string, { name: string; formId: string | null; formName: string | null }>()
    const flows: Array<{ source: string; target: string }> = []
    const subProcessElementMap = new Map<string, Element>()

    const localNameOf = (el: Element): string => el.localName || el.nodeName.split(':').pop() || ''

    const getParentSubProcessId = (el: Element): string | null => {
      let p: Node | null = el.parentNode
      while (p && p.nodeType === 1) {
        const pe = p as Element
        if (localNameOf(pe) === 'subProcess') return pe.getAttribute('id') || null
        p = pe.parentNode
      }
      return null
    }

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const ln = localNameOf(el)
      const id = el.getAttribute('id') || ''
      if (id && (ln === 'userTask' || ln === 'startEvent' || ln === 'endEvent' || ln === 'subProcess'
          || ln === 'serviceTask' || ln === 'task' || ln === 'exclusiveGateway' || ln === 'parallelGateway'
          || ln === 'inclusiveGateway' || ln === 'eventBasedGateway'
          || ln === 'intermediateCatchEvent' || ln === 'intermediateThrowEvent')) {
        parentSubProcOf.set(id, getParentSubProcessId(el))
      }
      if (ln === 'subProcess' && id) {
        subProcessElementMap.set(id, el)
      }
      if (ln === 'userTask') {
        const name = el.getAttribute('name') || ''
        let formId: string | null = null, formName: string | null = null
        const props = el.getElementsByTagName('*')
        for (let j = 0; j < props.length; j++) {
          const pn = localNameOf(props[j])
          if (pn === 'property' || pn === 'values') {
            const n = props[j].getAttribute('name'), v = props[j].getAttribute('value')
            if (n === 'formId' && v) formId = v
            if (n === 'formName' && v) formName = v
          }
        }
        tasks.set(id, { name, formId, formName })
      } else if (ln === 'sequenceFlow') {
        flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
      }
    }

    let currentId = ''
    for (const [id, info] of tasks) {
      if ((currentTaskDefinitionKey && id === currentTaskDefinitionKey) || (!currentTaskDefinitionKey && info.name === currentTaskName)) {
        currentId = id; break
      }
    }
    if (!currentId) return []

    /**
     * Stop BFS as soon as we step into the subProcess hosting the current MI sub-task — otherwise
     * a later sibling userTask in the main process (after the MI subProcess) would be misreported
     * as "previous" because BFS would queue it via the subProcess → next sequenceFlow.
     */
    const stopSubProcessId = parentSubProcOf.get(currentId) || null

    const forwardAdj = new Map<string, string[]>()
    for (const f of flows) {
      if (!forwardAdj.has(f.source)) forwardAdj.set(f.source, [])
      forwardAdj.get(f.source)!.push(f.target)
    }

    /**
     * All descendant userTask ids of a subProcess (transitive — handles nested subProcesses).
     * Used to surface inner tasks of completed/upstream subProcesses (MI or otherwise) as previous
     * forms once main-flow BFS passes the subProcess box. Without this, MI participants' submitted
     * values would only be reachable via implicit same-key transfer (Path 1) but never displayed
     * in the readonly "previous form" snapshot of a downstream main-flow user task.
     */
    const innerUserTaskIdsOf = (spId: string): string[] => {
      const el = subProcessElementMap.get(spId)
      if (!el) return []
      const inner = el.getElementsByTagName('*')
      const result: string[] = []
      for (let i = 0; i < inner.length; i++) {
        if (localNameOf(inner[i]) !== 'userTask') continue
        const tid = inner[i].getAttribute('id') || ''
        if (tid) result.push(tid)
      }
      return result
    }

    const mainStartIds: string[] = []
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if (localNameOf(el) !== 'startEvent') continue
      const id = el.getAttribute('id') || ''
      if (!id) continue
      if ((parentSubProcOf.get(id) || null) !== null) continue
      mainStartIds.push(id)
    }

    const visited = new Set<string>()
    const queue: string[] = []
    for (const s of mainStartIds) {
      if (!visited.has(s)) { visited.add(s); queue.push(s) }
    }
    const orderedPrevTaskIds: string[] = []
    const orderedPrevTaskIdSet = new Set<string>()
    const recordPrev = (id: string) => {
      if (id === currentId) return
      if (!tasks.has(id)) return
      if (orderedPrevTaskIdSet.has(id)) return
      orderedPrevTaskIdSet.add(id)
      orderedPrevTaskIds.push(id)
    }
    while (queue.length > 0) {
      const node = queue.shift()!
      if (node === currentId) break
      if (stopSubProcessId && node === stopSubProcessId) break
      if (tasks.has(node)) recordPrev(node)
      /**
       * When BFS reaches a subProcess box on the main flow (and it is NOT the boundary that hosts the
       * current task), all its descendant userTask completed during the MI expansion — surface them
       * so the downstream main-flow task's "previous form" panel sees every participant's submitted
       * snapshot. Without this, MI subtask values can only be brought forward by same-key implicit
       * transfer (Path 1), never shown as a readonly upstream-form panel (Path 2).
       */
      if (subProcessElementMap.has(node) && node !== stopSubProcessId) {
        for (const innerTaskId of innerUserTaskIdsOf(node)) {
          recordPrev(innerTaskId)
        }
      }
      for (const next of (forwardAdj.get(node) || [])) {
        if (!visited.has(next)) { visited.add(next); queue.push(next) }
      }
    }

    /**
     * Intra-MI: when the current task lives inside a subProcess, the outer BFS short-circuits at
     * `stopSubProcessId` and never reaches sibling inner userTasks that flow into the current one
     * (e.g. sequential MI with multiple inner user tasks — sub form1 → sub form2). Run a second BFS
     * seeded from the inner startEvent(s) of the boundary subProcess so earlier inner tasks become
     * available as previous forms.
     */
    if (stopSubProcessId) {
      const innerStartIds: string[] = []
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        if (localNameOf(el) !== 'startEvent') continue
        const id = el.getAttribute('id') || ''
        if (!id) continue
        if ((parentSubProcOf.get(id) || null) !== stopSubProcessId) continue
        innerStartIds.push(id)
      }
      const innerVisited = new Set<string>()
      const innerQueue: string[] = []
      for (const s of innerStartIds) {
        if (!innerVisited.has(s)) { innerVisited.add(s); innerQueue.push(s) }
      }
      while (innerQueue.length > 0) {
        const node = innerQueue.shift()!
        if (node === currentId) break
        if (tasks.has(node)) recordPrev(node)
        for (const next of (forwardAdj.get(node) || [])) {
          if (!innerVisited.has(next)) { innerVisited.add(next); innerQueue.push(next) }
        }
      }
    }

    const result: Array<{ formId: string | null; formName: string | null; taskName: string | null }> = []
    const seenKeys = new Set<string>()
    for (const taskId of orderedPrevTaskIds) {
      const info = tasks.get(taskId)
      if (!info) continue
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
