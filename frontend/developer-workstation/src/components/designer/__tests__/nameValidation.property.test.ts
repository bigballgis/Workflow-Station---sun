import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Feature: function-unit-design-review, Property 14: 表名/字段名格式校验
 * Validates: Requirements 13.5, 13.6
 *
 * For any string, the name validator should accept it if and only if it matches
 * the pattern ^[a-zA-Z][a-zA-Z0-9_]*$ (starts with a letter, followed by letters, digits, or underscores).
 */

const NAME_REGEX = /^[a-zA-Z][a-zA-Z0-9_]*$/

function validateName(name: string): boolean {
  return NAME_REGEX.test(name)
}

describe('Property 14: Table/Field Name Validation', () => {
  it('should accept valid names starting with a letter containing only [a-zA-Z0-9_]', () => {
    const validNameArb = fc.tuple(
      fc.char().filter(c => /[a-zA-Z]/.test(c)),
      fc.stringOf(fc.char().filter(c => /[a-zA-Z0-9_]/.test(c)))
    ).map(([first, rest]) => first + rest)

    fc.assert(
      fc.property(validNameArb, (name) => {
        expect(validateName(name)).toBe(true)
      }),
      { numRuns: 100 }
    )
  })

  it('should reject empty strings', () => {
    expect(validateName('')).toBe(false)
  })

  it('should reject strings starting with a digit', () => {
    const digitStartArb = fc.tuple(
      fc.char().filter(c => /[0-9]/.test(c)),
      fc.string()
    ).map(([d, rest]) => d + rest)

    fc.assert(
      fc.property(digitStartArb, (name) => {
        expect(validateName(name)).toBe(false)
      }),
      { numRuns: 100 }
    )
  })

  it('should reject strings containing special characters', () => {
    const specialCharArb = fc.tuple(
      fc.char().filter(c => /[a-zA-Z]/.test(c)),
      fc.stringOf(fc.char().filter(c => /[^a-zA-Z0-9_]/.test(c)), { minLength: 1 })
    ).map(([first, rest]) => first + rest)

    fc.assert(
      fc.property(specialCharArb, (name) => {
        expect(validateName(name)).toBe(false)
      }),
      { numRuns: 100 }
    )
  })

  it('should match regex for arbitrary strings', () => {
    fc.assert(
      fc.property(fc.string({ minLength: 0, maxLength: 50 }), (name) => {
        const expected = NAME_REGEX.test(name)
        expect(validateName(name)).toBe(expected)
      }),
      { numRuns: 100 }
    )
  })
})
