import { describe, expect, it } from 'vitest'
import {
  MAX_POST_EMAIL_RULES,
  filterConfigHasConditions,
  normalizePostEmailRules,
} from '@/utils/actionPostEmailRules'

describe('actionPostEmailRules', () => {
  it('normalizes legacy postEmail to one rule', () => {
    const rules = normalizePostEmailRules({
      postEmail: {
        enabled: true,
        emailTo: 'a@x.com',
        connectionId: 'c1',
        emailTemplateId: '2',
      },
    })
    expect(rules).toHaveLength(1)
    expect(rules[0].emailTo).toBe('a@x.com')
    expect(rules[0].enabled).toBe(true)
  })

  it('detects empty vs non-empty filter config', () => {
    expect(filterConfigHasConditions(null)).toBe(false)
    expect(filterConfigHasConditions({ logic: 'and', conditions: [], groups: [] })).toBe(false)
    expect(
      filterConfigHasConditions({
        logic: 'and',
        conditions: [{ fieldName: 'amount', operator: 'gt', value: '1' }],
      }),
    ).toBe(true)
  })

  it('exports max rules constant', () => {
    expect(MAX_POST_EMAIL_RULES).toBe(20)
  })

  it('returns no rules when config has no postEmailRules or legacy postEmail', () => {
    expect(normalizePostEmailRules({})).toHaveLength(0)
    expect(normalizePostEmailRules({ postEmailRules: [] })).toHaveLength(0)
    expect(normalizePostEmailRules({ postEmail: { enabled: false } })).toHaveLength(0)
  })
})
