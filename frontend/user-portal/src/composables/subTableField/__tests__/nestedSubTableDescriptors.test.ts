import { describe, expect, it } from 'vitest'
import { buildNestedSubTableDescriptors } from '../nestedSubTableDescriptors'
import type { SubTableBinding } from '../subTableFieldTypes'
import type { FormField } from '@/components/formRendererHelpers'

/**
 * A nested sub-table must honour the same Allow Add / Edit / Delete switches as a top-level
 * one. Before this was wired the row dialog hardcoded full CRUD, so a nested table stayed
 * fully editable no matter what the designer configured.
 */
function binding(id: number, name = 'package'): SubTableBinding {
  return {
    bindingId: id,
    tableId: 5000 + id,
    bindingType: 'SUB',
    bindingMode: 'EDITABLE',
    foreignKeyField: 'shipment_id',
    tableName: name,
    physicalTableName: name,
    tableType: 'SUB',
    tableDescription: '',
    columns: [{ field: 'label', label: 'Label' }],
    dialogColumns: [{ field: 'label', label: 'Label' }],
    primaryKeyFields: ['package_id'],
    data: [],
    formFields: [],
  } as SubTableBinding
}

function placed(bindingId: number, props: Partial<FormField> = {}): FormField {
  return { key: `__subTable_${bindingId}`, label: '', type: 'subTable', _bindingId: bindingId, ...props } as FormField
}

describe('buildNestedSubTableDescriptors', () => {
  it('carries the binding runtime inputs the nested field needs for FK/PK allocation', () => {
    const [d] = buildNestedSubTableDescriptors([placed(50141)], [binding(50141)])
    expect(d).toMatchObject({
      bindingId: 50141,
      tableName: 'package',
      tableId: 55141,
      foreignKeyField: 'shipment_id',
      primaryKeyFields: ['package_id'],
      bindingMode: 'EDITABLE',
    })
  })

  it('leaves per-op switches undefined when the designer never turned any off', () => {
    const [d] = buildNestedSubTableDescriptors([placed(50141)], [binding(50141)])
    expect(d.allowAdd).toBeUndefined()
    expect(d.allowEdit).toBeUndefined()
    expect(d.allowDelete).toBeUndefined()
  })

  it('forwards each explicitly disabled operation', () => {
    const [d] = buildNestedSubTableDescriptors(
      [placed(50141, { allowAdd: false, allowEdit: false, allowDelete: false })],
      [binding(50141)],
    )
    expect(d.allowAdd).toBe(false)
    expect(d.allowEdit).toBe(false)
    expect(d.allowDelete).toBe(false)
  })

  it('forwards a single disabled operation without touching the others', () => {
    const [d] = buildNestedSubTableDescriptors(
      [placed(50141, { allowEdit: false })],
      [binding(50141)],
    )
    expect(d.allowEdit).toBe(false)
    expect(d.allowAdd).toBeUndefined()
    expect(d.allowDelete).toBeUndefined()
  })

  it('reads switches off widgets nested inside layout containers (card / row / col)', () => {
    const layout = [{
      key: 'card_0', label: 'Shipment Info', type: 'card', span: 24,
      children: [{
        key: 'row_0', label: '', type: 'row', span: 24,
        children: [placed(50141, { allowDelete: false })],
      }],
    }] as unknown as FormField[]

    const [d] = buildNestedSubTableDescriptors(layout, [binding(50141)])
    expect(d.bindingId).toBe(50141)
    expect(d.allowDelete).toBe(false)
  })

  it('skips widgets whose binding is absent from the linked pool', () => {
    expect(buildNestedSubTableDescriptors([placed(99999)], [binding(50141)])).toEqual([])
  })

  it('keeps the first placement when the same binding is dropped twice', () => {
    const out = buildNestedSubTableDescriptors(
      [placed(50141, { allowEdit: false }), placed(50141)],
      [binding(50141)],
    )
    expect(out).toHaveLength(1)
    expect(out[0].allowEdit).toBe(false)
  })

  it('returns nothing for an empty form design or empty pool', () => {
    expect(buildNestedSubTableDescriptors([], [binding(50141)])).toEqual([])
    expect(buildNestedSubTableDescriptors(undefined, [binding(50141)])).toEqual([])
    expect(buildNestedSubTableDescriptors([placed(50141)], [])).toEqual([])
    expect(buildNestedSubTableDescriptors([placed(50141)], undefined)).toEqual([])
  })
})
