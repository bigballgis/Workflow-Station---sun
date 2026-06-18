import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import { getDisplayTags, matchesTags, normalizeTags } from '../tagStorage'

/**
 * A string arbitrary that produces non-blank strings after trim.
 * The normalizeTags function strips blank entries, so tests must use non-blank values.
 */
const nonBlankString = (minLength = 1, maxLength = 20) =>
  fc.string({ minLength, maxLength }).filter(s => s.trim().length > 0)

describe('Tag Storage Property Tests', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('Property 2: Tag Display Limit', () => {
    it('displays exactly maxDisplay normalized tags when normalized.length > maxDisplay', () => {
      fc.assert(
        fc.property(
          fc.array(nonBlankString(1, 20), { minLength: 4, maxLength: 20 }),
          fc.integer({ min: 1, max: 10 }),
          (tags: string[], maxDisplay: number) => {
            const result = getDisplayTags(tags, maxDisplay)
            const normalizedCount = normalizeTags(tags).length

            if (normalizedCount > maxDisplay) {
              expect(result.displayTags.length).toBe(maxDisplay)
              expect(result.extraCount).toBe(normalizedCount - maxDisplay)
            } else {
              expect(result.displayTags.length).toBe(normalizedCount)
              expect(result.extraCount).toBe(0)
            }
          }
        ),
        { numRuns: 100 }
      )
    })

    it('displayTags contains the first maxDisplay tags in order (after normalization)', () => {
      fc.assert(
        fc.property(
          fc.array(nonBlankString(1, 20), { minLength: 1, maxLength: 20 }),
          fc.integer({ min: 1, max: 10 }),
          (tags: string[], maxDisplay: number) => {
            const result = getDisplayTags(tags, maxDisplay)
            // getDisplayTags normalizes (trim+dedup) then slices; compare against normalized input
            const expectedTags = normalizeTags(tags).slice(0, maxDisplay)
            expect(result.displayTags).toEqual(expectedTags)
          }
        ),
        { numRuns: 100 }
      )
    })

    it('extraCount is always non-negative', () => {
      fc.assert(
        fc.property(
          fc.array(nonBlankString(1, 20), { minLength: 0, maxLength: 20 }),
          fc.integer({ min: 1, max: 10 }),
          (tags: string[], maxDisplay: number) => {
            const result = getDisplayTags(tags, maxDisplay)
            expect(result.extraCount).toBeGreaterThanOrEqual(0)
          }
        ),
        { numRuns: 100 }
      )
    })
  })

  describe('Property 3: Filter Results Correctness', () => {
    it('matchesTags returns true when filterTags is empty', () => {
      fc.assert(
        fc.property(
          fc.array(nonBlankString(1, 10), { minLength: 0, maxLength: 10 }),
          (itemTags: string[]) => {
            expect(matchesTags(itemTags, [])).toBe(true)
          }
        ),
        { numRuns: 100 }
      )
    })

    it('matchesTags returns true only when item has ALL filter tags', () => {
      fc.assert(
        fc.property(
          fc.array(nonBlankString(1, 10), { minLength: 1, maxLength: 10 }),
          fc.array(nonBlankString(1, 10), { minLength: 1, maxLength: 5 }),
          (itemTags: string[], filterTags: string[]) => {
            const result = matchesTags(itemTags, filterTags)
            const expected = filterTags.every(tag => itemTags.includes(tag))
            expect(result).toBe(expected)
          }
        ),
        { numRuns: 100 }
      )
    })

    it('matchesTags returns true when itemTags is superset of filterTags', () => {
      fc.assert(
        fc.property(
          fc.array(nonBlankString(1, 10), { minLength: 2, maxLength: 10 }),
          (baseTags: string[]) => {
            const filterTags = baseTags.slice(0, Math.ceil(baseTags.length / 2))
            expect(matchesTags(baseTags, filterTags)).toBe(true)
          }
        ),
        { numRuns: 100 }
      )
    })

    it('matchesTags returns false when item is missing any filter tag', () => {
      fc.assert(
        fc.property(
          fc.array(nonBlankString(1, 10), { minLength: 0, maxLength: 5 }),
          nonBlankString(1, 10),
          (itemTags: string[], missingTag: string) => {
            const cleanItemTags = itemTags.filter(t => t !== missingTag)
            const filterTags = [...cleanItemTags.slice(0, 2), missingTag]
            if (filterTags.length > 0 && !cleanItemTags.includes(missingTag)) {
              expect(matchesTags(cleanItemTags, filterTags)).toBe(false)
            }
          }
        ),
        { numRuns: 100 }
      )
    })
  })
})
