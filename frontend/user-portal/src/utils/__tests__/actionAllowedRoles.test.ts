import { describe, expect, it } from 'vitest'
import { isActionVisibleForSession } from '../actionAllowedRoles'

const restricted = JSON.stringify({ allowedRoles: ['ROLE_A', 'ROLE_B'] })

describe('isActionVisibleForSession', () => {
  it('shows the action when no roles are configured', () => {
    expect(isActionVisibleForSession('{}', ['ROLE_C'])).toBe(true)
    expect(isActionVisibleForSession(null, [])).toBe(true)
    expect(isActionVisibleForSession(JSON.stringify({ allowedRoles: [] }), ['ROLE_C'])).toBe(true)
    expect(isActionVisibleForSession(JSON.stringify({ url: '/api' }), ['ROLE_C'])).toBe(true)
    expect(isActionVisibleForSession({ requireComment: false }, ['ROLE_C'])).toBe(true)
    expect(isActionVisibleForSession({ allowedRoles: [] }, ['ROLE_C'])).toBe(true)
  })

  it('shows the action when the session holds one listed role', () => {
    expect(isActionVisibleForSession(restricted, ['ROLE_C', 'ROLE_B'])).toBe(true)
  })

  it('hides the action when the session matches none of the roles', () => {
    expect(isActionVisibleForSession(restricted, ['ROLE_C'])).toBe(false)
  })

  /** Platform access model D-05: a System Administrator's session roles are judged like anyone's. */
  it('hides a restricted action from a session without a listed role, System Administrator included', () => {
    expect(isActionVisibleForSession(restricted, [])).toBe(false)
    expect(isActionVisibleForSession(restricted, ['SYS_ADMIN'])).toBe(false)
  })

  it('shows legacy actions when configJson is not valid JSON', () => {
    expect(isActionVisibleForSession('{not-json', ['ROLE_A'])).toBe(true)
  })
})
