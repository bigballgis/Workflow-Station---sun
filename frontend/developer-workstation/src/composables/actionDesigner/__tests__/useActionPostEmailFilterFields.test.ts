import { describe, expect, it } from 'vitest'
import type { TableDefinition } from '@/api/functionUnit'
import { buildActionPostEmailFilterFieldOptions } from '../useActionPostEmailFilterFields'
import { SYSTEM_VIEW_FIELDS } from '@/api/mainTableView'
import {
  applyFilterFieldOption,
  filterFieldKey,
  type FilterCondition,
} from '@/utils/mainTableViewFilter'

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

describe('buildActionPostEmailFilterFieldOptions with popup ACTION table', () => {
  const actionTable = {
    id: 7,
    tableName: 'remark_log',
    tableDisplayName: 'Remarks',
    tableType: 'ACTION',
    fieldDefinitions: [
      { fieldName: 'remark_type', displayName: 'Remark type' },
      { fieldName: 'amount', displayName: 'Popup amount' },
    ],
  } as TableDefinition
  const otherActionTable = {
    id: 8,
    tableName: 'other_log',
    tableType: 'ACTION',
    fieldDefinitions: [{ fieldName: 'ghost', displayName: 'Ghost' }],
  } as TableDefinition

  it('adds only the bound ACTION table fields with source ACTION and groups all options', () => {
    const options = buildActionPostEmailFilterFieldOptions([mainTable(), actionTable, otherActionTable], {
      actionTableId: 7,
      mainGroupLabel: 'Main',
      actionGroupLabel: name => `Action: ${name}`,
    })

    const actionOptions = options.filter(o => o.source === 'ACTION')
    expect(actionOptions.map(o => o.fieldName)).toEqual(['remark_type', 'amount'])
    expect(actionOptions.every(o => o.group === 'Action: Remarks' && !o.systemField)).toBe(true)
    expect(options.some(o => o.fieldName === 'ghost')).toBe(false)
    expect(options.filter(o => !o.source).every(o => o.group === 'Main')).toBe(true)
    expect(options.length).toBe(SYSTEM_VIEW_FIELDS.length + 1 + 2)
  })

  it('keeps a MAIN field and an ACTION field with the same name distinct by key', () => {
    const options = buildActionPostEmailFilterFieldOptions([mainTable(), actionTable], { actionTableId: 7 })
    const keys = options.map(filterFieldKey)

    expect(keys).toContain('amount')
    expect(keys).toContain('ACTION:amount')
    expect(new Set(keys).size).toBe(keys.length)
  })

  it('stays flat and ungrouped when no ACTION table is bound', () => {
    const options = buildActionPostEmailFilterFieldOptions([mainTable(), actionTable], { actionTableId: null })

    expect(options.some(o => o.source || o.group !== undefined)).toBe(false)
  })
})

describe('applyFilterFieldOption', () => {
  it('sets source for ACTION options and clears it when switching back to a MAIN field', () => {
    const row: FilterCondition = { fieldName: 'amount', operator: 'eq', value: '1' }
    applyFilterFieldOption(row, { fieldName: 'remark_type', label: 'R', systemField: false, source: 'ACTION' })
    expect(row).toMatchObject({ fieldName: 'remark_type', source: 'ACTION' })

    applyFilterFieldOption(row, { fieldName: 'process_status', label: 'S', systemField: true })
    expect(row.fieldName).toBe('process_status')
    expect(row.systemField).toBe(true)
    expect('source' in row).toBe(false)
  })
})
