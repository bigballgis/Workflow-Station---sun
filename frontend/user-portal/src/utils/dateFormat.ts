import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'

// Enable UTC and timezone plugins
dayjs.extend(utc)
dayjs.extend(timezone)

/**
 * Format a UTC date string to local time
 * @param date - ISO 8601 date string (assumed to be UTC)
 * @param format - Output format (default: 'YYYY-MM-DD HH:mm')
 * @returns Formatted date string in local timezone
 */
export const formatDate = (date?: string | null, format: string = 'YYYY-MM-DD HH:mm'): string => {
  if (!date) return '-'
  
  try {
    // Parse as UTC and convert to local time
    return dayjs.utc(date).local().format(format)
  } catch (error) {
    console.error('Error formatting date:', error)
    return '-'
  }
}

/**
 * Format a UTC date string to local date only (no time)
 * @param date - ISO 8601 date string (assumed to be UTC)
 * @returns Formatted date string (YYYY-MM-DD)
 */
export const formatDateOnly = (date?: string | null): string => {
  return formatDate(date, 'YYYY-MM-DD')
}

/**
 * Format a UTC date string to local time with seconds
 * @param date - ISO 8601 date string (assumed to be UTC)
 * @returns Formatted date string (YYYY-MM-DD HH:mm:ss)
 */
export const formatDateTime = (date?: string | null): string => {
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

/**
 * Format a UTC date string to locale string
 * @param date - ISO 8601 date string (assumed to be UTC)
 * @param locale - Locale string (default: 'zh-CN')
 * @returns Formatted date string in locale format
 */
export const formatDateLocale = (date?: string | null, locale: string = 'zh-CN'): string => {
  if (!date) return '-'
  
  try {
    const localDate = dayjs.utc(date).local().toDate()
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
