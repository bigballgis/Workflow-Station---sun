import { describe, expect, it } from 'vitest'
import { validateBatchMembershipDesign } from '../batchSubTableMembership'
import type { FieldDefinition, TableBinding } from '@/api/functionUnit'

const rules = [{ props: { _bindingId: 101, allowBatchAdd: true } }]
const binding: TableBinding = { id: 101, tableId: 20, bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'structuralFk', sortOrder: 0 }
const fields: FieldDefinition[] = [
  { fieldName: 'item_key', dataType: 'VARCHAR', nullable: false, isPrimaryKey: true, pkGeneration: { strategy: 'manual' } },
  { fieldName: 'description', dataType: 'TEXT', nullable: false, isPrimaryKey: false },
]

describe('batch Add/Delete designer validation', () => {
  it('rejects a required readonly field without a valid literal default', () => {
    const complete = { '101': { rule: [{ field: 'description' }, { field: 'item_key' }, { field: 'amount' }] } }
    const amount: FieldDefinition = { fieldName: 'amount', dataType: 'INTEGER', nullable: false, isPrimaryKey: false }
    const permissions = { '101:amount': 'READONLY' }
    expect(validateBatchMembershipDesign(rules, complete, [binding], () => [...fields, amount], permissions)).toBe(false)
    expect(validateBatchMembershipDesign(rules, complete, [binding], () => [...fields, { ...amount, defaultValue: '0' }], permissions)).toBe(true)
    expect(validateBatchMembershipDesign(rules, complete, [binding], () => [...fields, { ...amount, defaultValue: '1.5' }], permissions)).toBe(false)
    expect(validateBatchMembershipDesign(rules, complete, [binding], () => [...fields, { ...amount, defaultValue: 'CURRENT_TIMESTAMP' }], permissions)).toBe(false)
    expect(validateBatchMembershipDesign(rules, complete, [binding], () => [...fields, { ...amount, dataType: 'DATE', defaultValue: '2026-02-31' }], permissions)).toBe(false)
    expect(validateBatchMembershipDesign(rules, complete, [binding], () => fields, { '101:item_key': 'READONLY' })).toBe(false)
  })
  it('requires all mandatory business fields and manual primary keys', () => {
    const incomplete = { '101': { rule: [{ field: 'description' }] } }
    expect(validateBatchMembershipDesign(rules, incomplete, [binding], () => fields)).toBe(false)
    const complete = { '101': { rule: [{ field: 'description' }, { field: 'item_key' }] } }
    expect(validateBatchMembershipDesign(rules, complete, [binding], () => fields)).toBe(true)
  })

  it('rejects MI participants and read-only business bindings', () => {
    expect(validateBatchMembershipDesign(rules, {}, [{ ...binding, bindingLinkMode: 'miParticipantRow' }], () => fields)).toBe(false)
    expect(validateBatchMembershipDesign(rules, {}, [{ ...binding, bindingMode: 'READONLY' }], () => fields)).toBe(false)
  })

  it('does not require hidden service-generated keys in the batch form', () => {
    const generated = fields.map(field => field.isPrimaryKey ? { ...field, pkGeneration: { strategy: 'uuid' } } : field)
    expect(validateBatchMembershipDesign(rules, { '101': { rule: [{ field: 'description' }] } }, [binding], () => generated)).toBe(true)
  })
})
