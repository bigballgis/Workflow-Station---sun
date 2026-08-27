/**
 * Shared list column contract consumed by ListColumnHeader / ListFilterDialog.
 *
 * The backend list endpoint declares each column once (field, kind, capabilities,
 * operator whitelist, options); the UI renders strictly from that declaration and
 * never invents operators the backend would reject.
 */

export type ListColumnKind = 'TEXT' | 'ENUM' | 'USER' | 'DATETIME' | 'NUMBER' | 'BOOLEAN'

export interface ListColumnOption {
  value: string
  label: string
}

export interface ListColumnMeta {
  field: string
  label: string
  kind: ListColumnKind
  filterable: boolean
  sortable: boolean
  /** Operator whitelist for this column, in the order the filter dialog lists them. */
  operators: string[]
  /** Closed value list for ENUM / USER / BOOLEAN columns. */
  options?: ListColumnOption[]
}

export interface ListColumnFilter {
  operator: string
  value: string
  /** Upper bound for range operators (between). */
  value2?: string
}

/** A filter on its way to a list endpoint: what the dialog produced, plus the column it applies to. */
export interface ListColumnFilterRequest extends ListColumnFilter {
  field: string
}

const NO_VALUE_OPERATORS = new Set([
  'isNull',
  'isNotNull',
  'today',
  'yesterday',
  'last7days',
  'last30days',
  'thisWeek',
  'thisMonth',
  'thisYear',
])
const RANGE_OPERATORS = new Set(['between'])

const OPERATOR_LABEL_KEYS: Record<string, string> = {
  contains: 'sharedList.opContains',
  notContains: 'sharedList.opNotContains',
  eq: 'sharedList.opEquals',
  ne: 'sharedList.opNotEquals',
  startsWith: 'sharedList.opStartsWith',
  endsWith: 'sharedList.opEndsWith',
  isNull: 'sharedList.opNoData',
  isNotNull: 'sharedList.opHasData',
  today: 'sharedList.opToday',
  yesterday: 'sharedList.opYesterday',
  last7days: 'sharedList.opLast7days',
  last30days: 'sharedList.opLast30days',
  thisWeek: 'sharedList.opThisWeek',
  thisMonth: 'sharedList.opThisMonth',
  thisYear: 'sharedList.opThisYear',
  on: 'sharedList.opOn',
  before: 'sharedList.opBefore',
  after: 'sharedList.opAfter',
  between: 'sharedList.opBetween',
  gt: 'sharedList.opGt',
  gte: 'sharedList.opGte',
  lt: 'sharedList.opLt',
  lte: 'sharedList.opLte',
}

export function operatorNeedsValue(operator: string): boolean {
  return !NO_VALUE_OPERATORS.has(operator)
}

export function operatorNeedsRange(operator: string): boolean {
  return RANGE_OPERATORS.has(operator)
}

/**
 * Unknown operators mean the column declaration and this map are out of sync —
 * surface loudly instead of rendering a blank menu entry.
 */
const TEXT_OPERATORS = [
  'contains', 'notContains', 'eq', 'ne', 'startsWith', 'endsWith', 'isNull', 'isNotNull',
]
const CLOSED_VALUE_OPERATORS = ['eq', 'ne', 'isNull', 'isNotNull']
const DATETIME_OPERATORS = [
  'today', 'yesterday', 'last7days', 'last30days',
  'thisWeek', 'thisMonth', 'thisYear',
  'on', 'before', 'after', 'between', 'isNull', 'isNotNull',
]
const NUMBER_OPERATORS = [
  'eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'between', 'isNull', 'isNotNull',
]

/** Kind → operator whitelist. Must stay in lockstep with `ListColumnMeta.operatorsFor` on the backend. */
export function operatorsFor(kind: ListColumnKind): string[] {
  if (kind === 'TEXT') return [...TEXT_OPERATORS]
  if (kind === 'ENUM' || kind === 'USER' || kind === 'BOOLEAN') return [...CLOSED_VALUE_OPERATORS]
  if (kind === 'DATETIME') return [...DATETIME_OPERATORS]
  return [...NUMBER_OPERATORS]
}

export function operatorLabelKey(operator: string): string {
  const key = OPERATOR_LABEL_KEYS[operator]
  if (!key) {
    throw new Error(
      `Unknown list filter operator "${operator}" — column declaration and shared operator map are out of sync`,
    )
  }
  return key
}

export function isCompleteFilter(filter: ListColumnFilter): boolean {
  if (!operatorNeedsValue(filter.operator)) {
    return true
  }
  if (operatorNeedsRange(filter.operator)) {
    return filter.value !== '' && (filter.value2 ?? '') !== ''
  }
  return filter.value !== ''
}
