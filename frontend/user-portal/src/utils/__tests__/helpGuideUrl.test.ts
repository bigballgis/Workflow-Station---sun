import { describe, expect, it } from 'vitest'
import { helpGuideAbsoluteUrl } from '../helpGuideUrl'

describe('helpGuideAbsoluteUrl', () => {
  it('prefixes /help and keeps origin', () => {
    expect(helpGuideAbsoluteUrl('/up-tasks-to-claim')).toBe(
      `${window.location.origin}/help/up-tasks-to-claim`,
    )
    expect(helpGuideAbsoluteUrl('up-tasks-to-claim#upgrade')).toBe(
      `${window.location.origin}/help/up-tasks-to-claim#upgrade`,
    )
    expect(helpGuideAbsoluteUrl('/up-tasks-to-claim#claim')).toBe(
      `${window.location.origin}/help/up-tasks-to-claim#claim`,
    )
  })
})
