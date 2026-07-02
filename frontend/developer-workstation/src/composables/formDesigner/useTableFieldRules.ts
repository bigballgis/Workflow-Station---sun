import type { ComputedRef, Ref } from 'vue'
import type { FieldDefinition, FormDefinition, TableBinding } from '@/api/functionUnit'
import { cloneFormRules, injectUploadButtonLabels, mergeLoadedFormOptions } from '@/utils/formDesigner'
import { resolveBindingKeyedEntry } from '@/utils/formConfigBindingResolve'
import {
  applyTableFieldDefaultToRule,
  applyTableFieldDefaultsToRulesAndModel,
  type TableFieldDefLike,
} from '@/utils/formCreateRuleDefaults'
import {
  applyTaskFieldPermissionsFromTableFields,
  applyTableFieldMetaToFormRule,
  shouldIncludeFieldOnFormCanvas,
  syncFormRulesWithTableFields,
  buildRequestIdFormRule,
  isRequestIdSyntheticField,
} from '@/utils/formFieldMeta'
import type { RequestIdConfig } from '@/api/functionUnit'

type DesignerLike = { getRule?: () => unknown[]; setRule?: (r: unknown[]) => void } | null | undefined

interface UseTableFieldRulesOptions {
  store: { tables: any[] }
  selectedForm: Ref<FormDefinition | null>
  designerRef: Ref<any>
  subDesignerRefs: Ref<any[]>
  designerSubBindings: ComputedRef<Array<{ bindingId: number; tableId: number }>>
  activeDesignerTab: Ref<string>
  getActiveDesignerRef: () => DesignerLike
  defaultFormOption: ComputedRef<Record<string, any>>
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Table-field → form-create rule mapping and Table Design default hydration
 * for FormDesigner: builds canvas rules from bound table fields and keeps
 * designer rules in sync with the latest table metadata.
 */
export function useTableFieldRules(options: UseTableFieldRulesOptions) {
  const {
    store, selectedForm, designerRef, subDesignerRefs, designerSubBindings,
    activeDesignerTab, getActiveDesignerRef, defaultFormOption, t,
  } = options

  /** PRIMARY-bound table field defs (Table Design defaults). */
  function getPrimaryBindingFieldDefinitions(): FieldDefinition[] {
    if (!selectedForm.value) return []
    const bindings = selectedForm.value.tableBindings ?? []
    const primary = bindings.find(
      (b: TableBinding) => String(b.bindingType ?? '').toUpperCase() === 'PRIMARY',
    )
    let tableId = primary?.tableId ?? selectedForm.value.boundTableId
    if (!tableId && bindings.length === 1) {
      tableId = bindings[0].tableId
    }
    return getTableFieldDefinitionsByTableId(tableId)
  }

  function getTableFieldDefinitionsByTableId(tableId?: number | null): FieldDefinition[] {
    if (!tableId) return []
    const table = store.tables.find(t => t.id === tableId)
    return table?.fieldDefinitions ?? []
  }

  /** Canvas load: Table Design default overrides stale rule.value from last form save. */
  function hydrateDesignerRulesFromLatestTableDefaults(
    rules: unknown[],
    fieldDefs: TableFieldDefLike[],
  ): void {
    if (!Array.isArray(rules) || rules.length === 0 || fieldDefs.length === 0) return
    applyTableFieldDefaultsToRulesAndModel(rules, fieldDefs, {}, false, { tableOverridesRule: true })
  }

  function refreshActiveDesignerRulesFromTableDefaults(): void {
    const designer = getActiveDesignerRef()
    if (!designer?.getRule || !designer.setRule) return
    let rules: unknown[] = []
    try {
      rules = designer.getRule() || []
    } catch {
      return
    }
    if (!rules.length) return
    rules = cloneFormRules(rules) as unknown[]
    let fieldDefs: TableFieldDefLike[] = getPrimaryBindingFieldDefinitions()
    if (activeDesignerTab.value !== 'main') {
      const bindingId = Number(activeDesignerTab.value)
      const binding = designerSubBindings.value.find(b => b.bindingId === bindingId)
      fieldDefs = getTableFieldDefinitionsByTableId(binding?.tableId)
    }
    hydrateDesignerRulesFromLatestTableDefaults(rules, fieldDefs)
    try {
      injectUploadButtonLabels(rules as ReturnType<typeof cloneFormRules>, t('form.clickToUpload'))
      designer.setRule(rules as ReturnType<typeof cloneFormRules>)
    } catch {
      // ignore designer sync errors
    }
  }

  /**
   * Convert database field type to form-create rule
   */
  function getTableFieldDefinitions(tableId: number): FieldDefinition[] {
    const table = store.tables.find(t => t.id === tableId)
    return table?.fieldDefinitions ? [...table.fieldDefinitions] : []
  }

  function mergeTaskPermissionsForFields(fields: FieldDefinition[]) {
    if (!selectedForm.value || selectedForm.value.formType !== 'TASK' || !fields.length) return
    selectedForm.value.fieldPermissions = applyTaskFieldPermissionsFromTableFields(
      selectedForm.value.fieldPermissions,
      fields,
    )
  }

  function refreshFormRulesFromTableMetadata() {
    if (!selectedForm.value) return
    const bindings = selectedForm.value.tableBindings || []

    const primary = bindings.find(b => b.bindingType === 'PRIMARY')
    if (primary && designerRef.value) {
      const fields = getTableFieldDefinitions(primary.tableId)
      if (fields.length) {
        try {
          const currentRules = designerRef.value.getRule() || []
          const synced = syncFormRulesWithTableFields(currentRules, fields)
          injectUploadButtonLabels(synced as any[], t('form.clickToUpload'))
          designerRef.value.setRule(synced)
          mergeTaskPermissionsForFields(fields)
        } catch {
          // designer not ready
        }
      }
    }

    designerSubBindings.value.forEach((binding, index) => {
      const subRef = subDesignerRefs.value[index]
      if (!subRef) return
      const fields = getTableFieldDefinitions(binding.tableId)
      if (!fields.length) return
      try {
        const currentRules = subRef.getRule() || []
        const synced = syncFormRulesWithTableFields(currentRules, fields)
        injectUploadButtonLabels(synced as any[], t('form.clickToUpload'))
        subRef.setRule(synced)
        mergeTaskPermissionsForFields(fields)
      } catch {
        // sub designer not ready
      }
    })
  }

  /**
   * 主表字段 → form-create rule。Request ID 作为可勾选虚拟字段混在 {@code fields} 里时,
   * 由 {@link fieldToFormRule} 转成派生只读 rule。
   */
  function mapFieldsToFormRules(fields: FieldDefinition[]): any[] {
    return fields
      .filter(shouldIncludeFieldOnFormCanvas)
      .map(fieldToFormRule)
      .filter((rule): rule is Record<string, unknown> => rule != null)
  }

  /** 取 tableId 对应表的 Request ID 配置(仅 MAIN 表会有)。 */
  function getRequestIdConfigByTableId(tableId?: number | null): RequestIdConfig | null {
    if (!tableId) return null
    const table = store.tables.find(t => t.id === tableId)
    return table?.requestIdConfig ?? null
  }

  function fieldToFormRule(field: FieldDefinition): Record<string, unknown> | null {
    if (!shouldIncludeFieldOnFormCanvas(field)) {
      return null
    }

    // Request ID 虚拟字段 → 派生只读 rule(值由运行时后端填充)
    if (isRequestIdSyntheticField(field)) {
      return buildRequestIdFormRule(field.displayName || t('form.requestId'))
    }

    const baseRule = {
      field: field.fieldName,
      title: field.displayName || field.fieldName,
      props: {},
      validate: [] as any[]
    }

    // Add required validation if field is not nullable
    if (!field.nullable) {
      baseRule.validate.push({
        required: true,
        message: `${field.displayName || field.fieldName} ${t('form.required').toLowerCase()}`,
        trigger: 'blur'
      })
    }

    // Map data type to form component
    let rule: Record<string, unknown>
    switch (field.dataType) {
      case 'VARCHAR':
        rule = {
          ...baseRule,
          type: 'input',
          props: {
            placeholder: `${t('common.inputPlaceholder')} ${field.displayName || field.fieldName}`,
            maxlength: field.length || 255,
            showWordLimit: true
          }
        }
        break
      case 'TEXT':
        rule = {
          ...baseRule,
          type: 'input',
          props: {
            type: 'textarea',
            placeholder: `${t('common.inputPlaceholder')} ${field.displayName || field.fieldName}`,
            rows: 3
          }
        }
        break
      case 'INTEGER':
      case 'BIGINT':
        rule = {
          ...baseRule,
          type: 'inputNumber',
          props: {
            placeholder: `${t('common.inputPlaceholder')} ${field.displayName || field.fieldName}`,
            precision: 0
          }
        }
        break
      case 'DECIMAL':
        rule = {
          ...baseRule,
          type: 'inputNumber',
          props: {
            placeholder: `${t('common.inputPlaceholder')} ${field.displayName || field.fieldName}`,
            precision: field.scale || 2
          }
        }
        break
      case 'BOOLEAN':
        rule = {
          ...baseRule,
          type: 'switch',
          props: {}
        }
        break
      case 'DATE':
        rule = {
          ...baseRule,
          type: 'datePicker',
          props: {
            type: 'date',
            placeholder: `${t('common.inputPlaceholder')} ${field.displayName || field.fieldName}`,
            valueFormat: 'YYYY-MM-DD'
          }
        }
        break
      case 'TIMESTAMP':
        rule = {
          ...baseRule,
          type: 'datePicker',
          props: {
            type: 'datetime',
            placeholder: `${t('common.inputPlaceholder')} ${field.displayName || field.fieldName}`,
            valueFormat: 'YYYY-MM-DD HH:mm:ss'
          }
        }
        break
      case 'FILE':
        rule = {
          ...baseRule,
          type: 'upload',
          props: {
            action: '/api/v1/upload',
            accept: '.jpg,.jpeg,.png,.pdf,.docx,.xlsx',
            limit: 1,
            multiple: false,
            listType: 'text',
            uploadText: t('form.clickToUpload'),
            tip: t('form.fileUploadTip')
          }
        }
        break
      default:
        rule = {
          ...baseRule,
          type: 'input',
          props: {
            placeholder: `${t('common.inputPlaceholder')} ${field.displayName || field.fieldName}`
          }
        }
    }
    applyTableFieldDefaultToRule(rule, field)
    return applyTableFieldMetaToFormRule(field, rule)
  }

  /**
   * AI Apply often persists empty {@code rule} or {@code {}} configJson while table bindings exist.
   * Merge defaults and, when rules are empty, build rules from the PRIMARY-bound table fields (same as manual import).
   */
  function buildEffectiveMainFormConfig(
    row: FormDefinition,
    bindings: { bindingType: string; tableId: number }[]
  ): Record<string, any> {
    const raw = (row.configJson || {}) as Record<string, any>
    const rawRule = Array.isArray(raw.rule) ? raw.rule : []
    const base: Record<string, any> = {
      rule: rawRule,
      options: mergeLoadedFormOptions(
        raw.options && Object.keys(raw.options).length ? raw.options : undefined,
        defaultFormOption.value,
        t('form.clickToUpload')
      ),
      subForms: raw.subForms && typeof raw.subForms === 'object' ? raw.subForms : {},
      subListViews: raw.subListViews && typeof raw.subListViews === 'object' ? raw.subListViews : {},
      relationViews: raw.relationViews && typeof raw.relationViews === 'object' ? raw.relationViews : {},
      subTablePortalViews:
        raw.subTablePortalViews && typeof raw.subTablePortalViews === 'object' ? raw.subTablePortalViews : {}
    }
    if (rawRule.length > 0) {
      return base
    }
    const primary = bindings.find((b) => b.bindingType === 'PRIMARY')
    const table = primary ? store.tables.find((t) => t.id === primary.tableId) : undefined
    const fields = table?.fieldDefinitions?.length
      ? [...table.fieldDefinitions].sort((a: any, b: any) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      : []
    if (fields.length === 0) {
      return base
    }
    base.rule = mapFieldsToFormRules(fields)
    return base
  }

  function buildEffectiveSubFormConfig(
    subForms: Record<string, unknown> | undefined,
    bindingId: number,
    bindings: TableBinding[],
    tableId: number,
  ): { rule: unknown[]; options: Record<string, unknown> } {
    const resolved = resolveBindingKeyedEntry(subForms, bindingId, bindings, 'SUB')
      ?? subForms?.[bindingId]
      ?? subForms?.[String(bindingId)]
    const resolvedMap = (resolved && typeof resolved === 'object'
      ? resolved
      : {}) as Record<string, unknown>
    const rawRule = Array.isArray(resolvedMap.rule) ? resolvedMap.rule : []
    const options = (resolvedMap.options && typeof resolvedMap.options === 'object'
      ? resolvedMap.options
      : {}) as Record<string, unknown>
    if (rawRule.length > 0) {
      return { rule: rawRule, options }
    }
    const fields = getTableFieldDefinitionsByTableId(tableId)
    if (!fields.length) {
      return { rule: [], options }
    }
    return { rule: mapFieldsToFormRules(fields), options }
  }

  return {
    getPrimaryBindingFieldDefinitions,
    getTableFieldDefinitionsByTableId,
    hydrateDesignerRulesFromLatestTableDefaults,
    refreshActiveDesignerRulesFromTableDefaults,
    getTableFieldDefinitions,
    mergeTaskPermissionsForFields,
    refreshFormRulesFromTableMetadata,
    mapFieldsToFormRules,
    getRequestIdConfigByTableId,
    fieldToFormRule,
    buildEffectiveMainFormConfig,
    buildEffectiveSubFormConfig,
  }
}
