import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  calculateSubTableRow,
  calculateSummary,
  validateSubTable,
  DependencyGraph,
} from '../businessLogicEngine'
import type {
  RowFormulaRule,
  SubTableValidationConfig,
  FormulaRule,
} from '../formRendererHelpers'
import { aggregationArb } from './businessLogicEngine.property.shared'

// ─── Property 11: Sub-table row calculation correctness ─────────────────────
// Feature: function-unit-design-review, Property 11: Sub-table row calculation correctness
// **Validates: Requirements 4.2**

describe('Property 11: Sub-table row calculation correctness', () => {
  it('row formula correctly computes target column from dependent columns', () => {
    const qtyArb = fc.integer({ min: 0, max: 1000 })
    const priceArb = fc.integer({ min: 0, max: 10000 })

    fc.assert(
      fc.property(qtyArb, priceArb, (quantity, unitPrice) => {
        const row: Record<string, unknown> = { quantity, unit_price: unitPrice }
        const formulas: RowFormulaRule[] = [
          { targetColumn: 'amount', expression: 'quantity * unit_price', dependsOn: ['quantity', 'unit_price'] },
        ]
        const result = calculateSubTableRow(row, formulas)
        expect(result.amount).toBeCloseTo(quantity * unitPrice, 5)
      }),
      { numRuns: 100 },
    )
  })

  it('missing dependent values are treated as 0', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 100 }), (quantity) => {
        const row: Record<string, unknown> = { quantity }
        const formulas: RowFormulaRule[] = [
          { targetColumn: 'amount', expression: 'quantity * unit_price', dependsOn: ['quantity', 'unit_price'] },
        ]
        const result = calculateSubTableRow(row, formulas)
        // unit_price is missing → treated as 0
        expect(result.amount).toBe(0)
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 12: Sub-table summary calculation correctness ─────────────────
// Feature: function-unit-design-review, Property 12: Sub-table summary calculation correctness
// **Validates: Requirements 4.4**

describe('Property 12: Sub-table summary calculation correctness', () => {
  it('SUM/AVG/COUNT/MIN/MAX produce correct aggregated values', () => {
    const rowsArb = fc.array(
      fc.record({ amount: fc.integer({ min: 0, max: 10000 }) }),
      { minLength: 1, maxLength: 20 },
    )

    fc.assert(
      fc.property(rowsArb, aggregationArb, (rows, agg) => {
        const result = calculateSummary(rows, 'amount', agg)
        const values = rows.map((r) => r.amount)

        switch (agg) {
          case 'SUM':
            expect(result).toBeCloseTo(values.reduce((a, b) => a + b, 0), 5)
            break
          case 'AVG':
            expect(result).toBeCloseTo(values.reduce((a, b) => a + b, 0) / values.length, 5)
            break
          case 'COUNT':
            expect(result).toBe(rows.length)
            break
          case 'MIN':
            expect(result).toBe(Math.min(...values))
            break
          case 'MAX':
            expect(result).toBe(Math.max(...values))
            break
        }
      }),
      { numRuns: 100 },
    )
  })

  it('empty rows return 0 for all aggregations', () => {
    fc.assert(
      fc.property(aggregationArb, (agg) => {
        const result = calculateSummary([], 'amount', agg)
        expect(result).toBe(0)
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 10: Sub-table validation completeness ─────────────────────────
// Feature: function-unit-design-review, Property 10: Sub-table validation completeness
// **Validates: Requirements 7.2, 7.4, 7.5, 7.6**

describe('Property 10: Sub-table validation completeness', () => {
  it('minRows/maxRows constraints are enforced correctly', () => {
    const minRowsArb = fc.integer({ min: 1, max: 5 })
    const maxRowsArb = fc.integer({ min: 5, max: 15 })
    const rowCountArb = fc.integer({ min: 0, max: 20 })

    fc.assert(
      fc.property(minRowsArb, maxRowsArb, rowCountArb, (minRows, maxRows, rowCount) => {
        const config: SubTableValidationConfig = { minRows, maxRows }
        const rows = Array.from({ length: rowCount }, () => ({}))
        const result = validateSubTable(rows, config)

        if (rowCount < minRows || rowCount > maxRows) {
          expect(result.valid).toBe(false)
          expect(result.rowCountError).toBeDefined()
        } else {
          expect(result.valid).toBe(true)
          expect(result.rowCountError).toBeUndefined()
        }
      }),
      { numRuns: 100 },
    )
  })

  it('cell validation rules are applied to each row', () => {
    const rowCountArb = fc.integer({ min: 1, max: 5 })
    const cellValueArb = fc.oneof(fc.constant(''), fc.string({ minLength: 1, maxLength: 10 }))

    fc.assert(
      fc.property(rowCountArb, fc.array(cellValueArb, { minLength: 1, maxLength: 5 }), (rowCount, cellValues) => {
        const actualRowCount = Math.min(rowCount, cellValues.length)
        const rows = cellValues.slice(0, actualRowCount).map((v) => ({ name: v }))
        const config: SubTableValidationConfig = {
          columnRules: {
            name: [{ type: 'required', message: 'Name is required' }],
          },
        }
        const result = validateSubTable(rows, config)

        const emptyRows = rows.filter((r) => r.name === '' || r.name === null || r.name === undefined)
        if (emptyRows.length > 0) {
          expect(result.valid).toBe(false)
          expect(result.cellErrors.size).toBeGreaterThan(0)
        } else {
          expect(result.valid).toBe(true)
        }
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 24: Dependency graph incremental evaluation ───────────────────
// Feature: function-unit-design-review, Property 24: Dependency graph incremental evaluation
// **Validates: Requirements 37.3**

describe('Property 24: Dependency graph incremental evaluation', () => {
  it('only rules depending on the changed field are returned by getAffectedRules', () => {
    const formulaCountArb = fc.integer({ min: 1, max: 5 })

    fc.assert(
      fc.property(formulaCountArb, (count) => {
        const graph = new DependencyGraph()
        const formulas: FormulaRule[] = []

        // Create formulas: f0 depends on 'x', f1 depends on 'y', f2 depends on 'x', etc.
        for (let i = 0; i < count; i++) {
          formulas.push({
            targetField: `result_${i}`,
            expression: `dep_${i % 2} * 2`,
            dependsOn: [i % 2 === 0 ? 'x' : 'y'],
          })
        }

        graph.build(formulas, [], [])

        const affectedByX = graph.getAffectedRules('x')
        const affectedByY = graph.getAffectedRules('y')

        // Rules depending on 'x' should only be even-indexed formulas
        for (const rule of affectedByX) {
          const formula = formulas[rule.ruleIndex]
          expect(formula.dependsOn).toContain('x')
        }

        // Rules depending on 'y' should only be odd-indexed formulas
        for (const rule of affectedByY) {
          const formula = formulas[rule.ruleIndex]
          expect(formula.dependsOn).toContain('y')
        }

        // Changing 'z' (not a dependency) should return no rules
        const affectedByZ = graph.getAffectedRules('z')
        expect(affectedByZ.length).toBe(0)
      }),
      { numRuns: 100 },
    )
  })

  it('transitive dependencies are included in affected rules', () => {
    const graph = new DependencyGraph()

    // a → b (formula: b = a * 2), b → c (formula: c = b + 1)
    const formulas: FormulaRule[] = [
      { targetField: 'b', expression: 'a * 2', dependsOn: ['a'] },
      { targetField: 'c', expression: 'b + 1', dependsOn: ['b'] },
    ]

    fc.assert(
      fc.property(fc.constant(null), () => {
        graph.build(formulas, [], [])
        const affected = graph.getAffectedRules('a')

        // Should include both b (direct) and c (transitive via b)
        const targetFields = affected.map((r) => r.targetField)
        expect(targetFields).toContain('b')
        expect(targetFields).toContain('c')
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 25: Circular dependency termination ───────────────────────────
// Feature: function-unit-design-review, Property 25: Circular dependency termination
// **Validates: Requirements 37.4**

describe('Property 25: Circular dependency termination', () => {
  it('engine terminates within MAX_ITERATIONS for any circular dependency rules', () => {
    const chainLengthArb = fc.integer({ min: 2, max: 8 })

    fc.assert(
      fc.property(chainLengthArb, (chainLength) => {
        const graph = new DependencyGraph()
        const formulas: FormulaRule[] = []

        // Create a circular chain: f0 → f1 → f2 → ... → f0
        for (let i = 0; i < chainLength; i++) {
          const nextField = `field_${(i + 1) % chainLength}`
          formulas.push({
            targetField: `field_${i}`,
            expression: `${nextField} + 1`,
            dependsOn: [nextField],
          })
        }

        graph.build(formulas, [], [])

        // This must terminate (not hang) — the graph caps at MAX_ITERATIONS
        const startTime = Date.now()
        const affected = graph.getAffectedRules('field_0')
        const elapsed = Date.now() - startTime

        // Must complete quickly (well under 1 second)
        expect(elapsed).toBeLessThan(1000)
        // Should return some rules (the circular chain is traversed up to the limit)
        expect(affected.length).toBeGreaterThan(0)
        // Should not exceed MAX_ITERATIONS worth of rules
        expect(affected.length).toBeLessThanOrEqual(
          chainLength * DependencyGraph.MAX_ITERATIONS,
        )
      }),
      { numRuns: 100 },
    )
  })

  it('self-referencing rule terminates', () => {
    fc.assert(
      fc.property(fc.constant(null), () => {
        const graph = new DependencyGraph()
        // field_a depends on itself
        const formulas: FormulaRule[] = [
          { targetField: 'field_a', expression: 'field_a + 1', dependsOn: ['field_a'] },
        ]

        graph.build(formulas, [], [])

        const startTime = Date.now()
        const affected = graph.getAffectedRules('field_a')
        const elapsed = Date.now() - startTime

        expect(elapsed).toBeLessThan(1000)
        expect(affected.length).toBeGreaterThan(0)
      }),
      { numRuns: 100 },
    )
  })
})
