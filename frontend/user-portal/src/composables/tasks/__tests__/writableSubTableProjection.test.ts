import { describe, expect, it } from 'vitest'
import { projectWritableSubTables } from '../writableSubTableProjection'

describe('writable subtable projection', () => {
  it('keeps explicit empty editable stores, not read-only or unavailable stores', () => {
    const tables = { 'dw:items': [], 'dw:parents': [{ key: 'P1' }], 'dw:hidden': [{ secret: true }] }
    expect(projectWritableSubTables(tables, [
      { tableName: 'parents', bindingMode: 'READONLY' },
      { tableName: 'items', bindingMode: 'EDITABLE' },
    ])).toEqual({ 'dw:items': [] })
    expect(tables['dw:parents']).toHaveLength(1)
  })
  it('retains a shared store through its writable binding and uses configured namespaces', () => {
    expect(projectWritableSubTables({ 'rt:items': [], 'dw:items': [{ key: 'I1' }] }, [
      { tableName: 'items', bindingMode: 'READONLY' },
      { tableName: 'items', bindingMode: 'EDITABLE' },
      { relationTableName: 'items', relationTableId: 22, bindingMode: 'READONLY' },
    ])).toEqual({ 'dw:items': [{ key: 'I1' }] })
  })
})
