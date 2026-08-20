/**
 * Custom PK template parse/preview. Runtime allocation lives in backend CustomPkFormat.
 * DATETIME uses Asia/Shanghai, matching table audit fields.
 */

import { formatJavaDatePattern, type PkResetPeriod } from './pkDatetimeFormat'

export const CUSTOM_FORMAT_STRATEGY = 'customFormat' as const
export const LEGACY_DATE_PREFIXED_STRATEGY = 'datePrefixedSequence' as const
export const CUSTOM_FORMAT_DEFAULT = '{DATETIME:yyyy-dd-MM}-{SEQNUM:4}'
export const CUSTOM_FORMAT_DEFAULT_DATE_PATTERN = 'yyyy-MM-dd'
export const CUSTOM_FORMAT_DEFAULT_SEQ_PAD = 4

export const CUSTOM_FORMAT_SNIPPETS = {
  datetime: '{DATETIME:yyyy-dd-MM}',
  seqnum: '{SEQNUM:4}',
  randstring: '{RANDSTRING:4}',
} as const

const DATETIME_PATTERN = /^[yMdHms\-/:\s]+$/
const SEQ_WIDTH = { min: 1, max: 20 }
const RAND_LENGTH = { min: 1, max: 16 }

export type CustomFormatSegment =
  | { kind: 'literal'; text: string }
  | { kind: 'datetime'; pattern: string }
  | { kind: 'seqnum'; width: number }
  | { kind: 'randstring'; length: number }

export interface ParsedCustomFormat {
  segments: CustomFormatSegment[]
  seqWidth: number
}

export function parseCustomFormat(format?: string | null): ParsedCustomFormat {
  if (!format || !format.trim()) {
    throw new Error('Custom PK format is required')
  }
  const segments: CustomFormatSegment[] = []
  let seqWidth = 0
  let i = 0
  while (i < format.length) {
    const open = format.indexOf('{', i)
    if (open < 0) {
      segments.push({ kind: 'literal', text: format.slice(i) })
      break
    }
    if (open > i) {
      segments.push({ kind: 'literal', text: format.slice(i, open) })
    }
    const close = format.indexOf('}', open + 1)
    if (close < 0) {
      throw new Error('Unclosed placeholder in custom PK format')
    }
    const segment = parsePlaceholder(format.slice(open + 1, close))
    if (segment.kind === 'seqnum' && seqWidth === 0) {
      seqWidth = segment.width
    }
    segments.push(segment)
    i = close + 1
  }
  if (seqWidth === 0) {
    throw new Error('Custom PK format must include {SEQNUM:n}')
  }
  return { segments, seqWidth }
}

export function tryParseCustomFormat(format?: string | null): ParsedCustomFormat | null {
  try {
    return parseCustomFormat(format)
  } catch {
    return null
  }
}

export function formatCustomPkPreview(
  format?: string | null,
  startValue = 1,
  now = new Date(),
): string {
  const parsed = tryParseCustomFormat(format)
  if (!parsed) return ''
  return parsed.segments.map((segment) => renderPreviewSegment(segment, startValue, now)).join('')
}

export function customFormatAllowsDailyReset(format?: string | null): boolean {
  const parsed = tryParseCustomFormat(format)
  return parsed != null && datetimePatternSource(parsed).includes('d')
}

export function customFormatAllowsMonthlyReset(format?: string | null): boolean {
  const parsed = tryParseCustomFormat(format)
  return parsed != null && datetimePatternSource(parsed).includes('M')
}

/**
 * When the designer edits the format string, drop a reset the new template cannot support.
 * parse/serialize keep the stored resetPeriod; backend allocate rejects illegal combos.
 */
export function coerceCustomResetPeriod(
  format?: string | null,
  resetPeriod?: PkResetPeriod,
): PkResetPeriod {
  const period = resetPeriod ?? 'none'
  const parsed = tryParseCustomFormat(format)
  if (!parsed) return period
  if (period === 'day' && !datetimePatternSource(parsed).includes('d')) return 'none'
  if (period === 'month' && !datetimePatternSource(parsed).includes('M')) return 'none'
  return period
}

export function migrateLegacyDatePrefixedFormat(
  datePattern?: string,
  padWidth?: number,
): string {
  const pattern = datePattern && datePattern.trim()
    ? datePattern
    : CUSTOM_FORMAT_DEFAULT_DATE_PATTERN
  const pad = padWidth && padWidth > 0 ? padWidth : CUSTOM_FORMAT_DEFAULT_SEQ_PAD
  return `{DATETIME:${pattern}}-{SEQNUM:${pad}}`
}

function datetimePatternSource(parsed: ParsedCustomFormat): string {
  return parsed.segments
    .filter((segment): segment is Extract<CustomFormatSegment, { kind: 'datetime' }> =>
      segment.kind === 'datetime')
    .map(segment => segment.pattern)
    .join('')
}

function parsePlaceholder(body: string): CustomFormatSegment {
  const colon = body.indexOf(':')
  const token = (colon < 0 ? body : body.slice(0, colon)).trim()
  const arg = colon < 0 ? '' : body.slice(colon + 1)
  if (token === 'DATETIME') return { kind: 'datetime', pattern: requireDatetimePattern(arg) }
  if (token === 'SEQNUM') return { kind: 'seqnum', width: requireWidth(arg, SEQ_WIDTH.min, SEQ_WIDTH.max) }
  if (token === 'RANDSTRING') {
    return { kind: 'randstring', length: requireWidth(arg, RAND_LENGTH.min, RAND_LENGTH.max) }
  }
  throw new Error(`Unknown placeholder: ${token}`)
}

function requireDatetimePattern(pattern: string): string {
  if (!pattern || !DATETIME_PATTERN.test(pattern)) {
    throw new Error(`Unsupported DATETIME pattern: ${pattern}`)
  }
  return pattern
}

function requireWidth(raw: string, min: number, max: number): number {
  const width = Number.parseInt(raw.trim(), 10)
  if (!Number.isInteger(width) || width < min || width > max) {
    throw new Error('Invalid placeholder width')
  }
  return width
}

function renderPreviewSegment(
  segment: CustomFormatSegment,
  startValue: number,
  now: Date,
): string {
  if (segment.kind === 'literal') return segment.text
  if (segment.kind === 'datetime') return formatJavaDatePattern(segment.pattern, now)
  if (segment.kind === 'seqnum') return String(startValue).padStart(segment.width, '0')
  return 'X'.repeat(segment.length)
}
