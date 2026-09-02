import type { TaskActionInfo } from '@/api/task'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import {
  findTabsRule,
  isTabPaneRule,
} from '@/components/formRendererHelpers'
import {
  resolveSubTablePrimaryKeyFields,
} from '@/composables/tasks/shared'
import type { PreparedFormPopupContext } from '@/composables/tasks/useCustomActions'
import type { TaskDetailCtx } from './context'

/**
 * FORM_POPUP helper callbacks for useCustomActions — extracted from the inline
 * option closures so the SFC wiring stays thin. Behavior unchanged.
 */
export function createTaskDetailPopupHelpers(ctx: TaskDetailCtx) {
  const { subTableBindings } = ctx

  /**
   * FORM_POPUP target form resolution — prefer cachedContentForms (full
   * tableBindings included) so the popup renders with full sub-table parity
   * without an extra round-trip. Closure captures cachedContentForms by reference;
   * it's populated by loadFunctionUnitContent well before any action button can fire.
   */
  const resolveFormPopupContent = (_action: TaskActionInfo, config: any): any | null => {
    if (!config?.formId) return null
    const forms = ctx.cachedContentForms
    if (!Array.isArray(forms) || forms.length === 0) return null
    return (
      forms.find((f: any) => String(f.sourceId) === String(config.formId))
        || (config.formName ? forms.find((f: any) => f.name === config.formName) : null)
        || null
    )
  }

  /**
   * FORM_POPUP rendering context — reuse the main-form helpers so popup canvas
   * (subTable / lookup / card / Link Form columns) renders at parity with the
   * Designer Form Preview. Replaces the legacy convertFormCreateRuleSimple path
   * that silently dropped any non-primitive widget (#1394).
   */
  const preparePopupContext = (formContent: any, formConfig: Record<string, unknown>): PreparedFormPopupContext | null => {
    const cfg = formConfig as Record<string, any>

    // ACTION-type forms (e.g. FORM_POPUP "Meeting Remark") design their real fields on the
    // ACTION binding's own canvas (configJson.subForms[bindingId].rule), the same place a SUB
    // binding's canvas lives — the top-level cfg.rule stays bound to the form's PRIMARY table
    // and is not what the designer actually authored for this popup. Prefer the ACTION binding's
    // rule when present; fall back to cfg.rule for legacy ACTION forms saved before this existed.
    // DW Preview counterpart: frontend/developer-workstation/src/utils/actionFormCanvasRule.ts
    let rules: any[] | null = null
    if (formContent?.formType === 'ACTION') {
      const actionBinding = ((formContent as any).tableBindings || []).find(
        (b: any) => b.bindingType === 'ACTION',
      )
      const actionRule = actionBinding ? cfg.subForms?.[actionBinding.bindingId]?.rule : null
      if (Array.isArray(actionRule) && actionRule.length > 0) {
        rules = actionRule
      }
    }
    if (!rules) {
      rules = cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : null)
    }
    if (!rules) return null

    const popupTabs: FormTab[] = []
    const popupFields: FormField[] = []
    const tabsRule = findTabsRule(rules)
    if (tabsRule?.children && Array.isArray(tabsRule.children)) {
      for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
        if (!isTabPaneRule(tabPane) || !tabPane.props) continue
        const tabFields: FormField[] =
          tabPane.children && Array.isArray(tabPane.children)
            ? ctx.extractFieldsRecursive(tabPane.children)
            : []
        popupTabs.push({
          name: tabPane.props.name || `tab_${popupTabs.length}`,
          label: tabPane.props.label || `Tab ${popupTabs.length + 1}`,
          fields: tabFields,
        })
      }
    } else {
      popupFields.push(...ctx.extractFieldsRecursive(rules))
    }

    const subForms = cfg.subForms || {}
    const tableBindings: any[] = (formContent as any).tableBindings || []
    const popupBindings: typeof subTableBindings.value = []
    const nativeIds: number[] = []
    for (const b of tableBindings) {
      if (b.bindingType === 'PRIMARY') continue
      const cols = ctx.deriveColumnsFromBinding(b, subForms, cfg)
      const subFormDesign = ctx.resolveSubFormDesign(b, subForms)
      popupBindings.push({
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
        columns: cols,
        formFields: subFormDesign.formFields,
        formOptions: subFormDesign.formOptions,
        assignmentConfig: b.assignmentConfig,
        primaryKeyFields: resolveSubTablePrimaryKeyFields(b.primaryKeyFields, b.bindingId, cfg),
        data: [],
      })
      const bid = Number(b.bindingId)
      if (Number.isFinite(bid)) nativeIds.push(bid)
    }
    // Link Form targets in the popup form may reference bindings declared in
    // other FU forms (e.g. subtable2). Merge those so SubTableField's Link Form
    // modal can resolve them at parity with the main form.
    ctx.mergeLinkFormTargetBindingsInto(popupBindings, ctx.cachedContentForms, cfg, subForms)

    return {
      fields: popupFields,
      tabs: popupTabs,
      subTableBindings: popupBindings,
      linkedSubTableBindings: popupBindings,
      nativeSubTableBindingIds: nativeIds,
      formConfig: cfg,
      viewContext: 'assigneeTodo' as const,
    }
  }

  return {
    resolveFormPopupContent,
    preparePopupContext,
  }
}
