import { describe, expect, it } from 'vitest'
import {
  COMPUTED_FIELD_GUIDE_PATH,
  computedFieldGuideAbsoluteUrl,
  helpGuideAbsoluteUrl,
} from '../computedFieldGuide'

describe('helpGuideAbsoluteUrl', () => {
  it('uses same-origin /help path, not the DW Vite base', () => {
    expect(COMPUTED_FIELD_GUIDE_PATH).toBe('/help/computed-fields')
    expect(computedFieldGuideAbsoluteUrl()).toBe(`${window.location.origin}/help/computed-fields`)
    expect(helpGuideAbsoluteUrl('/email-send#connection')).toBe(
      `${window.location.origin}/help/email-send#connection`,
    )
    expect(helpGuideAbsoluteUrl('email-monitor')).toBe(`${window.location.origin}/help/email-monitor`)
  })
})
