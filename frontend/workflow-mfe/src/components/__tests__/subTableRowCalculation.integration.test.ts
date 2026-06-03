import { describe, it, expect } from 'vitest'
import { evaluateFormula, calculateSubTableRow } from '../businessLogicEngine'
import type { RowFormulaRule } from '../formRendererHelpers'

/**
 * Integration test: SubTableAddDialog row calculation pipeline.
 *
 * Tests the same logic path used by SubTableAddDialog's watcher:
 *   rowFormulas + dependent column values → evaluateFormula → target column value
 *
 * Validates: Requirements 4.2
 */

describe('SubTableAddDialog Row Calculation Integration', () => {
  const rowFormulas: RowFormulaRule[] = [
    { targetColumn: 'amount', expression: 'qty * price', dependsOn: ['qty', 'price'] },
  ]

  it('should compute target column from dependent columns', () => {
    const row = { item: 'Widget', qty: 3, price: 10, amount: 0 }
    const result = calculateSubTableRow(row, rowFormulas)

    expect(result.amount).toBe(30)
    // Original non-formula fields should be preserved
    expect(result.item).toBe('Widget')
    expect(result.qty).toBe(3)
    expect(result.price).toBe(10)
  })

  it('should treat missing dependent values as 0', () => {
    const row = { item: 'Gadget', qty: 5, amount: 0 }
    // price is missing → treated as 0
    const result = calculateSubTableRow(row, rowFormulas)

    expect(result.amount).toBe(0)
  })

  it('should treat non-numeric dependent values as 0', () => {
    const row = { item: 'Thing', qty: 'abc', price: 10, amount: 0 }
    const result = calculateSubTableRow(row, rowFormulas)

    expect(result.amount).toBe(0)
  })

  it('should handle multiple row formulas', () => {
    const multiFormulas: RowFormulaRule[] = [
      { targetColumn: 'subtotal', expression: 'qty * price', dependsOn: ['qty', 'price'] },
      { targetColumn: 'tax', expression: 'qty * price * 0.1', dependsOn: ['qty', 'price'] },
    ]
    const row = { qty: 10, price: 100, subtotal: 0, tax: 0 }
    const result = calculateSubTableRow(row, multiFormulas)

    expect(result.subtotal).toBe(1000)
    expect(result.tax).toBeCloseTo(100, 5)
  })

  it('should handle formula with built-in functions (ROUND)', () => {
    const formulas: RowFormulaRule[] = [
      { targetColumn: 'rounded', expression: 'ROUND(qty * price, 2)', dependsOn: ['qty', 'price'] },
    ]
    const row = { qty: 3, price: 3.333, rounded: 0 }
    const result = calculateSubTableRow(row, formulas)

    expect(result.rounded).toBeCloseTo(9.999, 2)
  })

  it('should simulate the SubTableAddDialog watcher flow: sequential column updates', () => {
    // Simulates: user enters qty=5, then price=20 → amount should be 100
    const formData: Record<string, unknown> = { item: 'Test', qty: 0, price: 0, amount: 0 }

    // Step 1: user enters qty = 5
    formData.qty = 5
    for (const formula of rowFormulas) {
      const fieldValues: Record<string, unknown> = {}
      for (const dep of formula.dependsOn) {
        fieldValues[dep] = formData[dep]
      }
      formData[formula.targetColumn] = evaluateFormula(formula.expression, fieldValues)
    }
    expect(formData.amount).toBe(0) // price is still 0

    // Step 2: user enters price = 20
    formData.price = 20
    for (const formula of rowFormulas) {
      const fieldValues: Record<string, unknown> = {}
      for (const dep of formula.dependsOn) {
        fieldValues[dep] = formData[dep]
      }
      formData[formula.targetColumn] = evaluateFormula(formula.expression, fieldValues)
    }
    expect(formData.amount).toBe(100)
  })

  it('should handle empty row formulas gracefully', () => {
    const row = { qty: 5, price: 10 }
    const result = calculateSubTableRow(row, [])

    expect(result).toEqual({ qty: 5, price: 10 })
  })
})
