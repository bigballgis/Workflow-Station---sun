import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { evaluateCondition } from '../businessLogicEngine'
import type { ConditionExpression } from '../formRendererHelpers'
import { conditionOperatorArb } from './businessLogicEngine.property.shared'

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
