import { ElMessage } from 'element-plus'
import { processApi } from '@/api/process'
import {
  buildRelationTableFieldIndexFromDataTables,
  isAuditField,
  resolveSubFormDialogColumnsForBinding,
} from '@/components/subTableAddDialogHelpers'
import {
  resolveSubTablePrimaryKeyFields,
  hydrateChildSubTablesFromParentsNestedRows,
  flattenNestedSubTableRowsIntoPayload,
  buildBindingIdToRelationTableIdMap,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  enrichChildBindingRowsFromParentsNestedSubTables,
  coerceSubTablesVariableToMap,
  isSubTableRowMetaField,
} from '@/composables/tasks/shared'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'
import {
  getPortalUserId,
  getSavedSubTableRowsFromVariables,
} from './subTableRowHelpers'
import type { ApplicationDetailCtx } from './context'
import {
  attachAssignmentConfigsToBindings,
  stampAssignmentConfigsOnForms,
} from '@/utils/miAssignmentConfig'

export interface ApplicationDetailLoadersFns {
  loadProcessDetail: () => Promise<void>
  loadFunctionUnitContent: (processKey: string, prefetchedContent?: any) => Promise<void>
}

export function createApplicationDetailLoaders(ctx: ApplicationDetailCtx): ApplicationDetailLoadersFns {
  const {
    t,
    processId,
    snapshotTime,
    snapshotTaskName,
    isInitiatorMyRequestView,
    loading,
    processInfo,
    currentNodeId,
    bpmnXml,
    diagramReady,
    formData,
    currentFormName,
    subTableBindings,
    primaryTableBinding,
    functionUnitIdRef,
    lastBindingRelationTableMap,
    mainFormNativeSubTableBindingIds,
    mainFormConfig,
    relationViewConfigs,
    previousForms,
    selectedNodeId,
    nodeFormMap,
  } = ctx

  // Load process details
  const loadProcessDetail = async () => {
    loading.value = true
    diagramReady.value = false
    ctx.diagramParseScheduled = false
    clearBpmnParseCache()
    ctx.pendingNodeFormMapContent = null
    ctx.nodeFormMapBuildScheduled = false
    ctx.pendingApplicationDetailSecondary = null
    ctx.applicationDetailSecondaryScheduled = false
    try {
      const res = await processApi.getProcessDetail(processId)
      const data = res.data || res
      if (data) {
        processInfo.value = data
        if (data.variables) formData.value = data.variables
        const stCoerced = coerceSubTablesVariableToMap(formData.value.__subTables__)
        if (stCoerced) {
          formData.value = { ...formData.value, __subTables__: stCoerced }
        }

        const processKey = data.processDefinitionKey
        if (processKey) functionUnitIdRef.value = String(processKey)
        const historyPromise = ctx.loadProcessHistory()
        const fuFetchPromise = processKey
          ? processApi.getFunctionUnitContent(processKey).then(r => r.data || r).catch(err => {
              console.error('Failed to fetch function unit content:', err)
              return null
            })
          : Promise.resolve(null)

        const prefetchedFu = await fuFetchPromise

        if (processKey && prefetchedFu) {
          try {
            await loadFunctionUnitContent(processKey, prefetchedFu)
          } catch (error) {
            console.error('Failed to load function unit content, but continuing:', error)
          }
          ctx.scheduleApplicationDetailSecondary(historyPromise)
        } else {
          await historyPromise
        }

        // Completed Tasks: do not advance the diagram to the next active node.
        if (snapshotTaskName) {
          currentNodeId.value = ''
        }
      }
    } catch (error: any) {
      console.error('Failed to load process detail:', error)
      ElMessage.error(t('applicationDetail.loadFailed'))
    } finally {
      loading.value = false
      if (!diagramReady.value && !ctx.diagramParseScheduled && !bpmnXml.value) {
        diagramReady.value = true
      }
    }
  }

  // Load function unit content (optional prefetched payload avoids duplicate HTTP when parallel with history)
  const loadFunctionUnitContent = async (processKey: string, prefetchedContent?: any) => {
    try {
      const content =
        prefetchedContent ??
        (await processApi.getFunctionUnitContent(processKey).then(r => r.data || r))
      if (content.error) {
        console.error('Function unit content error:', content.error)
        return
      }
      stampAssignmentConfigsOnForms(content.forms, content.miAssignments)

      selectedNodeId.value = null

      let currentFormInfo: { formId: string | null, formName: string | null, scene: 'TASK' | 'REQUEST' } = { formId: null, formName: null, scene: 'TASK' }
      /**
       * Initiator My Request: still use dedicated BFS for `previousForms` (MI subprocess), but the
       * **main** form always follows the current BPMN userTask — including MI subtask (`subform_copy`)
       * and later approval steps — so it matches the designer’s per-node portalViews.
       */
      let useInitiatorFormOnly = false

      if (content.processes?.length > 0) {
        const xml = content.processes[0].data
        const viewerId = getPortalUserId()
        const initiatorId = (processInfo.value.startUserId || '').trim()
        useInitiatorFormOnly =
          !!viewerId &&
          !!initiatorId &&
          viewerId.trim() === initiatorId &&
          !snapshotTaskName &&
          !snapshotTime
        isInitiatorMyRequestView.value = useInitiatorFormOnly

        currentFormInfo = ctx.parseBpmnXmlAndGetFormId(xml)
        bpmnXml.value = xml
        ctx.refreshActiveMiSubProcessScopeFromBpmn()
      }

      if (content.forms?.length > 0) {      // Select the correct form based on the current node formId
        ctx.cachedContentForms = content.forms || []
        ctx.cachedRelationTableFieldIndex = buildRelationTableFieldIndexFromDataTables(content.dataTables)
        let selectedForm = content.forms[0] // Default to first

        // Name matching only within the same scene — a To Do and a My Requests design of one
        // node often share a name, and matching across them would quietly render the wrong one.
        const sceneOfForm = (f: any): 'TASK' | 'REQUEST' => (f?.scene === 'REQUEST' ? 'REQUEST' : 'TASK')

        // Prefer matching formId to sourceId (original form ID)
        if (currentFormInfo.formId) {
          const matchedForm = content.forms.find((f: any) =>
            String(f.sourceId) === currentFormInfo.formId
          )
          if (matchedForm) {
            selectedForm = matchedForm
          } else {
            if (currentFormInfo.formName) {
              const matchedByName = content.forms.find((f: any) =>
                f.name === currentFormInfo.formName && sceneOfForm(f) === currentFormInfo.scene
              )
              if (matchedByName) {
                selectedForm = matchedByName
              }
            }
          }
        } else if (currentFormInfo.formName) {
          // If no formId, try matching by formName
          const matchedForm = content.forms.find((f: any) =>
            f.name === currentFormInfo.formName && sceneOfForm(f) === currentFormInfo.scene
          )
          if (matchedForm) {
            selectedForm = matchedForm
          }
        }

        currentFormName.value = selectedForm.name

        let selectedFormConfig: Record<string, any> = {}
        try {
          const cfg =
            typeof selectedForm.data === 'string'
              ? JSON.parse(selectedForm.data)
              : (selectedForm.data || {})
          selectedFormConfig = cfg
          relationViewConfigs.value = cfg.relationViews || {}
        } catch {
          selectedFormConfig = {}
          relationViewConfigs.value = {}
        }

        ctx.parseFormConfig(selectedForm.data)

        // ACTION-table rows (e.g. FORM_POPUP "Meeting Remark" history) are per-request data that
        // must NOT come from the cached FU content payload above (shared across every request of
        // this FU) — fetched fresh per request in parallel, keyed by bindingId, and merged into
        // the matching binding's `data` after the tableBindings loop below (mirrors the To Do side
        // in useTaskDetailFuLoader.ts; this path uses processInstanceId since My Request has no
        // taskId in scope).
        const actionTableRowsPromise = processId
          ? processApi.getActionTableRowsForProcess(processId)
              .then(res => (res as any).data || [])
              .catch(e => { console.warn('[applicationDetail] Failed to load action table rows:', e); return [] as Array<{ bindingId: number; rows: Array<Record<string, unknown>> }> })
          : Promise.resolve([] as Array<{ bindingId: number; rows: Array<Record<string, unknown>> }>)

        // Load sub-table bindings (SUB and RELATED, not PRIMARY).
        // FORM_ONLY bindings without a subTable node still join linkableSubTableBindings so Link Form can resolve;
        // bottomSubTableBindings / unplacedSubTableBindings omit them to avoid empty duplicate sections.
        const bindings: typeof subTableBindings.value = []
        const tableBindings: any[] = selectedForm.tableBindings || []
        mainFormNativeSubTableBindingIds.value = tableBindings
          .filter((b: { bindingType?: string }) => b.bindingType !== 'PRIMARY')
          .map((b: { bindingId?: number }) => Number(b.bindingId))
          .filter((n: number) => Number.isFinite(n))
        mainFormConfig.value = selectedFormConfig
        const subFormsPayload = selectedFormConfig.subForms || {}
        for (const b of tableBindings) {
          if (b.bindingType === 'PRIMARY') {
            primaryTableBinding.value = {
              tableId: b.tableId != null ? Number(b.tableId) : null,
              tableName: b.tableDisplayName || b.tableName,
              fieldDefinitions: b.fieldDefinitions ?? [],
            }
            continue
          }
          let columns = ctx.resolveSubTableBindingColumnsForPortal(b, selectedFormConfig, content.forms)
          if (!Array.isArray(columns)) columns = []
          // DW parity: designed columns are the source of truth. Live fieldDefinitions only
          // serve as a fallback when the form has no designed columns at all (same as Todo phase).
          const fieldDefs = b.fieldDefinitions as Array<{ fieldName?: string; field_name?: string }> | undefined
          if (columns.length === 0 && fieldDefs?.length) {
            const existingFields = new Set<string>()
            for (const fd of fieldDefs) {
              const fn = String(fd.fieldName ?? fd.field_name ?? '').trim()
              if (!fn || existingFields.has(fn) || isSubTableRowMetaField(fn)) continue
              columns.push({ field: fn, label: fn, ...(isAuditField(fn) ? { readonly: true } : {}) })
              existingFields.add(fn)
            }
          }
          // Final pass: ensure audit columns already present from subListViews are readonly.
          for (const col of columns) {
            if (isAuditField(col.field)) (col as any).readonly = true
          }
          if (columns.length === 0) continue
          const subFormDesign = ctx.resolveSubFormDesign(b, subFormsPayload)
          const dialogColumns = resolveSubFormDialogColumnsForBinding(b, subFormsPayload, {
            lookupDbConfigs: ctx.lookupDbConfigs.value,
            relationViewConfigs: ctx.relationViewConfigs.value,
          })
          bindings.push({
            bindingId: b.bindingId,
            tableId: b.tableId != null ? Number(b.tableId) : null,
            bindingType: b.bindingType,
            bindingMode: b.bindingMode,
            foreignKeyField: b.foreignKeyField,
            tableName: b.tableDisplayName || b.tableName,
            physicalTableName: b.tableName,
            tableType: b.tableType,
            tableDescription: b.tableDescription,
            columns,
            ...(dialogColumns.length > 0 ? { dialogColumns } : {}),
            data: [],
            subMode: b.subMode,
            formFields: subFormDesign.formFields,
            formOptions: subFormDesign.formOptions,
            primaryKeyFields: resolveSubTablePrimaryKeyFields(
              b.primaryKeyFields,
              b.bindingId,
              selectedFormConfig
            ),
            fieldDefinitions: b.fieldDefinitions ?? [],
          })
        }

        ctx.mergeLinkFormTargetBindingsInto(bindings, content.forms as any[], selectedFormConfig, subFormsPayload)
        ctx.stripLinkOnlySubTableFieldsFromBindings(bindings, subFormsPayload, selectedFormConfig.rule, selectedFormConfig)

        const bindingRelationTableMap = buildBindingIdToRelationTableIdMap(content.forms as any[])
        lastBindingRelationTableMap.value = bindingRelationTableMap

        // Restore sub-table data from variables (promote nested link-form rows so bindings resolve like To Do).
        const rawSubTables = coerceSubTablesVariableToMap(formData.value.__subTables__)
        if (rawSubTables) {
          flattenNestedSubTableRowsIntoPayload(rawSubTables as Record<string, unknown>)
          formData.value = { ...formData.value, __subTables__: rawSubTables }
        }
        const savedSubTables = formData.value.__subTables__
        if (savedSubTables && typeof savedSubTables === 'object') {
          for (const binding of bindings) {
            // ACTION bindings never participate in __subTables__ (they write directly to their
            // own physical table, keyed by foreignKeyField=requestId) — filled separately below
            // from actionTableRowsPromise.
            if (binding.bindingType === 'ACTION') continue
            const raw = tableBindings.find((x: any) => Number(x.bindingId) === Number(binding.bindingId))
            const saved = getSavedSubTableRowsFromVariables(
              savedSubTables,
              {
                bindingId: binding.bindingId,
                tableName: raw?.tableName ?? (binding as { physicalTableName?: string }).physicalTableName,
                tableDisplayName: raw?.tableDisplayName ?? binding.tableName
              },
              binding.primaryKeyFields
            )
            if (saved) binding.data = saved
          }
          hydrateChildSubTablesFromParentsNestedRows(
            bindings,
            savedSubTables as Record<string, unknown>,
            bindingRelationTableMap
          )
          hydrateBindingsRowsFromVariablesBySharedRelationTableId(
            bindings,
            savedSubTables as Record<string, unknown>,
            bindingRelationTableMap
          )
          enrichChildBindingRowsFromParentsNestedSubTables(bindings)
        }
        attachAssignmentConfigsToBindings(bindings, content.miAssignments)
        // Fill ACTION binding rows from the dedicated per-request query (never __subTables__) —
        // applied last so nothing above (which only knows __subTables__ semantics) can touch it.
        const actionTableRowsByBindingId = new Map<number, Array<Record<string, unknown>>>()
        for (const entry of await actionTableRowsPromise) {
          if (entry?.bindingId != null) {
            actionTableRowsByBindingId.set(Number(entry.bindingId), entry.rows || [])
          }
        }
        for (const binding of bindings) {
          if (binding.bindingType !== 'ACTION') continue
          const rows = actionTableRowsByBindingId.get(Number(binding.bindingId))
          binding.data = rows ? [...rows] : []
        }
        subTableBindings.value = bindings
        ctx.applyLinkOnlySubTableFieldFilterToMainForm(selectedFormConfig)
        ctx.alignMainSubTableBindingsOnly()

        ctx.pendingApplicationDetailSecondary = {
          content,
          useInitiatorFormOnly,
          bpmnAllOrderedForms: [],
          bindingRelationTableMap,
          selectedForm,
          lookupSourceId:
            selectedForm.sourceId != null ? Number(selectedForm.sourceId) : null
        }
      } else {
        diagramReady.value = true
        previousForms.value = []
        subTableBindings.value = []
        nodeFormMap.value = new Map()
        isInitiatorMyRequestView.value = false
        selectedNodeId.value = null
      }
    } catch (error) {
      console.error('Failed to load function unit content:', error)
    }
  }

  return {
    loadProcessDetail,
    loadFunctionUnitContent,
  }
}
