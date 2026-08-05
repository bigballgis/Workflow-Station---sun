import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { resolvePortalEntitlementMessage } from '../portalEntitlementMessage'
import {
  consumeSsoLoginErrorMessage,
  setSsoLoginErrorMessage
} from '../sso'

describe('resolvePortalEntitlementMessage', () => {
  const fallback = 'friendly fallback'

  it('uses fallback when server message is empty', () => {
    expect(resolvePortalEntitlementMessage('', fallback)).toBe(fallback)
    expect(resolvePortalEntitlementMessage(null, fallback)).toBe(fallback)
    expect(resolvePortalEntitlementMessage(undefined, fallback)).toBe(fallback)
  })

  it('uses fallback when server leaked an i18n key', () => {
    expect(resolvePortalEntitlementMessage('auth.portal_entitlement_denied', fallback)).toBe(
      fallback
    )
    expect(resolvePortalEntitlementMessage('auth.login_failed', fallback)).toBe(fallback)
  })

  it('keeps human-readable server text', () => {
    const msg = '暂时无法登录用户门户：您的账号尚未加入任何访问组。'
    expect(resolvePortalEntitlementMessage(msg, fallback)).toBe(msg)
  })
})

describe('sso login error handoff', () => {
  const store = new Map<string, string>()

  beforeEach(() => {
    store.clear()
    vi.stubGlobal('sessionStorage', {
      getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
      setItem: (k: string, v: string) => {
        store.set(k, v)
      },
      removeItem: (k: string) => {
        store.delete(k)
      }
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('persists and consumes the message once', () => {
    setSsoLoginErrorMessage('  no access group  ')
    expect(consumeSsoLoginErrorMessage()).toBe('no access group')
    expect(consumeSsoLoginErrorMessage()).toBeNull()
  })

  it('ignores blank messages', () => {
    setSsoLoginErrorMessage('   ')
    expect(consumeSsoLoginErrorMessage()).toBeNull()
  })
})
