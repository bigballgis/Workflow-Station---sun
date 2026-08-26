import { describe, expect, it } from 'vitest'
import { helpGuideAbsoluteUrl } from '../helpGuideUrl'

describe('helpGuideAbsoluteUrl', () => {
  it('uses same-origin /help path', () => {
    expect(helpGuideAbsoluteUrl('/computed-fields')).toBe(
      `${window.location.origin}/help/computed-fields`,
    )
    expect(helpGuideAbsoluteUrl('email-send#connection')).toBe(
      `${window.location.origin}/help/email-send#connection`,
    )
    expect(helpGuideAbsoluteUrl('/computed-fields#relation')).toBe(
      `${window.location.origin}/help/computed-fields#relation`,
    )
  })
})
