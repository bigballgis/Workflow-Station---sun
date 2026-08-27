import { describe, expect, it } from 'vitest'
import { helpGuideAbsoluteUrl } from '../helpGuideUrl'

describe('helpGuideAbsoluteUrl', () => {
  it('uses same-origin /help path', () => {
    expect(helpGuideAbsoluteUrl('/task-delegate')).toBe(
      `${window.location.origin}/help/task-delegate`,
    )
    expect(helpGuideAbsoluteUrl('task-delegate')).toBe(
      `${window.location.origin}/help/task-delegate`,
    )
    expect(helpGuideAbsoluteUrl('/email-send#connection')).toBe(
      `${window.location.origin}/help/email-send#connection`,
    )
  })
})
