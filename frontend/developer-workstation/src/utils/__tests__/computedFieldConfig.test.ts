import { describe, expect, it } from 'vitest'
import {
  buildComputedFieldDefinition,
  computedFieldSummary,
  findNumericFormulaOnTextColumn,
  formulaProducesNumber,
  parseComputedFieldFromApi,
} from '@/utils/computedFieldConfig'

describe('computedFieldConfig', () => {
  it('parses API payload with ast and source', () => {
    const parsed = parseComputedFieldFromApi({
      version: 1,
      scope: 'row',
      source: '1 + 2',
      ast: { kind: 'literal', valueKind: 'number', value: 3 },
      dependsOn: [],
      onError: 'fail',
    })
    expect(parsed?.source).toBe('1 + 2')
    expect(parsed?.scope).toBe('row')
    expect(parsed?.onError).toBe('fail')
  })

  it('returns undefined for incomplete API payload', () => {
    expect(parseComputedFieldFromApi({ source: 'x' })).toBeUndefined()
    expect(parseComputedFieldFromApi(null)).toBeUndefined()
  })

  it('builds definition from valid formula', () => {
    const built = buildComputedFieldDefinition('10 + 5', 'row', 'null')
    expect(built.ok).toBe(true)
    if (built.ok) {
      expect(built.value.source).toBe('10 + 5')
      expect(built.value.scope).toBe('row')
      expect(built.value.onError).toBe('null')
      expect(built.value.dependsOn).toEqual([])
    }
  })

  it('rejects empty formula', () => {
    const built = buildComputedFieldDefinition('   ', 'row', 'fail')
    expect(built.ok).toBe(false)
  })

  it('summarizes configured formula', () => {
    const built = buildComputedFieldDefinition('price * qty', 'aggregate', 'fail')
    expect(built.ok).toBe(true)
    if (built.ok) {
      expect(computedFieldSummary(built.value)).toBe('Σ price * qty')
    }
    expect(computedFieldSummary(undefined)).toBe('')
  })

  it('treats SUM and arithmetic as numeric formulas', () => {
    const sum = buildComputedFieldDefinition('SUM(date_info.day)', 'aggregate', 'fail')
    const product = buildComputedFieldDefinition('price * quantity', 'row', 'fail')
    expect(sum.ok && formulaProducesNumber(sum.value.ast)).toBe(true)
    expect(product.ok && formulaProducesNumber(product.value.ast)).toBe(true)
  })

  it('finds a numeric formula stored on a VARCHAR column', () => {
    const built = buildComputedFieldDefinition('SUM(date_info.day)', 'aggregate', 'fail')
    expect(built.ok).toBe(true)
    if (!built.ok) return
    const hit = findNumericFormulaOnTextColumn([
      {
        fieldName: 'day',
        displayName: 'Day',
        dataType: 'VARCHAR',
        isComputed: true,
        computedField: built.value,
      },
    ])
    expect(hit?.fieldName).toBe('day')
    expect(findNumericFormulaOnTextColumn([
      { fieldName: 'day', dataType: 'INTEGER', isComputed: true, computedField: built.value },
    ])).toBeUndefined()
  })

  it('builds a sub-table parent lookup as a qualified field, not an aggregate', () => {
    const built = buildComputedFieldDefinition('leave_request.name', 'row', 'fail')
    expect(built.ok).toBe(true)
    if (!built.ok) return
    expect(built.value.dependsOn).toEqual(['leave_request.name'])
    expect(built.value.ast).toMatchObject({ type: 'field', table: 'leave_request', name: 'name' })
    expect(formulaProducesNumber(built.value.ast)).toBe(false)
  })
})
