import type { BatchAssignmentConfig } from './workspaceTypes'
import { batchRuleChildren } from './batchFormRules'

/** Assignment is a composite control: its BPMN contract defines its pickers.
 * Authored child rules customize those pickers, rather than enable their existence.
 * Resolve fields from the contract, never column-name conventions or live DW data.
 */
export function assignmentControlRules(
  children: Array<Record<string, any>>, config: BatchAssignmentConfig, t: (key: string) => string,
): Array<Record<string, any>> {
  const seen = new Set<string>()
  function visit(rules: Array<Record<string, any>>) {
    for (const rule of rules) {
      if (typeof rule.field === 'string') seen.add(rule.field)
      visit(batchRuleChildren(rule))
    }
  }
  visit(children)
  const controls = [
    { enabled: config.allowUser, field: config.assigneeField, label: 'subTable.batchAssigneeLabel', placeholder: 'subTable.batchAssigneeLabel' },
    { enabled: config.allowRole, field: config.buField, label: 'subTable.batchBusinessUnitLabel', placeholder: 'subTable.selectBusinessUnit' },
    { enabled: config.allowRole, field: config.roleField, label: 'subTable.batchRoleLabel', placeholder: 'subTable.selectRole' },
  ]
  const requiredControls = controls.filter(control => control.enabled && control.field && !seen.has(control.field))
    .map(control => ({ type: 'input', field: control.field!, title: t(control.label),
      props: { placeholder: t(control.placeholder), clearable: true, filterable: true }, style: { width: '100%' } }))
  return [...children, ...requiredControls]
}
