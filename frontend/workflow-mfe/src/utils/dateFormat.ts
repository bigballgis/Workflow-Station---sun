import dayjs from 'dayjs'

/**
 * Format a date string (Beijing time) for display
 * Backend returns dates in Asia/Shanghai timezone
 * @param date - date string or Java LocalDateTime array
 * @param format - Output format (default: 'YYYY-MM-DD HH:mm')
 */
export const formatDate = (date?: string | number[] | null, format: string = 'YYYY-MM-DD HH:mm'): string => {
  if (!date) return '-'

  try {
    let d: ReturnType<typeof dayjs>

    if (Array.isArray(date)) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = date as number[]
      d = dayjs(new Date(year, month - 1, day, hour, minute, second))
    } else {
      d = dayjs(date)
    }

    if (!d.isValid()) return '-'
    return d.format(format)
  } catch (error) {
    console.error('Error formatting date:', error)
    return '-'
  }
}

/**
 * Format a date string to date only (no time)
 * @param date - date string (Beijing time)
 * @returns Formatted date string (YYYY-MM-DD)
 */
export const formatDateOnly = (date?: string | null): string => {
  return formatDate(date, 'YYYY-MM-DD')
}

/**
 * Format a date string with seconds
 * @param date - date string (Beijing time)
 * @returns Formatted date string (YYYY-MM-DD HH:mm:ss)
 */
export const formatDateTime = (date?: string | null): string => {
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

/**
 * Format a date string to locale string
 * @param date - date string (Beijing time)
 * @param locale - Locale string (default: 'zh-CN')
 * @returns Formatted date string in locale format
 */
export const formatDateLocale = (date?: string | null, locale: string = 'zh-CN'): string => {
  if (!date) return '-'
  
  try {
    const localDate = dayjs(date).toDate()
    return localDate.toLocaleString(locale, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    })
  } catch (error) {
    console.error('Error formatting date:', error)
    return '-'
  }
}
