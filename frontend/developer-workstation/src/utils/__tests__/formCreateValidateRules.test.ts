import { describe, expect, it } from 'vitest'
import {
  ensureFormCreateRuleValidation,
  ensureFormCreateRulesValidationDeep,
  isFormCreateRuleRequired,
} from '../formCreateValidateRules'

describe('formCreateValidateRules (developer-workstation)', () => {
  it('isFormCreateRuleRequired reads $required', () => {
    expect(isFormCreateRuleRequired({ $required: true })).toBe(true)
    expect(isFormCreateRuleRequired({ validate: [{ len: 2, message: 'x' }] })).toBe(false)
  })

  it('ensureFormCreateRuleValidation adds required from $required and default trigger', () => {
    const rule: Record<string, unknown> = {
      field: 'name',
      type: 'input',
      $required: true,
      validate: [{ len: 2, mode: 'len', adapter: true, message: 'xxxxx' }],
    }
    ensureFormCreateRuleValidation(rule)
    expect(rule.validate).toHaveLength(2)
    expect(rule.validate).toMatchObject([
      { required: true, trigger: 'blur' },
      { len: 2, trigger: 'blur' },
    ])
  })

  it('ensureFormCreateRulesValidationDeep walks nested children', () => {
    const rules = [
      {
        type: 'el-card',
        children: [
          {
            field: 'x',
            type: 'input',
            validate: [{ required: true, message: 'm' }],
          },
        ],
      },
    ]
    ensureFormCreateRulesValidationDeep(rules)
    expect((rules[0] as { children: Array<{ validate: unknown[] }> }).children[0].validate[0]).toMatchObject({
      required: true,
      trigger: 'blur',
    })
  })
})
