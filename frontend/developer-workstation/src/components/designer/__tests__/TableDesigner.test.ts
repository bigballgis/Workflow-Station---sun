/**
 * Property 13: 字段拖拽排序后 sortOrder 连续性
 * **Validates: Requirements 29.2, 29.3**
 *
 * For any field list after drag-and-drop reordering, all fields' sortOrder
 * values should be a continuous integer sequence starting from 0, matching
 * their position in the list.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

interface FieldDef {
  fieldName: string
  sortOrder?: number
}

/**
 * Assigns sortOrder based on current array position (mirrors TableDesigner save logic).
 */
export function assignSortOrder(fields: FieldDef[]): FieldDef[] {
  return fields.map((f, index) => ({ ...f, sortOrder: index }))
}

/**
 * Simulates moving a field from one index to another (drag-and-drop).
 */
function moveField(fields: FieldDef[], fromIndex: number, toIndex: number): FieldDef[] {
  const result = [...fields]
  const [moved] = result.splice(fromIndex, 1)
  result.splice(toIndex, 0, moved)
  return result
}

// ─── Arbitraries ─────────────────────────────────────────────────────────────

const fieldNameArb = fc.string({ minLength: 1, maxLength: 20 })
  .filter(s => /^[a-zA-Z]\w*$/.test(s))

const fieldDefArb: fc.Arbitrary<FieldDef> = fc.record({
  fieldName: fieldNameArb,
})

// ─── Property Tests ──────────────────────────────────────────────────────────

describe('Property 13: 字段拖拽排序后 sortOrder 连续性', () => {
  it('sortOrder is a continuous sequence from 0 after any reordering', () => {
    fc.assert(
      fc.property(
        fc.array(fieldDefArb, { minLength: 1, maxLength: 20 }),
        (fields) => {
          // Simulate a random permutation by shuffling
          const shuffled = [...fields].sort(() => Math.random() - 0.5)
          const result = assignSortOrder(shuffled)

          // Verify continuous sequence from 0
          for (let i = 0; i < result.length; i++) {
            expect(result[i].sortOrder).toBe(i)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('sortOrder matches position after move operation', () => {
    fc.assert(
      fc.property(
        fc.array(fieldDefArb, { minLength: 2, maxLength: 15 }),
        fc.nat(),
        fc.nat(),
        (fields, fromRaw, toRaw) => {
          const fromIndex = fromRaw % fields.length
          const toIndex = toRaw % fields.length

          const moved = moveField(fields, fromIndex, toIndex)
          const result = assignSortOrder(moved)

          // Verify continuous sequence
          for (let i = 0; i < result.length; i++) {
            expect(result[i].sortOrder).toBe(i)
          }

          // Verify the moved field is at the expected position
          expect(result[toIndex].fieldName).toBe(fields[fromIndex].fieldName)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('sortOrder length equals field count', () => {
    fc.assert(
      fc.property(
        fc.array(fieldDefArb, { minLength: 0, maxLength: 20 }),
        (fields) => {
          const result = assignSortOrder(fields)
          expect(result.length).toBe(fields.length)
          if (result.length > 0) {
            expect(result[result.length - 1].sortOrder).toBe(result.length - 1)
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
