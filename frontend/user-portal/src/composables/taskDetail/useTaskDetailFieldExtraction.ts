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
  convertAuxiliaryLayoutField,
  extractTabsFromTabsRule,
  extractCollapsePanelsFromRule,
  getLayoutKey,
  getLayoutLabel,
} from '@/components/formRendererHelpers'
import { applyRuleDefaultToFormField } from '@/utils/formCreateRuleDefaults'
import type { TaskDetailCtx } from './context'

export interface TaskDetailFieldExtractionFns {
  extractFieldsRecursive: (items: any[]) => FormField[]
  convertFormCreateRule: (rule: any) => FormField | null
  parseFormConfig: (configStr: string) => void
}

export function createTaskDetailFieldExtraction(ctx: TaskDetailCtx): TaskDetailFieldExtractionFns {
  const { lookupDbConfigs, relationViewConfigs } = ctx
  const { formFields, formTabs, formFieldsAfterTabs, formFormOptions, formReadOnly } = ctx.taskForm

  // ===== BPMN parsing functions moved to useBpmnParser composable =====
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
      // Check if form is read-only (never clear node/BPMN/task-form readonly already applied)
      formReadOnly.value =
        formReadOnly.value === true
        || config.formReadOnly === true
        || config.formReadOnly === 'true'
    } catch (error) {
      console.error('Failed to parse form config:', error)
    }
  }

  // form-create runtime-only nodes: do not emit as fields, but **must** fall through to the
  // `item.children` recursion below — sub-table row layouts are wrapped in subForm/tableForm.
  const FC_SKIP_TYPES = new Set(['subForm', 'tableForm', 'tableFormColumn'])

  // Recursively extract fields
  const extractFieldsRecursive = (items: any[]): FormField[] => {
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
          span: 24
        })
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
          children: extractRowColumnFields(item, (children) => extractFieldsRecursive(children)),
        } as any)
        continue
      } else if (isColRule(item)) {
        fields.push({
          key: getLayoutKey(item, fields.length, 'col'),
          label: '',
          type: 'col',
          span: getColSpan(item),
          children: extractFieldsRecursive(getRuleChildren(item)),
        } as any)
        continue
      }
      if (isTabsRule(item)) {
        const nestedTabs = extractTabsFromTabsRule(item, (children) => extractFieldsRecursive(children))
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
        const collapsePanels = extractCollapsePanelsFromRule(item, (children) => extractFieldsRecursive(children))
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
          children: extractFieldsRecursive(getRuleChildren(item))
        } as any)
        continue
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
          _lookupViewFields: lookupCfg.showBackfillView === false ? [] : resolvedViewFields,
          _lookupShowBackfillView: lookupCfg.showBackfillView !== false
        }
        if (isFormCreateRuleReadonly(item)) {
          field.readonly = true
        }
        fields.push(field)
      } else if (FC_SKIP_TYPES.has(item.type)) {
        // Traverse children only (see block below); `continue` would drop all nested row fields.
      } else if (item.field) {
        const field = convertFormCreateRule(item)
        if (field) fields.push(field)
      }
      const childItems = getRuleChildren(item)
      if (childItems.length > 0) {
        if (FC_SKIP_TYPES.has(item.type) || (!isCardRule(item) && !isRowRule(item) && !isColRule(item) && !isTabsRule(item) && !isCollapseRule(item))) {
          fields.push(...extractFieldsRecursive(childItems))
        }
      }
    }
    return fields
  }

  // Convert form rules
  const convertFormCreateRule = (rule: any): FormField | null => {
    if (!rule || !rule.field) return null
    let dateType = 'date'
    if (rule.props?.type === 'datetime') dateType = 'datetime'
    else if (rule.props?.type === 'daterange') dateType = 'daterange'
    const typeMap: Record<string, string> = { 'input': 'text', 'inputNumber': 'number', 'select': 'select', 'radio': 'radio', 'checkbox': 'checkbox', 'switch': 'switch', 'datePicker': dateType, 'DatePicker': dateType, 'date-picker': dateType, 'el-date-picker': dateType, 'timePicker': 'time', 'cascader': 'cascader', 'rate': 'rate', 'slider': 'slider', 'colorPicker': 'colorPicker', 'treeSelect': 'treeselect', 'upload': 'upload', 'editor': 'editor', 'signature': 'signature', 'transfer': 'transfer' }
    const field: FormField = { key: rule.field, label: rule.title || rule.field, type: typeMap[rule.type] || 'text', required: rule.validate?.some((v: any) => v.required) || false, placeholder: rule.props?.placeholder || '', span: rule.col?.span || 24 }
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
    if (rule.type === 'userSelect' || rule.type === 'user') {
      field.type = 'user'
    }
    if (isFormCreateRuleReadonly(rule)) {
      field.readonly = true
    }
    applyRuleDefaultToFormField(field, rule as Record<string, unknown>)
    return field
  }

  return {
    extractFieldsRecursive,
    convertFormCreateRule,
    parseFormConfig,
  }
}
