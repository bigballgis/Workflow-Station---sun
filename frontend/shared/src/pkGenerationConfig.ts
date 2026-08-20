/**
 * PK generation strategy config parse/serialize
 * (manual / uuid / autoIncrement / prefixedSequence / dailyDateSequence / monthlyDateSequence
 *  / customFormat). Legacy datePrefixedSequence is migrated to customFormat on parse.
 *
 * CANONICAL copy — consumed by user-portal (parse only), developer-workstation and
 * admin-center (parse + serialize) via the @platform-shared vite alias. The apps keep
 * thin re-export shims at src/utils/pkGenerationConfig.ts so import paths stay stable.
 */
import {
  CUSTOM_FORMAT_DEFAULT,
  CUSTOM_FORMAT_DEFAULT_SEQ_PAD,
  CUSTOM_FORMAT_STRATEGY,
  LEGACY_DATE_PREFIXED_STRATEGY,
  formatCustomPkPreview,
  migrateLegacyDatePrefixedFormat,
} from './pkCustomFormat'
import { parseResetPeriod, resetPeriodScope, type PkResetPeriod } from './pkDatetimeFormat'

export type PkGenerationStrategy =
  | 'manual'
  | 'uuid'
  | 'autoIncrement'
  | 'prefixedSequence'
  | 'dailyDateSequence'
  | 'monthlyDateSequence'
  | 'datePrefixedSequence'
  | 'customFormat'

export type CalendarDateSequencePeriod = 'day' | 'month'

export type { PkResetPeriod }

export interface PkGenerationConfig {
  strategy?: PkGenerationStrategy
  startValue?: number
  padWidth?: number
  prefix?: string
  datePattern?: string
  resetPeriod?: PkResetPeriod
  format?: string
}

export const PK_GENERATION_STRATEGIES: PkGenerationStrategy[] = [
  'uuid',
  'manual',
  'autoIncrement',
  'prefixedSequence',
  'dailyDateSequence',
  'monthlyDateSequence',
  CUSTOM_FORMAT_STRATEGY,
]

/** Minimum sequence digits for calendar-period strategies (overflow grows beyond this). */
export const DAILY_DATE_SEQUENCE_DEFAULT_PAD = 4

export {
  CUSTOM_FORMAT_DEFAULT,
  CUSTOM_FORMAT_SNIPPETS,
  CUSTOM_FORMAT_STRATEGY,
  LEGACY_DATE_PREFIXED_STRATEGY,
  coerceCustomResetPeriod,
  customFormatAllowsDailyReset,
  customFormatAllowsMonthlyReset,
  formatCustomPkPreview,
  tryParseCustomFormat,
} from './pkCustomFormat'

export { formatJavaDatePattern, parseResetPeriod, resetPeriodScope } from './pkDatetimeFormat'

export function isCalendarDateSequence(strategy?: PkGenerationStrategy | string): boolean {
  return strategy === 'dailyDateSequence' || strategy === 'monthlyDateSequence'
}

export function isCustomFormat(strategy?: PkGenerationStrategy | string): boolean {
  return strategy === CUSTOM_FORMAT_STRATEGY
}

export function isLegacyDatePrefixed(strategy?: PkGenerationStrategy | string): boolean {
  return strategy === LEGACY_DATE_PREFIXED_STRATEGY
}

export function parsePkGeneration(
  raw?: Record<string, unknown> | PkGenerationConfig | null,
): PkGenerationConfig {
  if (!raw || typeof raw !== 'object') {
    return { strategy: 'uuid' }
  }
  if (isLegacyDatePrefixed(raw.strategy as string)) {
    return parseMigratedCustom(raw)
  }
  const strategy = raw.strategy as PkGenerationStrategy | undefined
  const resolved = PK_GENERATION_STRATEGIES.includes(strategy as PkGenerationStrategy)
    ? strategy
    : 'uuid'
  const format = typeof raw.format === 'string' ? raw.format : defaultCustomFormat(resolved)
  const resetPeriod = parseResetPeriod((raw as PkGenerationConfig).resetPeriod)
  return {
    strategy: resolved,
    startValue: typeof raw.startValue === 'number' ? raw.startValue : 1,
    padWidth: typeof raw.padWidth === 'number' ? raw.padWidth : defaultPadWidth(resolved),
    prefix: typeof raw.prefix === 'string' ? raw.prefix : '',
    datePattern: '',
    resetPeriod,
    format,
  }
}

function parseMigratedCustom(
  raw: Record<string, unknown> | PkGenerationConfig,
): PkGenerationConfig {
  const padWidth = typeof raw.padWidth === 'number' ? raw.padWidth : CUSTOM_FORMAT_DEFAULT_SEQ_PAD
  const format = migrateLegacyDatePrefixedFormat(
    typeof raw.datePattern === 'string' ? raw.datePattern : undefined,
    padWidth,
  )
  return parsePkGeneration({
    strategy: CUSTOM_FORMAT_STRATEGY,
    startValue: raw.startValue,
    format,
    resetPeriod: (raw as PkGenerationConfig).resetPeriod,
  })
}

function defaultPadWidth(strategy?: PkGenerationStrategy): number {
  if (isCalendarDateSequence(strategy)) {
    return DAILY_DATE_SEQUENCE_DEFAULT_PAD
  }
  return 6
}

function defaultCustomFormat(strategy?: PkGenerationStrategy): string {
  return isCustomFormat(strategy) ? CUSTOM_FORMAT_DEFAULT : ''
}

function calendarScope(strategy?: PkGenerationStrategy): 'perDay' | 'perMonth' | 'perTable' {
  if (strategy === 'dailyDateSequence') return 'perDay'
  if (strategy === 'monthlyDateSequence') return 'perMonth'
  return 'perTable'
}

export function serializePkGeneration(
  config?: PkGenerationConfig | Record<string, unknown> | null,
  isPrimaryKey?: boolean,
): Record<string, unknown> | undefined {
  if (!isPrimaryKey) return undefined
  const parsed = parsePkGeneration(config)
  if (parsed.strategy === 'manual') {
    return { strategy: 'manual' }
  }
  if (parsed.strategy === 'uuid') {
    return { strategy: 'uuid' }
  }
  if (parsed.strategy === CUSTOM_FORMAT_STRATEGY) {
    return serializeCustomFormat(parsed)
  }
  const out: Record<string, unknown> = {
    strategy: parsed.strategy,
    scope: calendarScope(parsed.strategy),
    startValue: parsed.startValue ?? 1,
  }
  if (parsed.strategy === 'prefixedSequence') {
    out.padWidth = parsed.padWidth ?? 6
    out.prefix = parsed.prefix ?? ''
  }
  if (isCalendarDateSequence(parsed.strategy)) {
    out.padWidth = parsed.padWidth ?? DAILY_DATE_SEQUENCE_DEFAULT_PAD
  }
  return out
}

function serializeCustomFormat(parsed: PkGenerationConfig): Record<string, unknown> {
  const resetPeriod = parsed.resetPeriod ?? 'none'
  return {
    strategy: CUSTOM_FORMAT_STRATEGY,
    scope: resetPeriodScope(resetPeriod),
    startValue: parsed.startValue ?? 1,
    format: parsed.format || CUSTOM_FORMAT_DEFAULT,
    resetPeriod,
  }
}

export function pkGenerationNeedsExtraConfig(strategy?: PkGenerationStrategy): boolean {
  return strategy === 'autoIncrement'
    || strategy === 'prefixedSequence'
    || isCalendarDateSequence(strategy)
    || isCustomFormat(strategy)
    || isLegacyDatePrefixed(strategy)
}

/** uuid / prefixedSequence / calendar-period sequences allocate strings — inputNumber cannot bind them. */
export function pkStrategyAllocatesString(strategy?: PkGenerationStrategy | string): boolean {
  return strategy === 'uuid'
    || strategy === 'prefixedSequence'
    || isCalendarDateSequence(strategy)
    || isCustomFormat(strategy)
    || isLegacyDatePrefixed(strategy)
}

/**
 * Designer preview for calendar-period PK strategies.
 * Day/month boundary is Asia/Shanghai (UTC+8), matching table-design audit fields ({@code created_at}).
 */
export function formatCalendarDateSequencePreview(
  period: CalendarDateSequencePeriod,
  padWidth = DAILY_DATE_SEQUENCE_DEFAULT_PAD,
  startValue = 1,
  now = new Date(),
): string {
  const utc8 = new Date(now.getTime() + (now.getTimezoneOffset() + 480) * 60000)
  const pad2 = (n: number) => String(n).padStart(2, '0')
  const ymd = period === 'month'
    ? `${utc8.getFullYear()}${pad2(utc8.getMonth() + 1)}`
    : `${utc8.getFullYear()}${pad2(utc8.getMonth() + 1)}${pad2(utc8.getDate())}`
  const width = padWidth > 0 ? padWidth : DAILY_DATE_SEQUENCE_DEFAULT_PAD
  return `${ymd}${String(startValue).padStart(width, '0')}`
}

export function formatDailyDateSequencePreview(
  padWidth = DAILY_DATE_SEQUENCE_DEFAULT_PAD,
  startValue = 1,
  now = new Date(),
): string {
  return formatCalendarDateSequencePreview('day', padWidth, startValue, now)
}

export function formatMonthlyDateSequencePreview(
  padWidth = DAILY_DATE_SEQUENCE_DEFAULT_PAD,
  startValue = 1,
  now = new Date(),
): string {
  return formatCalendarDateSequencePreview('month', padWidth, startValue, now)
}

export function formatPkGenerationPreview(
  config: PkGenerationConfig,
  now = new Date(),
): string {
  if (isCalendarDateSequence(config.strategy)) {
    return formatCalendarDateSequencePreview(
      config.strategy === 'monthlyDateSequence' ? 'month' : 'day',
      config.padWidth,
      config.startValue,
      now,
    )
  }
  if (isCustomFormat(config.strategy) || isLegacyDatePrefixed(config.strategy)) {
    const format = isLegacyDatePrefixed(config.strategy)
      ? migrateLegacyDatePrefixedFormat(config.datePattern, config.padWidth)
      : config.format
    return formatCustomPkPreview(format, config.startValue, now)
  }
  if (config.strategy !== 'prefixedSequence') return ''
  const prefix = config.prefix ?? ''
  const pad = config.padWidth ?? 6
  const start = config.startValue ?? 1
  return `${prefix}${String(start).padStart(pad, '0')}`
}
