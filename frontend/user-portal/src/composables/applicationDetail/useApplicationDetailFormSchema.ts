import type { FormField } from '@/components/FormRenderer.vue'
import {
  normalizePortalViews,
  isFormCreateRuleReadonly,
  isFormCreateRuleHidden,
  isRowRule,
  isColRule,
  getRuleChildren,
  getRowGutter,
  getColSpan,
  extractRowColumnFields,
  parseFormRulesLayout,
  isTabsRule,
  isCardRule,
  isCollapseRule,
  extractCollapsePanelsFromRule,
  extractTabsFromTabsRule,
  getLayoutKey,
  getLayoutLabel,
  convertAuxiliaryLayoutField,
} from '@/components/formRendererHelpers'
import { applyRuleDefaultToFormField } from '@/utils/formCreateRuleDefaults'
import { applyFormCreateValidationToFormField } from '@/utils/formCreateValidateRules'
import { resolveSubTableSchemaByTableId } from '@/components/subTableAddDialogHelpers'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailFormSchemaFns {
  parseFormConfig: (configStr: string) => void
  extractFieldsRecursive: (items: any[], ctx?: { skipSubTable?: boolean }) => FormField[]
  resolveSubFormDesign: (binding: any, subForms?: Record<string, any>) => { formFields: FormField[]; formOptions?: Record<string, any> }
  convertFormCreateRule: (rule: any) => FormField | null
}

export function createApplicationDetailFormSchema(appCtx: ApplicationDetailCtx): ApplicationDetailFormSchemaFns {
  const { lookupDbConfigs, relationViewConfigs, formFields, formTabs, formFieldsAfterTabs, formFormOptions } = appCtx

  // Parse form configuration
  const parseFormConfig = (configStr: string) => {
    if (!configStr) return
    try {
      const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
      const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
      if (rules) {
        // Extract labelWidth config (ignore backend config, use fixed value to prevent label truncation)
        // if (config.options?.form?.labelWidth) {
        //   formLabelWidth.value = config.options.form.labelWidth
        // }

        const layout = parseFormRulesLayout(rules, (items) => extractFieldsRecursive(items))
        formTabs.value = layout.tabs
        formFields.value = layout.fields
        formFieldsAfterTabs.value = layout.fieldsAfterTabs
        formFormOptions.value = (config.options && typeof config.options === 'object') ? config.options : {}
      }
    } catch (error) {
      console.error('Failed to parse form config:', error)
    }
  }

  // form-create runtime-only nodes: do not emit as fields, but children must be traversed
  // (sub-table row layouts use subForm/tableForm wrappers).
  const FC_SKIP_TYPES = new Set(['subForm', 'tableForm', 'tableFormColumn'])

  // Recursively extract fields.
  // `skipSubTable`: when traversing subForm/tableForm wrappers on the main canvas, do not promote
  // nested subTable widgets (e.g. link-form target subtable2) to the page-level field list.
  const extractFieldsRecursive = (
    items: any[],
    ctx: { skipSubTable?: boolean } = {},
  ): FormField[] => {
    const fields: FormField[] = []
    for (const item of items) {
      if (item.field && isFormCreateRuleHidden(item)) {
        continue
      }
      const bindingId = item._bindingId ?? item.props?._bindingId
      if (item.type === 'subTable' && bindingId != null) {
        if (isFormCreateRuleHidden(item)) {
          continue
        }
        if (!ctx.skipSubTable) {
          const rawPv = item.props?.portalViews
          const hasWidgetPortalViews =
            rawPv != null && typeof rawPv === 'object' && Object.keys(rawPv).length > 0
          fields.push({
            key: `__subTable_${bindingId}`,
            label: '',
            type: 'subTable',
            _bindingId: Number(bindingId),
            ...(hasWidgetPortalViews ? { portalViews: normalizePortalViews(rawPv) } : {}),
            // 子表逐操作权限：仅显式 false 才下发（undefined 由 SubTableField 回退 editable）
            ...(item.props?.allowAdd === false ? { allowAdd: false } : {}),
            ...(item.props?.allowEdit === false ? { allowEdit: false } : {}),
            ...(item.props?.allowDelete === false ? { allowDelete: false } : {}),
            span: 24,
          })
        }
        continue
      }
      const auxField = convertAuxiliaryLayoutField(item, fields.length)
      if (auxField) {
        fields.push(auxField)
        continue
      }
      if (isRowRule(item)) {
        fields.push({
          key: getLayoutKey(item, fields.length, 'row'),
          label: '',
          type: 'row',
          span: 24,
          gutter: getRowGutter(item),
          children: extractRowColumnFields(item, (children) => extractFieldsRecursive(children, ctx)),
        } as any)
        continue
      } else if (isColRule(item)) {
        fields.push({
          key: getLayoutKey(item, fields.length, 'col'),
          label: '',
          type: 'col',
          span: getColSpan(item),
          children: extractFieldsRecursive(getRuleChildren(item), ctx),
        } as any)
        continue
      }
      if (isTabsRule(item)) {
        const nestedTabs = extractTabsFromTabsRule(item, (children) => extractFieldsRecursive(children, ctx))
        if (nestedTabs.length > 0) {
          fields.push({
            key: getLayoutKey(item, fields.length, 'tabs'),
            label: '',
            type: 'tabs',
            span: 24,
            tabs: nestedTabs,
          } as any)
        }
        continue
      }
      if (isCollapseRule(item)) {
        const collapsePanels = extractCollapsePanelsFromRule(item, (children) => extractFieldsRecursive(children, ctx))
        if (collapsePanels.length > 0) {
          fields.push({
            key: getLayoutKey(item, fields.length, 'collapse'),
            label: '',
            type: 'collapse',
            span: 24,
            collapsePanels,
          } as any)
        }
        continue
      }
      if (isCardRule(item)) {
        fields.push({
          key: getLayoutKey(item, fields.length, 'card'),
          label: getLayoutLabel(item),
          type: 'card',
          span: item.col?.span || 24,
          children: extractFieldsRecursive(getRuleChildren(item), ctx)
        } as any)
        continue
      } else if (item.type === 'recordNote') {
        const rnProps = item.props || {}
        const rnScope = rnProps.scope === 'TABLE' ? 'TABLE' : 'RECORD'
        fields.push({
          key: `__recordNote_${rnScope.toLowerCase()}`,
          label: '',
          type: 'recordNote',
          span: 24,
          _recordNote: {
            scope: rnScope,
            panelTitle: typeof rnProps.panelTitle === 'string' ? rnProps.panelTitle : undefined,
            allowAttachment: rnProps.allowAttachment !== false,
            maxFileSizeMb: Number(rnProps.maxFileSizeMb) || 10,
            allowEditOwn: rnProps.allowEditOwn !== false,
            pageSize: Number(rnProps.pageSize) || 5,
          },
        } as any)
      } else if (item.type === 'lookup' && item.field) {
        let lookupCfg: any = {}
        try {
          const raw = item.props?.lookupConfig
          lookupCfg = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
        } catch { lookupCfg = {} }
        const dbCfg = lookupDbConfigs.value[item.field]
        // Resolve view fields: prefer configJson.relationViews (designed in developer-workstation),
        // then fall back to rt_view_fields (from getLookupConfigs)
        let resolvedViewFields: any[] = []
        if (lookupCfg.bindingId && relationViewConfigs.value[lookupCfg.bindingId]) {
          resolvedViewFields = relationViewConfigs.value[lookupCfg.bindingId].viewFields || []
        }
        if (!resolvedViewFields.length) {
          resolvedViewFields = dbCfg?.viewFields || []
        }
        const field: any = {
          key: item.field,
          label: item.title || item.field,
          type: 'lookup',
          placeholder: item.props?.placeholder || 'Click to search',
          span: item.col?.span || 24,
          _lookupTableId: lookupCfg.tableId || dbCfg?.tableId || 0,
          _lookupSearchFields: (lookupCfg.searchFields?.length ? lookupCfg.searchFields : null) || dbCfg?.searchFields || [],
          _lookupDisplayField: (lookupCfg.displayFields?.[0]) || dbCfg?.displayField || '',
          _lookupDisplayFields: lookupCfg.displayFields || [],
          _lookupSelectedDisplayField: lookupCfg.selectedDisplayField || lookupCfg.displayField || '',
          _lookupFilterConditions: Array.isArray(lookupCfg.filterConditions) ? lookupCfg.filterConditions : [],
          _lookupDerivedFrom: lookupCfg.derivedFrom,
          _lookupMultiple: lookupCfg.multiple === true,
          _lookupConfig: typeof item.props?.lookupConfig === 'string'
            ? item.props.lookupConfig
            : JSON.stringify(lookupCfg || {}),
          _lookupViewFields: lookupCfg.showBackfillView === false ? [] : resolvedViewFields,
          _lookupShowBackfillView: lookupCfg.showBackfillView !== false
        }
        if (isFormCreateRuleReadonly(item)) {
          field.readonly = true
        }
        fields.push(field)
      } else if (FC_SKIP_TYPES.has(item.type)) {
        // Traverse children only; `continue` would drop nested sub-table row fields.
      } else if (item.field) {
        const field = convertFormCreateRule(item)
        if (field) fields.push(field)
      }
      const childItems = getRuleChildren(item)
      if (childItems.length > 0) {
        const childCtx = FC_SKIP_TYPES.has(item.type) ? { skipSubTable: true } : ctx
        if (FC_SKIP_TYPES.has(item.type) || (!isCardRule(item) && !isRowRule(item) && !isColRule(item) && !isTabsRule(item) && !isCollapseRule(item))) {
          fields.push(...extractFieldsRecursive(childItems, childCtx))
        }
      }
    }
    return fields
  }

  /** Link Form / sub-table row dialog: same contract as tasks/detail.vue — fields from designer subForm. */
  function resolveSubFormDesign(binding: any, subForms?: Record<string, any>): { formFields: FormField[]; formOptions?: Record<string, any> } {
    const design =
      binding.subFormConfig ||
      subForms?.[binding.bindingId] ||
      subForms?.[String(binding.bindingId)] ||
      {}
    let rule = Array.isArray(design.rule) ? design.rule : []
    let options = design.options
    if (rule.length === 0 && binding.tableId != null && Number.isFinite(Number(binding.tableId))) {
      const alt = resolveSubTableSchemaByTableId(Number(binding.tableId), appCtx.cachedContentForms, binding.bindingId)
      if (alt) {
        const altDesign = alt.subForms[alt.bindingId] ?? alt.subForms[String(alt.bindingId)] ?? {}
        if (Array.isArray(altDesign.rule) && altDesign.rule.length > 0) {
          rule = altDesign.rule
          options = altDesign.options ?? options
        }
      }
    }
    return {
      formFields: rule.length > 0 ? extractFieldsRecursive(rule) : [],
      formOptions: options
    }
  }

  // Convert form rules
  const convertFormCreateRule = (rule: any): FormField | null => {
    if (!rule || !rule.field) return null
    let dateType = 'date'
    if (rule.props?.type === 'datetime') dateType = 'datetime'
    else if (rule.props?.type === 'daterange') dateType = 'daterange'
    const typeMap: Record<string, string> = { 'input': 'text', 'inputNumber': 'number', 'select': 'select', 'radio': 'radio', 'checkbox': 'checkbox', 'switch': 'switch', 'datePicker': dateType, 'DatePicker': dateType, 'date-picker': dateType, 'el-date-picker': dateType, 'timePicker': 'time', 'cascader': 'cascader', 'rate': 'rate', 'slider': 'slider', 'colorPicker': 'colorPicker', 'treeSelect': 'treeselect', 'upload': 'upload', 'editor': 'editor', 'signature': 'signature', 'transfer': 'transfer' }
    const field: FormField = { key: rule.field, label: rule.title || rule.field, type: typeMap[rule.type] || 'text', placeholder: rule.props?.placeholder || '', span: rule.col?.span || 24 }
    const rawOptions = rule.options || rule.props?.options
    if (rawOptions) {
      if (rule.type === 'cascader') {
        field.options = rawOptions
      } else {
        field.options = rawOptions.map((opt: any) => ({ label: opt.label || opt.value, value: opt.value }))
      }
    }
    if (rule.type === 'cascader') { field.cascaderProps = rule.props?.props || rule.props?.cascaderProps }
    if (rule.type === 'input' && rule.props?.type === 'textarea') { field.type = 'textarea'; field.rows = rule.props?.rows || 3 }
    if (rule.type === 'input' && rule.props?.type === 'password') { field.type = 'password' }
    if (rule.type === 'timePicker' && rule.props?.isRange === true) { field.type = 'timerange' }
    if (rule.type === 'rate') { field.max = rule.props?.max || 5 }
    if (rule.type === 'slider') { field.min = rule.props?.min ?? 0; field.max = rule.props?.max ?? 100; field.step = rule.props?.step || 1 }
    if (rule.type === 'upload') {
      const action = rule.props?.action
      field.uploadUrl = (action && action !== '/') ? action : '/api/v1/upload'
      field.uploadAccept = rule.props?.accept || ''
      field.uploadLimit = rule.props?.limit || 1
    }
    if (isFormCreateRuleReadonly(rule)) {
      field.readonly = true
    }
    applyRuleDefaultToFormField(field, rule as Record<string, unknown>)
    applyFormCreateValidationToFormField(field, rule as Record<string, unknown>)
    return field
  }

  return {
    parseFormConfig,
    extractFieldsRecursive,
    resolveSubFormDesign,
    convertFormCreateRule,
  }
}
