import type { ListColumnFilter, ListColumnKind, ListColumnMeta } from './columnMeta'

function textOf(raw: unknown): string {
  if (raw == null) return ''
  if (Array.isArray(raw)) return raw.map((item) => textOf(item)).filter(Boolean).join(', ')
  if (typeof raw === 'object') return JSON.stringify(raw)
  return String(raw)
}

function isEmpty(raw: unknown): boolean {
  return textOf(raw).trim() === ''
}

function dayStamp(raw: unknown): string | null {
  const text = textOf(raw)
  if (text.length >= 10 && /^\d{4}-\d{2}-\d{2}/.test(text)) return text.slice(0, 10)
  const parsed = Date.parse(text)
  if (!Number.isFinite(parsed)) return null
  const d = new Date(parsed)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function isoDay(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function relativeDayRange(operator: string, now: Date): { start: string; end: string } | null {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const iso = (offset: number) => {
    const d = new Date(today)
    d.setDate(d.getDate() + offset)
    return isoDay(d)
  }
  if (operator === 'today') return { start: iso(0), end: iso(0) }
  if (operator === 'yesterday') return { start: iso(-1), end: iso(-1) }
  if (operator === 'last7days') return { start: iso(-6), end: iso(0) }
  if (operator === 'last30days') return { start: iso(-29), end: iso(0) }
  if (operator === 'thisWeek') {
    const weekday = (today.getDay() + 6) % 7
    return { start: iso(-weekday), end: iso(0) }
  }
  if (operator === 'thisMonth') {
    return { start: isoDay(new Date(today.getFullYear(), today.getMonth(), 1)), end: iso(0) }
  }
  if (operator === 'thisYear') {
    return { start: isoDay(new Date(today.getFullYear(), 0, 1)), end: iso(0) }
  }
  return null
}

function matchesText(raw: unknown, operator: string, value: string, value2?: string): boolean {
  if (operator === 'isNull') return isEmpty(raw)
  if (operator === 'isNotNull') return !isEmpty(raw)
  const cell = textOf(raw)
  const needle = value
  const lower = cell.toLowerCase()
  const n = needle.toLowerCase()
  if (operator === 'contains') return lower.includes(n)
  if (operator === 'notContains') return isEmpty(raw) || !lower.includes(n)
  if (operator === 'eq') return cell === needle
  if (operator === 'ne') return isEmpty(raw) || cell !== needle
  if (operator === 'startsWith') return lower.startsWith(n)
  if (operator === 'endsWith') return lower.endsWith(n)
  if (operator === 'between') return cell >= needle && cell <= (value2 ?? '')
  return false
}

function matchesDate(raw: unknown, operator: string, value: string, value2?: string, now = new Date()): boolean {
  if (operator === 'isNull') return isEmpty(raw)
  if (operator === 'isNotNull') return !isEmpty(raw)
  const day = dayStamp(raw)
  if (!day) return operator === 'ne' || operator === 'notContains'
  const relative = relativeDayRange(operator, now)
  if (relative) return day >= relative.start && day <= relative.end
  if (operator === 'on') return day === value
  if (operator === 'before') return day < value
  if (operator === 'after') return day > value
  if (operator === 'between') return day >= value && day <= (value2 ?? '')
  if (operator === 'eq') return day === value
  if (operator === 'ne') return day !== value
  return false
}

export function cellMatchesFilter(
  raw: unknown,
  kind: ListColumnKind,
  filter: ListColumnFilter,
  now = new Date(),
): boolean {
  if (kind === 'DATETIME') {
    return matchesDate(raw, filter.operator, filter.value, filter.value2, now)
  }
  if (kind === 'NUMBER') {
    if (filter.operator === 'isNull') return isEmpty(raw)
    if (filter.operator === 'isNotNull') return !isEmpty(raw)
    const n = Number(textOf(raw))
    if (!Number.isFinite(n)) return filter.operator === 'ne'
    const v = Number(filter.value)
    const v2 = Number(filter.value2 ?? '')
    if (filter.operator === 'eq') return n === v
    if (filter.operator === 'ne') return n !== v
    if (filter.operator === 'gt') return n > v
    if (filter.operator === 'gte') return n >= v
    if (filter.operator === 'lt') return n < v
    if (filter.operator === 'lte') return n <= v
    if (filter.operator === 'between') return n >= v && n <= v2
    return false
  }
  if (kind === 'BOOLEAN') {
    const cell = textOf(raw).toLowerCase()
    if (filter.operator === 'isNull') return isEmpty(raw)
    if (filter.operator === 'isNotNull') return !isEmpty(raw)
    const want = filter.value.toLowerCase()
    if (filter.operator === 'eq') return cell === want
    if (filter.operator === 'ne') return cell !== want
    return false
  }
  return matchesText(raw, filter.operator, filter.value, filter.value2)
}

export function compareCells(
  a: unknown,
  b: unknown,
  kind: ListColumnKind,
  direction: 'ASC' | 'DESC',
): number {
  const emptyA = isEmpty(a)
  const emptyB = isEmpty(b)
  if (emptyA && emptyB) return 0
  if (emptyA) return direction === 'ASC' ? -1 : 1
  if (emptyB) return direction === 'ASC' ? 1 : -1
  let cmp = 0
  if (kind === 'NUMBER') {
    cmp = Number(textOf(a)) - Number(textOf(b))
  } else if (kind === 'DATETIME') {
    cmp = (dayStamp(a) ?? '').localeCompare(dayStamp(b) ?? '')
  } else {
    cmp = textOf(a).localeCompare(textOf(b), undefined, { sensitivity: 'base' })
  }
  return direction === 'DESC' ? -cmp : cmp
}

export function applyClientListQuery<T>(opts: {
  rows: readonly T[]
  columns: readonly ListColumnMeta[]
  getValue: (row: T, field: string) => unknown
  filters: Record<string, ListColumnFilter>
  sort: { field: string | null; direction: 'ASC' | 'DESC' | null }
  page: number
  size: number
}): { content: T[]; totalElements: number } {
  const byField = new Map(opts.columns.map((col) => [col.field, col]))
  let next = opts.rows.slice()
  for (const [field, filter] of Object.entries(opts.filters)) {
    const column = byField.get(field)
    if (!column || !column.filterable) {
      throw new Error(`column ${field} is not filterable`)
    }
    next = next.filter((row) => cellMatchesFilter(opts.getValue(row, field), column.kind, filter))
  }
  if (opts.sort.field && opts.sort.direction) {
    const column = byField.get(opts.sort.field)
    if (!column || !column.sortable) {
      throw new Error(`column ${opts.sort.field} is not sortable`)
    }
    const direction = opts.sort.direction
    next = next.slice().sort((a, b) =>
      compareCells(opts.getValue(a, opts.sort.field!), opts.getValue(b, opts.sort.field!), column.kind, direction),
    )
  }
  const totalElements = next.length
  const start = Math.max(0, (opts.page - 1) * opts.size)
  return { content: next.slice(start, start + opts.size), totalElements }
}
