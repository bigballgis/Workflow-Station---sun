import { describe, expect, it } from 'vitest'
import {
  applySensitiveMask,
  DEFAULT_SENSITIVE_MASK_CONFIG,
  shouldShowMaskedDisplay,
  type SensitiveMaskConfig,
} from '../sensitiveMask'

const last4: SensitiveMaskConfig = {
  ...DEFAULT_SENSITIVE_MASK_CONFIG,
  enabled: true,
  preset: 'last4',
}

describe('sensitiveMask (portal)', () => {
  it('last4 and short-string full mask', () => {
    expect(applySensitiveMask('6222021234567890', last4)).toBe('************7890')
    expect(applySensitiveMask('12', last4)).toBe('**')
  })

  it('edit plain when reveal off; blur mask when reveal on', () => {
    expect(shouldShowMaskedDisplay(last4, { isReadonly: false, isFocused: false })).toBe(false)
    const reveal = { ...last4, revealPlainOnFocus: true }
    expect(shouldShowMaskedDisplay(reveal, { isReadonly: false, isFocused: false })).toBe(true)
  })
})
