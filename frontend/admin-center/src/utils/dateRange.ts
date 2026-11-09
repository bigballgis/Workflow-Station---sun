/**
 * The instants that bound a range of whole days picked in a date-range filter (`YYYY-MM-DD`, the picker's
 * `value-format`): 00:00:00.000 of the first day and 23:59:59.999 of the last, in the viewer's own time zone with
 * that zone's offset on each of those dates, as ISO-8601 UTC strings.
 */
export function localDayRangeToIso(fromDate: string, toDate: string): { startTime: string; endTime: string } {
  return {
    startTime: localDateAt(fromDate, 0, 0, 0, 0).toISOString(),
    endTime: localDateAt(toDate, 23, 59, 59, 999).toISOString(),
  }
}

function localDateAt(date: string, hours: number, minutes: number, seconds: number, ms: number): Date {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day, hours, minutes, seconds, ms)
}
