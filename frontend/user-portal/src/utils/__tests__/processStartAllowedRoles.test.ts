import { describe, expect, it } from 'vitest'
import { isStartSubmitBlockedByAllowedRoles } from '../processStartAllowedRoles'

describe('isStartSubmitBlockedByAllowedRoles', () => {
  it('blocks default submit when BPMN had actions but none visible', () => {
    expect(isStartSubmitBlockedByAllowedRoles(true, 3, 0)).toBe(true)
  })

  it('does not block when no BPMN action ids', () => {
    expect(isStartSubmitBlockedByAllowedRoles(false, 0, 0)).toBe(false)
  })

  it('does not block when some actions remain visible', () => {
    expect(isStartSubmitBlockedByAllowedRoles(true, 3, 1)).toBe(false)
  })

  it('does not block when lookup returned nothing (legacy default submit path)', () => {
    expect(isStartSubmitBlockedByAllowedRoles(true, 0, 0)).toBe(false)
  })
})
