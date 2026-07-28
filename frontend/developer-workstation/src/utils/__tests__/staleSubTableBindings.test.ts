import { describe, expect, it } from 'vitest'
import {
  collectStaleSubTableBindingIds,
  collectSubTableBindingIds,
} from '../staleSubTableBindings'

describe('staleSubTableBindings', () => {
  it('collects binding ids from top-level and props', () => {
    const rules = [
      { type: 'input', field: 'a' },
      { type: 'subTable', _bindingId: 273, props: {} },
      {
        type: 'elCard',
        children: [{ type: 'subTable', props: { _bindingId: 271 } }],
      },
    ]
    expect(collectSubTableBindingIds(rules).sort()).toEqual([271, 273])
  })

  it('flags ids not present on the target form', () => {
    const rules = [
      { type: 'subTable', _bindingId: 273 },
      { type: 'subTable', _bindingId: 50064 },
    ]
    expect(collectStaleSubTableBindingIds(rules, [50064, 50065])).toEqual([273])
  })
})
