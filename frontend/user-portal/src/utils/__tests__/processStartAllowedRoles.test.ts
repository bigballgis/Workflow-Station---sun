import { describe, expect, it } from 'vitest'
import { startSubmitMode } from '../processStartAllowedRoles'

const submit = { actionType: 'PROCESS_SUBMIT' }
const approve = { actionType: 'APPROVE' }
const reject = { actionType: 'REJECT' }

describe('startSubmitMode', () => {
  it('offers the default Submit when no Action is bound', () => {
    expect(startSubmitMode([], [])).toBe('default')
  })

  /** Approve / Reject cannot start a request, so the first node still needs its Submit. */
  it('offers the default Submit next to bound Actions that cannot submit', () => {
    expect(startSubmitMode([approve, reject], [approve, reject])).toBe('default')
  })

  it('uses a bound submit Action instead of adding a second Submit', () => {
    expect(startSubmitMode([submit, reject], [submit, reject])).toBe('configured')
  })

  /** The default button must not bypass Allowed Roles on the submit Action. */
  it('blocks submitting when Allowed Roles hide every bound submit Action', () => {
    expect(startSubmitMode([submit, reject], [reject])).toBe('blocked')
    expect(startSubmitMode([submit], [])).toBe('blocked')
  })

  it('offers the default Submit when Allowed Roles hide only Actions that cannot submit', () => {
    expect(startSubmitMode([approve], [])).toBe('default')
  })
})
