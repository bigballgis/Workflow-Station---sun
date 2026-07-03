export type LookupFilterMatchType = 'eq' | 'contains' | 'startsWith' | 'endsWith'

export interface LookupFilterCondition {
  fieldName: string
  value: string
  matchType?: LookupFilterMatchType
}

export interface LookupFilterMatchOption {
  value: LookupFilterMatchType
  label: string
}

const DEFAULT_MATCH_TYPE: LookupFilterMatchType = 'eq'

export function normalizeLookupFilterMatchType(matchType?: string | null): LookupFilterMatchType {
  if (matchType === 'contains' || matchType === 'startsWith' || matchType === 'endsWith') {
    return matchType
  }
  return DEFAULT_MATCH_TYPE
}

export function isBooleanDataType(dataType?: string): boolean {
  const dt = (dataType || '').toUpperCase()
  return dt === 'BOOLEAN' || dt === 'BOOL'
}

export function isNumericDataType(dataType?: string): boolean {
  const dt = (dataType || '').toUpperCase()
  return dt.includes('INT')
    || dt === 'BIGINT'
    || dt.includes('DECIMAL')
    || dt.includes('NUMERIC')
    || dt.includes('FLOAT')
    || dt.includes('DOUBLE')
}

export function isDateDataType(dataType?: string): boolean {
  return (dataType || '').toUpperCase() === 'DATE'
}

export function isTimestampDataType(dataType?: string): boolean {
  const dt = (dataType || '').toUpperCase()
  return dt.includes('TIMESTAMP') || dt === 'DATETIME'
}

export function getLookupFilterMatchOptions(dataType?: string): LookupFilterMatchOption[] {
  if (isBooleanDataType(dataType)) {
    return [{ value: 'eq', label: 'Exact' }]
  }
  return [
    { value: 'eq', label: 'Exact' },
    { value: 'contains', label: 'Contains' },
    { value: 'startsWith', label: 'Starts with' },
    { value: 'endsWith', label: 'Ends with' },
  ]
}

export function normalizeLookupFilterCondition(raw: unknown): LookupFilterCondition | null {
  if (!raw || typeof raw !== 'object') return null
  const rec = raw as Record<string, unknown>
  const fieldName = rec.fieldName == null ? '' : String(rec.fieldName).trim()
  if (!fieldName) return null
  const matchType = normalizeLookupFilterMatchType(
    rec.matchType == null ? undefined : String(rec.matchType),
  )
  const value = rec.value == null ? '' : String(rec.value)
  return { fieldName, value, matchType }
}

export function serializeLookupFilterValue(value: unknown): string {
  if (value == null || value === '') return ''
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return String(value)
}

function normalizeComparable(value: string): string {
  const trimmed = value.trim()
  const lower = trimmed.toLowerCase()
  if (lower === 'true' || lower === 'false') return lower
  return trimmed
}

export function rowMatchesLookupFilter(
  rowValue: unknown,
  filterValue: string,
  matchType: LookupFilterMatchType,
): boolean {
  const cell = rowValue == null ? '' : String(rowValue)
  const target = filterValue ?? ''
  switch (matchType) {
    case 'contains':
      return cell.toLowerCase().includes(target.toLowerCase())
    case 'startsWith':
      return cell.toLowerCase().startsWith(target.toLowerCase())
    case 'endsWith':
      return cell.toLowerCase().endsWith(target.toLowerCase())
    case 'eq':
    default:
      return normalizeComparable(cell) === normalizeComparable(target)
  }
}

export function applyLookupFixedFilters(
  rows: Record<string, unknown>[],
  conditions: LookupFilterCondition[] | undefined,
): Record<string, unknown>[] {
  const active = (conditions || []).filter(condition => condition.fieldName && condition.value !== '')
  if (active.length === 0) return rows
  return rows.filter(row =>
    active.every(condition =>
      rowMatchesLookupFilter(
        row[condition.fieldName],
        condition.value,
        normalizeLookupFilterMatchType(condition.matchType),
      ),
    ),
  )
}
