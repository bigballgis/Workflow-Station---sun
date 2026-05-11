import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  evaluateCondition,
  evaluateFormula,
  containsDangerousKeyword,
  processLinkage,
  validateField,
  validateCrossFields,
  calculateSubTableRow,
  calculateSummary,
  validateSubTable,
  DependencyGraph,
  BusinessLogicEngine,
} from '../businessLogicEngine'
import type {
  ConditionExpression,
  LinkageRule,
  CrossFieldRule,
  ValidationRule,
  RowFormulaRule,
  SubTableValidationConfig,
  FormulaRule,
} from '../formRendererHelpers'

// ─── Shared Arbitraries ─────────────────────────────────────────────────────

const fieldNameArb = fc.string({ minLength: 1, maxLength: 20 }).filter((s) => /^[a-zA-Z]\w*$/.test(s))

const conditionOperatorArb = fc.constantFrom(
  'equals' as const,
  'not-equals' as const,
  'contains' as const,
  'greater-than' as const,
  'less-than' as const,
  'is-empty' as const,
  'is-not-empty' as const,
)

const scalarValueArb = fc.oneof(
  fc.string({ minLength: 0, maxLength: 20 }),
  fc.integer({ min: -1000, max: 1000 }),
  fc.double({ min: -1000, max: 1000, noNaN: true, noDefaultInfinity: true }),
  fc.boolean(),
  fc.constant(null),
  fc.constant(undefined),
  fc.constant(''),
)

const aggregationArb = fc.constantFrom('SUM' as const, 'AVG' as const, 'COUNT' as const, 'MIN' as const, 'MAX' as const)

// ─── Property 2: Condition visibility evaluation correctness ────────────────
// Feature: function-unit-design-review, Property 2: Condition visibility evaluation correctness
// **Validates: Requirements 1.3, 1.4**

describe('Property 2: Condition visibility evaluation correctness', () => {
  it('field is visible iff condition evaluates to true against formData', () => {
    const leafConditionArb: fc.Arbitrary<ConditionExpression> = fc.record({
      field: fc.constant('status'),
      operator: fc.constantFrom('equals' as const, 'not-equals' as const),
      value: fc.constantFrom('active', 'inactive', 'pending'),
    })

    const formDataArb = fc.record({
      status: fc.constantFrom('active', 'inactive', 'pending', 'other'),
    })

    fc.assert(
      fc.property(leafConditionArb, formDataArb, (condition, formData) => {
        const result = evaluateCondition(condition, formData)
        const fieldValue = formData[condition.field]

        if (condition.operator === 'equals') {
           
          expect(result).toBe(fieldValue == condition.value)
        } else if (condition.operator === 'not-equals') {
           
          expect(result).toBe(fieldValue != condition.value)
        }
      }),
      { numRuns: 100 },
    )
  })

  it('returns true when referenced field does not exist in formData (ignore rule)', () => {
    fc.assert(
      fc.property(conditionOperatorArb, (operator) => {
        const condition: ConditionExpression = { field: 'nonExistent', operator, value: 'x' }
        const result = evaluateCondition(condition, {})
        expect(result).toBe(true)
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 3: Condition operator evaluation correctness ──────────────────
// Feature: function-unit-design-review, Property 3: Condition operator evaluation correctness
// **Validates: Requirements 1.5, 1.6**

describe('Property 3: Condition operator evaluation correctness', () => {
  it('each operator produces the correct boolean result for any pair of values', () => {
    const numericPairArb = fc.tuple(
      fc.integer({ min: -1000, max: 1000 }),
      fc.integer({ min: -1000, max: 1000 }),
    )

    fc.assert(
      fc.property(numericPairArb, ([a, b]) => {
        const formData = { f: a }

        expect(evaluateCondition({ field: 'f', operator: 'equals', value: b }, formData))
           
          .toBe(a == b)

        expect(evaluateCondition({ field: 'f', operator: 'not-equals', value: b }, formData))
           
          .toBe(a != b)

        expect(evaluateCondition({ field: 'f', operator: 'greater-than', value: b }, formData))
          .toBe(a > b)

        expect(evaluateCondition({ field: 'f', operator: 'less-than', value: b }, formData))
          .toBe(a < b)
      }),
      { numRuns: 100 },
    )
  })

  it('contains operator works for strings', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 0, maxLength: 20 }),
        fc.string({ minLength: 0, maxLength: 10 }),
        (str, sub) => {
          const formData = { f: str }
          const result = evaluateCondition({ field: 'f', operator: 'contains', value: sub }, formData)
          expect(result).toBe(str.includes(sub))
        },
      ),
      { numRuns: 100 },
    )
  })

  it('is-empty and is-not-empty are complementary', () => {
    const emptyishArb = fc.oneof(
      fc.constant(null),
      fc.constant(undefined),
      fc.constant(''),
      fc.constant([]),
      fc.string({ minLength: 1, maxLength: 10 }),
      fc.integer(),
    )

    fc.assert(
      fc.property(emptyishArb, (val) => {
        const formData = { f: val }
        const isEmpty = evaluateCondition({ field: 'f', operator: 'is-empty' }, formData)
        const isNotEmpty = evaluateCondition({ field: 'f', operator: 'is-not-empty' }, formData)
        expect(isEmpty).toBe(!isNotEmpty)
      }),
      { numRuns: 100 },
    )
  })

  it('AND/OR logic combinations evaluate correctly', () => {
    const boolArb = fc.boolean()

    fc.assert(
      fc.property(boolArb, boolArb, fc.constantFrom('AND' as const, 'OR' as const), (matchA, matchB, logic) => {
        const formData = { a: matchA ? 'yes' : 'no', b: matchB ? 'yes' : 'no' }
        const condition: ConditionExpression = {
          field: 'a',
          operator: 'equals',
          value: 'yes',
          logic,
          children: [
            { field: 'a', operator: 'equals', value: 'yes' },
            { field: 'b', operator: 'equals', value: 'yes' },
          ],
        }
        const result = evaluateCondition(condition, formData)
        if (logic === 'AND') {
          expect(result).toBe(matchA && matchB)
        } else {
          expect(result).toBe(matchA || matchB)
        }
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 4: Calculation formula engine correctness ─────────────────────
// Feature: function-unit-design-review, Property 4: Calculation formula engine correctness
// **Validates: Requirements 2.2, 2.3, 2.4**

describe('Property 4: Calculation formula engine correctness', () => {
  it('addition of two numeric inputs produces correct result', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -10000, max: 10000 }),
        fc.integer({ min: -10000, max: 10000 }),
        (a, b) => {
          const result = evaluateFormula('a + b', { a, b })
          expect(result).toBeCloseTo(a + b, 5)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('multiplication of two numeric inputs produces correct result', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1000, max: 1000 }),
        fc.integer({ min: -1000, max: 1000 }),
        (a, b) => {
          const result = evaluateFormula('a * b', { a, b })
          expect(result).toBeCloseTo(a * b, 5)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('missing or non-numeric values are treated as 0', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1000, max: 1000 }),
        (a) => {
          const result = evaluateFormula('a + b', { a, b: 'notANumber' })
          expect(result).toBeCloseTo(a, 5)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('empty or invalid expression returns 0', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('', '+++', '???'),
        (expr) => {
          const result = evaluateFormula(expr, { a: 5 })
          expect(result).toBe(0)
        },
      ),
      { numRuns: 100 },
    )
  })
})

// ─── Property 5: Formula syntax validation ──────────────────────────────────
// Feature: function-unit-design-review, Property 5: Formula syntax validation
// **Validates: Requirements 2.6**

describe('Property 5: Formula syntax validation', () => {
  it('valid arithmetic expressions are accepted (return non-zero for non-trivial input)', () => {
    const validExprArb = fc.constantFrom(
      'a + b',
      'a * b',
      'a - b',
      '(a + b) * 2',
      'a / 2',
    )

    fc.assert(
      fc.property(validExprArb, (expr) => {
        // Should not be flagged as dangerous
        expect(containsDangerousKeyword(expr)).toBe(false)
        // Should produce a finite number
        const result = evaluateFormula(expr, { a: 10, b: 5 })
        expect(Number.isFinite(result)).toBe(true)
      }),
      { numRuns: 100 },
    )
  })

  it('dangerous keywords (eval, Function, import, require, window, document) are rejected', () => {
    const dangerousKeywordArb = fc.constantFrom('eval', 'Function', 'import', 'require', 'window', 'document')

    fc.assert(
      fc.property(dangerousKeywordArb, (keyword) => {
        const expr = `${keyword}("alert(1)")`
        expect(containsDangerousKeyword(expr)).toBe(true)
        // evaluateFormula should return 0 for dangerous expressions
        const result = evaluateFormula(expr, {})
        expect(result).toBe(0)
      }),
      { numRuns: 100 },
    )
  })

  it('expressions without dangerous keywords pass the check', () => {
    const safeExprArb = fc.constantFrom(
      'SUM(a, b, c)',
      'AVG(x, y)',
      'ROUND(total, 2)',
      'IF(a > 0, a, 0)',
      'price * quantity',
      'MIN(a, b)',
      'MAX(a, b)',
    )

    fc.assert(
      fc.property(safeExprArb, (expr) => {
        expect(containsDangerousKeyword(expr)).toBe(false)
      }),
      { numRuns: 100 },
    )
  })
})


// ─── Property 6: Field linkage option filtering ─────────────────────────────
// Feature: function-unit-design-review, Property 6: Field linkage option filtering
// **Validates: Requirements 3.4**

describe('Property 6: Field linkage option filtering', () => {
  it('only options matching the source value via filterConfig are returned', () => {
    const sourceValueArb = fc.constantFrom('A', 'B', 'C', 'D')
    const optionArb = fc.record({
      label: fc.string({ minLength: 1, maxLength: 10 }),
      value: fc.string({ minLength: 1, maxLength: 5 }),
      parentId: fc.constantFrom('A', 'B', 'C', 'D'),
    })
    const optionsArb = fc.array(optionArb, { minLength: 0, maxLength: 10 })

    fc.assert(
      fc.property(sourceValueArb, optionsArb, (sourceValue, options) => {
        const linkage: LinkageRule = {
          sourceField: 'province',
          targetField: 'city',
          linkageType: 'option-filtering',
          filterConfig: {
            filterField: 'parentId',
            filterOperator: 'equals',
            filterSource: '$source',
          },
        }
        const formData = { province: sourceValue }
        const result = processLinkage(linkage, sourceValue, formData, options)

        const expected = options.filter((o) => o.parentId == sourceValue)
        expect(result.filteredOptions).toEqual(expected)
      }),
      { numRuns: 100 },
    )
  })

  it('contains filter operator returns options where filterField contains source value', () => {
    const sourceValueArb = fc.constantFrom('ab', 'cd', 'ef')
    const optionArb = fc.record({
      label: fc.string({ minLength: 1, maxLength: 10 }),
      value: fc.string({ minLength: 1, maxLength: 5 }),
      tag: fc.string({ minLength: 0, maxLength: 10 }),
    })
    const optionsArb = fc.array(optionArb, { minLength: 0, maxLength: 10 })

    fc.assert(
      fc.property(sourceValueArb, optionsArb, (sourceValue, options) => {
        const linkage: LinkageRule = {
          sourceField: 'src',
          targetField: 'tgt',
          linkageType: 'option-filtering',
          filterConfig: {
            filterField: 'tag',
            filterOperator: 'contains',
            filterSource: '$source',
          },
        }
        const formData = { src: sourceValue }
        const result = processLinkage(linkage, sourceValue, formData, options)

        const expected = options.filter((o) => typeof o.tag === 'string' && o.tag.includes(String(sourceValue)))
        expect(result.filteredOptions).toEqual(expected)
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 7: Field linkage value auto-fill ──────────────────────────────
// Feature: function-unit-design-review, Property 7: Field linkage value auto-fill
// **Validates: Requirements 3.6**

describe('Property 7: Field linkage value auto-fill', () => {
  it('correct target value is populated from valueMapping for any source value', () => {
    const mappingArb = fc.dictionary(
      fc.constantFrom('k1', 'k2', 'k3', 'k4', 'k5'),
      fc.oneof(fc.string({ minLength: 1, maxLength: 10 }), fc.integer({ min: 0, max: 100 })),
      { minKeys: 1, maxKeys: 5 },
    )
    const sourceKeyArb = fc.constantFrom('k1', 'k2', 'k3', 'k4', 'k5', 'missing')

    fc.assert(
      fc.property(mappingArb, sourceKeyArb, (mapping, sourceKey) => {
        const linkage: LinkageRule = {
          sourceField: 'src',
          targetField: 'tgt',
          linkageType: 'value-auto-fill',
          valueMapping: mapping,
        }
        const formData = { src: sourceKey }
        const result = processLinkage(linkage, sourceKey, formData)

        if (sourceKey in mapping) {
          expect(result.autoFillValue).toBe(mapping[sourceKey])
        } else {
          expect(result.autoFillValue).toBeUndefined()
        }
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 8: Form validation rule execution ─────────────────────────────
// Feature: function-unit-design-review, Property 8: Form validation rule execution
// **Validates: Requirements 5.2, 5.6, 5.7**

describe('Property 8: Form validation rule execution', () => {
  it('required rule fails for empty values and passes for non-empty values', () => {
    const emptyArb = fc.constantFrom(null, undefined, '', [])
    const nonEmptyArb = fc.oneof(
      fc.string({ minLength: 1, maxLength: 20 }),
      fc.integer(),
      fc.constant(true),
    )

    const rule: ValidationRule = { type: 'required', message: 'Field is required' }

    fc.assert(
      fc.property(emptyArb, (val) => {
        const errors = validateField(val, [rule])
        expect(errors.length).toBeGreaterThan(0)
      }),
      { numRuns: 100 },
    )

    fc.assert(
      fc.property(nonEmptyArb, (val) => {
        const errors = validateField(val, [rule])
        expect(errors.length).toBe(0)
      }),
      { numRuns: 100 },
    )
  })

  it('number rule validates min/max bounds correctly', () => {
    const numArb = fc.integer({ min: -1000, max: 1000 })
    const boundsArb = fc.tuple(
      fc.integer({ min: -500, max: 0 }),
      fc.integer({ min: 1, max: 500 }),
    )

    fc.assert(
      fc.property(numArb, boundsArb, (val, [min, max]) => {
        const rule: ValidationRule = { type: 'number', min, max, message: 'Out of range' }
        const errors = validateField(val, [rule])
        if (val >= min && val <= max) {
          expect(errors.length).toBe(0)
        } else {
          expect(errors.length).toBeGreaterThan(0)
        }
      }),
      { numRuns: 100 },
    )
  })

  it('email rule rejects strings without @ and domain', () => {
    const invalidEmailArb = fc.string({ minLength: 1, maxLength: 20 }).filter((s) => !s.includes('@') || !s.includes('.'))
    const rule: ValidationRule = { type: 'email', message: 'Invalid email' }

    fc.assert(
      fc.property(invalidEmailArb, (val) => {
        const errors = validateField(val, [rule])
        // Strings without @ or without . after @ should fail
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val)) {
          expect(errors.length).toBeGreaterThan(0)
        }
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 9: Cross-field validation operator correctness ────────────────
// Feature: function-unit-design-review, Property 9: Cross-field validation operator correctness
// **Validates: Requirements 6.2, 6.4, 6.6**

describe('Property 9: Cross-field validation operator correctness', () => {
  it('numeric cross-field operators produce correct results', () => {
    const numPairArb = fc.tuple(
      fc.integer({ min: -1000, max: 1000 }),
      fc.integer({ min: -1000, max: 1000 }),
    )
    const operatorArb = fc.constantFrom(
      'greater-than' as const,
      'less-than' as const,
      'equals' as const,
      'not-equals' as const,
    )

    fc.assert(
      fc.property(numPairArb, operatorArb, ([a, b], operator) => {
        const rule: CrossFieldRule = {
          fields: ['fieldA', 'fieldB'],
          operator,
          message: 'Validation failed',
          targetField: 'fieldB',
        }
        const formData = { fieldA: a, fieldB: b }
        const result = validateCrossFields([rule], formData)

        let expectedValid: boolean
        switch (operator) {
          case 'greater-than': expectedValid = a > b; break
          case 'less-than': expectedValid = a < b; break
           
          case 'equals': expectedValid = a == b; break
           
          case 'not-equals': expectedValid = a != b; break
        }

        expect(result.valid).toBe(expectedValid)
        if (!expectedValid) {
          expect(result.errors.length).toBe(1)
          expect(result.errors[0].targetField).toBe('fieldB')
        }
      }),
      { numRuns: 100 },
    )
  })

  it('skips validation when either field is empty', () => {
    const emptyValArb = fc.constantFrom(null, undefined, '')

    fc.assert(
      fc.property(emptyValArb, fc.integer(), (emptyVal, num) => {
        const rule: CrossFieldRule = {
          fields: ['a', 'b'],
          operator: 'greater-than',
          message: 'fail',
          targetField: 'b',
        }
        const formData = { a: emptyVal, b: num }
        const result = validateCrossFields([rule], formData)
        // Should skip validation and return valid
        expect(result.valid).toBe(true)
      }),
      { numRuns: 100 },
    )
  })
})

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
