import { describe, expect, it } from 'vitest'

import {
  buildDesignerBaseRulePrependHiddenReadonly,
  buildDesignerComponentRuleDefaultStripHiddenReadonly,
  stripHiddenAndReadonlyFromPropsRules,
} from '../designerPropsPanelRules'

describe('designerPropsPanelRules', () => {
  it('buildDesignerBaseRulePrependHiddenReadonly puts Hidden then Readonly in Basis', () => {
    const { prepend, rule } = buildDesignerBaseRulePrependHiddenReadonly()
    expect(prepend).toBe(true)
    const items = rule()
    expect(items[0].field).toBe('hidden')
    expect(items[0].title).toBe('Hidden')
    expect(items[1].field).toBe('formCreateProps>readonly')
    expect(items[1].title).toBe('Readonly')
  })

  it('buildDesignerComponentRuleDefaultStripHiddenReadonly strips menu props', () => {
    const fn = buildDesignerComponentRuleDefaultStripHiddenReadonly()
    const out = fn(
      {
        _menu: {
          props: () => [
            { field: 'placeholder' },
            { field: 'formCreateProps>readonly' },
          ],
        },
      },
      {},
    )
    expect(out.map((r) => r.field)).toEqual(['placeholder'])
  })

  it('stripHiddenAndReadonlyFromPropsRules removes hidden and readonly from Props', () => {
    const rules = [
      { field: 'placeholder' },
      { field: 'hidden' },
      { field: 'readonly' },
      { field: 'formCreateProps>readonly' },
    ]
    expect(stripHiddenAndReadonlyFromPropsRules(rules).map((r) => r.field)).toEqual(['placeholder'])
  })
})
