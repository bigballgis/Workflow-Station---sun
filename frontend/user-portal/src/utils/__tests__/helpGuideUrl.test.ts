import { describe, expect, it } from 'vitest'
import { helpGuideAbsoluteUrl } from '../helpGuideUrl'

describe('helpGuideAbsoluteUrl', () => {
  it('prefixes /help and keeps origin, hash, and path', () => {
    expect(helpGuideAbsoluteUrl('/up-tasks-to-claim')).toBe(
      `${window.location.origin}/help/up-tasks-to-claim`,
    )
    expect(helpGuideAbsoluteUrl('up-tasks-to-claim#upgrade')).toBe(
      `${window.location.origin}/help/up-tasks-to-claim#upgrade`,
    )
    expect(helpGuideAbsoluteUrl('/up-tasks-to-claim#claim')).toBe(
      `${window.location.origin}/help/up-tasks-to-claim#claim`,
    )
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
