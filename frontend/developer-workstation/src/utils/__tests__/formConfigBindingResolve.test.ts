import { describe, expect, it } from 'vitest'
import { resolveBindingKeyedEntry, resolveRelationViewEntry } from '@/utils/formConfigBindingResolve'

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

  it('maps stale orphan keys to RELATED bindings by sort order', () => {
    const map = {
      '300': { viewFields: [{ fieldName: 'username' }], allFields: [{ fieldName: 'username' }] },
      '301': { viewFields: [], allFields: [] },
    }
    const bindings = [
      { id: 50035, bindingType: 'RELATED' as const, sortOrder: 2 },
      { id: 50037, bindingType: 'RELATED' as const, sortOrder: 3 },
    ]
    expect(resolveRelationViewEntry(map, 50035, bindings)?.viewFields).toHaveLength(1)
  })

  it('falls back viewFields from allFields when viewFields missing', () => {
    const map = {
      '300': { allFields: [{ fieldName: 'username' }], viewFields: [] },
    }
    const bindings = [{ id: 50035, bindingType: 'RELATED' as const, sortOrder: 2 }]
    expect(resolveRelationViewEntry(map, 50035, bindings)?.viewFields).toHaveLength(1)
  })
})
