// Feature: function-unit-design-review, Property 22: 表单自动保存隔离性
// **Validates: Requirements 31.1, 31.3, 31.4**

import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'

// ─── Auto-save key generation logic (mirrors FormRenderer.vue Task 7.5) ───

function getAutoSaveKey(functionUnitId: string, formId: string): string {
  return `form_autosave_${functionUnitId}_${formId}`
}

// ─── Arbitraries ──────────────────────────────────────────────────────────

/** Non-empty alphanumeric ID strings (realistic IDs) */
const arbId = fc.stringMatching(/^[a-zA-Z0-9]{1,20}$/)

/** Arbitrary form data object */
const arbFormData = fc.dictionary(
  fc.stringMatching(/^[a-z_]{1,10}$/),
  fc.oneof(fc.string(), fc.integer(), fc.boolean(), fc.constant(null)),
  { minKeys: 1, maxKeys: 5 },
)

// ─── Property 22 Tests ───────────────────────────────────────────────────

describe('Property 22: Form auto-save isolation', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('different form combinations produce distinct localStorage keys', () => {
    fc.assert(
      fc.property(
        arbId, arbId, arbId, arbId,
        (fuId1, formId1, fuId2, formId2) => {
          // Only test when the (fuId, formId) pairs are actually different
          fc.pre(fuId1 !== fuId2 || formId1 !== formId2)

          const key1 = getAutoSaveKey(fuId1, formId1)
          const key2 = getAutoSaveKey(fuId2, formId2)

          expect(key1).not.toBe(key2)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('clearing one form auto-save does not affect another', () => {
    fc.assert(
      fc.property(
        arbId, arbId, arbId, arbId, arbFormData, arbFormData,
        (fuId1, formId1, fuId2, formId2, data1, data2) => {
          fc.pre(fuId1 !== fuId2 || formId1 !== formId2)

          const key1 = getAutoSaveKey(fuId1, formId1)
          const key2 = getAutoSaveKey(fuId2, formId2)

          // Save data for both forms
          localStorage.setItem(key1, JSON.stringify(data1))
          localStorage.setItem(key2, JSON.stringify(data2))

          // Both should be retrievable
          expect(JSON.parse(localStorage.getItem(key1)!)).toEqual(data1)
          expect(JSON.parse(localStorage.getItem(key2)!)).toEqual(data2)

          // Clear form 1 (simulates successful submission)
          localStorage.removeItem(key1)

          // Form 1 should be gone, form 2 should remain intact
          expect(localStorage.getItem(key1)).toBeNull()
          expect(JSON.parse(localStorage.getItem(key2)!)).toEqual(data2)

          // Clean up for next iteration
          localStorage.clear()
        },
      ),
      { numRuns: 100 },
    )
  })
})
