import { describe, expect, it } from 'vitest'
import { buildComputedFieldDefinition } from '@platform-shared/computedFieldConfig'
import {
  applyComputedReadonlyToFormFields,
  collectComputedColumns,
  computedColumnNames,
  previewComputedRow,
} from '../computedFieldRuntime'
import type { BindingFieldDefinition } from '../subTableRowRuntime/types'

function computedField(
  fieldName: string,
  source: string,
  scope: 'row' | 'aggregate' = 'row',
  onError: 'fail' | 'null' = 'fail',
): BindingFieldDefinition {
  const built = buildComputedFieldDefinition(source, scope, onError)
  if (!built.ok) throw new Error(`test formula does not parse: ${source} — ${built.message}`)
  return {
    fieldName,
    isComputed: true,
    computedField: built.value as unknown as Record<string, unknown>,
  }
}

function plainField(fieldName: string): BindingFieldDefinition {
  return { fieldName }
}

describe('collectComputedColumns', () => {
  it('returns nothing when no field is computed', () => {
    expect(collectComputedColumns([plainField('price'), plainField('qty')])).toEqual([])
  })

  it('reports COMPUTED_FIELD_DEFINITION_INVALID when flagged computed without a usable formula', () => {
    const columns = collectComputedColumns([
      { fieldName: 'total', isComputed: true },
      { fieldName: 'other', isComputed: true, computedField: { source: 'price', ast: null } },
    ])
    expect(columns.map(c => c.fieldName)).toEqual(['total', 'other'])
    expect(columns.every(c => c.parseError === 'COMPUTED_FIELD_DEFINITION_INVALID')).toBe(true)
    const result = previewComputedRow(columns, { total: 99 })
    expect(result.errors.total).toBe('COMPUTED_FIELD_DEFINITION_INVALID')
    expect(result.errors.other).toBe('COMPUTED_FIELD_DEFINITION_INVALID')
    expect(result.values).toEqual({})
  })

  it('orders a formula after the formula it reads', () => {
    const columns = collectComputedColumns([
      computedField('grand_total', 'total * 1.1'),
      computedField('total', 'price * qty'),
      plainField('price'),
    ])
    expect(columns.map(c => c.fieldName)).toEqual(['total', 'grand_total'])
  })

  it('records the sub-tables an aggregate reaches into', () => {
    const [column] = collectComputedColumns([
      computedField('line_total', 'SUM(OrderLines.amount)', 'aggregate'),
    ])
    expect(column.referencedTables).toEqual(['orderlines'])
  })

  it('leaves out columns caught in a dependency cycle instead of looping', () => {
    const columns = collectComputedColumns([
      computedField('a', 'b + 1'),
      computedField('b', 'a + 1'),
      computedField('c', 'price * 2'),
    ])
    expect(columns.map(x => x.fieldName)).toEqual(['c'])
  })
})

describe('previewComputedRow', () => {
  it('evaluates a row formula against the values on screen', () => {
    const columns = collectComputedColumns([computedField('total', 'price * qty')])
    const result = previewComputedRow(columns, { price: 12.5, qty: 4 })
    expect(result.values.total).toBe('50.0')
    expect(result.errors).toEqual({})
    expect(result.skipped).toEqual([])
  })

  it('subtracts two YYYY-MM-DD date picker values as whole days', () => {
    const columns = collectComputedColumns([
      computedField('day', 'enddate - startdate'),
      plainField('startdate'),
      plainField('enddate'),
    ])
    const result = previewComputedRow(columns, {
      startdate: '2026-08-16',
      enddate: '2026-08-18',
    })
    expect(result.errors).toEqual({})
    expect(result.values.day).toBe('2')
  })

  it('feeds one formula result into the next', () => {
    const columns = collectComputedColumns([
      computedField('grand_total', 'total + 5'),
      computedField('total', 'price * qty'),
    ])
    const result = previewComputedRow(columns, { price: 10, qty: 2 })
    expect(result.values.total).toBe('20')
    expect(result.values.grand_total).toBe('25')
  })

  it('reports an error code rather than a plausible number when onError=fail', () => {
    const columns = collectComputedColumns([computedField('ratio', 'price / qty')])
    const result = previewComputedRow(columns, { price: 10, qty: 0 })
    expect(result.values.ratio).toBeUndefined()
    expect(result.errors.ratio).toBe('DIVISION_BY_ZERO')
  })

  it('previews a blank for onError=null, matching what the server stores', () => {
    const columns = collectComputedColumns([computedField('ratio', 'price / qty', 'row', 'null')])
    const result = previewComputedRow(columns, { price: 10, qty: 0 })
    expect(result.values.ratio).toBeNull()
    expect(result.errors).toEqual({})
  })

  it('lets a downstream formula see the blank left by an onError=null failure', () => {
    const columns = collectComputedColumns([
      computedField('ratio', 'price / qty', 'row', 'null'),
      computedField('label', 'IF(ISBLANK(ratio), "n/a", "ok")'),
    ])
    const result = previewComputedRow(columns, { price: 10, qty: 0 })
    expect(result.values.label).toBe('n/a')
  })

  it('skips an aggregate whose sub-table is not loaded on this screen', () => {
    const columns = collectComputedColumns([
      computedField('order_total', 'SUM(OrderLines.amount)', 'aggregate'),
    ])
    const result = previewComputedRow(columns, {}, {})
    expect(result.skipped).toEqual(['order_total'])
    expect(result.values.order_total).toBeUndefined()
    expect(result.errors).toEqual({})
  })

  it('evaluates an aggregate once its sub-table rows are present', () => {
    const columns = collectComputedColumns([
      computedField('order_total', 'SUM(OrderLines.amount)', 'aggregate'),
    ])
    const result = previewComputedRow(columns, {}, {
      orderlines: [{ amount: 10 }, { amount: 32.5 }],
    })
    expect(result.values.order_total).toBe('42.5')
    expect(result.skipped).toEqual([])
  })

  it('previews an empty sub-table as an empty aggregate, not as unavailable', () => {
    const columns = collectComputedColumns([
      computedField('line_count', 'COUNT(OrderLines)', 'aggregate'),
    ])
    const result = previewComputedRow(columns, {}, { orderlines: [] })
    expect(result.values.line_count).toBe('0')
    expect(result.skipped).toEqual([])
  })

  it('does not mutate the row it was given', () => {
    const columns = collectComputedColumns([computedField('total', 'price * qty')])
    const row = { price: 2, qty: 3 }
    previewComputedRow(columns, row)
    expect(row).toEqual({ price: 2, qty: 3 })
  })

  it('is a no-op when the table has no computed columns', () => {
    const result = previewComputedRow([], { price: 1 })
    expect(result.values).toEqual({})
    expect(result.errors).toEqual({})
    expect(result.skipped).toEqual([])
  })

  it('reads a MAIN-table column from parents via table.column', () => {
    const columns = collectComputedColumns([computedField('requester', 'leave_request.name')])
    const result = previewComputedRow(
      columns,
      {},
      {},
      { leave_request: { name: 'Vin' } },
    )
    expect(result.values.requester).toBe('Vin')
    expect(result.errors).toEqual({})
  })

  it('reports UNKNOWN_TABLE when a qualified parent row is not supplied', () => {
    const columns = collectComputedColumns([computedField('requester', 'leave_request.name')])
    const result = previewComputedRow(columns, {}, {})
    expect(result.errors.requester).toBe('UNKNOWN_TABLE')
  })
})

describe('computedColumnNames', () => {
  it('lower-cases the names so lookups are case-insensitive', () => {
    const names = computedColumnNames([computedField('Total_Amount', 'price * qty'), plainField('price')])
    expect(names.has('total_amount')).toBe(true)
    expect(names.has('price')).toBe(false)
  })

  it('returns an empty set for a table with no definitions', () => {
    expect(computedColumnNames(undefined).size).toBe(0)
  })
})

describe('applyComputedReadonlyToFormFields', () => {
  const defs = [computedField('fee_with_tax', 'handling_fee * 1.1'), plainField('handling_fee')]

  it('marks a top-level computed input read-only and leaves plain ones alone', () => {
    const fields = [
      { key: 'handling_fee', readonly: false },
      { key: 'fee_with_tax', readonly: false },
    ]
    const result = applyComputedReadonlyToFormFields(fields, defs)
    expect(result[0].readonly).toBe(false)
    expect(result[1].readonly).toBe(true)
  })

  it('reaches inputs nested inside layout containers', () => {
    const fields = [
      {
        key: '__layout_card',
        children: [
          { key: '__layout_col', children: [{ key: 'fee_with_tax', readonly: false }] },
          { key: 'handling_fee', readonly: false },
        ],
      },
    ]
    const result = applyComputedReadonlyToFormFields(fields, defs)
    const card = result[0]
    expect(card.children?.[0].children?.[0].readonly).toBe(true)
    expect(card.children?.[1].readonly).toBe(false)
  })

  it('returns the original array untouched when the table has no computed column', () => {
    const fields = [{ key: 'handling_fee', readonly: false }]
    expect(applyComputedReadonlyToFormFields(fields, [plainField('handling_fee')])).toBe(fields)
  })
})
