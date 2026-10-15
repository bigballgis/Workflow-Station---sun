import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useSubTableLinkFormDialog } from '../useSubTableLinkFormDialog'
import type { SubTableBinding, SubTableFieldProps } from '../subTableFieldTypes'

function dialog(overrides: Partial<SubTableBinding> = {}) {
  const binding: SubTableBinding = {
    bindingId: 42, tableId: 22, tableName: 'Child', bindingType: 'SUB', bindingMode: 'READONLY',
    tableType: 'SUB', tableDescription: '', columns: [], data: [], primaryKeyFields: ['entry_token'],
    fieldDefinitions: [{ fieldName: 'owner_ref', isForeignKey: true, refTableId: 11, refPrimaryKeyFields: ['party_key'] }],
    formFields: [{ key: 'entry_token', label: 'Key', type: 'input' }, { key: 'quantity', label: 'Quantity', type: 'number' }],
    ...overrides,
  }
  const api = useSubTableLinkFormDialog({ bindingId: 7, tableId: 11, title: 'Parent', columns: [],
    primaryKeyFields: ['party_key'], linkedSubTableBindings: [binding] } as SubTableFieldProps,
  ref([]), () => {}, key => key, { resolveSubTableRowPk: () => null, filterRowsByMiLinkFormParent: (_parent, rows) => rows })
  api.activeLinkColumn.value = { field: 'details', label: 'Details', props: { boundSubTableBindingId: 42 } }
  return { api, binding }
}

describe('actual linked form child seed', () => {
  const child = { entry_token: 'CHILD-A', owner_ref: 'PARENT-A', quantity: 0,
    __subTables__: { 'dw:grandchildren': [{ token: 'OWN' }] } }

  it('uses configured child-to-parent FK, not equality between unrelated primary keys', () => {
    const { api, binding } = dialog({ data: [child] })
    const result = api.buildLinkedFormData(binding, { readonly: true }, { party_key: 'PARENT-A' })
    expect(result.entry_token).toBe('CHILD-A')
    expect(result.quantity).toBe(0)
    expect(result.__subTables__).toEqual(child.__subTables__)
  })

  it('never borrows a foreign child even if its PK coincides with a parent business field', () => {
    const { api, binding } = dialog({ data: [child] })
    const result = api.buildLinkedFormData(binding, { readonly: true }, { party_key: 'PARENT-B', entry_token: 'CHILD-A' })
    expect(result.entry_token).toBeNull()
    expect(result.quantity).toBeNull()
    expect(result.__subTables__).toBeUndefined()
  })

  it('does not choose the first of multiple children belonging to the same parent', () => {
    const { api, binding } = dialog({ data: [child, { ...child, entry_token: 'CHILD-B' }] })
    expect(api.buildLinkedFormData(binding, { readonly: true }, { party_key: 'PARENT-A' }).entry_token).toBeNull()
  })

  it('honors the declared filter FK when another FK points to the same parent table', () => {
    const { api, binding } = dialog({ filterFkFieldName: 'reviewer_ref', fieldDefinitions: [
      { fieldName: 'owner_ref', isForeignKey: true, refTableId: 11, refPrimaryKeyFields: ['party_key'] },
      { fieldName: 'reviewer_ref', isForeignKey: true, refTableId: 11, refPrimaryKeyFields: ['party_key'] },
    ], data: [{ ...child, owner_ref: 'OTHER', reviewer_ref: 'PARENT-A' }] })
    expect(api.buildLinkedFormData(binding, { readonly: true }, { party_key: 'PARENT-A' }).entry_token).toBe('CHILD-A')
  })

  it('matches composite parent references using the platform encoding', () => {
    const { api, binding } = dialog({ fieldDefinitions: [{ fieldName: 'owner_ref', isForeignKey: true,
      refTableId: 11, refPrimaryKeyFields: ['zone', 'party_key'] }],
    data: [{ ...child, owner_ref: 'party_key=PARENT-A\u001fzone=NORTH' }] })
    expect(api.buildLinkedFormData(binding, { readonly: true }, { party_key: 'PARENT-A', zone: 'NORTH' }).entry_token).toBe('CHILD-A')
    expect(api.buildLinkedFormData(binding, { readonly: true }, { party_key: 'PARENT-A' }).entry_token).toBeNull()
    expect(api.buildLinkedFormData(binding, { readonly: true }, { party_key: 'PARENT-A', zone: '' }).entry_token).toBeNull()
  })

  it('preserves the selected row for same-table links without borrowing a sibling', () => {
    const { api, binding } = dialog({ tableId: 11, data: [child, { ...child, entry_token: 'CHILD-B' }] })
    expect(api.buildLinkedFormData(binding, {}, { entry_token: 'CHILD-B' }).entry_token).toBe('CHILD-B')
    expect(api.buildLinkedFormData(binding, {}, { entry_token: 'MISSING' }).entry_token).toBeNull()
  })

  it('refuses undeclared or ambiguous parent relationships', () => {
    for (const fieldDefinitions of [[], [
      { fieldName: 'owner_ref', isForeignKey: true, refTableId: 11, refPrimaryKeyFields: ['party_key'] },
      { fieldName: 'reviewer_ref', isForeignKey: true, refTableId: 11, refPrimaryKeyFields: ['party_key'] },
    ]]) {
      const { api, binding } = dialog({ data: [child], fieldDefinitions })
      expect(api.buildLinkedFormData(binding, {}, { party_key: 'PARENT-A' }).entry_token).toBeNull()
    }
  })

  it('uses platform identity for an unsaved same-table row without a complete business PK', () => {
    const { api, binding } = dialog({ tableId: 11, data: [
      { platformRowUuid: 'ROW-A', quantity: 0 }, { platformRowUuid: 'ROW-B', quantity: 2 },
    ] })
    expect(api.buildLinkedFormData(binding, {}, { platformRowUuid: 'ROW-B' }).quantity).toBe(2)
    expect(api.buildLinkedFormData(binding, {}, { platformRowUuid: 'MISSING' }).quantity).toBeNull()
  })
})
