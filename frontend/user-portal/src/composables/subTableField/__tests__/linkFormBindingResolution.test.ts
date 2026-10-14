import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useSubTableLinkFormDialog } from '../useSubTableLinkFormDialog'
import type { SubTableBinding, SubTableFieldProps } from '../subTableFieldTypes'

describe('explicit linked binding identity', () => {
  const defaults = { bindingType: 'SUB', bindingMode: 'READONLY', tableType: 'SUB', tableDescription: '', columns: [] }
  const target: SubTableBinding = { ...defaults, bindingId: 42, tableName: 'Shared label', designerTableName: 'actual_child', data: [] }
  const unrelated: SubTableBinding = { ...defaults, bindingId: 41, tableName: 'Shared label', designerTableName: 'other_child', data: [{ amount: 9 }] }
  const resolve = (bindings: SubTableBinding[]) => useSubTableLinkFormDialog(
    { linkedSubTableBindings: bindings } as SubTableFieldProps, ref([]), () => {}, key => key,
    { resolveSubTableRowPk: () => null, filterRowsByMiLinkFormParent: (_parent, rows) => rows },
  ).resolveLinkBindingForColumn

  it('never replaces an explicit empty target with a same-label populated table', () => {
    expect(resolve([unrelated, target])({ field: 'details', label: 'Details',
      props: { boundSubTableBindingId: 42, boundSubTableName: 'Shared label' } })).toBe(target)
  })

  it('does not infer a missing explicit binding from a shared display name', () => {
    expect(resolve([unrelated])({ field: 'details', label: 'Details',
      props: { boundSubTableBindingId: 42, boundSubTableName: 'Shared label' } })).toBeUndefined()
  })
})
