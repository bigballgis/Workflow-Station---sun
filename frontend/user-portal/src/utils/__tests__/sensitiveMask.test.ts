import { describe, expect, it } from 'vitest'
import {
  applySensitiveMask,
  DEFAULT_SENSITIVE_MASK_CONFIG,
  formatMaskedDisplay,
  isInputTypeEligibleForMask,
  isSensitiveMaskActive,
  maskRangeToUiRow,
  normalizeSensitiveMaskConfig,
  shouldShowMaskedDisplay,
  uiRowToMaskRange,
  type SensitiveMaskConfig,
} from '../sensitiveMask'

const last4: SensitiveMaskConfig = {
  ...DEFAULT_SENSITIVE_MASK_CONFIG,
  enabled: true,
  preset: 'last4',
}

describe('sensitiveMask (portal)', () => {
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

  it('applySensitiveMask ends masks both ends and keeps middle', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ends',
      maskPrefix: 3,
      maskSuffix: 4,
    }
    expect(applySensitiveMask('6222021234567890', cfg)).toBe('***202123456****')
  })

  it('applySensitiveMask ends with custom mask lengths', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ends',
      maskPrefix: 2,
      maskSuffix: 2,
      maskChar: '#',
    }
    expect(applySensitiveMask('ABCDEFGH', cfg)).toBe('##CDEF##')
  })

  it('applySensitiveMask ends short string fully masks', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ends',
      maskPrefix: 3,
      maskSuffix: 4,
    }
    expect(applySensitiveMask('123456', cfg)).toBe('******')
  })

  it('applySensitiveMask ends with zero lengths shows plain', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ends',
      maskPrefix: 0,
      maskSuffix: 0,
    }
    expect(applySensitiveMask('ABCDEF', cfg)).toBe('ABCDEF')
  })

  it('applySensitiveMask ranges masks intervals with negative end index', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ranges',
      maskRanges: [
        { start: 0, end: 3 },
        { start: -4, end: null },
      ],
    }
    expect(applySensitiveMask('6222021234567890', cfg)).toBe('***202123456****')
  })

  it('applySensitiveMask ranges empty list shows plain', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ranges',
      maskRanges: [],
    }
    expect(applySensitiveMask('ABCDEF', cfg)).toBe('ABCDEF')
  })

  it('applySensitiveMask ranges merges overlapping intervals', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ranges',
      maskRanges: [
        { start: 1, end: 4 },
        { start: 3, end: 6 },
      ],
      maskChar: '#',
    }
    expect(applySensitiveMask('ABCDEFGH', cfg)).toBe('A#####GH')
  })

  it('applySensitiveMask ranges short string clamps indexes', () => {
    const cfg: SensitiveMaskConfig = {
      ...last4,
      preset: 'ranges',
      maskRanges: [
        { start: 0, end: 10 },
        { start: -2, end: null },
      ],
    }
    expect(applySensitiveMask('AB', cfg)).toBe('**')
  })

  it('uiRowToMaskRange / maskRangeToUiRow left and right', () => {
    expect(uiRowToMaskRange({ side: 'left', offset: 2, length: 3 })).toEqual({ start: 2, end: 5 })
    expect(uiRowToMaskRange({ side: 'right', offset: 0, length: 4 })).toEqual({ start: -4, end: null })
    expect(uiRowToMaskRange({ side: 'right', offset: 2, length: 3 })).toEqual({ start: -5, end: -2 })
    expect(maskRangeToUiRow({ start: 2, end: 5 })).toEqual({ side: 'left', offset: 2, length: 3 })
    expect(maskRangeToUiRow({ start: -4, end: null })).toEqual({ side: 'right', offset: 0, length: 4 })
    expect(maskRangeToUiRow({ start: -5, end: -2 })).toEqual({ side: 'right', offset: 2, length: 3 })
  })

  it('applySensitiveMask short string fully masks (rule A)', () => {
    expect(applySensitiveMask('123', last4)).toBe('***')
    expect(applySensitiveMask('1234', last4)).toBe('****')
  })

  it('applySensitiveMask counts spaces as characters (rule A)', () => {
    expect(applySensitiveMask('12 345678', last4)).toBe('*****5678')
  })

  it('normalizeSensitiveMaskConfig defaults and clamps', () => {
    expect(normalizeSensitiveMaskConfig(null)).toBeNull()
    expect(normalizeSensitiveMaskConfig({ enabled: true })).toEqual({
      enabled: true,
      preset: 'all',
      keepPrefix: 0,
      keepSuffix: 4,
      maskPrefix: 3,
      maskSuffix: 4,
      maskRanges: [
        { start: 0, end: 3 },
        { start: -4, end: null },
      ],
      maskChar: '*',
      revealPlainOnFocus: false,
    })
    expect(
      normalizeSensitiveMaskConfig({ enabled: true, preset: 'ranges', maskRanges: [] }),
    ).toMatchObject({
      preset: 'ranges',
      maskRanges: [],
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
