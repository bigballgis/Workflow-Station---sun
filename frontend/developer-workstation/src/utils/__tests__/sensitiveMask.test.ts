import { describe, expect, it } from 'vitest'
import {
  applySensitiveMask,
  DEFAULT_SENSITIVE_MASK_CONFIG,
  formatMaskedDisplay,
  isInputTypeEligibleForMask,
  isSensitiveMaskActive,
  normalizeSensitiveMaskConfig,
  shouldShowMaskedDisplay,
  type SensitiveMaskConfig,
} from '../sensitiveMask'

const last4: SensitiveMaskConfig = {
  ...DEFAULT_SENSITIVE_MASK_CONFIG,
  enabled: true,
  preset: 'last4',
}

describe('sensitiveMask', () => {
  it('isInputTypeEligibleForMask rejects textarea and password', () => {
    expect(isInputTypeEligibleForMask(undefined)).toBe(true)
    expect(isInputTypeEligibleForMask('text')).toBe(true)
    expect(isInputTypeEligibleForMask('textarea')).toBe(false)
    expect(isInputTypeEligibleForMask('password')).toBe(false)
  })

  it('applySensitiveMask last4 keeps only suffix', () => {
    expect(applySensitiveMask('6222021234567890', last4)).toBe('************7890')
  })

  it('applySensitiveMask first4Last4', () => {
    const cfg: SensitiveMaskConfig = { ...last4, preset: 'first4Last4' }
    expect(applySensitiveMask('6222021234567890', cfg)).toBe('6222********7890')
  })

  it('applySensitiveMask first3Last4', () => {
    const cfg: SensitiveMaskConfig = { ...last4, preset: 'first3Last4' }
    expect(applySensitiveMask('13812345678', cfg)).toBe('138****5678')
  })

  it('applySensitiveMask all', () => {
    const cfg: SensitiveMaskConfig = { ...last4, preset: 'all' }
    expect(applySensitiveMask('123456', cfg)).toBe('******')
  })

  it('applySensitiveMask custom keep counts', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'custom',
      keepPrefix: 2,
      keepSuffix: 2,
      maskChar: '#',
    }
    expect(applySensitiveMask('ABCDEFGH', cfg)).toBe('AB####GH')
  })

  it('applySensitiveMask short string fully masks (rule A)', () => {
    expect(applySensitiveMask('123', last4)).toBe('***')
    expect(applySensitiveMask('1234', last4)).toBe('****')
  })

  it('applySensitiveMask counts spaces as characters (rule A)', () => {
    // "12 345678" length 9 → keep last 4 "5678", mask the other 5 chars (incl. space)
    expect(applySensitiveMask('12 345678', last4)).toBe('*****5678')
  })

  it('normalizeSensitiveMaskConfig defaults and clamps', () => {
    expect(normalizeSensitiveMaskConfig(null)).toBeNull()
    expect(normalizeSensitiveMaskConfig({ enabled: true })).toEqual({
      enabled: true,
      preset: 'last4',
      keepPrefix: 0,
      keepSuffix: 4,
      maskChar: '*',
      revealPlainOnFocus: false,
    })
  })

  it('shouldShowMaskedDisplay: readonly/list always; edit respects reveal switch', () => {
    expect(shouldShowMaskedDisplay(last4, { isReadonly: true, isFocused: false })).toBe(true)
    expect(shouldShowMaskedDisplay(last4, { isReadonly: false, isFocused: false, isListCell: true })).toBe(true)
    expect(shouldShowMaskedDisplay(last4, { isReadonly: false, isFocused: false })).toBe(false)
    const reveal = { ...last4, revealPlainOnFocus: true }
    expect(shouldShowMaskedDisplay(reveal, { isReadonly: false, isFocused: false })).toBe(true)
    expect(shouldShowMaskedDisplay(reveal, { isReadonly: false, isFocused: true })).toBe(false)
  })

  it('isSensitiveMaskActive false for password type even when enabled', () => {
    expect(isSensitiveMaskActive(last4, 'password')).toBe(false)
    expect(isSensitiveMaskActive(last4, undefined)).toBe(true)
  })

  it('formatMaskedDisplay never returns stars when inactive', () => {
    const off = { ...last4, enabled: false }
    expect(formatMaskedDisplay('6222021234567890', off, { isReadonly: true, isFocused: false }))
      .toBe('6222021234567890')
  })
})
