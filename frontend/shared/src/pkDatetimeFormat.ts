/**
 * Asia/Shanghai date-token formatting used by custom PK DATETIME previews.
 */

const MONTHS_EN = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const DATE_TOKENS = ['yyyy', 'yy', 'MMM', 'MM', 'dd', 'd', 'HH', 'H', 'mm', 'm', 'ss', 's'] as const

export type PkResetPeriod = 'none' | 'day' | 'month'

export function parseResetPeriod(raw?: unknown): PkResetPeriod {
  if (raw === 'day' || raw === 'month' || raw === 'none') return raw
  return 'none'
}

export function resetPeriodScope(resetPeriod?: PkResetPeriod): 'perDay' | 'perMonth' | 'perTable' {
  if (resetPeriod === 'day') return 'perDay'
  if (resetPeriod === 'month') return 'perMonth'
  return 'perTable'
}

export function shanghaiParts(now = new Date()): {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  second: number
} {
  const utc8 = new Date(now.getTime() + (now.getTimezoneOffset() + 480) * 60000)
  return {
    year: utc8.getFullYear(),
    month: utc8.getMonth() + 1,
    day: utc8.getDate(),
    hour: utc8.getHours(),
    minute: utc8.getMinutes(),
    second: utc8.getSeconds(),
  }
}

export function formatJavaDatePattern(pattern: string, now = new Date()): string {
  const parts = shanghaiParts(now)
  let i = 0
  let out = ''
  while (i < pattern.length) {
    const token = DATE_TOKENS.find(t => pattern.startsWith(t, i))
    if (token) {
      out += formatDateToken(token, parts)
      i += token.length
    } else {
      out += pattern.charAt(i)
      i += 1
    }
  }
  return out
}

function pad(n: number, width: number): string {
  return String(n).padStart(width, '0')
}

function formatDateToken(
  token: (typeof DATE_TOKENS)[number],
  parts: ReturnType<typeof shanghaiParts>,
): string {
  switch (token) {
    case 'yyyy': return String(parts.year)
    case 'yy': return String(parts.year).slice(-2)
    case 'MMM': return MONTHS_EN[parts.month - 1] ?? ''
    case 'MM': return pad(parts.month, 2)
    case 'dd': return pad(parts.day, 2)
    case 'd': return String(parts.day)
    case 'HH': return pad(parts.hour, 2)
    case 'H': return String(parts.hour)
    case 'mm': return pad(parts.minute, 2)
    case 'm': return String(parts.minute)
    case 'ss': return pad(parts.second, 2)
    case 's': return String(parts.second)
    default: return token
  }
}
