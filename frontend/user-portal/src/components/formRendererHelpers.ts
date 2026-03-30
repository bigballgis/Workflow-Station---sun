/**
 * Shared helpers for FormRenderer — extracted so they can be imported by both
 * the Vue component (which uses <script setup>) and unit/property tests.
 */

export interface FormField {
  key: string
  label: string
  type: string
  required?: boolean
  placeholder?: string
  span?: number
  options?: Array<{ label: string; value: string | number }>
  multiple?: boolean
  filterable?: boolean
  maxLength?: number
  min?: number
  max?: number
  step?: number
  precision?: number
  rows?: number
  activeText?: string
  inactiveText?: string
  cascaderProps?: object
  currency?: string
  alertTitle?: string
  alertType?: 'success' | 'warning' | 'info' | 'error'
  userOptions?: Array<{ id: string; name: string }>
  buOptions?: Array<{ id: string; name: string; code?: string }>
  rules?: Array<Record<string, unknown>>
  defaultValue?: string | number | boolean | null
  tabName?: string
  uploadUrl?: string
  uploadAccept?: string
  uploadLimit?: number
  _bindingId?: number  // set when type === 'subTable'
}

export interface FormTab {
  name: string
  label: string
  fields: FormField[]
}

/**
 * Recursively extract FormField entries from a form-create rule array.
 * Handles subTable placeholder entries (type === 'subTable') before delegating
 * regular field items to the provided converter.
 *
 * @param items - Array of form-create rule items
 * @param converter - Function that converts a regular rule item to a FormField (or null to skip)
 */
export function extractFieldsRecursive(
  items: Record<string, unknown>[],
  converter: (item: Record<string, unknown>) => FormField | null = () => null
): FormField[] {
  const fields: FormField[] = []
  for (const item of items) {
    if (item.type === 'subTable' && item._bindingId != null) {
      fields.push({
        key: `__subTable_${item._bindingId}`,
        label: '',
        type: 'subTable',
        _bindingId: item._bindingId as number,
        span: 24
      })
    } else if (item.field) {
      const field = converter(item)
      if (field) fields.push(field)
    }
    if (item.children && Array.isArray(item.children)) {
      fields.push(...extractFieldsRecursive(item.children as Record<string, unknown>[], converter))
    }
  }
  return fields
}

/**
 * Parse a JSON form config string and return the tabs array with their fields.
 * Handles subTable placeholder entries inside tab panes.
 *
 * @param configStr - JSON string of the form config (e.g. { rule: [...] })
 * @returns Array of FormTab objects, or empty array if no tabs found
 */
export function parseFormConfigToTabs(configStr: string): FormTab[] {
  try {
    const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    let rules: Record<string, unknown>[] | null = null
    if (config.rule && Array.isArray(config.rule)) {
      rules = config.rule
    } else if (Array.isArray(config)) {
      rules = config
    }
    if (!rules) return []

    const tabsRule = rules.find((r: Record<string, unknown>) => r.type === 'el-tabs')
    if (!tabsRule || !Array.isArray(tabsRule.children)) return []

    const tabs: FormTab[] = []
    for (const tabPane of tabsRule.children) {
      if (tabPane.type === 'el-tab-pane' && tabPane.props) {
        const tabName = tabPane.props.name || `tab_${tabs.length}`
        const tabLabel = tabPane.props.label || `Tab ${tabs.length + 1}`
        const tabFields: FormField[] = []
        if (tabPane.children && Array.isArray(tabPane.children)) {
          tabFields.push(...extractFieldsRecursive(tabPane.children))
        }
        tabs.push({ name: tabName, label: tabLabel, fields: tabFields })
      }
    }
    return tabs
  } catch {
    return []
  }
}

// ---------------------------------------------------------------------------
// Business Logic Config types — configJson 完整结构与子类型
// 所有新增字段均为可选（optional），确保旧版 configJson 向后兼容
// ---------------------------------------------------------------------------

/** configJson 完整结构 */
export interface FormBusinessLogicConfig {
  rule: any[]  // FormCreateRule (form-create rule objects)
  options: Record<string, any>
  subForms: Record<string, SubFormConfig>
  // 业务逻辑扩展（所有字段可选，向后兼容旧版 configJson）
  formulas?: FormulaRule[]
  linkages?: LinkageRule[]
  crossFieldRules?: CrossFieldRule[]
  summaryRules?: SummaryRule[]
  subTableValidation?: Record<string, SubTableValidationConfig>
}

export interface FormulaRule {
  targetField: string
  expression: string       // mathjs 安全表达式
  dependsOn: string[]
}

export interface LinkageRule {
  sourceField: string
  targetField: string
  linkageType: 'option-filtering' | 'value-auto-fill' | 'field-state-change'
  filterConfig?: {
    filterField: string
    filterOperator: 'equals' | 'contains' | 'in'
    filterSource: '$source'
  }
  valueMapping?: Record<string, any>
  stateConfig?: {
    condition: ConditionExpression
    disabled?: boolean
    required?: boolean
  }
}

export interface ConditionExpression {
  field: string
  operator: 'equals' | 'not-equals' | 'contains' | 'greater-than' | 'less-than' | 'is-empty' | 'is-not-empty'
  value?: any
  logic?: 'AND' | 'OR'
  children?: ConditionExpression[]
}

export interface CrossFieldRule {
  fields: string[]
  operator: 'greater-than' | 'less-than' | 'equals' | 'not-equals' | 'date-after' | 'date-before'
  message: string
  targetField: string
}

export interface SummaryRule {
  sourceBindingId: number
  sourceColumn: string
  targetField: string
  aggregation: 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'
}

export interface SubTableValidationConfig {
  minRows?: number
  maxRows?: number
  columnRules?: Record<string, ValidationRule[]>
}

export interface SubFormConfig {
  rule: any[]  // FormCreateRule
  options?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
}

export interface RowFormulaRule {
  targetColumn: string
  expression: string       // mathjs 安全表达式
  dependsOn: string[]
}

export interface ValidationRule {
  type: 'required' | 'pattern' | 'number' | 'email' | 'phone' | 'custom'
  pattern?: string
  min?: number
  max?: number
  minLength?: number
  maxLength?: number
  message: string
}
