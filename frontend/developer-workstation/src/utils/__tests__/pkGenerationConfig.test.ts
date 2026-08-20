import { describe, expect, it } from 'vitest'
import {
  coerceCustomResetPeriod,
  customFormatAllowsDailyReset,
  formatCustomPkPreview,
  formatDailyDateSequencePreview,
  formatMonthlyDateSequencePreview,
  parsePkGeneration,
  pkGenerationNeedsExtraConfig,
  pkStrategyAllocatesString,
  serializePkGeneration,
  tryParseCustomFormat,
} from '@/utils/pkGenerationConfig'

describe('parsePkGeneration calendar sequences', () => {
  it('defaults padWidth to 4 for daily and monthly', () => {
    const daily = parsePkGeneration({ strategy: 'dailyDateSequence' })
    expect(daily.strategy).toBe('dailyDateSequence')
    expect(daily.padWidth).toBe(4)
    expect(daily.startValue).toBe(1)

    const monthly = parsePkGeneration({ strategy: 'monthlyDateSequence' })
    expect(monthly.strategy).toBe('monthlyDateSequence')
    expect(monthly.padWidth).toBe(4)
  })

  it('serializes daily as perDay and monthly as perMonth without a static prefix', () => {
    expect(serializePkGeneration(
      { strategy: 'dailyDateSequence', padWidth: 2, startValue: 1 },
      true,
    )).toEqual({
      strategy: 'dailyDateSequence',
      scope: 'perDay',
      startValue: 1,
      padWidth: 2,
    })
    expect(serializePkGeneration(
      { strategy: 'monthlyDateSequence', padWidth: 4, startValue: 1 },
      true,
    )).toEqual({
      strategy: 'monthlyDateSequence',
      scope: 'perMonth',
      startValue: 1,
      padWidth: 4,
    })
  })

  it('needs extra config and allocates a string', () => {
    expect(pkGenerationNeedsExtraConfig('dailyDateSequence')).toBe(true)
    expect(pkGenerationNeedsExtraConfig('monthlyDateSequence')).toBe(true)
    expect(pkGenerationNeedsExtraConfig('customFormat')).toBe(true)
    expect(pkStrategyAllocatesString('dailyDateSequence')).toBe(true)
    expect(pkStrategyAllocatesString('monthlyDateSequence')).toBe(true)
    expect(pkStrategyAllocatesString('datePrefixedSequence')).toBe(true)
    expect(pkStrategyAllocatesString('customFormat')).toBe(true)
  })
})

describe('customFormat', () => {
  it('serializes default screenshot template with global scope', () => {
    expect(serializePkGeneration({
      strategy: 'customFormat',
      format: '{DATETIME:yyyy-dd-MM}-{SEQNUM:4}',
      startValue: 1000,
    }, true)).toEqual({
      strategy: 'customFormat',
      scope: 'perTable',
      startValue: 1000,
      format: '{DATETIME:yyyy-dd-MM}-{SEQNUM:4}',
      resetPeriod: 'none',
    })
  })

  it('serializes daily reset as perDay', () => {
    expect(serializePkGeneration({
      strategy: 'customFormat',
      format: '{DATETIME:yyyy-MM-dd}-{SEQNUM:4}',
      startValue: 1000,
      resetPeriod: 'day',
    }, true)).toEqual({
      strategy: 'customFormat',
      scope: 'perDay',
      startValue: 1000,
      format: '{DATETIME:yyyy-MM-dd}-{SEQNUM:4}',
      resetPeriod: 'day',
    })
  })

  it('keeps stored daily reset when DATETIME has no day token; designer coerce is separate', () => {
    expect(customFormatAllowsDailyReset('{DATETIME:MM-MMM}-{SEQNUM:4}')).toBe(false)
    expect(coerceCustomResetPeriod('{DATETIME:MM-MMM}-{SEQNUM:4}', 'day')).toBe('none')
    const parsed = parsePkGeneration({
      strategy: 'customFormat',
      format: '{DATETIME:MM-MMM}-{SEQNUM:4}',
      resetPeriod: 'day',
    })
    expect(parsed.resetPeriod).toBe('day')
    expect(serializePkGeneration(parsed, true)).toMatchObject({
      resetPeriod: 'day',
      format: '{DATETIME:MM-MMM}-{SEQNUM:4}',
    })
  })

  it('previews year-day-month in Asia/Shanghai', () => {
    const june8 = new Date('2026-06-08T04:00:00Z')
    expect(formatCustomPkPreview('{DATETIME:yyyy-dd-MM}-{SEQNUM:4}', 1000, june8))
      .toBe('2026-08-06-1000')
  })

  it('rejects DATETIMEUTC and missing SEQNUM', () => {
    expect(tryParseCustomFormat('{DATETIMEUTC:yyyy-MM-dd}-{SEQNUM:4}')).toBeNull()
    expect(tryParseCustomFormat('{DATETIME:yyyy-MM-dd}')).toBeNull()
  })

  it('migrates legacy datePrefixedSequence to customFormat', () => {
    const parsed = parsePkGeneration({
      strategy: 'datePrefixedSequence',
      datePattern: 'yyyy-MM-dd',
      padWidth: 4,
      startValue: 1000,
      resetPeriod: 'day',
    })
    expect(parsed.strategy).toBe('customFormat')
    expect(parsed.format).toBe('{DATETIME:yyyy-MM-dd}-{SEQNUM:4}')
    expect(parsed.resetPeriod).toBe('day')
    expect(serializePkGeneration(parsed, true)).toEqual({
      strategy: 'customFormat',
      scope: 'perDay',
      startValue: 1000,
      format: '{DATETIME:yyyy-MM-dd}-{SEQNUM:4}',
      resetPeriod: 'day',
    })
  })

  it('preserves resetPeriod when migrating illegal MM-MMM daily datePrefixedSequence', () => {
    const parsed = parsePkGeneration({
      strategy: 'datePrefixedSequence',
      datePattern: 'MM-MMM',
      resetPeriod: 'day',
    })
    expect(parsed.strategy).toBe('customFormat')
    expect(parsed.format).toBe('{DATETIME:MM-MMM}-{SEQNUM:4}')
    expect(parsed.resetPeriod).toBe('day')
  })
})

describe('formatDailyDateSequencePreview', () => {
  it('formats YYYYMMDD plus padded start in Asia/Shanghai', () => {
    // 2026-07-14 23:30 Shanghai == 15:30 UTC
    const still14 = new Date('2026-07-14T15:30:00Z')
    expect(formatDailyDateSequencePreview(4, 1, still14)).toBe('202607140001')

    // 2026-07-15 00:30 Shanghai == 16:30 UTC
    const just15 = new Date('2026-07-14T16:30:00Z')
    expect(formatDailyDateSequencePreview(4, 1, just15)).toBe('202607150001')
  })

  it('uses padWidth as a minimum; overflow grows', () => {
    const noonUtc = new Date('2026-07-14T04:00:00Z')
    expect(formatDailyDateSequencePreview(2, 1, noonUtc)).toBe('2026071401')
    expect(formatDailyDateSequencePreview(2, 100, noonUtc)).toBe('20260714100')
  })
})

describe('formatMonthlyDateSequencePreview', () => {
  it('formats YYYYMM plus padded start in Asia/Shanghai', () => {
    const stillAugust = new Date('2026-08-31T15:30:00Z')
    expect(formatMonthlyDateSequencePreview(4, 1, stillAugust)).toBe('2026080001')

    const september = new Date('2026-08-31T16:30:00Z')
    expect(formatMonthlyDateSequencePreview(4, 1, september)).toBe('2026090001')
  })

  it('uses padWidth as a minimum; overflow grows', () => {
    const noonUtc = new Date('2026-08-17T04:00:00Z')
    expect(formatMonthlyDateSequencePreview(2, 1, noonUtc)).toBe('20260801')
    expect(formatMonthlyDateSequencePreview(2, 100, noonUtc)).toBe('202608100')
  })
})
