type Rule = Record<string, any>
export const batchLayoutAliases: Record<string, string> = {
  fcRow: 'el-row', row: 'el-row', 'el-row': 'el-row', fcCol: 'el-col', col: 'el-col', 'el-col': 'el-col',
  elCard: 'el-card', card: 'el-card', 'el-card': 'el-card',
  elTabs: 'el-tabs', tabs: 'el-tabs', 'el-tabs': 'el-tabs',
  elTabPane: 'el-tab-pane', tabPane: 'el-tab-pane', TabPane: 'el-tab-pane', 'el-tab-pane': 'el-tab-pane',
  elCollapse: 'el-collapse', collapse: 'el-collapse', 'el-collapse': 'el-collapse',
  elCollapseItem: 'el-collapse-item', collapseItem: 'el-collapse-item', 'el-collapse-item': 'el-collapse-item',
}
export function batchRuleChildren(rule: Rule): Rule[] {
  return [rule.children, rule.props?.children, rule.props?.list, rule.props?.items, rule.props?.fields].find(Array.isArray) ?? []
}
const auxiliaryTypes = new Set(['divider', 'elDivider', 'fcTitle', 'title', 'text', 'alert', 'fcAlert', 'space', 'fcSpace'])
export function unsupportedBatchControl(rules: Rule[], fields: ReadonlySet<string>): string | null {
  for (const rule of rules) {
    if (rule.type === 'miAssignment') continue
    if (typeof rule.field === 'string' && rule.field.trim()) {
      if (!fields.has(rule.field)) return String(rule.type)
    } else if (!batchLayoutAliases[rule.type] && !auxiliaryTypes.has(rule.type)) return String(rule.type)
    const unsupported = unsupportedBatchControl(batchRuleChildren(rule), fields)
    if (unsupported) return unsupported
  }
  return null
}
