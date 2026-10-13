import { describe, expect, it } from 'vitest'
import type { TableDefinition } from '@/api/functionUnit'
import { buildActionPostEmailFilterFieldOptions } from '../useActionPostEmailFilterFields'
import { SYSTEM_VIEW_FIELDS } from '@/api/mainTableView'

function mainTable(partial: Partial<TableDefinition> = {}): TableDefinition {
  return {
    id: 1,
    tableName: 'order_main',
    tableDisplayName: 'Order',
    tableType: 'MAIN',
    fieldDefinitions: [{ fieldName: 'amount', displayName: 'Amount' }],
    ...partial,
  } as TableDefinition
}

describe('buildActionPostEmailFilterFieldOptions', () => {
  it('includes system fields and MAIN table fieldDefinitions', () => {
    const options = buildActionPostEmailFilterFieldOptions([mainTable()])

    expect(options.some(o => o.fieldName === 'process_status' && o.systemField)).toBe(true)
    expect(options.some(o => o.fieldName === 'amount' && !o.systemField)).toBe(true)
    expect(options.length).toBe(SYSTEM_VIEW_FIELDS.length + 1)
  })

  it('recognizes MAIN case-insensitively via isMainTableDefinition', () => {
    const options = buildActionPostEmailFilterFieldOptions([
      mainTable({ tableType: 'main', fieldDefinitions: [{ fieldName: 'qty', displayName: 'Qty' }] }),
    ])

    expect(options.some(o => o.fieldName === 'qty')).toBe(true)
  })

  it('does not treat PRIMARY tableType as MAIN (binding enum, not table type)', () => {
    const options = buildActionPostEmailFilterFieldOptions([
      {
        id: 99,
        tableName: 'wrong',
        tableType: 'PRIMARY',
        fieldDefinitions: [{ fieldName: 'ghost_field', displayName: 'Ghost' }],
      } as TableDefinition,
      mainTable({ fieldDefinitions: [{ fieldName: 'real_field', displayName: 'Real' }] }),
    ])

    expect(options.some(o => o.fieldName === 'ghost_field')).toBe(false)
    expect(options.some(o => o.fieldName === 'real_field')).toBe(true)
  })

  it('excludes SUB table fields from options', () => {
    const options = buildActionPostEmailFilterFieldOptions([
      mainTable({ fieldDefinitions: [] }),
      {
        id: 2,
        tableName: 'lines',
        tableType: 'SUB',
        fieldDefinitions: [{ fieldName: 'line_amount', displayName: 'Line' }],
      } as TableDefinition,
    ])

    expect(options.some(o => o.fieldName === 'line_amount')).toBe(false)
    expect(options.length).toBe(SYSTEM_VIEW_FIELDS.length)
  })

  it('returns only system fields when no MAIN table exists', () => {
    const options = buildActionPostEmailFilterFieldOptions([
      {
        id: 2,
        tableName: 'lines',
        tableType: 'SUB',
        fieldDefinitions: [{ fieldName: 'x', displayName: 'X' }],
      } as TableDefinition,
    ])

    expect(options.length).toBe(SYSTEM_VIEW_FIELDS.length)
    expect(options.every(o => o.systemField)).toBe(true)
  })
})
