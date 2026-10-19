import { describe, expect, it } from 'vitest'
import { isActionVisibleForSession } from '../actionAllowedRoles'

const restricted = JSON.stringify({ allowedRoles: ['ROLE_A', 'ROLE_B'] })

describe('isActionVisibleForSession', () => {
  it('shows the action when no roles are configured', () => {
    expect(isActionVisibleForSession('{}', ['ROLE_C'], false)).toBe(true)
    expect(isActionVisibleForSession(null, [], false)).toBe(true)
    expect(isActionVisibleForSession(JSON.stringify({ allowedRoles: [] }), ['ROLE_C'], false)).toBe(true)
    expect(isActionVisibleForSession(JSON.stringify({ url: '/api' }), ['ROLE_C'], false)).toBe(true)
    expect(isActionVisibleForSession({ requireComment: false }, ['ROLE_C'], false)).toBe(true)
    expect(isActionVisibleForSession({ allowedRoles: [] }, ['ROLE_C'], false)).toBe(true)
  })

  it('shows the action when the session holds one listed role', () => {
    expect(isActionVisibleForSession(restricted, ['ROLE_C', 'ROLE_B'], false)).toBe(true)
  })

  it('hides the action when the session matches none of the roles', () => {
    expect(isActionVisibleForSession(restricted, ['ROLE_C'], false)).toBe(false)
  })

  it('shows every action to a system administrator', () => {
    expect(isActionVisibleForSession(restricted, [], true)).toBe(true)
  })

  it('shows legacy actions when configJson is not valid JSON', () => {
    expect(isActionVisibleForSession('{not-json', ['ROLE_A'], false)).toBe(true)
  })
})
