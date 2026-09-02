import { relationTableApi } from '@/api/relationTable'
import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'
import {
  getPortalUserId,
  hasAssignmentData,
  rowAssigneeUserId,
} from './subTableRowHelpers'
import {
  parseBpmnXmlAndGetAllFormIds,
  findInitiatorCurrentStepIndexInAllOrdered,
  findMiSubTaskFormIdFromBpmn,
} from './bpmnOrderParsers'
import type { PreviousFormEntry, ApplicationDetailSecondaryCtx } from './useApplicationDetailState'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailSecondaryFns {
  loadApplicationDetailLookupConfigs: (selectedForm: any) => Promise<void>
  runApplicationDetailSecondary: (secondaryCtx: ApplicationDetailSecondaryCtx) => Promise<void>
  scheduleApplicationDetailSecondary: (historyPromise: Promise<void>) => void
}

export function createApplicationDetailSecondary(ctx: ApplicationDetailCtx): ApplicationDetailSecondaryFns {
  const {
    snapshotTaskName,
    processInfo,
    historyRecords,
    previousForms,
    subTableBindings,
    nodeFormMap,
    subTaskFormSchema,
    subTaskFormId,
    bpmnXml,
    diagramReady,
    lookupDbConfigs,
  } = ctx

  async function loadApplicationDetailLookupConfigs(selectedForm: any) {
    lookupDbConfigs.value = {}
    if (selectedForm?.sourceId == null) return
    try {
      const lcRes = await relationTableApi.getLookupConfigs(Number(selectedForm.sourceId))
      if (!lcRes?.data) return
      for (const lc of lcRes.data) {
        let sf: string[] = []
        try {
          sf =
            typeof lc.searchFields === 'string'
              ? JSON.parse(lc.searchFields || '[]')
              : lc.searchFields || []
        } catch {
          sf = []
        }
        lookupDbConfigs.value[lc.componentId] = {
          tableId: lc.tableId,
          searchFields: sf,
          displayField: lc.displayField || '',
          viewFields: lc.viewFields || []
        }
      }
    } catch (e: unknown) {
      console.warn('[app] Failed to load lookup configs:', e)
    }
  }

  async function runApplicationDetailSecondary(secondaryCtx: ApplicationDetailSecondaryCtx) {
    const { content, useInitiatorFormOnly, bindingRelationTableMap, selectedForm } = secondaryCtx
    let bpmnAllOrderedForms = secondaryCtx.bpmnAllOrderedForms
    let miSubTaskFormSourceId: string | null = null

    if (content.processes?.length > 0) {
      const xml = content.processes[0].data as string
      if (bpmnAllOrderedForms.length === 0) {
        bpmnAllOrderedForms = parseBpmnXmlAndGetAllFormIds(xml)
      }
      miSubTaskFormSourceId = findMiSubTaskFormIdFromBpmn(xml)

      subTaskFormSchema.value = null
      subTaskFormId.value = null
      if (content.forms?.length > 1) {
        let detected = false
        if (miSubTaskFormSourceId) {
          const taskForm = content.forms.find((f: any) => String(f.sourceId) === miSubTaskFormSourceId)
          if (taskForm) {
            try {
              const cfg = typeof taskForm.data === 'string' ? JSON.parse(taskForm.data) : (taskForm.data || {})
              cfg._formName = taskForm.name
              subTaskFormSchema.value = cfg
              subTaskFormId.value = String(taskForm.id)
              detected = true
            } catch { /* ignore parse errors */ }
          }
        }
        if (!detected) {
          const taskForm = content.forms.find(
            (f: any) => f.id !== selectedForm.id && f.name !== selectedForm.name
          )
          if (taskForm) {
            try {
              const cfg = typeof taskForm.data === 'string' ? JSON.parse(taskForm.data) : (taskForm.data || {})
              cfg._formName = taskForm.name
              subTaskFormSchema.value = cfg
            } catch { /* ignore parse errors */ }
          }
        }
      }

      const normHistNameInit = (s: string | null | undefined) => (s || '').trim().replace(/\s+/g, ' ')
      let initiatorSliceIndex: number | null = null
      const prevFormIds = useInitiatorFormOnly
        ? (() => {
            const allOrdered =
              bpmnAllOrderedForms.length > 0 ? bpmnAllOrderedForms : parseBpmnXmlAndGetAllFormIds(xml)
            const curRaw = snapshotTaskName || processInfo.value.currentNode || ''
            const curN = normHistNameInit(curRaw)
            if (curRaw && String(curRaw).trim()) {
              const idx = findInitiatorCurrentStepIndexInAllOrdered(xml, curRaw, allOrdered)
              if (idx != null && idx >= 0) {
                initiatorSliceIndex = idx
                return allOrdered.slice(0, idx)
              }
            }
            const completedKeys = new Set(
              ctx.parseBpmnXmlAndGetPreviousFormIds(xml)
                .map(i => i.formId || i.formName || i.taskName || '')
                .filter(k => k.length > 0)
            )
            let ordered = allOrdered.filter(i =>
              completedKeys.has(i.formId || i.formName || i.taskName || '')
            )
            const reachedHistoryNames = new Set(
              historyRecords.value
                .filter(h => h.status === 'completed' || h.status === 'current')
                .map(h => normHistNameInit(h.nodeName))
                .filter(n => n.length > 0)
            )
            if (reachedHistoryNames.size > 0) {
              ordered = ordered.filter(info => {
                const prevFormGuess = content.forms.find(
                  (f: any) =>
                    (info.formId && String(f.sourceId) === info.formId) ||
                    (info.formName && f.name === info.formName) ||
                    (info.taskName && f.name === info.taskName)
                )
                const isMiTaskForm =
                  !!prevFormGuess &&
                  !!(
                    (subTaskFormId.value && String(prevFormGuess.id) === subTaskFormId.value) ||
                    (subTaskFormSchema.value && prevFormGuess.name === subTaskFormSchema.value._formName)
                  )
                if (!isMiTaskForm) return true
                const t = normHistNameInit(info.taskName)
                if (!t.length) return true
                return reachedHistoryNames.has(t)
              })
            }
            return ordered
          })()
        : ctx.parseBpmnXmlAndGetPreviousFormIds(xml)

      const collectedPrevForms: PreviousFormEntry[] = []
      for (const info of prevFormIds) {
        let prevForm: any = null
        let skipReason: string | null = null
        if (info.formId) {
          if (info.formId === String(selectedForm.sourceId)) {
            skipReason = 'sourceIdEqSelected'
          } else prevForm = content.forms.find((f: any) => String(f.sourceId) === info.formId)
        }
        if (!skipReason && !prevForm && info.formName) {
          if (info.formName === selectedForm.name) {
            skipReason = 'formNameEqSelected'
          } else prevForm = content.forms.find((f: any) => f.name === info.formName)
        }
        if (!skipReason && !prevForm && (info as { taskName?: string }).taskName) {
          const tn = (info as { taskName?: string }).taskName
          if (tn === selectedForm.name) {
            skipReason = 'taskNameEqSelected'
          } else prevForm = content.forms.find((f: any) => f.name === tn)
        }
        if (!skipReason && (!prevForm || prevForm.id === selectedForm.id)) {
          skipReason = !prevForm ? 'noFormMatch' : 'idEqSelected'
        }
        if (!skipReason && collectedPrevForms.some(e => e.formId === String(prevForm.id))) {
          skipReason = 'duplicate'
        }
        const isKnownMiSubTaskForm = !skipReason && !!(
          (subTaskFormId.value && String(prevForm.id) === subTaskFormId.value) ||
          (subTaskFormSchema.value && prevForm.name === subTaskFormSchema.value._formName)
        )
        if (!skipReason && isKnownMiSubTaskForm) {
          const bindings = prevForm.tableBindings || []
          if (!bindings.some((b: any) => b.bindingType !== 'PRIMARY')) {
            skipReason = 'miSubTaskNoNonPrimaryBindings'
          }
        }
        if (skipReason) continue

        collectedPrevForms.push(
          ctx.buildPreviousFormEntry(
            prevForm,
            { isKnownMiSubTask: !!isKnownMiSubTaskForm },
            content.forms,
            bindingRelationTableMap
          )
        )
      }

      if (
        useInitiatorFormOnly &&
        initiatorSliceIndex != null &&
        processInfo.value.status === 'RUNNING' &&
        (subTaskFormId.value || subTaskFormSchema.value)
      ) {
        const orderedFull =
          bpmnAllOrderedForms.length > 0 ? bpmnAllOrderedForms : parseBpmnXmlAndGetAllFormIds(xml)
        const atCur = orderedFull[initiatorSliceIndex]
        if (atCur) {
          let curForm = content.forms.find(
            (f: any) =>
              (atCur.formId && String(f.sourceId) === atCur.formId) ||
              (atCur.formName && f.name === atCur.formName) ||
              (atCur.taskName && f.name === atCur.taskName)
          )
          if (!curForm && atCur.formId && miSubTaskFormSourceId && String(atCur.formId) === String(miSubTaskFormSourceId)) {
            if (subTaskFormId.value) {
              curForm = content.forms.find((f: any) => String(f.id) === subTaskFormId.value)
            }
            if (!curForm && subTaskFormSchema.value?._formName) {
              curForm = content.forms.find((f: any) => f.name === subTaskFormSchema.value._formName)
            }
          }
          const matchesMiForm =
            !!curForm &&
            !!(
              (subTaskFormId.value && String(curForm.id) === subTaskFormId.value) ||
              (subTaskFormSchema.value && curForm.name === subTaskFormSchema.value._formName)
            )
          if (matchesMiForm && !collectedPrevForms.some(e => e.formId === String(curForm.id))) {
            collectedPrevForms.push(
              ctx.buildPreviousFormEntry(
                curForm,
                { isKnownMiSubTask: true, isActiveMiSubTaskStep: true },
                content.forms,
                bindingRelationTableMap
              )
            )
          }
        }
      }

      previousForms.value = collectedPrevForms
    } else {
      previousForms.value = []
    }

    ctx.alignProcessSubTableBindingsBySharedTable()
    ctx.scheduleBuildApplicationNodeFormMap(content)
    if (bpmnXml.value) {
      ctx.scheduleParseApplicationBpmnDiagram(bpmnXml.value)
    } else {
      diagramReady.value = true
    }

    if (snapshotTaskName) {
      const viewerId = getPortalUserId()
      if (viewerId) {
        const filterByAssignee = (bindings: typeof subTableBindings.value) => {
          for (const binding of bindings) {
            if (!binding.data || binding.data.length === 0) continue
            const assigneeField = resolveAssigneeFieldForBinding(binding as never)
            if (!assigneeField || !hasAssignmentData(binding.data, assigneeField)) continue
            const filtered = binding.data.filter(
              (row: any) =>
                rowAssigneeUserId(row, assigneeField) === viewerId &&
                String(row.task_status ?? row.sub_task_status ?? '').toUpperCase() === 'COMPLETED',
            )
            if (filtered.length > 0) {
              binding.data = filtered
            } else {
              const byAssignee = binding.data.filter(
                (row: any) => rowAssigneeUserId(row, assigneeField) === viewerId,
              )
              if (byAssignee.length > 0) {
                binding.data = byAssignee
              }
            }
          }
        }
        filterByAssignee(subTableBindings.value)
        for (const prevForm of previousForms.value) {
          filterByAssignee(prevForm.subTableBindings)
        }
        for (const nodeForm of nodeFormMap.value.values()) {
          filterByAssignee(nodeForm.subTableBindings as typeof subTableBindings.value)
        }
      }
      ctx.hydrateCurrentFormDataFromCompletedSubTaskRows()
    }
  }

  function scheduleApplicationDetailSecondary(historyPromise: Promise<void>) {
    const secondaryCtx = ctx.pendingApplicationDetailSecondary
    if (!secondaryCtx) return
    ctx.pendingApplicationDetailSecondary = null
    if (ctx.applicationDetailSecondaryScheduled) return
    ctx.applicationDetailSecondaryScheduled = true
    const run = async () => {
      ctx.applicationDetailSecondaryScheduled = false
      try {
        await historyPromise
        if (secondaryCtx.lookupSourceId != null) {
          await loadApplicationDetailLookupConfigs(secondaryCtx.selectedForm)
        }
        await runApplicationDetailSecondary(secondaryCtx)
      } catch (e) {
        console.error('[ApplicationDetail] secondary load failed:', e)
      }
    }
    const kick = () => {
      void run()
    }
    if (typeof requestIdleCallback === 'function') {
      requestIdleCallback(kick, { timeout: 800 })
    } else {
      setTimeout(kick, 0)
    }
  }

  return {
    loadApplicationDetailLookupConfigs,
    runApplicationDetailSecondary,
    scheduleApplicationDetailSecondary,
  }
}
