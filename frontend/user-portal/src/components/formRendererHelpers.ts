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
