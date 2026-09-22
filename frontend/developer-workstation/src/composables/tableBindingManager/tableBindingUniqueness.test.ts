import { describe, expect, it } from 'vitest'
import {
  firstUnusedStructuralFkName,
  subTableStillBindable,
  usedStructuralFkNames,
} from './tableBindingUniqueness'

const fields = [
  { id: 1, fieldName: 'case_id', isForeignKey: true, refTableId: 10 },
  { id: 2, fieldName: 'party_id', isForeignKey: true, refTableId: 11 },
  { id: 3, fieldName: 'id', isForeignKey: false },
]

describe('tableBindingUniqueness', () => {
  it('allows a second SUB binding while an unused declared FK remains', () => {
    expect(subTableStillBindable(
      [{ id: 101, tableId: 20, bindingType: 'SUB', filterFkFieldId: 1, foreignKeyField: 'case_id' }],
      20,
      fields,
    )).toBe(true)
  })

  it('blocks the table once every declared FK is used', () => {
    expect(subTableStillBindable(
      [
        { id: 101, tableId: 20, bindingType: 'SUB', filterFkFieldId: 1, foreignKeyField: 'case_id' },
        { id: 102, tableId: 20, bindingType: 'SUB', filterFkFieldId: 2, foreignKeyField: 'party_id' },
      ],
      20,
      fields,
    )).toBe(false)
  })

  it('blocks a second binding when the table has no declared FK', () => {
    expect(subTableStillBindable(
      [{ id: 101, tableId: 20, bindingType: 'SUB', foreignKeyField: 'id' }],
      20,
      [{ id: 3, fieldName: 'id', isForeignKey: false }],
    )).toBe(false)
  })

  it('picks the unused FK name for auto-fill', () => {
    const used = usedStructuralFkNames(
      [{ id: 101, tableId: 20, bindingType: 'SUB', filterFkFieldId: 1, foreignKeyField: 'case_id' }],
      fields,
    )
    expect(firstUnusedStructuralFkName(fields, used)).toBe('party_id')
  })
})
