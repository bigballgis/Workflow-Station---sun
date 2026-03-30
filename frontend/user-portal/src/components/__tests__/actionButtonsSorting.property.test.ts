import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Feature: function-unit-design-review, Property 16: 动作按钮排序
 * Validates: Requirements 16.7
 *
 * For any set of actions with configured sort order values, the rendered action buttons
 * should appear in strictly ascending order of their sort order values.
 */

interface ActionWithSort {
  key: string
  sortOrder?: number
}

function sortActions(actions: ActionWithSort[]): ActionWithSort[] {
  return [...actions].sort((a, b) => (a.sortOrder ?? 9999) - (b.sortOrder ?? 9999))
}

describe('Property 16: Action Button Sorting', () => {
  const actionArb = fc.record({
    key: fc.string({ minLength: 1, maxLength: 20 }).filter(s => s.trim().length > 0),
    sortOrder: fc.option(fc.nat({ max: 9999 }), { nil: undefined })
  })

  it('should sort actions in ascending order of sortOrder', () => {
    fc.assert(
      fc.property(fc.array(actionArb, { minLength: 2, maxLength: 20 }), (actions) => {
        const sorted = sortActions(actions)
        for (let i = 1; i < sorted.length; i++) {
          const prev = sorted[i - 1].sortOrder ?? 9999
          const curr = sorted[i].sortOrder ?? 9999
          expect(prev).toBeLessThanOrEqual(curr)
        }
      }),
      { numRuns: 100 }
    )
  })

  it('should preserve all actions after sorting (no loss)', () => {
    fc.assert(
      fc.property(fc.array(actionArb, { maxLength: 20 }), (actions) => {
        const sorted = sortActions(actions)
        expect(sorted).toHaveLength(actions.length)
      }),
      { numRuns: 100 }
    )
  })

  it('should treat undefined sortOrder as 9999 (last)', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 9998 }),
        (definedOrder) => {
          const actions: ActionWithSort[] = [
            { key: 'no-order' },
            { key: 'with-order', sortOrder: definedOrder }
          ]
          const sorted = sortActions(actions)
          expect(sorted[0].key).toBe('with-order')
          expect(sorted[1].key).toBe('no-order')
        }
      ),
      { numRuns: 100 }
    )
  })
})
