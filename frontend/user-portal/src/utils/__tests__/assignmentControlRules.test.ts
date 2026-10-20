import { describe, expect, it } from 'vitest'
import { assignmentControlRules } from '@platform-shared/batch/assignmentControlRules'
const config = { allowUser: true, allowRole: true, assigneeField: 'reviewer_reference', buField: 'queue_unit', roleField: 'queue_role' }
const t = (key: string) => key
describe('Assignment composite control', () => {
  it('uses configured field names for a marker-only design', () => {
    expect(assignmentControlRules([], config, t).map(rule => rule.field)).toEqual(['reviewer_reference', 'queue_unit', 'queue_role'])
  })
  it('keeps customized nested fields and adds only missing intrinsic controls', () => {
    const children = [{ type: 'elCard', props: { header: 'Custom' }, children: [
      { type: 'input', field: 'reviewer_reference', title: 'Designed reviewer', style: { width: '237px' }, props: { readonly: true } },
    ] }]
    const snapshot = structuredClone(children)
    const result = assignmentControlRules(children, config, t)
    expect(result[0]).toEqual(snapshot[0])
    expect(result.slice(1).map(rule => rule.field)).toEqual(['queue_unit', 'queue_role'])
    expect(children).toEqual(snapshot)
  })
  it('does not invent a BU or person column for role-only contracts without a BU', () => {
    expect(assignmentControlRules([], { allowUser: false, allowRole: true, roleField: 'pool' }, t).map(rule => rule.field)).toEqual(['pool'])
  })
  it('preserves an explicitly hidden custom field instead of duplicating it', () => {
    const field = { type: 'input', field: config.assigneeField, hidden: true }
    const result = assignmentControlRules([field], config, t)
    expect(result.filter(rule => rule.field === config.assigneeField)).toEqual([field])
  })
})
