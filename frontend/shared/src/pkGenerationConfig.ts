/**
 * PK generation strategy config parse/serialize
 * (manual / uuid / autoIncrement / prefixedSequence / dailyDateSequence / monthlyDateSequence).
 *
 * CANONICAL copy — consumed by user-portal (parse only), developer-workstation and
 * admin-center (parse + serialize) via the @platform-shared vite alias. The apps keep
 * thin re-export shims at src/utils/pkGenerationConfig.ts so import paths stay stable.
 */
export type PkGenerationStrategy =
  | 'manual'
  | 'uuid'
  | 'autoIncrement'
  | 'prefixedSequence'
  | 'dailyDateSequence'
  | 'monthlyDateSequence'

export type CalendarDateSequencePeriod = 'day' | 'month'

export interface PkGenerationConfig {
  strategy?: PkGenerationStrategy
  startValue?: number
  padWidth?: number
  prefix?: string
}

export const PK_GENERATION_STRATEGIES: PkGenerationStrategy[] = [
  'uuid',
  'manual',
  'autoIncrement',
  'prefixedSequence',
  'dailyDateSequence',
  'monthlyDateSequence',
]

/** Minimum sequence digits for calendar-period strategies (overflow grows beyond this). */
export const DAILY_DATE_SEQUENCE_DEFAULT_PAD = 4

export function isCalendarDateSequence(strategy?: PkGenerationStrategy | string): boolean {
  return strategy === 'dailyDateSequence' || strategy === 'monthlyDateSequence'
}

export function parsePkGeneration(
  raw?: Record<string, unknown> | PkGenerationConfig | null,
): PkGenerationConfig {
  if (!raw || typeof raw !== 'object') {
    return { strategy: 'uuid' }
  }
  const strategy = raw.strategy as PkGenerationStrategy | undefined
  const resolved = PK_GENERATION_STRATEGIES.includes(strategy as PkGenerationStrategy)
    ? strategy
    : 'uuid'
  return {
    strategy: resolved,
    startValue: typeof raw.startValue === 'number' ? raw.startValue : 1,
    padWidth: typeof raw.padWidth === 'number' ? raw.padWidth : defaultPadWidth(resolved),
    prefix: typeof raw.prefix === 'string' ? raw.prefix : '',
  }
}

function defaultPadWidth(strategy?: PkGenerationStrategy): number {
  return isCalendarDateSequence(strategy) ? DAILY_DATE_SEQUENCE_DEFAULT_PAD : 6
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

export function pkGenerationNeedsExtraConfig(strategy?: PkGenerationStrategy): boolean {
  return strategy === 'autoIncrement'
    || strategy === 'prefixedSequence'
    || isCalendarDateSequence(strategy)
}

/** uuid / prefixedSequence / calendar-period sequences allocate strings — inputNumber cannot bind them. */
export function pkStrategyAllocatesString(strategy?: PkGenerationStrategy | string): boolean {
  return strategy === 'uuid'
    || strategy === 'prefixedSequence'
    || isCalendarDateSequence(strategy)
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
