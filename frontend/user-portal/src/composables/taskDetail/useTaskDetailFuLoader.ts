import { processApi } from '@/api/process'
import { relationTableApi } from '@/api/relationTable'
import {
  collectSubTableFieldsFromLayout,
} from '@/components/formRendererHelpers'
import {
  resolveSubTablePrimaryKeyFields,
  buildBindingIdToRelationTableIdMap,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  hydrateChildSubTablesFromParentsNestedRows,
  enrichChildBindingRowsFromParentsNestedSubTables,
  coerceSubTablesVariableToMap,
  scrubMiCorruptLinkChildRowsForParent,
  buildMiCollectionSliceKeySet,
  isSubTableRowMetaField,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  stripSubTableRowMetaFields,
  finalizeSharedProcessSubTableBindingRows,
  applySharedAttachmentFinalizeAndMaterialize,
} from '@/composables/tasks/shared'
import {
  bindingMatchesMiSubTableName,
  extractMiParticipantRowIdFromCurrentItem,
} from '@/composables/tasks/miSubProcessScope'
import {
  mergeListViewFieldColumn,
  mergeMissingTableFieldColumns,
  inferColumnTypeFromFieldAndValue,
  buildRelationTableFieldIndexFromDataTables,
  isAuditField,
  resolveSubFormDialogColumnsForBinding,
} from '@/components/subTableAddDialogHelpers'
import { createFuContentCache } from './fuContentCache'
import { stampMiCollectionFromBpmn } from './miCollectionStamp'
import {
  cloneSubTableRows,
  cloneAndFlattenSubTablesMap,
  bindingIdsPreferStrictSubTableLookup,
} from './subTableRowUtils'
import type { TaskDetailCtx } from './context'
import {
  attachAssignmentConfigsToBindings,
  stampAssignmentConfigsOnForms,
} from '@/utils/miAssignmentConfig'

export interface TaskDetailFuLoaderFns {
  loadFunctionUnitContent: (
    processKey: string,
    prefetchedContent?: any,
    preFlattenedSubTables?: Record<string, unknown>,
  ) => Promise<void>
}

export function createTaskDetailFuLoader(ctx: TaskDetailCtx): TaskDetailFuLoaderFns {
  const {
    t,
    processError,
    lookupDbConfigs,
    relationViewConfigs,
    fuFormSubTableFields,
    mainFormConfig,
    mainFormNativeSubTableBindingIds,
    primaryReadOnly,
    primaryTableBinding,
    primaryTableFieldNames,
    lastBindingRelationTableMap,
    miSubProcessScope,
    subTableBindings,
    previousForms,
    nodeFormMap,
  } = ctx
  const {
    formData,
    formFields,
    formTabs,
    formFieldsAfterTabs,
    formReadOnly,
    currentFormName,
  } = ctx.taskForm
  const {
    bpmnXml,
    processNodes,
    parseBpmnXml,
    parseBpmnXmlAndGetFormId,
  } = ctx.bpmn
  const { getCachedFuContent, setCachedFuContent } = createFuContentCache()

  // Load function unit content (optional prefetched payload avoids duplicate HTTP when parallel with history)
  const loadFunctionUnitContent = async (
    processKey: string,
    prefetchedContent?: any,
    preFlattenedSubTables?: Record<string, unknown>,
  ) => {
    processError.value = null
    try {
      // Check module-level cache first (avoids re-parsing when navigating between tasks of same process)
      let content: any = prefetchedContent ?? getCachedFuContent(processKey)
      if (!content) {
        // taskId grants task participants (assignee/candidate/initiator) content access even
        // when they lack the FU's start-access roles (backend falls back to the role gate).
        content = await processApi.getFunctionUnitContent(processKey, ctx.taskId).then(r => (r as any).data || r)
        if (content && !content.error) {
          setCachedFuContent(processKey, content)
        }
      }
      if (content.error) {
        console.error('Function unit content error:', content.error)
        processError.value = t('task.processLoadFailed')
        return
      }

      stampAssignmentConfigsOnForms(content.forms, content.miAssignments)
      ctx.cachedContentForms = content.forms || []
      ctx.cachedRelationTableFieldIndex = buildRelationTableFieldIndexFromDataTables(content.dataTables)

      let currentFormInfo: { formId: string | null, formName: string | null, readOnly: boolean } = { formId: null, formName: null, readOnly: false }

      // Parse process diagram
      if (content.processes?.length > 0) {
        // First get the current node formId and formName
        currentFormInfo = parseBpmnXmlAndGetFormId(content.processes[0].data)
        bpmnXml.value = content.processes[0].data
        parseBpmnXml(content.processes[0].data)
        ctx.refreshMiSubProcessScopeFromBpmn()
      }

      // Parse forms — failures must not block workflow diagram (bpmnXml already set above).
      if (content.forms?.length > 0) {
        try {
        let selectedForm = content.forms[0] // Default to first

        // Prefer matching formId to sourceId (original form ID)
        if (currentFormInfo.formId) {
          const matchedForm = content.forms.find((f: any) =>
            String(f.sourceId) === currentFormInfo.formId
          )
          if (matchedForm) {
            selectedForm = matchedForm
          } else {
            // If sourceId match fails, try matching by formName
            if (currentFormInfo.formName) {
              const matchedByName = content.forms.find((f: any) => f.name === currentFormInfo.formName)
              if (matchedByName) {
                selectedForm = matchedByName
              }
            }
          }
        } else if (currentFormInfo.formName) {
          // If no formId, try matching by formName
          const matchedForm = content.forms.find((f: any) => f.name === currentFormInfo.formName)
          if (matchedForm) {
            selectedForm = matchedForm
          }
        }

        currentFormName.value = selectedForm.name

        // Load lookup configs from rt_lookup_configs before parsing form
        lookupDbConfigs.value = {}
        // Start lookup configs fetch in parallel (non-blocking) — will be awaited before binding column derivation
        const lookupConfigsPromise = selectedForm.sourceId
          ? relationTableApi.getLookupConfigs(Number(selectedForm.sourceId))
              .then(lcRes => {
                const configs: Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: string[] }> = {}
                for (const lc of (lcRes.data || [])) {
                  let sf: string[] = []
                  try { sf = typeof lc.searchFields === 'string' ? JSON.parse(lc.searchFields || '[]') : (lc.searchFields || []) } catch { sf = [] }
                  configs[lc.componentId] = { tableId: lc.tableId, searchFields: sf, displayField: lc.displayField || '', viewFields: lc.viewFields || [] }
                }
                return configs
              })
              .catch(e => { console.warn('[task] Failed to load lookup configs:', e); return {} as Record<string, any> })
          : Promise.resolve({} as Record<string, any>)

        // ACTION-table rows (e.g. FORM_POPUP "Meeting Remark" history) are per-request data that
        // must NOT come from the cached FU content payload above (shared across every task/request
        // of this FU) — fetched fresh per task in parallel, keyed by bindingId, and merged into the
        // matching binding's `data` after the tableBindings loop below (skipping __subTables__ for
        // ACTION bindings entirely).
        const actionTableRowsPromise = ctx.taskId
          ? processApi.getActionTableRows(ctx.taskId)
              .then(res => (res as any).data || [])
              .catch(e => { console.warn('[task] Failed to load action table rows:', e); return [] as Array<{ bindingId: number; rows: Array<Record<string, unknown>> }> })
          : Promise.resolve([] as Array<{ bindingId: number; rows: Array<Record<string, unknown>> }>)

        // Parse relationViews from configJson BEFORE parseFormConfig so lookup view fields are available
        try {
          const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
          relationViewConfigs.value = cfg.relationViews || {}
        } catch { relationViewConfigs.value = {} }

        ctx.parseFormConfig(selectedForm.data)
        fuFormSubTableFields.value = collectSubTableFieldsFromLayout(
          formFields.value,
          formTabs.value,
          formFieldsAfterTabs.value,
        )

        // If BPMN explicitly marks readOnly, override the form config value
        if (currentFormInfo.readOnly) {
          formReadOnly.value = true
        }

        // Parse subForms from configJson
        let subForms: Record<string, any> = {}
        let formConfigForSubTables: Record<string, any> = {}
        try {
          const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
          formConfigForSubTables = cfg
          subForms = cfg.subForms || {}
        } catch {}

        // Load sub-table bindings for this form (SUB and RELATED, not PRIMARY)
        const bindings: typeof subTableBindings.value = []
        const tableBindings: any[] = selectedForm.tableBindings || []

        // Persist main-form designer context for selected-node FormRenderer parity (#1395):
        //  - mainFormConfig powers Link Form / portalViews resolution
        //  - mainFormNativeSubTableBindingIds tags "placed-on-canvas" bindings vs merged Link Form targets
        mainFormConfig.value = formConfigForSubTables
        mainFormNativeSubTableBindingIds.value = tableBindings
          .filter((b: any) => b.bindingType !== 'PRIMARY')
          .map((b: any) => Number(b.bindingId))
          .filter((n: number) => Number.isFinite(n))

        // When the PRIMARY table binding has bindingMode READONLY, force primary form fields read-only
        // without affecting sub-table editability (sub-tables check their own bindingMode).
        // (This is set via Form Designer → Manage Table Bindings → Edit → Binding Mode)
        const primaryBinding = tableBindings.find((b: any) => b.bindingType === 'PRIMARY')
        if (primaryBinding) {
          primaryTableBinding.value = {
            tableId: primaryBinding.tableId ?? null,
            tableName: primaryBinding.tableDisplayName || primaryBinding.tableName,
            fieldDefinitions: primaryBinding.fieldDefinitions ?? [],
          }
          const names = new Set<string>()
          for (const fd of primaryBinding.fieldDefinitions ?? []) {
            const n = fd?.fieldName ?? fd?.field_name
            if (n != null && String(n).trim() !== '') names.add(String(n))
          }
          primaryTableFieldNames.value = names
        } else {
          primaryTableBinding.value = null
          primaryTableFieldNames.value = new Set()
        }
        if (primaryBinding?.bindingMode === 'READONLY') {
          primaryReadOnly.value = true
        }

        // Await lookup configs before deriving columns (needs lookupDbConfigs for lookup-type fields)
        const resolvedLookups = await lookupConfigsPromise
        lookupDbConfigs.value = resolvedLookups
        const actionTableRowsByBindingId = new Map<number, Array<Record<string, unknown>>>()
        for (const entry of await actionTableRowsPromise) {
          if (entry?.bindingId != null) {
            actionTableRowsByBindingId.set(Number(entry.bindingId), entry.rows || [])
          }
        }

        for (const b of tableBindings) {
          if (b.bindingType === 'PRIMARY') continue
          let columns = ctx.deriveColumnsFromBinding(b, subForms, formConfigForSubTables)
          // DW parity: designed columns (subListViews / sub-form rule) are the source of truth.
          // Table schema only serves as a fallback when the form has no designed columns at all,
          // trying dataTables JSON first (rich metadata) then live fieldDefinitions (always current).
          if (b.tableId != null) {
            const tableIdNum = Number(b.tableId)
            if (Number.isFinite(tableIdNum)) {
              columns = mergeMissingTableFieldColumns(columns, ctx.cachedRelationTableFieldIndex.get(tableIdNum))
            }
          }
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
          const subFormDesign = ctx.resolveSubFormDesign(b, subForms)
          const dialogColumns = resolveSubFormDialogColumnsForBinding(b, subForms, {
            lookupDbConfigs: lookupDbConfigs.value,
            relationViewConfigs: relationViewConfigs.value,
          })
          bindings.push({
            bindingId: b.bindingId,
            tableId: b.tableId ?? null,
            bindingType: b.bindingType,
            bindingMode: b.bindingMode,
            foreignKeyField: b.foreignKeyField,
            tableName: b.tableDisplayName || b.tableName,
            physicalTableName: b.tableName,
            relationTableId: (b as any).relationTableId ?? null,
            relationTableName: (b as any).relationTableName ?? null,
            tableType: b.tableType,
            tableDescription: b.tableDescription,
            columns,
            ...(dialogColumns.length > 0 ? { dialogColumns } : {}),
            formFields: subFormDesign.formFields,
            formOptions: subFormDesign.formOptions,
            primaryKeyFields: resolveSubTablePrimaryKeyFields(
              b.primaryKeyFields,
              b.bindingId,
              formConfigForSubTables
            ),
            fieldDefinitions: b.fieldDefinitions ?? [],
            bindingLinkMode: b.bindingLinkMode,
            data: []
          })
        }
        // Link Form columns may reference bindings omitted from this form's tableBindings; merge from FU forms so bindingMap resolves subtable2.
        ctx.mergeLinkFormTargetBindingsInto(bindings, content.forms, formConfigForSubTables, subForms)
        stampMiCollectionFromBpmn(ctx, bindings)
        const bindingRelationTableMap = buildBindingIdToRelationTableIdMap(content.forms as any[])
        lastBindingRelationTableMap.value = bindingRelationTableMap
        const rawSubTables = coerceSubTablesVariableToMap(formData.value.__subTables__)
        if (rawSubTables) {
          const flattened = preFlattenedSubTables ?? cloneAndFlattenSubTablesMap(rawSubTables)
          const ciLoad = (formData.value._currentItem ?? formData.value.currentItem) as
            | Record<string, unknown>
            | undefined
          const scopeLoad = miSubProcessScope.value
          const collBindingLoad = scopeLoad?.subTableName
            ? bindings.find(b => bindingMatchesMiSubTableName(b, scopeLoad.subTableName))
            : undefined
          const ridLoad = extractMiParticipantRowIdFromCurrentItem(ciLoad, collBindingLoad?.primaryKeyFields, {
            rowIdVariable: scopeLoad?.rowIdVariable ?? 'currentItem.rowId',
          })
          if (ridLoad != null) {
            scrubMiCorruptLinkChildRowsForParent(flattened, ridLoad, {
              skipSliceKeys: buildMiCollectionSliceKeySet(
                bindings,
                bindingRelationTableMap,
                scopeLoad?.subTableName,
              ),
            })
          }
          formData.value = { ...formData.value, __subTables__: flattened }
        }
        const savedSubTables = formData.value.__subTables__
        if (savedSubTables && typeof savedSubTables === 'object') {
          const ambiguousMain = bindingIdsPreferStrictSubTableLookup(bindings)
          bindings.forEach(binding => {
            // ACTION bindings never participate in __subTables__ (see ActionFormPopupSubmitComponent —
            // they write directly to their own physical table, keyed by foreignKeyField=requestId).
            // Their data is filled separately, after all __subTables__-oriented passes below, from
            // actionTableRowsByBindingId — never from this process-variable path.
            if (binding.bindingType === 'ACTION') return
            const saved = ctx.getSavedSubTableRows(savedSubTables, binding, ambiguousMain.has(binding.bindingId))
            if (saved) {
              binding.data = cloneSubTableRows(saved)
            }
          })
        }
        hydrateChildSubTablesFromParentsNestedRows(
          bindings,
          savedSubTables && typeof savedSubTables === 'object' ? (savedSubTables as Record<string, unknown>) : null,
          bindingRelationTableMap
        )
        if (savedSubTables && typeof savedSubTables === 'object') {
          hydrateBindingsRowsFromVariablesBySharedRelationTableId(
            bindings,
            savedSubTables as Record<string, unknown>,
            bindingRelationTableMap,
            {
              // MI dashboard/collection bindings hydrate their own slice (filtered to the current
              // participant) via getSavedSubTableRows above and resyncMiParticipantSubTablesFromVariables
              // later. Sibling BPMN nodes duplicate this same designer table under their OWN bindingId,
              // and their rows can share this binding's exact PK (all just aliases of the same logical
              // participant collection) — pulling those siblings in here re-introduces another
              // participant's stale row, which then wins the merge purely by array order (#1524-class:
              // reload showing another participant's old value instead of this task's own saved edit).
              excludeBinding: b => isMiDashboardSubTableBinding(b),
            },
          )
        }
        enrichChildBindingRowsFromParentsNestedSubTables(bindings)
        // When subForms have no rule, columns are empty causing no columns/assignee inference; infer columns from loaded row data
        bindings.forEach(binding => {
          // ACTION bindings' data is filled after this block, from actionTableRowsByBindingId —
          // skip the __subTables__-oriented shared-process/MI filtering passes below entirely.
          if (binding.bindingType === 'ACTION') return
          if ((!binding.columns || binding.columns.length === 0) && binding.data?.length) {
            const row0 = binding.data[0]
            if (row0 && typeof row0 === 'object') {
              binding.columns = Object.keys(row0)
                .filter(k => !isSubTableRowMetaField(k))
                .map(k => {
                const inferred = inferColumnTypeFromFieldAndValue(k, row0[k])
                if (inferred === 'upload') {
                  return mergeListViewFieldColumn(
                    { fieldName: k, comment: k, dataType: 'FILE' },
                    { field: k, label: k },
                    null,
                  )
                }
                return { field: k, label: k, type: 'text' as const }
              })
            }
          }
          if (!isMiDashboardSubTableBinding(binding) && Array.isArray(binding.data)) {
            if (isMiParticipantScopedSubTableBinding(binding)) {
              binding.data = binding.data.map(row =>
                row && typeof row === 'object'
                  ? stripSubTableRowMetaFields(row as Record<string, unknown>)
                  : row
              )
            } else {
              binding.data = finalizeSharedProcessSubTableBindingRows(binding.data, binding)
            }
          }
        })
        if (savedSubTables && typeof savedSubTables === 'object') {
          applySharedAttachmentFinalizeAndMaterialize(
            bindings,
            formData.value as Record<string, unknown>,
            {
              flattened: savedSubTables as Record<string, unknown>,
              bindingTableById: bindingRelationTableMap,
            },
          )
        }
        ctx.ensureSubTableBindingsFromLayoutAndConfig(bindings, formConfigForSubTables)
        // ensure* appends layout-only bindings after the stamp above — re-apply so they carry it too.
        stampMiCollectionFromBpmn(ctx, bindings)
        attachAssignmentConfigsToBindings(bindings, content.miAssignments)
        // Fill ACTION binding rows from the dedicated per-request query (never __subTables__) —
        // applied last so nothing above (which only knows __subTables__ semantics) can touch it.
        for (const binding of bindings) {
          if (binding.bindingType !== 'ACTION') continue
          const rows = actionTableRowsByBindingId.get(Number(binding.bindingId))
          binding.data = rows ? [...rows] : []
        }
        subTableBindings.value = bindings
        ctx.syncFormLayoutWithSubTableBindings()
        // Previous-node read-only form collection — moved verbatim to useTaskDetailPrevForms.ts.
        ctx.collectPreviousFormsFromContent(content, currentFormInfo, selectedForm, savedSubTables)

        try {
          ctx.alignProcessSubTableBindingsBySharedTable()
        } catch (alignErr) {
          console.warn('[detail] Sub-table align skipped (diagram kept):', alignErr)
        }
        } catch (formLoadErr) {
          console.error('Task form binding load failed (diagram kept):', formLoadErr)
        }
      } else {
        previousForms.value = []
      }

      // Defer nodeFormMap (diagram node clicks) — independent of form hydrate success.
      if (content.processes?.length > 0) {
        ctx.deferredNodeFormMapContent = {
          content,
          bindingRelationTableMap: lastBindingRelationTableMap.value,
        }
        nodeFormMap.value = new Map()
      } else {
        processError.value = t('task.processLoadFailed')
      }
    } catch (error: any) {
      console.error('Failed to load function unit content:', error)
      // Only surface diagram error when BPMN was not loaded — form/sub-table failures are non-fatal.
      if (!bpmnXml.value && processNodes.value.length === 0) {
        if (error.response?.status === 403) {
          processError.value = t('task.noPermission')
        } else {
          processError.value = t('task.processLoadFailed')
        }
      }
    }
  }

  return {
    loadFunctionUnitContent,
  }
}
