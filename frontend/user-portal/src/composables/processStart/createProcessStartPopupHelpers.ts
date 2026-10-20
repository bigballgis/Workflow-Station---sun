import type { Ref } from 'vue'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import {
  findTabsRule,
  isTabPaneRule,
} from '@/components/formRendererHelpers'
import { resolveSubTablePrimaryKeyFields } from '@/composables/tasks/shared'
import type { PreparedFormPopupContext } from '@/composables/tasks/customActionTypes'
import type { TaskActionInfo } from '@/api/task'

/**
 * FORM_POPUP helpers for New Request — mirrors task-detail popup parity using start-page caches.
 */
export function createProcessStartPopupHelpers(deps: {
  cachedContentForms: Ref<unknown[]>
  extractFieldsRecursive: (rules: unknown[]) => FormField[]
  deriveColumnsFromBinding: (b: unknown, subForms: Record<string, unknown>, cfg: Record<string, unknown>) => unknown[]
}) {
  const { cachedContentForms, extractFieldsRecursive, deriveColumnsFromBinding } = deps

  const resolveFormPopupContent = (_action: TaskActionInfo, config: { formId?: unknown; formName?: string }) => {
    if (!config?.formId) return null
    const forms = cachedContentForms.value
    if (!Array.isArray(forms) || forms.length === 0) return null
    return (
      forms.find((f: { sourceId?: unknown }) => String(f.sourceId) === String(config.formId))
      || (config.formName
        ? forms.find((f: { name?: string }) => f.name === config.formName)
        : null)
      || null
    )
  }

  const preparePopupContext = (
    formContent: Record<string, unknown>,
    formConfig: Record<string, unknown>,
  ): PreparedFormPopupContext | null => {
    const cfg = formConfig as Record<string, unknown>
    let rules: unknown[] | null = null
    if (formContent?.formType === 'ACTION') {
      const actionBinding = ((formContent.tableBindings as unknown[]) || []).find(
        (b: { bindingType?: string }) => b.bindingType === 'ACTION',
      ) as { bindingId?: number } | undefined
      const actionRule = actionBinding
        ? (cfg.subForms as Record<string, { rule?: unknown[] }> | undefined)?.[String(actionBinding.bindingId)]?.rule
        : null
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
            ? extractFieldsRecursive(tabPane.children)
            : []
        popupTabs.push({
          name: (tabPane.props as { name?: string }).name || `tab_${popupTabs.length}`,
          label: (tabPane.props as { label?: string }).label || `Tab ${popupTabs.length + 1}`,
          fields: tabFields,
        })
      }
    } else {
      popupFields.push(...extractFieldsRecursive(rules))
    }

    const subForms = (cfg.subForms || {}) as Record<string, unknown>
    const tableBindings: unknown[] = (formContent.tableBindings as unknown[]) || []
    const popupBindings: Record<string, unknown>[] = []
    const nativeIds: number[] = []
    for (const b of tableBindings) {
      const binding = b as Record<string, unknown>
      if (binding.bindingType === 'PRIMARY') continue
      const cols = deriveColumnsFromBinding(b, subForms, cfg)
      const subFormDesign = { formFields: [] as FormField[], formOptions: undefined as Record<string, unknown> | undefined }
      popupBindings.push({
        bindingId: binding.bindingId,
        tableId: binding.tableId ?? null,
        bindingType: binding.bindingType,
        bindingMode: binding.bindingMode,
        foreignKeyField: binding.foreignKeyField,
        tableName: binding.tableDisplayName || binding.tableName,
        designerTableName: binding.tableName,
        relationTableId: binding.relationTableId ?? null,
        relationTableName: binding.relationTableName ?? null,
        tableType: binding.tableType,
        tableDescription: binding.tableDescription,
        columns: cols,
        formFields: subFormDesign.formFields,
        formOptions: subFormDesign.formOptions,
        assignmentConfig: binding.assignmentConfig,
        primaryKeyFields: resolveSubTablePrimaryKeyFields(
          binding.primaryKeyFields as string[] | undefined,
          binding.bindingId as number,
          cfg,
        ),
        data: [],
      })
      const bid = Number(binding.bindingId)
      if (Number.isFinite(bid)) nativeIds.push(bid)
    }

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

  return { resolveFormPopupContent, preparePopupContext }
}
