import type { ProcessNode } from '@/components/ProcessDiagram.vue'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import {
  filterLinkOnlyStandaloneSubTableFields,
  findTabsRule,
  isTabPaneRule,
} from '@/components/formRendererHelpers'
import { isAuditField } from '@/components/subTableAddDialogHelpers'
import {
  resolveSubTablePrimaryKeyFields,
  hydrateChildSubTablesFromParentsNestedRows,
  buildBindingIdToRelationTableIdMap,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  enrichChildBindingRowsFromParentsNestedSubTables,
  applySharedAttachmentFinalizeAndMaterialize,
  isSubTableRowMetaField,
} from '@/composables/tasks/shared'
import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'
import {
  getSavedSubTableRowsFromVariables,
} from './subTableRowHelpers'
import type { ApplicationDiagramNodeFormInfo, PreviousFormEntry } from './useApplicationDetailState'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailNodeFormMapFns {
  handleDiagramNodeClick: (node: ProcessNode) => void
  clearDiagramNodeSelection: () => void
  scheduleBuildApplicationNodeFormMap: (content: any) => void
  ensureApplicationNodeFormMapBuilt: () => void
  buildApplicationNodeFormMap: (content: any) => void
}

export function createApplicationDetailNodeFormMap(ctx: ApplicationDetailCtx): ApplicationDetailNodeFormMapFns {
  const {
    snapshotTaskName,
    snapshotTaskDefinitionKey,
    snapshotActivityId,
    processInfo,
    formData,
    selectedNodeId,
    nodeFormMap,
    lastBindingRelationTableMap,
  } = ctx

  function handleDiagramNodeClick(node: ProcessNode) {
    ensureApplicationNodeFormMapBuilt()
    if (!node?.id) {
      selectedNodeId.value = null
      return
    }
    if (selectedNodeId.value === node.id) {
      selectedNodeId.value = null
    } else {
      selectedNodeId.value = node.id
    }
  }

  function clearDiagramNodeSelection() {
    selectedNodeId.value = null
  }

  /**
   * BPMN userTask / serviceTask / subProcess → bound form metadata for diagram clicks.
   * Aligns with tasks/detail.vue `nodeFormMap` (My Request is always read-only).
   */
  /** Diagram node preview map is expensive; build after first paint or on first node click. */
  function scheduleBuildApplicationNodeFormMap(content: any) {
    ctx.pendingNodeFormMapContent = content
    if (ctx.nodeFormMapBuildScheduled) return
    ctx.nodeFormMapBuildScheduled = true
    const run = () => {
      ctx.nodeFormMapBuildScheduled = false
      const payload = ctx.pendingNodeFormMapContent
      ctx.pendingNodeFormMapContent = null
      if (payload) buildApplicationNodeFormMap(payload)
    }
    if (typeof requestIdleCallback === 'function') {
      requestIdleCallback(run, { timeout: 2500 })
    } else {
      setTimeout(run, 0)
    }
  }

  function ensureApplicationNodeFormMapBuilt() {
    if (nodeFormMap.value.size > 0) return
    if (ctx.pendingNodeFormMapContent) {
      const payload = ctx.pendingNodeFormMapContent
      ctx.pendingNodeFormMapContent = null
      ctx.nodeFormMapBuildScheduled = false
      buildApplicationNodeFormMap(payload)
    }
  }

  function buildApplicationNodeFormMap(content: any) {
    const newMap = new Map<string, ApplicationDiagramNodeFormInfo>()
    const bpmnData = content.processes?.[0]?.data as string | undefined
    const formsList = content.forms as any[] | undefined
    if (!bpmnData || !formsList?.length) {
      nodeFormMap.value = newMap
      return
    }

    const normLabel = (s: string | null | undefined) => (s || '').trim().replace(/\s+/g, ' ')
    const curRaw =
      (snapshotActivityId.value ||
        snapshotTaskDefinitionKey ||
        snapshotTaskName ||
        processInfo.value.currentNode ||
        '') + ''
    const curNorm = normLabel(curRaw)
    const savedSubTables = formData.value.__subTables__
    const bindingRelationTableMap = buildBindingIdToRelationTableIdMap(formsList)
    lastBindingRelationTableMap.value = bindingRelationTableMap

    try {
      const doc = getCachedBpmnDocument(bpmnData)
      if (!doc) {
        nodeFormMap.value = newMap
        return
      }
      const allElements = doc.getElementsByTagName('*')
      for (let i = 0; i < allElements.length; i++) {
        const el = allElements[i]!
        const localName = el.localName || el.nodeName.split(':').pop()
        if (localName !== 'userTask' && localName !== 'subProcess' && localName !== 'serviceTask') continue
        const nodeId = el.getAttribute('id') || ''
        if (!nodeId) continue

        let formId: string | null = null
        let formName: string | null = null
        const props = el.getElementsByTagName('*')
        for (let j = 0; j < props.length; j++) {
          const p = props[j]!
          const ln = p.localName || p.nodeName.split(':').pop()
          if (ln === 'property' || ln === 'values') {
            const n = p.getAttribute('name')
            const v = p.getAttribute('value')
            if (n === 'formId' && v) formId = v
            if (n === 'formName' && v) formName = v
          }
        }

        let matchedForm: any = null
        if (formId) {
          matchedForm = formsList.find((f: any) => String(f.sourceId) === formId)
        }
        if (!matchedForm && formName) {
          matchedForm = formsList.find((f: any) => f.name === formName)
        }
        if (!matchedForm) continue

        const nodeName = el.getAttribute('name') || nodeId
        const isCurrentStep =
          (!!snapshotTaskDefinitionKey && nodeId === String(snapshotTaskDefinitionKey).trim()) ||
          (!!snapshotActivityId.value && nodeId === String(snapshotActivityId.value).trim()) ||
          (!!curNorm.length && normLabel(nodeName) === curNorm) ||
          (!!curRaw.trim() && nodeId === curRaw.trim())

        const nodeFields: FormField[] = []
        const nodeTabs: FormTab[] = []
        const nodeBindings: PreviousFormEntry['subTableBindings'] = []
        const nativeSubTableBindingIds = (matchedForm.tableBindings || [])
          .filter((b: { bindingType?: string }) => b.bindingType !== 'PRIMARY')
          .map((b: { bindingId?: number }) => Number(b.bindingId))
          .filter((n: number) => Number.isFinite(n))
        let configForSubTables: Record<string, any> = {}
        try {
          const cfg = typeof matchedForm.data === 'string' ? JSON.parse(matchedForm.data) : (matchedForm.data || {})
          const rules = cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : null)
          if (rules) {
            const tabsRule = findTabsRule(rules)
            if (tabsRule?.children) {
              for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
                if (isTabPaneRule(tabPane) && tabPane.props) {
                  const tabFields: FormField[] = []
                  if (tabPane.children) tabFields.push(...ctx.extractFieldsRecursive(tabPane.children))
                  nodeTabs.push({
                    name: tabPane.props.name || `tab_${nodeTabs.length}`,
                    label: tabPane.props.label || `Tab ${nodeTabs.length + 1}`,
                    fields: tabFields
                  })
                }
              }
            } else {
              nodeFields.push(...ctx.extractFieldsRecursive(rules))
            }
          }
          let subForms: Record<string, any> = {}
          try {
            configForSubTables = cfg
            subForms = cfg.subForms || {}
          } catch {
            /* ignore */
          }
          const subTablePortalViewsPayload = cfg.subTablePortalViews || {}
          for (const b of matchedForm.tableBindings || []) {
            if (b.bindingType === 'PRIMARY') continue
            let cols = ctx.resolveSubTableBindingColumnsForPortal(b, configForSubTables, formsList)
            if (!Array.isArray(cols)) cols = []
            // DW parity: designed columns are the source of truth. Live fieldDefinitions only
            // serve as a fallback when the form has no designed columns at all (same as Todo phase).
            const fieldDefs = b.fieldDefinitions as Array<{ fieldName?: string; field_name?: string }> | undefined
            if (cols.length === 0 && fieldDefs?.length) {
              const existingFields = new Set<string>()
              for (const fd of fieldDefs) {
                const fn = String(fd.fieldName ?? fd.field_name ?? '').trim()
                if (!fn || existingFields.has(fn) || isSubTableRowMetaField(fn)) continue
                cols.push({ field: fn, label: fn, ...(isAuditField(fn) ? { readonly: true } : {}) })
                existingFields.add(fn)
              }
            }
            // Final pass: ensure audit columns already present from subListViews are readonly.
            for (const col of cols) {
              if (isAuditField(col.field)) (col as any).readonly = true
            }
            if (cols.length === 0) continue
            const subFormDesign = ctx.resolveSubFormDesign(b, subForms)
            const bindingPortalViews =
              subTablePortalViewsPayload[b.bindingId]
              ?? subTablePortalViewsPayload[String(b.bindingId)]
              ?? null
            const binding = {
              bindingId: b.bindingId,
              tableId: b.tableId != null ? Number(b.tableId) : null,
              bindingType: b.bindingType,
              bindingMode: b.bindingMode,
              foreignKeyField: b.foreignKeyField,
              tableName: b.tableDisplayName || b.tableName,
              physicalTableName: b.tableName,
              tableType: b.tableType,
              tableDescription: b.tableDescription,
              columns: cols,
              data: [] as any[],
              subMode: b.subMode,
              formFields: subFormDesign.formFields,
              formOptions: subFormDesign.formOptions,
              portalViews: bindingPortalViews,
              primaryKeyFields: resolveSubTablePrimaryKeyFields(b.primaryKeyFields, b.bindingId, configForSubTables)
            }
            if (savedSubTables && typeof savedSubTables === 'object') {
              const saved = getSavedSubTableRowsFromVariables(
                savedSubTables,
                {
                  bindingId: b.bindingId,
                  tableName: b.tableName,
                  tableDisplayName: b.tableDisplayName
                },
                binding.primaryKeyFields
              )
              if (saved) binding.data = saved
            }
            nodeBindings.push(binding)
          }
          ctx.mergeLinkFormTargetBindingsInto(nodeBindings, formsList, configForSubTables, subForms)
          ctx.stripLinkOnlySubTableFieldsFromBindings(nodeBindings, subForms, configForSubTables.rule, configForSubTables)
          if (savedSubTables && typeof savedSubTables === 'object') {
            for (const binding of nodeBindings) {
              const saved = getSavedSubTableRowsFromVariables(
                savedSubTables,
                {
                  bindingId: binding.bindingId,
                  tableName: (binding as { physicalTableName?: string }).physicalTableName,
                  tableDisplayName: binding.tableName
                },
                binding.primaryKeyFields
              )
              if (saved) binding.data = saved
            }
          }
          hydrateChildSubTablesFromParentsNestedRows(
            nodeBindings,
            savedSubTables && typeof savedSubTables === 'object' ? (savedSubTables as Record<string, unknown>) : null,
            bindingRelationTableMap
          )
          if (savedSubTables && typeof savedSubTables === 'object') {
            hydrateBindingsRowsFromVariablesBySharedRelationTableId(
              nodeBindings,
              savedSubTables as Record<string, unknown>,
              bindingRelationTableMap
            )
          }
          enrichChildBindingRowsFromParentsNestedSubTables(nodeBindings)
          applySharedAttachmentFinalizeAndMaterialize(nodeBindings, formData.value as Record<string, unknown>, {
            flattened:
              savedSubTables && typeof savedSubTables === 'object'
                ? (savedSubTables as Record<string, unknown>)
                : null,
            bindingTableById: bindingRelationTableMap,
          })

          const formRulesForFilter =
            cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : [])
          const nativeIdSet = new Set(nativeSubTableBindingIds)
          if (formRulesForFilter.length > 0 && nodeBindings.length > 0) {
            if (nodeFields.length > 0) {
              const filtered = filterLinkOnlyStandaloneSubTableFields(
                nodeFields,
                nodeBindings,
                formRulesForFilter,
                nativeIdSet,
                configForSubTables,
              )
              nodeFields.length = 0
              nodeFields.push(...filtered)
            }
            for (const tab of nodeTabs) {
              tab.fields = filterLinkOnlyStandaloneSubTableFields(
                tab.fields,
                nodeBindings,
                formRulesForFilter,
                nativeIdSet,
                configForSubTables,
              )
            }
          }
        } catch {
          /* ignore per-node parse errors */
        }

        newMap.set(nodeId, {
          formName: matchedForm.name || nodeName,
          isCurrentStep,
          fields: nodeFields,
          tabs: nodeTabs,
          values: { ...formData.value },
          subTableBindings: nodeBindings,
          nativeSubTableBindingIds,
          formConfig: configForSubTables,
        })
      }
    } catch (e) {
      console.warn('[ApplicationDetail] buildApplicationNodeFormMap failed:', e)
    }

    nodeFormMap.value = newMap
  }

  return {
    handleDiagramNodeClick,
    clearDiagramNodeSelection,
    scheduleBuildApplicationNodeFormMap,
    ensureApplicationNodeFormMapBuilt,
    buildApplicationNodeFormMap,
  }
}
