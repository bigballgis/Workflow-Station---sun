/**
 * Calendar-date parsing for computed fields.
 *
 * Date arithmetic is a separate path from VALUE() / toNumber(): "2026-01-02" is not a number, and
 * wrapping it in VALUE() still fails. Only '-' and DATEDIFF consult this file, so accidental text
 * never becomes a day count.
 *
 * Year-first dates only (YYYY then - / . then month/day). Trailing time is accepted and ignored so
 * DATE and TIMESTAMP columns still subtract as whole days. Ambiguous day-first forms such as
 * 02/06/2026 are not dates.
 */
import { type ComputedValue } from './types'

/**
 * YYYY sep M{1,2} sep D{1,2}, same separator throughout, optional time:
 * space or T, then H:MM[:SS[.fraction]] and optional Z / ±HH:mm.
 */
const DATE_TEXT =
  /^(\d{4})([./-])(\d{1,2})\2(\d{1,2})(?:[Tt ](\d{1,2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?(?:Z|[+-]\d{2}:\d{2})?)?$/

/**
 * Epoch day (UTC calendar) when {@code value} is text that is a real calendar date; otherwise null.
 * Numbers, booleans and blank are never dates — a unix timestamp stays a number.
 */
export function calendarEpochDay(value: ComputedValue): number | null {
  if (value.kind !== 'text') return null
  return parseCalendarEpochDay(value.value.trim())
}

/** Parses a year-first calendar date. Invalid calendars (2026-02-29) return null. */
export function parseCalendarEpochDay(raw: string): number | null {
  const match = DATE_TEXT.exec(raw)
  if (!match) return null
  if (!clockSuffixOk(match[5], match[6], match[7])) return null
  const year = Number(match[1])
  const month = Number(match[3])
  const day = Number(match[4])
  const utc = Date.UTC(year, month - 1, day)
  const reconstructed = new Date(utc)
  if (
    reconstructed.getUTCFullYear() !== year
    || reconstructed.getUTCMonth() !== month - 1
    || reconstructed.getUTCDate() !== day
  ) {
    return null
  }
  return Math.floor(utc / 86_400_000)
}

function clockSuffixOk(hourText: string | undefined, minuteText: string | undefined, secondText: string | undefined): boolean {
  if (hourText == null) return true
  const hour = Number(hourText)
  const minute = Number(minuteText)
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return false
  if (secondText == null) return true
  const second = Number(secondText)
  return second >= 0 && second <= 59
}
