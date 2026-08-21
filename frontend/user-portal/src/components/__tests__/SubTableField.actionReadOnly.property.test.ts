import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * ACTION-type table bindings (FORM_POPUP 弹窗写入的记录表，如 "Meeting Remark") must render
 * read-only in the runtime SubTableField regardless of the editable/allow* props — see
 * SubTableField.vue's isActionBinding/canAdd/canEdit/canDelete. Mirrors the sibling spec test
 * in developer-workstation's subTablePermission.property.test.ts (same formula, same invariant).
 */
function canDo(bindingType: string | undefined, editable: boolean | undefined, flag: boolean | undefined): boolean {
  if (bindingType === 'ACTION') return false
  return editable === true && flag !== false
}

describe('SubTableField (user-portal) — ACTION binding forces read-only', () => {
  const boolOrUndef = fc.constantFrom<boolean | undefined>(true, false, undefined)

  it('ACTION binding ⇒ read-only regardless of editable/allow* values', () => {
    fc.assert(
      fc.property(boolOrUndef, boolOrUndef, boolOrUndef, boolOrUndef, (editable, add, edit, del) => {
        expect(canDo('ACTION', editable, add)).toBe(false)
        expect(canDo('ACTION', editable, edit)).toBe(false)
        expect(canDo('ACTION', editable, del)).toBe(false)
      }),
      { numRuns: 50 },
    )
  })

  it('SUB binding unaffected — same editable && (flag !== false) formula as before', () => {
    fc.assert(
      fc.property(fc.boolean(), boolOrUndef, (editable, flag) => {
        expect(canDo('SUB', editable, flag)).toBe(editable === true && flag !== false)
        expect(canDo(undefined, editable, flag)).toBe(editable === true && flag !== false)
      }),
      { numRuns: 50 },
    )
  })
})
