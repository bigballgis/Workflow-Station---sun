import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import {
  findTabsRule,
  isTabPaneRule,
} from '@/components/formRendererHelpers'
import {
  resolveSubTablePrimaryKeyFields,
  isMiDashboardSubTableBinding,
  finalizeMiCollectionSubTableBindingRows,
} from '@/composables/tasks/shared'
import {
  cloneSubTableRows,
  bindingIdsPreferStrictSubTableLookup,
} from './subTableRowUtils'
import type { PreviousFormEntry } from './useTaskDetailState'
import type { TaskDetailCtx } from './context'
import { stampMiCollectionFromBpmn } from './miCollectionStamp'

export interface TaskDetailPrevFormsFns {
  collectPreviousFormsFromContent: (
    content: any,
    currentFormInfo: { formId: string | null; formName: string | null; readOnly: boolean },
    selectedForm: any,
    savedSubTables: any,
  ) => void
}

/**
 * Previous-node read-only form collection — extracted verbatim from
 * loadFunctionUnitContent (behavior unchanged).
 */
export function createTaskDetailPrevForms(ctx: TaskDetailCtx): TaskDetailPrevFormsFns {
  const { previousForms } = ctx
  const { formLabelWidth } = ctx.taskForm
  const { parseBpmnXmlAndGetPreviousFormIds } = ctx.bpmn

  // Collect all distinct forms bound to nodes before the current one (read-only display)
  // Only consider when the current node successfully matched its own form
  function collectPreviousFormsFromContent(
    content: any,
    currentFormInfo: { formId: string | null; formName: string | null; readOnly: boolean },
    selectedForm: any,
    savedSubTables: any,
  ) {
    if (content.processes?.length > 0 && (currentFormInfo.formId || currentFormInfo.formName)) {
      const prevFormIds = parseBpmnXmlAndGetPreviousFormIds(content.processes[0].data)
      const collectedPrevForms: PreviousFormEntry[] = []

      for (const info of prevFormIds) {
        // Skip forms identical to the current one
        let prevForm: any = null
        if (info.formId) {
          if (info.formId === String(selectedForm.sourceId)) continue
          prevForm = content.forms.find((f: any) => String(f.sourceId) === info.formId)
        }
        if (!prevForm && info.formName) {
          if (info.formName === selectedForm.name) continue
          prevForm = content.forms.find((f: any) => f.name === info.formName)
        }
        // fallback: match form by BPMN node name
        if (!prevForm && (info as any).taskName) {
          if ((info as any).taskName === selectedForm.name) continue
          prevForm = content.forms.find((f: any) => f.name === (info as any).taskName)
        }
        if (!prevForm || prevForm.id === selectedForm.id) continue
        // Deduplicate (show each form only once)
        if (collectedPrevForms.some(e => e.formId === String(prevForm.id))) continue

        // Parse form fields
        const parsedFields: FormField[] = []
        const parsedTabs: FormTab[] = []
        try {
          const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
          const rules = cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : null)
          if (rules) {
            const tabsRule = findTabsRule(rules)
            if (tabsRule?.children) {
              for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
                if (isTabPaneRule(tabPane) && tabPane.props) {
                  const tabFields: FormField[] = []
                  if (tabPane.children) tabFields.push(...ctx.extractFieldsRecursive(tabPane.children))
                  parsedTabs.push({ name: tabPane.props.name || `tab_${parsedTabs.length}`, label: tabPane.props.label || `Tab ${parsedTabs.length + 1}`, fields: tabFields })
                }
              }
            } else {
              parsedFields.push(...ctx.extractFieldsRecursive(rules))
            }
          }
        } catch {}

        // Parse sub-table bindings
        let prevSubForms: Record<string, any> = {}
        let prevConfigForSubTables: Record<string, any> = {}
        let prevSubTablePortalViews: Record<string, any> = {}
        try {
          const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
          prevConfigForSubTables = cfg
          prevSubForms = cfg.subForms || {}
          prevSubTablePortalViews = cfg.subTablePortalViews || {}
        } catch {}
        const prevBindings: PreviousFormEntry['subTableBindings'] = []
        for (const b of (prevForm.tableBindings || [])) {
          if (b.bindingType === 'PRIMARY') continue
          const cols = ctx.deriveColumnsFromBinding(b, prevSubForms, prevConfigForSubTables)
          const subFormDesign = ctx.resolveSubFormDesign(b, prevSubForms)
          const bindingPortalViews =
            prevSubTablePortalViews[b.bindingId] ?? prevSubTablePortalViews[String(b.bindingId)] ?? null
          const binding = {
            bindingId: b.bindingId, tableId: b.tableId ?? null, bindingType: b.bindingType, bindingMode: b.bindingMode,
            foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName, physicalTableName: b.tableName,
            tableType: b.tableType, tableDescription: b.tableDescription, columns: cols,
            formFields: subFormDesign.formFields,
            formOptions: subFormDesign.formOptions,
            portalViews: bindingPortalViews,
            primaryKeyFields: resolveSubTablePrimaryKeyFields(
              b.primaryKeyFields,
              b.bindingId,
              prevConfigForSubTables
            ),
            data: [] as any[]
          }
          prevBindings.push(binding)
        }
        // Must precede the MI ghost-row filter below: isMiDashboardSubTableBinding reads this flag.
        stampMiCollectionFromBpmn(ctx, prevBindings)
        const ambiguousPrev = bindingIdsPreferStrictSubTableLookup(prevBindings as any[])
        if (savedSubTables && typeof savedSubTables === 'object') {
          for (const binding of prevBindings) {
            const saved = ctx.getSavedSubTableRows(savedSubTables, binding, ambiguousPrev.has(binding.bindingId))
            if (saved) {
              binding.data = cloneSubTableRows(
                isMiDashboardSubTableBinding(binding)
                  ? finalizeMiCollectionSubTableBindingRows(saved, binding)
                  : saved,
              )
            }
          }
        }
        // Previous forms: field metadata only on critical path — full sub-table enrich deferred with nodeFormMap.

        collectedPrevForms.push({
          formId: String(prevForm.id),
          formName: prevForm.name,
          labelWidth: formLabelWidth.value,
          fields: parsedFields,
          tabs: parsedTabs,
          subTableBindings: prevBindings
        })
      }

      previousForms.value = collectedPrevForms
      ctx.hydrateCurrentSubTablesFromPreviousForms()
    } else {
      previousForms.value = []
    }
  }

  return {
    collectPreviousFormsFromContent,
  }
}
