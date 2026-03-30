/**
 * Property 15: Field array change detection
 * **Validates: Requirements 41.1**
 *
 * Tests that the simple array length + key comparison correctly detects
 * when a field array has changed, matching the behavior of the previous
 * JSON.stringify approach for field key arrays.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

// ─── Pure detection function (mirrors FormRenderer allFields watcher logic) ──

interface MinimalField {
  key: string
}

function hasFieldArrayChanged(
  newFields: MinimalField[],
  oldFields: MinimalField[],
): boolean {
  return (
    newFields.length !== oldFields.length ||
    newFields.some((f, i) => f.key !== oldFields[i]?.key)
  )
}

// ─── Reference implementation using JSON.stringify (the old approach) ─────────

function hasFieldArrayChangedReference(
  newFields: MinimalField[],
  oldFields: MinimalField[],
): boolean {
  return (
    JSON.stringify(newFields.map(f => f.key)) !==
    JSON.stringify(oldFields.map(f => f.key))
  )
}

// ─── Arbitraries ─────────────────────────────────────────────────────────────

const fieldKeyArb = fc.string({ minLength: 1, maxLength: 15 }).filter(s => /^[a-zA-Z]\w*$/.test(s))

const fieldArb: fc.Arbitrary<MinimalField> = fc.record({
  key: fieldKeyArb,
})

const fieldArrayArb = fc.array(fieldArb, { minLength: 0, maxLength: 20 })

// ─── Property Tests ──────────────────────────────────────────────────────────

describe('Property 15: Field array change detection', () => {
  it('detects change equivalently to JSON.stringify for any two field arrays', () => {
    fc.assert(
      fc.property(fieldArrayArb, fieldArrayArb, (newFields, oldFields) => {
        const optimized = hasFieldArrayChanged(newFields, oldFields)
        const reference = hasFieldArrayChangedReference(newFields, oldFields)
        expect(optimized).toBe(reference)
      }),
      { numRuns: 200 },
    )
  })

  it('returns false when both arrays are identical', () => {
    fc.assert(
      fc.property(fieldArrayArb, (fields) => {
        expect(hasFieldArrayChanged(fields, fields)).toBe(false)
      }),
      { numRuns: 100 },
    )
  })

  it('returns false for deep clones of the same array', () => {
    fc.assert(
      fc.property(fieldArrayArb, (fields) => {
        const clone = fields.map(f => ({ key: f.key }))
        expect(hasFieldArrayChanged(fields, clone)).toBe(false)
      }),
      { numRuns: 100 },
    )
  })

  it('returns true when lengths differ', () => {
    fc.assert(
      fc.property(
        fieldArrayArb.filter(a => a.length > 0),
        (fields) => {
          const shorter = fields.slice(0, -1)
          expect(hasFieldArrayChanged(fields, shorter)).toBe(true)
          expect(hasFieldArrayChanged(shorter, fields)).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('returns true when a key at any position differs', () => {
    fc.assert(
      fc.property(
        fieldArrayArb.filter(a => a.length >= 1),
        fc.nat(),
        fieldKeyArb,
        (fields, rawIdx, newKey) => {
          const idx = rawIdx % fields.length
          // Only test when the new key is actually different
          if (newKey === fields[idx].key) return
          const modified = fields.map((f, i) =>
            i === idx ? { key: newKey } : { key: f.key },
          )
          expect(hasFieldArrayChanged(modified, fields)).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })
})
