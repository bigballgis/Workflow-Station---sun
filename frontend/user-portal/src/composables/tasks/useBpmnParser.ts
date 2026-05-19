import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProcessNode, ProcessFlow } from '@/components/ProcessDiagram.vue'
import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'

const ck = (s: unknown) => String(s ?? '').trim()
const normLabel = (s: unknown) => ck(s).replace(/\s+/g, ' ')

export function useBpmnParser(options: {
  taskInfo: Ref<Record<string, any>>
  historyRecords: Ref<any[]>
  isCompletedTask: Ref<boolean>
}) {
  const { t } = useI18n()

  const processNodes = ref<ProcessNode[]>([])
  const processFlows = ref<ProcessFlow[]>([])
  const completedNodeIds = ref<string[]>([])
  const currentNodeId = ref('')
  const bpmnXml = ref('')

  function parseBpmnXmlAndGetFormId(xml: string): { formId: string | null; formName: string | null; readOnly: boolean } {
    if (!xml) return { formId: null, formName: null, readOnly: false }
    try {
      const doc = getCachedBpmnDocument(xml)
      if (!doc) return { formId: null, formName: null, readOnly: false }
      const currentTaskDefinitionKey = options.taskInfo.value.taskDefinitionKey || ''
      const currentTaskName = options.taskInfo.value.taskName || ''
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

  function parseBpmnXmlAndGetPreviousFormIds(xml: string): Array<{ formId: string | null; formName: string | null; taskName: string | null }> {
    if (!xml) return []
    try {
      const doc = getCachedBpmnDocument(xml)
      if (!doc) return []
      const allElements = doc.getElementsByTagName('*')
      const currentTaskDefinitionKey = options.taskInfo.value.taskDefinitionKey || ''
      const currentTaskName = options.taskInfo.value.taskName || ''
      const tasks = new Map<string, { name: string; formId: string | null; formName: string | null }>()
      const flows: Array<{ source: string; target: string }> = []
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const ln = el.localName || el.nodeName.split(':').pop()
        if (ln === 'userTask') {
          const id = el.getAttribute('id') || ''
          const name = el.getAttribute('name') || ''
          let formId: string | null = null, formName: string | null = null
          const props = el.getElementsByTagName('*')
          for (let j = 0; j < props.length; j++) {
            const pn = props[j].localName || props[j].nodeName.split(':').pop()
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
      const forwardAdj = new Map<string, string[]>()
      for (const f of flows) {
        if (!forwardAdj.has(f.source)) forwardAdj.set(f.source, [])
        forwardAdj.get(f.source)!.push(f.target)
      }
      let startId = ''
      for (let i = 0; i < allElements.length; i++) {
        if ((allElements[i].localName || allElements[i].nodeName.split(':').pop()) === 'startEvent') {
          startId = allElements[i].getAttribute('id') || ''; break
        }
      }
      const visited = new Set<string>()
      const queue: string[] = [startId]
      const orderedPrevTaskIds: string[] = []
      visited.add(startId)
      while (queue.length > 0) {
        const node = queue.shift()!
        if (node === currentId) break
        if (tasks.has(node) && node !== currentId) orderedPrevTaskIds.push(node)
        for (const next of (forwardAdj.get(node) || [])) {
          if (!visited.has(next)) { visited.add(next); queue.push(next) }
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

  function parseBpmnXml(xml: string) {
    if (!xml) return
    try {
      currentNodeId.value = ''
      const doc = getCachedBpmnDocument(xml)
      if (!doc) return
      const nodes: ProcessNode[] = []
      const flows: ProcessFlow[] = []
      const completed: string[] = []

      // Position map from BPMNDiagram
      const positionMap = new Map<string, { x: number; y: number; width: number; height: number }>()
      doc.querySelectorAll('BPMNShape, bpmndi\\:BPMNShape').forEach((shape: Element) => {
        const bpmnElement = shape.getAttribute('bpmnElement')
        const bounds = shape.querySelector('Bounds, dc\\:Bounds')
        if (bpmnElement && bounds) {
          positionMap.set(bpmnElement, {
            x: parseFloat(bounds.getAttribute('x') || '0'),
            y: parseFloat(bounds.getAttribute('y') || '0'),
            width: parseFloat(bounds.getAttribute('width') || '100'),
            height: parseFloat(bounds.getAttribute('height') || '80'),
          })
        }
      })

      const completedHistoryIds = new Set<string>()
      const completedNodeNames = new Set<string>()
      options.historyRecords.value.forEach((record: any) => {
        if (record.nodeId && record.status === 'completed') completedHistoryIds.add(record.nodeId)
        if (record.nodeName && record.status === 'completed') completedNodeNames.add(record.nodeName)
      })

      const hasApproval = options.historyRecords.value.some((h: any) => h.status === 'completed' && h.nodeName?.includes('Approval'))
      const hasRejection = options.historyRecords.value.some((h: any) => h.status === 'rejected')
      const showCurrentStep = !options.isCompletedTask.value
      const currentTaskDefinitionKey = options.taskInfo.value.taskDefinitionKey || ''
      const currentTaskName = options.taskInfo.value.taskName || ''
      let currentNodeFound = false

      const getParentSubProcessId = (element: Element): string | null => {
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

      const allElements = doc.getElementsByTagName('*')
      const subProcessMap = new Map<string, Element>()
      for (let i = 0; i < allElements.length; i++) {
        const ln = allElements[i].localName || allElements[i].nodeName.split(':').pop()
        if (ln === 'subProcess') {
          const spId = allElements[i].getAttribute('id')
          if (spId) subProcessMap.set(spId, allElements[i])
        }
      }

      const enteredSubProcesses = new Set<string>()
      for (const [spId, sp] of subProcessMap) {
        const spName = sp.getAttribute('name') || ''
        if (showCurrentStep && ((spName && normLabel(spName) === normLabel(currentTaskName)) || ck(spId) === ck(currentTaskName))) {
          enteredSubProcesses.add(spId); continue
        }
        if (spName && options.historyRecords.value.some((h: any) => h.nodeName === spName)) {
          enteredSubProcesses.add(spId); continue
        }
        const childElements = sp.getElementsByTagName('*')
        for (let i = 0; i < childElements.length; i++) {
          const childLocal = childElements[i].localName || childElements[i].nodeName.split(':').pop()
          if (childLocal !== 'userTask' && childLocal !== 'serviceTask') continue
          const taskName = childElements[i].getAttribute('name') || ''
          const taskId = childElements[i].getAttribute('id') || ''
          if ((showCurrentStep && normLabel(taskName) === normLabel(currentTaskName)) || options.historyRecords.value.some((h: any) => h.nodeName === taskName || h.nodeId === taskId)) {
            enteredSubProcesses.add(spId); break
          }
        }
      }

      const activeMultiInstanceSubProcesses = new Set<string>()
      for (const [spId, sp] of subProcessMap) {
        if (!enteredSubProcesses.has(spId)) continue
        const spChildren = sp.getElementsByTagName('*')
        let isMultiInstance = false
        for (let i = 0; i < spChildren.length; i++) {
          if ((spChildren[i].localName || spChildren[i].nodeName.split(':').pop()) === 'multiInstanceLoopCharacteristics') {
            isMultiInstance = true; break
          }
        }
        if (!isMultiInstance) continue
        const spName = sp.getAttribute('name') || ''
        if (showCurrentStep && ((spName && normLabel(spName) === normLabel(currentTaskName)) || ck(spId) === ck(currentTaskName))) {
          activeMultiInstanceSubProcesses.add(spId); continue
        }
        for (let i = 0; i < spChildren.length; i++) {
          const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
          if (childLocal !== 'userTask') continue
          const taskName = spChildren[i].getAttribute('name') || ''
          const taskId = spChildren[i].getAttribute('id') || ''
          if ((showCurrentStep && (normLabel(taskName) === normLabel(currentTaskName) || ck(taskId) === ck(currentTaskName) || ck(taskId) === ck(currentTaskDefinitionKey)))
            || (showCurrentStep && options.historyRecords.value.some((h: any) => h.nodeName === taskName && h.status === 'current'))) {
            activeMultiInstanceSubProcesses.add(spId); break
          }
        }
      }

      if (showCurrentStep && !options.isCompletedTask.value) {
        doc.querySelectorAll('userTask').forEach((taskEl: Element) => {
          const uid = ck(taskEl.getAttribute('id'))
          const unameNorm = normLabel(taskEl.getAttribute('name'))
          const defKey = ck(currentTaskDefinitionKey)
          if (unameNorm !== normLabel(currentTaskName) && uid !== ck(currentTaskName) && uid !== defKey && unameNorm !== normLabel(defKey)) return
          let node: Node | null = taskEl.parentNode
          while (node && node.nodeType === 1) {
            const el = node as Element
            const ln = el.localName || el.nodeName.split(':').pop()
            if (ln === 'subProcess') {
              const sid = el.getAttribute('id') || ''
              if (sid && enteredSubProcesses.has(sid)) {
                const descendants = el.getElementsByTagName('*')
                for (let di = 0; di < descendants.length; di++) {
                  if ((descendants[di].localName || descendants[di].nodeName.split(':').pop()) === 'multiInstanceLoopCharacteristics') {
                    activeMultiInstanceSubProcesses.add(sid); break
                  }
                }
              }
            }
            if (ln === 'process' || ln === 'definitions') break
            node = el.parentNode
          }
        })
      }

      const completedMultiInstanceSubProcesses = new Set<string>()
      for (const [spId, sp] of subProcessMap) {
        if (!enteredSubProcesses.has(spId) || activeMultiInstanceSubProcesses.has(spId)) continue
        const spChildren = sp.getElementsByTagName('*')
        let isMultiInstance = false
        for (let i = 0; i < spChildren.length; i++) {
          if ((spChildren[i].localName || spChildren[i].nodeName.split(':').pop()) === 'multiInstanceLoopCharacteristics') {
            isMultiInstance = true; break
          }
        }
        if (!isMultiInstance) continue
        let allDone = true, userTaskCount = 0
        for (let i = 0; i < spChildren.length; i++) {
          const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
          if (childLocal !== 'userTask') continue
          userTaskCount++
          const taskName = spChildren[i].getAttribute('name') || ''
          const taskId = spChildren[i].getAttribute('id') || ''
          const hm = options.historyRecords.value.find((h: any) => h.nodeName === taskName || h.nodeId === taskId)
          if (!hm || (hm.status !== 'completed' && hm.status !== 'rejected')) { allDone = false; break }
        }
        if (userTaskCount > 0 && allDone) completedMultiInstanceSubProcesses.add(spId)
      }

      const isDescendantOfActiveMiSubProcess = (element: Element): boolean => {
        let node: Node | null = element.parentNode
        while (node && node.nodeType === 1) {
          const el = node as Element
          const ln = el.localName || el.nodeName.split(':').pop()
          if (ln === 'subProcess') {
            const sid = el.getAttribute('id') || ''
            if (sid && activeMultiInstanceSubProcesses.has(sid)) return true
          }
          if (ln === 'process' || ln === 'definitions') break
          node = el.parentNode
        }
        return false
      }

      const findBpmnElementByIdAny = (nodeId: string): Element | null => {
        for (let i = 0; i < allElements.length; i++) {
          if (ck(allElements[i].getAttribute('id')) === ck(nodeId)) return allElements[i]
        }
        return null
      }

      /** Sequence flows inside the BPMN document (IDs used for highlighting executed MI paths). */
      const flowEdges: Array<{ id: string; sourceRef: string; targetRef: string }> = []
      for (let i = 0; i < allElements.length; i++) {
        const ln = allElements[i].localName || allElements[i].nodeName.split(':').pop()
        if (ln !== 'sequenceFlow') continue
        const fid = ck(allElements[i].getAttribute('id'))
        flowEdges.push({
          id: fid || `flow_internal_${flowEdges.length}`,
          sourceRef: allElements[i].getAttribute('sourceRef') || '',
          targetRef: allElements[i].getAttribute('targetRef') || '',
        })
      }

      const isUnderGivenSubProcess = (elementRef: Element | null, boundarySpId: string): boolean => {
        let node: Node | null = elementRef?.parentNode ?? null
        while (node && node.nodeType === 1) {
          const wrap = node as Element
          if ((wrap.localName || wrap.nodeName.split(':').pop()) === 'subProcess' && ck(wrap.getAttribute('id')) === ck(boundarySpId)) return true
          if ((wrap.localName || wrap.nodeName.split(':').pop()) === 'process' || (wrap.localName || wrap.nodeName.split(':').pop()) === 'definitions') break
          node = wrap.parentNode
        }
        return false
      }

      const nearestActiveMiSubProcessAncestorId = (from: Element): string | null => {
        let node: Node | null = from.parentNode
        while (node && node.nodeType === 1) {
          const wrap = node as Element
          if ((wrap.localName || wrap.nodeName.split(':').pop()) === 'subProcess') {
            const sid = ck(wrap.getAttribute('id'))
            if (sid && activeMultiInstanceSubProcesses.has(sid)) return sid
          }
          if ((wrap.localName || wrap.nodeName.split(':').pop()) === 'process' || (wrap.localName || wrap.nodeName.split(':').pop()) === 'definitions') break
          node = wrap.parentNode
        }
        return null
      }

      // Start events (MI subprocess: internal start already executed once the instance is entered — show completed/green)
      doc.querySelectorAll('startEvent').forEach((event: Element, index: number) => {
        const id = event.getAttribute('id') || `start_${index}`
        const pos = positionMap.get(id)
        const parentSpId = getParentSubProcessId(event)
        let status: ProcessNode['status'] = 'completed'
        if (parentSpId && !enteredSubProcesses.has(parentSpId)) status = 'pending'
        else if (showCurrentStep && parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
          status = 'completed'
        }
        nodes.push({ id, name: event.getAttribute('name') || t('task.startNode'), type: 'start', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
        if (status === 'completed') completed.push(id)
      })

      const isDownstreamUserTaskInsideSameActiveMi = (openTaskId: string, candidateTaskId: string, boundarySpId: string): boolean => {
        const openEl = findBpmnElementByIdAny(openTaskId)
        if (!openEl || !isUnderGivenSubProcess(openEl, boundarySpId)) return false
        if (ck(openTaskId) === ck(candidateTaskId)) return false
        const queue: string[] = [openTaskId]
        const visited = new Set<string>()
        while (queue.length > 0) {
          const u = queue.shift()!
          if (visited.has(u)) continue
          visited.add(u)
          for (const f of flowEdges) {
            if (ck(f.sourceRef) !== ck(u)) continue
            const tar = ck(f.targetRef)
            const tarEl = findBpmnElementByIdAny(tar)
            if (!tarEl || !isUnderGivenSubProcess(tarEl, boundarySpId)) continue
            if ((tarEl.localName || tarEl.nodeName.split(':').pop()) === 'userTask' && tar === ck(candidateTaskId)) return true
            queue.push(tar)
          }
        }
        return false
      }

      // Current open BPMN userTask
      let currentOpenBpmnUserTaskId = ''
      if (showCurrentStep && !options.isCompletedTask.value) {
        doc.querySelectorAll('userTask').forEach((ut: Element) => {
          const uid = ck(ut.getAttribute('id'))
          if (!uid) return
          if (uid === ck(currentTaskDefinitionKey)) currentOpenBpmnUserTaskId = uid
        })
        if (!currentOpenBpmnUserTaskId) {
          doc.querySelectorAll('userTask').forEach((ut: Element) => {
            const uid = ck(ut.getAttribute('id'))
            if (!uid) return
            const unm = normLabel(ut.getAttribute('name'))
            if (unm === normLabel(currentTaskName) || uid === ck(currentTaskName)) currentOpenBpmnUserTaskId = uid
          })
        }
      }

      const shouldSuppressSiblingAggregationComplete = (userTaskEl: Element, userTaskBpmnId: string): boolean => {
        const boundary = nearestActiveMiSubProcessAncestorId(userTaskEl)
        if (!boundary || !currentOpenBpmnUserTaskId) return false
        if (ck(userTaskBpmnId) === ck(currentOpenBpmnUserTaskId)) return false
        return isDownstreamUserTaskInsideSameActiveMi(currentOpenBpmnUserTaskId, userTaskBpmnId, boundary)
      }

      // User tasks
      doc.querySelectorAll('userTask').forEach((task: Element, index: number) => {
        const id = task.getAttribute('id') || `task_${index}`
        const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
        const pos = positionMap.get(id)
        let status: ProcessNode['status'] = 'pending'
        const openTaskMatches = showCurrentStep && (normLabel(name) === normLabel(currentTaskName) || ck(id) === ck(currentTaskName) || ck(id) === ck(currentTaskDefinitionKey) || normLabel(name) === normLabel(currentTaskDefinitionKey))

        if (options.isCompletedTask.value && (completedHistoryIds.has(id) || completedNodeNames.has(name) || ck(name) === ck(currentTaskName) || ck(id) === ck(currentTaskName) || ck(id) === ck(currentTaskDefinitionKey))) {
          status = 'completed'; completed.push(id)
        } else if (openTaskMatches) {
          status = 'current'; currentNodeId.value = id; currentNodeFound = true
        } else if (completedHistoryIds.has(id) || completedNodeNames.has(name)) {
          if (showCurrentStep && isDescendantOfActiveMiSubProcess(task) && (ck(id) === ck(currentTaskDefinitionKey) || normLabel(name) === normLabel(currentTaskName) || ck(id) === ck(currentTaskName) || normLabel(name) === normLabel(currentTaskDefinitionKey))) {
            status = 'current'; currentNodeId.value = id; currentNodeFound = true
          } else if (shouldSuppressSiblingAggregationComplete(task, id)) {
            status = 'pending'
          } else {
            status = 'completed'; completed.push(id)
          }
        } else if (!currentNodeFound) {
          const hm = options.historyRecords.value.find((h: any) => normLabel(h.nodeName) === normLabel(name))
          const sameOpenMi = showCurrentStep && isDescendantOfActiveMiSubProcess(task) && (ck(id) === ck(currentTaskDefinitionKey) || normLabel(name) === normLabel(currentTaskName) || ck(id) === ck(currentTaskName) || normLabel(name) === normLabel(currentTaskDefinitionKey))
          if (hm && hm.status === 'completed' && !sameOpenMi) {
            if (shouldSuppressSiblingAggregationComplete(task, id)) status = 'pending'
            else { status = 'completed'; completed.push(id) }
          }
        }
        nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      })

      const subprocessContainsCurrentOpenTask = (sp: Element): boolean => {
        if (!showCurrentStep || options.isCompletedTask.value) return false
        const tasks = sp.getElementsByTagName('userTask')
        for (let ti = 0; ti < tasks.length; ti++) {
          const task = tasks[ti]
          const uid = ck(task.getAttribute('id'))
          const unm = normLabel(task.getAttribute('name'))
          const openTaskMatches =
            unm === normLabel(currentTaskName) ||
            ck(uid) === ck(currentTaskName) ||
            ck(uid) === ck(currentTaskDefinitionKey) ||
            unm === normLabel(currentTaskDefinitionKey)
          if (openTaskMatches) return true
        }
        return false
      }

      /** Sub-process boxes must appear in {@link processNodes} so bpmn-js `element.click` can resolve {@code element.id} (matching {@link nodeFormMap} keys). */
      let subprocessParseIndex = 0
      subProcessMap.forEach((sp, spId) => {
        subprocessParseIndex++
        const pos = positionMap.get(spId)
        const name =
          sp.getAttribute('name')?.trim() ||
          t('task.taskFallbackName', { index: subprocessParseIndex })
        let status: ProcessNode['status'] = 'pending'
        if (!enteredSubProcesses.has(spId)) {
          status = 'pending'
        } else if (options.isCompletedTask.value) {
          status = 'completed'
          completed.push(spId)
        } else if (
          showCurrentStep &&
          (activeMultiInstanceSubProcesses.has(spId) || subprocessContainsCurrentOpenTask(sp))
        ) {
          status = 'current'
        } else {
          status = 'completed'
          completed.push(spId)
        }
        nodes.push({
          id: spId,
          name,
          type: 'subprocess',
          status,
          x: pos?.x,
          y: pos?.y,
          width: pos?.width,
          height: pos?.height,
        })
      })

      // Gateways (XOR / parallel / inclusive): completed when any incoming flow originates from an already-completed BPMN element (propagates for chained gateways).
      type GwRow = { id: string; name: string; pos: { x: number; y: number; width: number; height: number } | undefined; parentSpId: string | null }
      const gatewayRows: GwRow[] = []
      for (let gi = 0; gi < allElements.length; gi++) {
        const gw = allElements[gi]
        const ln = gw.localName || gw.nodeName.split(':').pop() || ''
        if (ln !== 'exclusiveGateway' && ln !== 'parallelGateway' && ln !== 'inclusiveGateway') continue
        const gid = gw.getAttribute('id') || ''
        if (!gid) continue
        gatewayRows.push({
          id: gid,
          name: gw.getAttribute('name') || '',
          pos: positionMap.get(gid),
          parentSpId: getParentSubProcessId(gw),
        })
      }

      const completedIdSet = new Set(completed.map(ck))
      let gwPass = true
      for (let guard = 0; guard < 25 && gwPass; guard++) {
        gwPass = false
        for (const g of gatewayRows) {
          if (completedIdSet.has(ck(g.id))) continue
          if (g.parentSpId && !enteredSubProcesses.has(g.parentSpId)) continue
          const incoming = flowEdges.filter(f => ck(f.targetRef) === ck(g.id)).map(f => ck(f.sourceRef))
          if (!incoming.some(sid => completedIdSet.has(sid))) continue
          completedIdSet.add(ck(g.id))
          completed.push(ck(g.id))
          gwPass = true
        }
      }

      gatewayRows.forEach((g, idx) => {
        const status: ProcessNode['status'] = completedIdSet.has(ck(g.id)) ? 'completed' : 'pending'
        nodes.push({
          id: g.id,
          name: g.name || t('task.taskFallbackName', { index: idx + 1 }),
          type: 'gateway',
          status,
          x: g.pos?.x,
          y: g.pos?.y,
          width: g.pos?.width,
          height: g.pos?.height,
        })
      })

      // Sequence flows
      const waypointsMap = new Map<string, Array<{ x: number; y: number }>>()
      doc.querySelectorAll('BPMNEdge, bpmndi\\:BPMNEdge').forEach((edge: Element) => {
        const bpmnElement = edge.getAttribute('bpmnElement')
        if (bpmnElement) {
          const waypoints: Array<{ x: number; y: number }> = []
          edge.querySelectorAll('waypoint, di\\:waypoint').forEach((wp: Element) => {
            waypoints.push({ x: parseFloat(wp.getAttribute('x') || '0'), y: parseFloat(wp.getAttribute('y') || '0') })
          })
          if (waypoints.length > 0) waypointsMap.set(bpmnElement, waypoints)
        }
      })
      doc.querySelectorAll('sequenceFlow').forEach((flow: Element, index: number) => {
        const id = flow.getAttribute('id') || `flow_${index}`
        flows.push({ id, sourceRef: flow.getAttribute('sourceRef') || '', targetRef: flow.getAttribute('targetRef') || '', name: flow.getAttribute('name') || '', waypoints: waypointsMap.get(id) })
      })

      processNodes.value = nodes
      processFlows.value = flows
      completedNodeIds.value = completed
    } catch (error) {
      console.error('Failed to parse BPMN XML:', error)
    }
  }

  return {
    processNodes,
    processFlows,
    completedNodeIds,
    currentNodeId,
    bpmnXml,
    parseBpmnXml,
    parseBpmnXmlAndGetFormId,
    parseBpmnXmlAndGetPreviousFormIds,
  }
}
