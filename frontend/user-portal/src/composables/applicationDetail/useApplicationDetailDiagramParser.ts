import type { ProcessNode, ProcessFlow } from '@/components/ProcessDiagram.vue'
import { isRejectedName } from '@/utils/statusMatcher'
import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'
import {
  applyDraftReturnDiagramStatus,
  resolveDiagramStatusSuppressMode,
} from '@/utils/bpmnDiagramDraftReturn'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailDiagramParserFns {
  scheduleParseApplicationBpmnDiagram: (xml: string) => void
  parseBpmnXml: (xml: string) => void
}

export function createApplicationDetailDiagramParser(ctx: ApplicationDetailCtx): ApplicationDetailDiagramParserFns {
  const {
    t,
    snapshotTaskName,
    snapshotTaskDefinitionKey,
    processInfo,
    processNodes,
    processFlows,
    currentNodeId,
    completedNodeIds,
    diagramReady,
    historyRecords,
    snapshotActivityId,
    hasIncompleteMiRows,
    hasCompletedMiRows,
  } = ctx

  /** Heavy BPMN node/flow parse is deferred so form + sub-tables can paint first. */
  function scheduleParseApplicationBpmnDiagram(xml: string) {
    if (!xml) {
      diagramReady.value = true
      return
    }
    if (ctx.diagramParseScheduled) return
    ctx.diagramParseScheduled = true
    const run = () => {
      ctx.diagramParseScheduled = false
      parseBpmnXml(xml)
      diagramReady.value = true
    }
    if (typeof requestIdleCallback === 'function') {
      requestIdleCallback(run, { timeout: 1500 })
    } else {
      setTimeout(run, 0)
    }
  }

  // Parse BPMN XML
  const parseBpmnXml = (xml: string) => {
    if (!xml) return
    try {
      const doc = getCachedBpmnDocument(xml)
      if (!doc) return
      const nodes: ProcessNode[] = []
      const flows: ProcessFlow[] = []
      const completed: string[] = []
      // Only enable snapshot view while process is RUNNING; show real completed state when ended (avoid orange Current Step)
      const snapshotNodeKey = snapshotActivityId.value || snapshotTaskDefinitionKey || snapshotTaskName || ''
      const snapshotActive = !!(snapshotNodeKey && processInfo.value.status === 'RUNNING')

      // Parse position info
      const positionMap = new Map()
      doc.querySelectorAll('BPMNShape, bpmndi\\:BPMNShape').forEach(shape => {
        const bpmnElement = shape.getAttribute('bpmnElement')
        const bounds = shape.querySelector('Bounds, dc\\:Bounds')
        if (bpmnElement && bounds) {
          positionMap.set(bpmnElement, {
            x: parseFloat(bounds.getAttribute('x') || '0'),
            y: parseFloat(bounds.getAttribute('y') || '0'),
            width: parseFloat(bounds.getAttribute('width') || '100'),
            height: parseFloat(bounds.getAttribute('height') || '80')
          })
        }
      })

      // Create mapping from node name to history record status
      const nodeStatusMap = new Map<string, 'completed' | 'current' | 'pending' | 'rejected'>()
      const completedNodeNames = new Set<string>()
      historyRecords.value.forEach(record => {
        if (record.nodeName) {
          nodeStatusMap.set(record.nodeName, record.status as 'completed' | 'current' | 'pending' | 'rejected')
          if (record.status === 'completed') {
            completedNodeNames.add(record.nodeName)
          }
        }
      })

      // Get current node name (normalized for robust matching)
      const currentNodeName = processInfo.value.currentNode || ''
      const normNodeName = currentNodeName.trim().replace(/\s+/g, ' ')
      let foundCurrentNode = false

      // Detect subProcess elements and determine which have been entered
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

      // Build a map of subProcess ID → Element (handle both prefixed and unprefixed selectors)
      const subProcessMap = new Map<string, Element>()
      const allElements = doc.getElementsByTagName('*')
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]
        const localName = el.localName || el.nodeName.split(':').pop()
        if (localName === 'subProcess') {
          const spId = el.getAttribute('id')
          if (spId) subProcessMap.set(spId, el)
        }
      }

      const enteredSubProcesses = new Set<string>()
      for (const [spId, sp] of subProcessMap) {
        const spName = sp.getAttribute('name') || ''
        if ((spName && spName === currentNodeName) || spId === currentNodeName) {
          enteredSubProcesses.add(spId)
          continue
        }
        if (spName && normNodeName && spName.trim().replace(/\s+/g, ' ') === normNodeName) {
          enteredSubProcesses.add(spId)
          continue
        }
        const childElements = sp.getElementsByTagName('*')
        for (let i = 0; i < childElements.length; i++) {
          const childLocal = childElements[i].localName || childElements[i].nodeName.split(':').pop()
          if (childLocal !== 'userTask' && childLocal !== 'serviceTask' && childLocal !== 'sendTask') continue
          const taskName = childElements[i].getAttribute('name') || ''
          const taskId = childElements[i].getAttribute('id') || ''
          const taskNameNorm = taskName.trim().replace(/\s+/g, ' ')
          if (
            taskName === currentNodeName ||
            (!!normNodeName && taskNameNorm === normNodeName) ||
            historyRecords.value.some(h => h.nodeName === taskName || h.nodeId === taskId)
          ) {
            enteredSubProcesses.add(spId)
            break
          }
        }
      }

      // Detect active multi-instance subprocesses whose child tasks are still running
      const activeMultiInstanceSubProcesses = new Set<string>()
      if (processInfo.value.status === 'RUNNING' && !snapshotActive) {
        for (const [spId, sp] of subProcessMap) {
          if (!enteredSubProcesses.has(spId)) continue
          const spChildren = sp.getElementsByTagName('*')
          let isMultiInstance = false
          for (let i = 0; i < spChildren.length; i++) {
            const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
            if (childLocal === 'multiInstanceLoopCharacteristics') {
              isMultiInstance = true
              break
            }
          }
          if (!isMultiInstance) continue
          const spName = sp.getAttribute('name') || ''
          const sn = spName.trim().replace(/\s+/g, ' ')
          if ((spName && (!!normNodeName && sn === normNodeName)) || spId === currentNodeName) {
            activeMultiInstanceSubProcesses.add(spId)
            continue
          }
          for (let i = 0; i < spChildren.length; i++) {
            const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
            if (childLocal !== 'userTask') continue
            const taskName = spChildren[i].getAttribute('name') || ''
            const taskId = spChildren[i].getAttribute('id') || ''
            const tn = taskName.trim().replace(/\s+/g, ' ')
            if (
              tn === normNodeName ||
              taskId === currentNodeName.trim() ||
              taskName === currentNodeName ||
              (taskName && nodeStatusMap.get(taskName) === 'current')
            ) {
              activeMultiInstanceSubProcesses.add(spId)
              break
            }
          }
        }

        doc.querySelectorAll('userTask').forEach(taskEl => {
          const uid = (taskEl.getAttribute('id') || '').trim()
          const unameNorm = (taskEl.getAttribute('name') || '').trim().replace(/\s+/g, ' ')
          const curTrim = currentNodeName.trim()
          const matchesOpen =
            (!!normNodeName && unameNorm === normNodeName) ||
            uid === curTrim ||
            uid === currentNodeName
          if (!matchesOpen) return
          let walker: Node | null = taskEl.parentNode
          while (walker && walker.nodeType === 1) {
            const wrap = walker as Element
            const lname = wrap.localName || wrap.nodeName.split(':').pop()
            if (lname === 'subProcess') {
              const sid = wrap.getAttribute('id') || ''
              if (sid && enteredSubProcesses.has(sid)) {
                const desc = wrap.getElementsByTagName('*')
                let hasMi = false
                for (let di = 0; di < desc.length; di++) {
                  const ln = desc[di].localName || desc[di].nodeName.split(':').pop()
                  if (ln === 'multiInstanceLoopCharacteristics') {
                    hasMi = true
                    break
                  }
                }
                if (hasMi) activeMultiInstanceSubProcesses.add(sid)
              }
            }
            if (lname === 'process' || lname === 'definitions') break
            walker = wrap.parentNode
          }
        })
      }
      // Completed multi-instance subprocesses: entered MI subprocesses where all child userTasks are done
      const completedMultiInstanceSubProcesses = new Set<string>()
      for (const [spId, sp] of subProcessMap) {
        if (!enteredSubProcesses.has(spId)) continue
        if (activeMultiInstanceSubProcesses.has(spId)) continue
        const spChildren = sp.getElementsByTagName('*')
        let isMultiInstance = false
        for (let i = 0; i < spChildren.length; i++) {
          const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
          if (childLocal === 'multiInstanceLoopCharacteristics') {
            isMultiInstance = true
            break
          }
        }
        if (!isMultiInstance) continue
        let allDone = true
        let userTaskCount = 0
        for (let i = 0; i < spChildren.length; i++) {
          const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
          if (childLocal !== 'userTask') continue
          userTaskCount++
          const taskName = spChildren[i].getAttribute('name') || ''
          const taskId = spChildren[i].getAttribute('id') || ''
          const historyMatch = historyRecords.value.find(h => h.nodeName === taskName || h.nodeId === taskId)
          if (!historyMatch || (historyMatch.status !== 'completed' && historyMatch.status !== 'rejected')) {
            allDone = false
            break
          }
        }
        if (userTaskCount > 0 && allDone) {
          completedMultiInstanceSubProcesses.add(spId)
        }
      }

      // Completed-task snapshot: multi-instance subprocess with a single userTask that matches snapshotTaskName
      const completedSnapshotSingleTaskSubProcesses = new Set<string>()
      if (snapshotTaskName) {
        for (const [spId, sp] of subProcessMap) {
          if (!enteredSubProcesses.has(spId)) continue
          const spChildren = sp.getElementsByTagName('*')
          let isMultiInstance = false
          let userTaskCount = 0
          let snapshotMatchesChild = false
          for (let i = 0; i < spChildren.length; i++) {
            const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
            if (childLocal === 'multiInstanceLoopCharacteristics') {
              isMultiInstance = true
            }
            if (childLocal === 'userTask') {
              userTaskCount++
              const taskName = spChildren[i].getAttribute('name') || ''
              const taskId = spChildren[i].getAttribute('id') || ''
              if (taskName === snapshotNodeKey || taskId === snapshotNodeKey) {
                snapshotMatchesChild = true
              }
            }
          }
          if (isMultiInstance && userTaskCount === 1 && snapshotMatchesChild) {
            completedSnapshotSingleTaskSubProcesses.add(spId)
          }
        }
      }

      const ckDiag = (s: unknown) => String(s ?? '').trim()
      const normLabDiag = (s: unknown) => ckDiag(s).replace(/\s+/g, ' ')

      const flowEdgesDiag: Array<{ sourceRef: string; targetRef: string }> = []
      for (let fi = 0; fi < allElements.length; fi++) {
        const fln = allElements[fi].localName || allElements[fi].nodeName.split(':').pop()
        if (fln !== 'sequenceFlow') continue
        flowEdgesDiag.push({
          sourceRef: allElements[fi].getAttribute('sourceRef') || '',
          targetRef: allElements[fi].getAttribute('targetRef') || '',
        })
      }

      const findBpmnElementByIdAnyDiag = (nodeId: string): Element | null => {
        for (let fi = 0; fi < allElements.length; fi++) {
          if (ckDiag(allElements[fi].getAttribute('id')) === ckDiag(nodeId)) return allElements[fi]
        }
        return null
      }

      const isUnderGivenSubProcessDiag = (elementRef: Element | null, boundarySpId: string): boolean => {
        let node: Node | null = elementRef?.parentNode ?? null
        while (node && node.nodeType === 1) {
          const wrap = node as Element
          const wln = wrap.localName || wrap.nodeName.split(':').pop()
          if (wln === 'subProcess' && ckDiag(wrap.getAttribute('id')) === ckDiag(boundarySpId)) return true
          if (wln === 'process' || wln === 'definitions') break
          node = wrap.parentNode
        }
        return false
      }

      const nearestActiveMiSubProcessAncestorIdDiag = (from: Element): string | null => {
        let node: Node | null = from.parentNode
        while (node && node.nodeType === 1) {
          const wrap = node as Element
          const wln = wrap.localName || wrap.nodeName.split(':').pop()
          if (wln === 'subProcess') {
            const sid = ckDiag(wrap.getAttribute('id'))
            if (sid && activeMultiInstanceSubProcesses.has(sid)) return sid
          }
          if (wln === 'process' || wln === 'definitions') break
          node = wrap.parentNode
        }
        return null
      }

      const isDescendantOfActiveMiSubProcessDiag = (element: Element): boolean => {
        let node: Node | null = element.parentNode
        while (node && node.nodeType === 1) {
          const el = node as Element
          const lnn = el.localName || el.nodeName.split(':').pop()
          if (lnn === 'subProcess') {
            const sid = el.getAttribute('id') || ''
            if (sid && activeMultiInstanceSubProcesses.has(sid)) return true
          }
          if (lnn === 'process' || lnn === 'definitions') break
          node = el.parentNode
        }
        return false
      }

      const isDownstreamUserTaskInsideSameActiveMiDiag = (openTaskId: string, candidateTaskId: string, boundarySpId: string): boolean => {
        const openEl = findBpmnElementByIdAnyDiag(openTaskId)
        if (!openEl || !isUnderGivenSubProcessDiag(openEl, boundarySpId)) return false
        if (ckDiag(openTaskId) === ckDiag(candidateTaskId)) return false
        const queue: string[] = [openTaskId]
        const visited = new Set<string>()
        while (queue.length > 0) {
          const u = queue.shift()!
          if (visited.has(u)) continue
          visited.add(u)
          for (const f of flowEdgesDiag) {
            if (ckDiag(f.sourceRef) !== ckDiag(u)) continue
            const tar = ckDiag(f.targetRef)
            const tarEl = findBpmnElementByIdAnyDiag(tar)
            if (!tarEl || !isUnderGivenSubProcessDiag(tarEl, boundarySpId)) continue
            if ((tarEl.localName || tarEl.nodeName.split(':').pop()) === 'userTask' && tar === ckDiag(candidateTaskId)) return true
            queue.push(tar)
          }
        }
        return false
      }

      let currentOpenBpmnUserTaskIdDiag = ''
      if (processInfo.value.status === 'RUNNING' && !snapshotActive) {
        const ctk = (snapshotTaskDefinitionKey || '').trim()
        doc.querySelectorAll('userTask').forEach((ut: Element) => {
          const uid = ckDiag(ut.getAttribute('id'))
          if (!uid) return
          if (ctk && uid === ckDiag(ctk)) currentOpenBpmnUserTaskIdDiag = uid
        })
        if (!currentOpenBpmnUserTaskIdDiag) {
          doc.querySelectorAll('userTask').forEach((ut: Element) => {
            const uid = ckDiag(ut.getAttribute('id'))
            if (!uid) return
            const unm = normLabDiag(ut.getAttribute('name'))
            if (unm === normLabDiag(currentNodeName) || uid === ckDiag(currentNodeName)) currentOpenBpmnUserTaskIdDiag = uid
          })
        }
      }

      const shouldSuppressSiblingAggregationCompleteDiag = (userTaskEl: Element, userTaskBpmnId: string): boolean => {
        const boundary = nearestActiveMiSubProcessAncestorIdDiag(userTaskEl)
        if (!boundary || !currentOpenBpmnUserTaskIdDiag) return false
        if (ckDiag(userTaskBpmnId) === ckDiag(currentOpenBpmnUserTaskIdDiag)) return false
        return isDownstreamUserTaskInsideSameActiveMiDiag(currentOpenBpmnUserTaskIdDiag, userTaskBpmnId, boundary)
      }

      // Parse start events (subprocess-internal starts are pending until the subprocess is entered)
      doc.querySelectorAll('startEvent').forEach((event, index) => {
        const id = event.getAttribute('id') || `start_${index}`
        const pos = positionMap.get(id)
        const parentSpId = getParentSubProcessId(event)
        let startStatus: 'completed' | 'current' | 'pending' = 'completed'
        if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
          startStatus = 'pending'
        } else if (parentSpId && completedSnapshotSingleTaskSubProcesses.has(parentSpId)) {
          startStatus = 'completed'
        } else if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
          // Align with todo (useBpmnParser): internal start is completed once MI instance is active
          startStatus = 'completed'
        }
        nodes.push({ id, name: event.getAttribute('name') || t('task.startNode'), type: 'start', status: startStatus, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
        if (startStatus === 'completed') {
          completed.push(id)
        }
      })

      const completedHistoryIdsForMi = new Set<string>()
      const completedNodeNamesForMi = new Set<string>()
      historyRecords.value.forEach(record => {
        if (record.status === 'completed' && record.nodeId) completedHistoryIdsForMi.add(String(record.nodeId).trim())
        if (record.status === 'completed' && record.nodeName) completedNodeNamesForMi.add(record.nodeName)
      })

      // Parse user tasks
      doc.querySelectorAll('userTask').forEach((task, index) => {
        const id = task.getAttribute('id') || `task_${index}`
        const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
        const pos = positionMap.get(id)

        let status: 'completed' | 'current' | 'pending' | 'rejected' = 'pending'
        const parentSpId = getParentSubProcessId(task)
        const inActiveMi = !!(parentSpId && activeMultiInstanceSubProcesses.has(parentSpId))

        // Prefer status from history records
        const historyStatus = nodeStatusMap.get(name)
        if (snapshotActive) {
          // Snapshot mode: only show status up to snapshotTaskName
          if (name === snapshotNodeKey || id === snapshotNodeKey) {
            status = 'completed'
            completed.push(id)
            foundCurrentNode = true
          } else if (!foundCurrentNode) {
            // Nodes before snapshotTaskName: determine from history, or treat as completed
            if (historyStatus) {
              status = historyStatus
            } else {
              status = 'completed'
            }
            if (status === 'completed' || status === 'rejected') {
              completed.push(id)
            }
          } else {
            // Nodes after snapshotTaskName: keep as pending
            status = 'pending'
          }
        } else if (
          processInfo.value.status === 'RUNNING'
          && !snapshotActive
          && inActiveMi
        ) {
          /** Same rules as todo task detail (`useBpmnParser`): current step + downstream suppression inside MI */
          const ctd = (snapshotTaskDefinitionKey || '').trim()
          const openTaskMatches =
            normLabDiag(name) === normLabDiag(currentNodeName)
            || ckDiag(id) === ckDiag(currentNodeName)
            || (ctd && (ckDiag(id) === ckDiag(ctd) || normLabDiag(name) === normLabDiag(ctd)))

          if (openTaskMatches) {
            status = 'current'
            currentNodeId.value = id
            foundCurrentNode = true
          } else if (completedHistoryIdsForMi.has(id) || completedNodeNamesForMi.has(name)) {
            if (
              isDescendantOfActiveMiSubProcessDiag(task)
              && (
                (ctd && (ckDiag(id) === ckDiag(ctd) || normLabDiag(name) === normLabDiag(ctd)))
                || normLabDiag(name) === normLabDiag(currentNodeName)
                || ckDiag(id) === ckDiag(currentNodeName)
              )
            ) {
              status = 'current'
              currentNodeId.value = id
              foundCurrentNode = true
            } else if (shouldSuppressSiblingAggregationCompleteDiag(task, id)) {
              status = 'pending'
            } else {
              status = 'completed'
              completed.push(id)
            }
          } else if (!foundCurrentNode) {
            const hm = historyRecords.value.find(h => normLabDiag(h.nodeName) === normLabDiag(name))
            const sameOpenMi =
              isDescendantOfActiveMiSubProcessDiag(task)
              && (
                (ctd && (ckDiag(id) === ckDiag(ctd) || normLabDiag(name) === normLabDiag(ctd)))
                || normLabDiag(name) === normLabDiag(currentNodeName)
                || ckDiag(id) === ckDiag(currentNodeName)
              )
            if (hm && hm.status === 'completed' && !sameOpenMi) {
              if (shouldSuppressSiblingAggregationCompleteDiag(task, id)) status = 'pending'
              else {
                status = 'completed'
                completed.push(id)
              }
            }
          }
        } else if (historyStatus) {
          status = historyStatus
          if (status === 'completed' || status === 'rejected') {
            completed.push(id)
          }
        } else if (processInfo.value.status === 'COMPLETED') {
          // Process completed: only mark nodes that were actually executed (matched via history records)
          const historyMatch = historyRecords.value.find(h => h.nodeName === name || h.nodeId === id)
          if (historyMatch) {
            status = historyMatch.status === 'rejected' ? 'rejected' : 'completed'
            completed.push(id)
          }
        } else if (processInfo.value.status === 'RUNNING') {
          // Process running: determine status based on current node name.
          // Normalize whitespace for robust comparison, then fall back to matching by taskDefinitionKey.
          const normName = currentNodeName.trim().replace(/\s+/g, ' ')
          const normBpmnName = name.trim().replace(/\s+/g, ' ')
          if (normBpmnName === normName || id === currentNodeName) {
            status = 'current'
            currentNodeId.value = id
            foundCurrentNode = true
          } else if (!foundCurrentNode) {
            // Nodes before current: only mark as completed if found in history records
            // Avoid incorrectly marking gateway-skipped branch nodes as completed
            const historyMatch = historyRecords.value.find(h => h.nodeName === name || h.nodeId === id)
            if (historyMatch && (historyMatch.status === 'completed' || historyMatch.status === 'rejected')) {
              status = historyMatch.status
              completed.push(id)
            }
          }
        }

        nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      })
      // Parse service / send tasks (designer keeps sendTask; deploy converts email sendTask → serviceTask)
      doc.querySelectorAll('serviceTask, sendTask').forEach((task, index) => {
        const id = task.getAttribute('id') || `service_${index}`
        const name = task.getAttribute('name') || t('applicationDetail.serviceFallbackName', { index: index + 1 })
        const pos = positionMap.get(id)
        const historyMatch = historyRecords.value.find(h => h.nodeName === name || h.nodeId === id)
        const historyStatus = historyMatch?.status || nodeStatusMap.get(name)
        const status = historyStatus === 'completed' || historyStatus === 'rejected'
          ? historyStatus
          : 'pending'
        nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
        if (status === 'completed' || status === 'rejected') completed.push(id)
      })

      // Parse subProcess elements
      let spIdx = 0
      for (const [spId, sp] of subProcessMap) {
        const name = sp.getAttribute('name') || ''
        const pos = positionMap.get(spId)

        let spStatus: 'completed' | 'current' | 'pending' = 'pending'
        let isMiSubProcess = false
        const spDescForMi = sp.getElementsByTagName('*')
        for (let mi = 0; mi < spDescForMi.length; mi++) {
          const miLocal = spDescForMi[mi].localName || spDescForMi[mi].nodeName.split(':').pop()
          if (miLocal === 'multiInstanceLoopCharacteristics') {
            isMiSubProcess = true
            break
          }
        }
        // Ended process: MI subprocess must show completed (green), not Current Step; Flowable still reports last activity as currentNode.
        if (
          processInfo.value.status === 'COMPLETED' &&
          enteredSubProcesses.has(spId) &&
          isMiSubProcess
        ) {
          spStatus = 'completed'
        } else if (
          processInfo.value.status === 'COMPLETED' &&
          hasCompletedMiRows() &&
          enteredSubProcesses.has(spId) &&
          isMiSubProcess
        ) {
          spStatus = 'completed'
        } else if (
          processInfo.value.status === 'RUNNING' &&
          hasIncompleteMiRows() &&
          enteredSubProcesses.has(spId) &&
          isMiSubProcess
        ) {
          spStatus = 'current'
        } else if (snapshotActive && completedSnapshotSingleTaskSubProcesses.has(spId)) {
          spStatus = 'completed'
        } else if (enteredSubProcesses.has(spId)) {
          const childElements = sp.getElementsByTagName('*')
          let hasCurrentChild = false
          let allChildrenDone = true
          let userTaskCount = 0
          for (let i = 0; i < childElements.length; i++) {
            const childLocal = childElements[i].localName || childElements[i].nodeName.split(':').pop()
            if (childLocal !== 'userTask') continue
            userTaskCount++
            const taskName = childElements[i].getAttribute('name') || ''
            const taskId = childElements[i].getAttribute('id') || ''
            // While RUNNING, currentNode match means activities still in this subprocess. When COMPLETED, same name/id is often the last finished task; do not mark as current.
            if (
              processInfo.value.status !== 'COMPLETED' &&
              (taskName === currentNodeName || taskId === currentNodeName)
            ) {
              hasCurrentChild = true
              break
            }
            const historyMatch = historyRecords.value.find(h => h.nodeName === taskName || h.nodeId === taskId)
            if (!historyMatch || (historyMatch.status !== 'completed' && historyMatch.status !== 'rejected')) {
              allChildrenDone = false
            }
          }
          if (!userTaskCount) allChildrenDone = false
          spStatus = hasCurrentChild ? 'current' : allChildrenDone ? 'completed' : 'current'
        }
        nodes.push({ id: spId, name, type: 'subprocess', status: spStatus, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
        if (spStatus === 'completed') completed.push(spId)
        spIdx++
      }

      // Pre-parse sequence flows (used for subsequent gateway status determination)
      const earlyFlows: Array<{sourceRef: string, targetRef: string}> = []
      doc.querySelectorAll('sequenceFlow').forEach(flow => {
        earlyFlows.push({
          sourceRef: flow.getAttribute('sourceRef') || '',
          targetRef: flow.getAttribute('targetRef') || ''
        })
      })

      // Parse gateways
      doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
        const id = gateway.getAttribute('id') || `gateway_${index}`
        const name = gateway.getAttribute('name') || ''
        const pos = positionMap.get(id)
        const parentSpId = getParentSubProcessId(gateway)

        // Determine gateway status from history records
        let status: 'completed' | 'current' | 'pending' = 'pending'
        const gwIncomingIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
        const gwHasCompletedPred = gwIncomingIds.some(srcId => completed.includes(srcId))

        if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
          // MI: match `useBpmnParser` — gateways are never "current" (orange); gray until an incoming node is completed, then green.
          status = gwHasCompletedPred ? 'completed' : 'pending'
        } else if (snapshotActive) {
          // Snapshot mode: check if the gateway incoming nodes are completed
          if (completedNodeNames.has(name)) {
            status = 'completed'
          } else if (gwHasCompletedPred) {
            status = 'completed'
          }
        } else if (completedNodeNames.has(name)) {
          status = 'completed'
        } else if (processInfo.value.status === 'COMPLETED') {
          // Process completed: only mark gateways on the actually executed path
          if (gwHasCompletedPred) {
            status = 'completed'
          }
        } else {
          // Check for completed incoming nodes (via sequenceFlow)
          if (gwHasCompletedPred) {
            status = 'completed'
          }
        }

        nodes.push({ id, name, type: 'gateway', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
        if (status === 'completed') completed.push(id)
      })

      // Parse end events
      doc.querySelectorAll('endEvent').forEach((event, index) => {
        const id = event.getAttribute('id') || `end_${index}`
        const name = event.getAttribute('name') || t('task.endNode')
        const pos = positionMap.get(id)
        const parentSpId = getParentSubProcessId(event)

        // Check if end node should be marked as completed
        let status: 'completed' | 'current' | 'pending' | 'rejected' = 'pending'

        // SubProcess-internal endEvents stay pending when the subProcess hasn't been entered
        if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
          status = 'pending'
        } else if (parentSpId && completedSnapshotSingleTaskSubProcesses.has(parentSpId)) {
          status = 'completed'
        } else if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
          status = 'pending'
        } else if (parentSpId && completedMultiInstanceSubProcesses.has(parentSpId)) {
          status = 'completed'
        } else if (completedNodeNames.has(name)) {
          // Match by exact node ID first to avoid cross-process name collision
          const idMatch = historyRecords.value.find(h => h.nodeId === id)
          if (idMatch) {
            status = isRejectedName(name) ? 'rejected' : 'completed'
          } else if (!parentSpId) {
            status = isRejectedName(name) ? 'rejected' : 'completed'
          }
        } else if (snapshotActive) {
          status = 'pending'
        } else if (processInfo.value.status === 'COMPLETED') {
          if (name === currentNodeName) {
            status = isRejectedName(name) ? 'rejected' : 'completed'
          }
        } else if (processInfo.value.status === 'REJECTED') {
          if (isRejectedName(name)) {
            status = 'rejected'
          }
        }
        nodes.push({ id, name, type: 'end', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
        if (status === 'completed' || status === 'rejected') completed.push(id)
      })

      // Parse connector waypoints
      const waypointsMap = new Map()
      doc.querySelectorAll('BPMNEdge, bpmndi\\:BPMNEdge').forEach(edge => {
        const bpmnElement = edge.getAttribute('bpmnElement')
        if (bpmnElement) {
          const waypoints: Array<{x: number, y: number}> = []
          edge.querySelectorAll('waypoint, di\\:waypoint').forEach(wp => {
            waypoints.push({ x: parseFloat(wp.getAttribute('x') || '0'), y: parseFloat(wp.getAttribute('y') || '0') })
          })
          if (waypoints.length > 0) waypointsMap.set(bpmnElement, waypoints)
        }
      })

      // Parse sequence flows
      doc.querySelectorAll('sequenceFlow').forEach((flow, index) => {
        const id = flow.getAttribute('id') || `flow_${index}`
        flows.push({ id, sourceRef: flow.getAttribute('sourceRef') || '', targetRef: flow.getAttribute('targetRef') || '', name: flow.getAttribute('name') || '', waypoints: waypointsMap.get(id) })
      })

      // Draft return-to-first-step: history keeps completed records from the prior pass,
      // but downstream stages must render as pending again (parity with todo task detail).
      if (processInfo.value.status === 'RUNNING' && !snapshotActive) {
        const suppressMode = resolveDiagramStatusSuppressMode(xml, {
          currentTaskName: currentNodeName,
          historyRecords: historyRecords.value,
        })
        if (suppressMode === 'draft-return') {
          const draftReturn = applyDraftReturnDiagramStatus(nodes, xml, currentNodeId.value)
          processNodes.value = draftReturn.nodes
          processFlows.value = flows
          completedNodeIds.value = draftReturn.completedNodeIds
          return
        }
      }

      processNodes.value = nodes
      processFlows.value = flows
      completedNodeIds.value = completed
    } catch (error) {
      console.error('Failed to parse BPMN XML:', error)
    }
  }

  return {
    scheduleParseApplicationBpmnDiagram,
    parseBpmnXml,
  }
}
