import { describe, expect, it } from 'vitest'
import { resolveBindingKeyedEntry } from '@/utils/formConfigBindingResolve'

describe('resolveBindingKeyedEntry', () => {
  it('returns direct entry when binding id matches', () => {
    const map = { '50064': { rule: [{ field: 'name' }] } }
    const bindings = [{ id: 50064, bindingType: 'SUB' as const, sortOrder: 4 }]
    expect(resolveBindingKeyedEntry(map, 50064, bindings, 'SUB')).toEqual({ rule: [{ field: 'name' }] })
  })

  it('maps stale orphan keys to SUB bindings by sort order', () => {
    const map = {
      '302': { rule: [{ field: 'name' }] },
      '303': { rule: [] },
    }
    const bindings = [
      { id: 50064, bindingType: 'SUB' as const, sortOrder: 4 },
      { id: 50105, bindingType: 'SUB' as const, sortOrder: 4 },
    ]
    expect(resolveBindingKeyedEntry(map, 50064, bindings, 'SUB')).toEqual({ rule: [{ field: 'name' }] })
  })
})
