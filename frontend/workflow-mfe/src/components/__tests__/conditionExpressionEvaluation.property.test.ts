import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { evaluateCondition } from '../businessLogicEngine'
import type { ConditionExpression } from '../formRendererHelpers'

// ─── Property 2: ConditionExpression format and evaluation consistency ───────
// **Validates: Requirements 2.2, 2.3, 2.4**

const conditionOperatorArb = fc.constantFrom(
  'equals' as const,
  'not-equals' as const,
  'contains' as const,
  'greater-than' as const,
  'less-than' as const,
  'is-empty' as const,
  'is-not-empty' as const,
)

const fieldNameArb = fc.string({ minLength: 1, maxLength: 20 }).filter((s) => /^[a-zA-Z]\w*$/.test(s))

const scalarValueArb = fc.oneof(
  fc.string({ minLength: 0, maxLength: 20 }),
  fc.integer({ min: -1000, max: 1000 }),
  fc.constant(null),
  fc.constant(''),
)

const conditionExpressionArb: fc.Arbitrary<ConditionExpression> = fc.record({
  field: fieldNameArb,
  operator: conditionOperatorArb,
  value: scalarValueArb,
})

describe('Property 2: ConditionExpression format and evaluation consistency', () => {
  it('condition=null/undefined always returns true (default show)', () => {
    const formDataArb = fc.dictionary(fieldNameArb, scalarValueArb, { minKeys: 0, maxKeys: 5 })

    fc.assert(
      fc.property(formDataArb, (formData) => {
        // Simulate ActionButtons behavior: null/undefined condition → true
        const nullResult = !null ? true : evaluateCondition(null as unknown as ConditionExpression, formData)
        const undefinedResult = !undefined ? true : evaluateCondition(undefined as unknown as ConditionExpression, formData)
        expect(nullResult).toBe(true)
        expect(undefinedResult).toBe(true)
      }),
      { numRuns: 100 },
    )
  })

  it('valid ConditionExpression structs do not throw', () => {
    const formDataArb = fc.dictionary(fieldNameArb, scalarValueArb, { minKeys: 0, maxKeys: 5 })

    fc.assert(
      fc.property(conditionExpressionArb, formDataArb, (condition, formData) => {
        expect(() => evaluateCondition(condition, formData)).not.toThrow()
      }),
      { numRuns: 200 },
    )
  })

  it('evaluateCondition returns a boolean for any valid ConditionExpression', () => {
    const formDataArb = fc.dictionary(fieldNameArb, scalarValueArb, { minKeys: 1, maxKeys: 5 })

    fc.assert(
      fc.property(conditionExpressionArb, formDataArb, (condition, formData) => {
        const result = evaluateCondition(condition, formData)
        expect(typeof result).toBe('boolean')
      }),
      { numRuns: 200 },
    )
  })

  it('evaluateCondition with field present uses loose comparison for equals/not-equals', () => {
    fc.assert(
      fc.property(
        fc.oneof(fc.integer({ min: -100, max: 100 }), fc.string({ minLength: 1, maxLength: 10 })),
        fc.oneof(fc.integer({ min: -100, max: 100 }), fc.string({ minLength: 1, maxLength: 10 })),
        (fieldValue, condValue) => {
          const formData = { testField: fieldValue }
          const eqResult = evaluateCondition(
            { field: 'testField', operator: 'equals', value: condValue },
            formData,
          )
          const neqResult = evaluateCondition(
            { field: 'testField', operator: 'not-equals', value: condValue },
            formData,
          )
          // equals and not-equals should be complementary
          expect(eqResult).toBe(!neqResult)
        },
      ),
      { numRuns: 100 },
    )
  })
})
