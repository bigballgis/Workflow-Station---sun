import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { getByPath } from '../n8nAutoFillEngine'

/**
 * Feature: n8n-output-autofill-generalization, Property 1: Dot notation path resolution round-trip
 * Validates: Requirements 4.2, 7.1, 13.1, 13.2, 13.4
 *
 * For any nested object constructed by setting a value at a known dot-notation path,
 * calling getByPath(obj, path) should return the original value that was set.
 */
describe('Property 1: Dot notation path resolution round-trip', () => {
  // Arbitrary for generating a non-empty array of valid JS identifier keys
  const keyArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,9}$/)
  const pathArb = fc.array(keyArb, { minLength: 1, maxLength: 5 })
  const leafValueArb = fc.oneof(
    fc.string(),
    fc.integer(),
    fc.boolean(),
    fc.constant(0),
    fc.constant('')
  )

  it('should resolve a value set at a dot-notation path', () => {
    fc.assert(
      fc.property(pathArb, leafValueArb, (keys, value) => {
        // Build nested object from keys
        const obj: any = {}
        let current = obj
        for (let i = 0; i < keys.length - 1; i++) {
          current[keys[i]] = {}
          current = current[keys[i]]
        }
        current[keys[keys.length - 1]] = value

        const path = keys.join('.')
        const result = getByPath(obj, path)

        // For falsy-but-defined values (0, '', false), getByPath returns null due to ?? null
        // So we check: if value is nullish, result should be null; otherwise result === value
        if (value == null) {
          expect(result).toBeNull()
        } else if (value === 0 || value === '' || value === false) {
          // These are falsy — getByPath uses `current ?? null` so 0/false/'' become null
          // Actually let me re-check: `0 ?? null` is `0` (nullish coalescing only triggers on null/undefined)
          expect(result).toBe(value)
        } else {
          expect(result).toBe(value)
        }
      }),
      { numRuns: 100 }
    )
  })

  it('should return null for missing intermediate keys', () => {
    fc.assert(
      fc.property(pathArb, (keys) => {
        // Empty object — any path should return null
        const result = getByPath({}, keys.join('.'))
        expect(result).toBeNull()
      }),
      { numRuns: 100 }
    )
  })

  it('should return null when an intermediate node is not an object', () => {
    fc.assert(
      fc.property(
        keyArb,
        keyArb,
        fc.oneof(fc.string(), fc.integer(), fc.boolean()),
        (key1, key2, primitiveValue) => {
          const obj = { [key1]: primitiveValue }
          const result = getByPath(obj, `${key1}.${key2}`)
          expect(result).toBeNull()
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should return null for null/undefined objects', () => {
    expect(getByPath(null, 'a.b')).toBeNull()
    expect(getByPath(undefined, 'a.b')).toBeNull()
  })
})
