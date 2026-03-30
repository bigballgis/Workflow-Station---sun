import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Feature: function-unit-design-review, Property 18: 关键字搜索过滤
 * Validates: Requirements 25.1
 *
 * For any keyword string and for any list of function units, the search filter
 * should return only items where the keyword appears (case-insensitive) in the
 * name, code, or description fields. The result set should be a subset of the
 * input list.
 */

interface FunctionUnitLike {
  name: string
  code: string
  description?: string
}

/**
 * Pure search filter function — mirrors the logic in index.vue's filteredFunctionUnits computed.
 */
function filterFunctionUnits(units: FunctionUnitLike[], keyword: string): FunctionUnitLike[] {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return units
  return units.filter(unit =>
    (unit.name?.toLowerCase().includes(kw)) ||
    (unit.code?.toLowerCase().includes(kw)) ||
    (unit.description?.toLowerCase().includes(kw))
  )
}

const arbFunctionUnit = fc.record({
  name: fc.string({ minLength: 0, maxLength: 30 }),
  code: fc.string({ minLength: 0, maxLength: 20 }),
  description: fc.option(fc.string({ minLength: 0, maxLength: 50 }), { nil: undefined }),
})

describe('Property 18: Keyword Search Filtering', () => {
  it('result is always a subset of the input list', () => {
    fc.assert(
      fc.property(
        fc.array(arbFunctionUnit, { minLength: 0, maxLength: 20 }),
        fc.string({ minLength: 0, maxLength: 15 }),
        (units, keyword) => {
          const result = filterFunctionUnits(units, keyword)
          for (const item of result) {
            expect(units).toContain(item)
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('empty keyword returns all items', () => {
    fc.assert(
      fc.property(
        fc.array(arbFunctionUnit, { minLength: 0, maxLength: 20 }),
        fc.constantFrom('', '  ', '\t'),
        (units, keyword) => {
          const result = filterFunctionUnits(units, keyword)
          expect(result).toEqual(units)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('every returned item contains the keyword in name, code, or description (case-insensitive)', () => {
    fc.assert(
      fc.property(
        fc.array(arbFunctionUnit, { minLength: 0, maxLength: 20 }),
        fc.string({ minLength: 1, maxLength: 10 }),
        (units, keyword) => {
          const kw = keyword.trim().toLowerCase()
          if (!kw) return // skip empty after trim
          const result = filterFunctionUnits(units, keyword)
          for (const item of result) {
            const matches =
              item.name.toLowerCase().includes(kw) ||
              item.code.toLowerCase().includes(kw) ||
              (item.description?.toLowerCase().includes(kw) ?? false)
            expect(matches).toBe(true)
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('items NOT in the result do NOT contain the keyword in any searchable field', () => {
    fc.assert(
      fc.property(
        fc.array(arbFunctionUnit, { minLength: 0, maxLength: 20 }),
        fc.string({ minLength: 1, maxLength: 10 }),
        (units, keyword) => {
          const kw = keyword.trim().toLowerCase()
          if (!kw) return
          const result = filterFunctionUnits(units, keyword)
          const excluded = units.filter(u => !result.includes(u))
          for (const item of excluded) {
            const matches =
              item.name.toLowerCase().includes(kw) ||
              item.code.toLowerCase().includes(kw) ||
              (item.description?.toLowerCase().includes(kw) ?? false)
            expect(matches).toBe(false)
          }
        }
      ),
      { numRuns: 100 }
    )
  })
})
