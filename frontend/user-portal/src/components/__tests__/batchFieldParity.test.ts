import { describe, expect, it } from 'vitest'
import { extractBatchFields } from '@platform-shared/batch/fields'
import { firstBatchFieldError } from '@platform-shared/batch/validation'
import { batchControlIssue } from '@platform-shared/batch/controlContract'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
import { resolveRuleDefaultValue } from '@/utils/formCreateRuleDefaults'

const check = (rule: Record<string, unknown>, value: unknown) => firstBatchFieldError([{ probe: value }], extractBatchFields([{ ...rule, field: 'probe', title: 'Probe' }])!)
describe('ordinary and Batch field value contracts', () => {
  it('uses the configured switch values rather than assuming boolean', () => {
    expect(check({ type: 'switch', required: true, props: { activeValue: 1, inactiveValue: 0 } }, 0)).toBeNull()
    expect(check({ type: 'switch', props: { activeValue: 'Y', inactiveValue: 'N' } }, 'N')).toBeNull()
    expect(check({ type: 'switch' }, 'N')).not.toBeNull()
    expect(check({ type: 'switch', validate: [{ required: true }] }, false)).toBeNull()
    expect(check({ type: 'inputNumber', validate: [{ required: true }] }, 0)).toBeNull()
    expect(resolveRuleDefaultValue({ type: 'switch', value: '0', props: { activeValue: 1, inactiveValue: 0 } })).toBe(0)
    expect(resolveRuleDefaultValue({ type: 'switch', value: 'N', props: { activeValue: 'Y', inactiveValue: 'N' } })).toBe('N')
  })
  it('validates each multiselect option and treats an empty required list as empty', () => {
    const rule = { type: 'select', props: { multiple: true }, options: [{ value: 'a' }, { value: 'b' }] }
    expect(check(rule, ['a', 'b'])).toBeNull()
    expect(check(rule, ['other'])).not.toBeNull()
    expect(check({ ...rule, required: true }, [])).not.toBeNull()
    expect(check({ ...rule, props: { allowCreate: true } }, 'new-option')).toBeNull()
  })
  it('accepts date range values and enforces their shape', () => {
    const rule = { type: 'datePicker', props: { type: 'daterange', valueFormat: 'YYYY-MM-DD' } }
    expect(check(rule, ['2026-11-01', '2026-11-04'])).toBeNull()
    expect(check(rule, ['2026-11-01'])).not.toBeNull()
  })
  it('uses the ordinary date validator for datetime values', () => {
    expect(check({ type: 'datetime', validate: [{ type: 'date' }] }, '2026-11-04 12:13:14')).toBeNull()
    expect(check({ type: 'datetime', validate: [{ type: 'date' }] }, 'invalid')).not.toBeNull()
  })
  it('enforces email, pattern, arrays and custom validation messages', () => {
    const rule = { type: 'input', validate: [{ type: 'email', message: 'Enter a valid email' }] }
    expect(check(rule, 'person@example.invalid')).toBeNull()
    expect(check(rule, 'invalid')).toMatchObject({ message: 'Enter a valid email' })
    const pattern = { type: 'input', validate: [{ pattern: '^A' }] }
    expect(batchControlIssue([{ ...pattern, field: 'probe' }])).toBeNull()
    expect(check(pattern, 'ABC')).toBeNull()
    expect(check(pattern, 'BCD')).not.toBeNull()
    expect(check({ type: 'select', props: { multiple: true }, options: [{ value: 'a' }], validate: [{ type: 'array', min: 2 }] }, ['a'])).not.toBeNull()
  })
  it('supports designer email/pattern modes and required aliases', () => {
    expect(check({ type: 'input', validate: [{ mode: 'email' }] }, 'invalid')).not.toBeNull()
    expect(check({ type: 'input', $required: true }, '')).not.toBeNull()
    expect(check({ type: 'input', validate: [{ mode: 'required' }] }, '')).not.toBeNull()
    expect(check({ type: 'input', props: { maxLength: '3' } }, 'long')).not.toBeNull()
    expect(check({ type: 'input', validate: [{ mode: 'min', min: 3 }] }, '10')).toBeNull()
    expect(check({ type: 'input', validate: [{ mode: 'min', min: 3 }] }, '2')).not.toBeNull()
  })
  it('skips presentation validation for hidden fields and validates them after showing', () => {
    const rules = [{ type: 'input', field: 'probe', title: 'Probe', required: true, hidden: true }]
    const session = createBatchRowEventRunner(parseFormCreateEventHandler).createSession({}, rules, () => false)
    expect(() => session.validate(extractBatchFields(rules)!)).not.toThrow()
    session.api.hidden(false, 'probe')
    expect(() => session.validate(extractBatchFields(rules)!)).toThrow()
  })
})
