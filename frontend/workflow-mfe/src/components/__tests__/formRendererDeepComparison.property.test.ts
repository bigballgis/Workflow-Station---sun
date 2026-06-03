/**
 * Property 14: Deep comparison equivalence
 * **Validates: Requirements 40.1**
 *
 * Tests that lodash isEqual produces equivalent results to JSON.stringify
 * comparison for the types of objects used in FormRenderer (plain objects
 * with JSON-safe values — no undefined, NaN, Infinity, Date, etc.).
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { isEqual } from 'lodash-es'

// ─── Arbitrary for JSON-safe values (matching FormRenderer formData shape) ───

const jsonSafeValue: fc.Arbitrary<unknown> = fc.letrec(tie => ({
  value: fc.oneof(
    { depthSize: 'small' },
    fc.string(),
    fc.integer(),
    fc.double({ noNaN: true, noDefaultInfinity: true }),
    fc.boolean(),
    fc.constant(null),
    fc.array(tie('value'), { maxLength: 4 }),
    fc.dictionary(
      fc.string({ minLength: 1, maxLength: 8 }).filter(s => /^[a-zA-Z]\w*$/.test(s)),
      tie('value'),
      { maxKeys: 4 },
    ),
  ),
})).value

const jsonSafeObject: fc.Arbitrary<Record<string, unknown>> = fc.dictionary(
  fc.string({ minLength: 1, maxLength: 10 }).filter(s => /^[a-zA-Z]\w*$/.test(s)),
  jsonSafeValue,
  { minKeys: 0, maxKeys: 8 },
)

// ─── Property Tests ──────────────────────────────────────────────────────────

describe('Property 14: Deep comparison equivalence', () => {
  it('isEqual(a, b) matches JSON.stringify(a) === JSON.stringify(b) for JSON-safe objects', () => {
    fc.assert(
      fc.property(jsonSafeObject, jsonSafeObject, (a, b) => {
        const isEqualResult = isEqual(a, b)
        const jsonResult = JSON.stringify(a) === JSON.stringify(b)
        expect(isEqualResult).toBe(jsonResult)
      }),
      { numRuns: 200 },
    )
  })

  it('isEqual(a, a) is always true (reflexive)', () => {
    fc.assert(
      fc.property(jsonSafeObject, (a) => {
        expect(isEqual(a, a)).toBe(true)
      }),
      { numRuns: 100 },
    )
  })

  it('isEqual(a, clone(a)) is true for deep clones', () => {
    fc.assert(
      fc.property(jsonSafeObject, (a) => {
        const clone = JSON.parse(JSON.stringify(a))
        expect(isEqual(a, clone)).toBe(true)
      }),
      { numRuns: 100 },
    )
  })
})
