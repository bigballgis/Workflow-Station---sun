import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  evaluateFormula,
  containsDangerousKeyword,
  processLinkage,
  validateField,
  validateCrossFields,
} from '../businessLogicEngine'
import type {
  LinkageRule,
  CrossFieldRule,
  ValidationRule,
} from '../formRendererHelpers'

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
