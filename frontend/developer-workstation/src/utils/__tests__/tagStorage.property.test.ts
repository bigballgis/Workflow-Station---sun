import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import { getDisplayTags, matchesTags } from '../tagStorage'

/**
 * Property-Based Tests for Function Unit Grid Feature
 * Feature: function-unit-grid
 */
describe('Tag Storage Property Tests', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  /**
   * Property 2: Tag Display Limit
   * For any function unit with N tags where N > 3, the card SHALL display
   * exactly 3 tags plus a "+{N-3}" indicator.
   * **Validates: Requirements 3.1**
   */
  describe('Property 2: Tag Display Limit', () => {
    it('displays exactly maxDisplay tags when tags.length > maxDisplay', () => {
      fc.assert(
        fc.property(
          fc.array(fc.string({ minLength: 1, maxLength: 20 }), { minLength: 4, maxLength: 20 }),
          fc.integer({ min: 1, max: 10 }),
          (tags: string[], maxDisplay: number) => {
            const result = getDisplayTags(tags, maxDisplay)
            
            if (tags.length > maxDisplay) {
              // Should display exactly maxDisplay tags
              expect(result.displayTags.length).toBe(maxDisplay)
              // Extra count should be tags.length - maxDisplay
              expect(result.extraCount).toBe(tags.length - maxDisplay)
            } else {
              // Should display all tags
              expect(result.displayTags.length).toBe(tags.length)
              expect(result.extraCount).toBe(0)
            }
          }
        ),
        { numRuns: 100 }
      )
    })

    it('displayTags contains the first maxDisplay tags in order', () => {
      fc.assert(
        fc.property(
          fc.array(fc.string({ minLength: 1, maxLength: 20 }), { minLength: 1, maxLength: 20 }),
          fc.integer({ min: 1, max: 10 }),
          (tags: string[], maxDisplay: number) => {
            const result = getDisplayTags(tags, maxDisplay)
            const expectedTags = tags.slice(0, maxDisplay)
            
            expect(result.displayTags).toEqual(expectedTags)
          }
        ),
        { numRuns: 100 }
      )
    })

    it('extraCount is always non-negative', () => {
      fc.assert(
        fc.property(
          fc.array(fc.string(), { minLength: 0, maxLength: 20 }),
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

  /**
   * Property 3: Filter Results Correctness
   * For any filter criteria (name, status, tags), the filtered result SHALL
   * only contain function units that match ALL specified criteria.
   * **Validates: Requirements 4.4**
   */
  describe('Property 3: Filter Results Correctness', () => {
    it('matchesTags returns true when filterTags is empty', () => {
      fc.assert(
        fc.property(
          fc.array(fc.string(), { minLength: 0, maxLength: 10 }),
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
          fc.array(fc.string({ minLength: 1, maxLength: 10 }), { minLength: 1, maxLength: 10 }),
          fc.array(fc.string({ minLength: 1, maxLength: 10 }), { minLength: 1, maxLength: 5 }),
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
          fc.array(fc.string({ minLength: 1, maxLength: 10 }), { minLength: 2, maxLength: 10 }),
          (baseTags: string[]) => {
            // Take a subset of baseTags as filterTags
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
          fc.array(fc.string({ minLength: 1, maxLength: 10 }), { minLength: 0, maxLength: 5 }),
          fc.string({ minLength: 1, maxLength: 10 }),
          (itemTags: string[], missingTag: string) => {
            // Ensure missingTag is not in itemTags
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
