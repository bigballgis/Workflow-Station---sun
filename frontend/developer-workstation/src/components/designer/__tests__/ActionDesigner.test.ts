import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

// ─── Extracted logic for testability ────────────────────────────────────────
// These mirror the parseActionIds and removeActionFromAllNodes filtering logic
// from ActionDesigner.vue

/**
 * Parse action IDs — supports numeric and string ID formats.
 * Format: [12,22] or [action-dl-verify-docs,action-dl-approve-loan]
 */
function parseActionIds(value: string): Array<string | number> {
  if (!value) return []
  try {
    return JSON.parse(value) as number[]
  } catch {
    const cleaned = value.replace(/[\[\]\s]/g, '')
    if (!cleaned) return []
    return cleaned.split(',').map(s => s.trim()).filter(s => s)
  }
}

/**
 * Filter out a target action ID using string comparison (mixed ID support).
 */
function filterActionId(
  ids: Array<string | number>,
  actionId: string | number,
): Array<string | number> {
  return ids.filter(id => String(id) !== String(actionId))
}

// ─── Property 3: removeActionFromAllNodes mixed ID format handling ──────────
// **Validates: Requirements 4.1, 4.2**

describe('Property 3: removeActionFromAllNodes mixed ID format handling', () => {
  const numericIdArb = fc.integer({ min: 1, max: 10000 })
  const stringIdArb = fc.string({ minLength: 1, maxLength: 30 })
    .filter(s => /^[a-zA-Z][\w-]*$/.test(s))
  const mixedIdArb = fc.oneof(numericIdArb, stringIdArb)

  it('parseActionIds handles numeric JSON arrays', () => {
    const idsArb = fc.array(numericIdArb, { minLength: 0, maxLength: 10 })

    fc.assert(
      fc.property(idsArb, (ids) => {
        const serialized = JSON.stringify(ids)
        const parsed = parseActionIds(serialized)
        expect(parsed).toEqual(ids)
      }),
      { numRuns: 100 },
    )
  })

  it('parseActionIds handles string ID bracket format', () => {
    const idsArb = fc.array(stringIdArb, { minLength: 1, maxLength: 10 })

    fc.assert(
      fc.property(idsArb, (ids) => {
        const serialized = `[${ids.join(',')}]`
        const parsed = parseActionIds(serialized)
        expect(parsed).toEqual(ids)
      }),
      { numRuns: 100 },
    )
  })

  it('removal leaves no target ID in the result', () => {
    const idsArb = fc.array(mixedIdArb, { minLength: 1, maxLength: 20 })

    fc.assert(
      fc.property(idsArb, mixedIdArb, (ids, targetId) => {
        const result = filterActionId(ids, targetId)
        // After removal, no element should match the target via string comparison
        const hasTarget = result.some(id => String(id) === String(targetId))
        expect(hasTarget).toBe(false)
      }),
      { numRuns: 200 },
    )
  })

  it('removal preserves all other IDs unchanged', () => {
    const idsArb = fc.array(mixedIdArb, { minLength: 1, maxLength: 20 })

    fc.assert(
      fc.property(idsArb, mixedIdArb, (ids, targetId) => {
        const result = filterActionId(ids, targetId)
        const otherIds = ids.filter(id => String(id) !== String(targetId))
        expect(result).toEqual(otherIds)
      }),
      { numRuns: 200 },
    )
  })

  it('removal of non-existent ID returns original list unchanged', () => {
    const idsArb = fc.array(numericIdArb, { minLength: 1, maxLength: 10 })

    fc.assert(
      fc.property(idsArb, (ids) => {
        // Use an ID that's guaranteed not in the list
        const nonExistentId = 'non-existent-action-id'
        const result = filterActionId(ids, nonExistentId)
        expect(result).toEqual(ids)
      }),
      { numRuns: 100 },
    )
  })

  it('parseActionIds returns empty array for empty/invalid input', () => {
    fc.assert(
      fc.property(fc.constantFrom('', '[]', '  '), (input) => {
        const result = parseActionIds(input)
        expect(result).toEqual([])
      }),
      { numRuns: 10 },
    )
  })
})
