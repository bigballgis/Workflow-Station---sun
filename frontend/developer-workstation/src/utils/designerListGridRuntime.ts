/**
 * Shared runtime helpers for the Developer Workstation designer list grids
 * (Table / Form / Action / Connection / Email Template / Email Monitor / Version).
 *
 * Mirrors the User Portal "Views" grid interaction (draggable column widths +
 * per-column filter dropdown), trimmed to filter-only (no sort/group/move).
 */

/** Dark resize cursor SVG — keep in sync with designerListColumnResize.scss. */
const RESIZE_CURSOR_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">' +
  '<line x1="8" y1="3" x2="8" y2="17" stroke="#303133" stroke-width="2"/>' +
  '<line x1="12" y1="3" x2="12" y2="17" stroke="#303133" stroke-width="2"/>' +
  '<path d="M4 10 L6.5 7 L6.5 13 Z" fill="#303133"/>' +
  '<path d="M16 10 L13.5 7 L13.5 13 Z" fill="#303133"/>' +
  '</svg>'

export const DWL_COL_RESIZE_CURSOR = `url("data:image/svg+xml,${encodeURIComponent(
  RESIZE_CURSOR_SVG,
)}") 10 10, ew-resize`

export const DWL_COLUMN_WIDTH_MIN = 60
export const DWL_COLUMN_WIDTH_MAX = 600
export const DWL_COLUMN_WIDTH_DEFAULT = 160

export function clampColumnWidth(width: number): number {
  return Math.min(DWL_COLUMN_WIDTH_MAX, Math.max(DWL_COLUMN_WIDTH_MIN, Math.round(width)))
}

/** Filter operators, matching the User Portal grid filter set. */
export type GridFilterOperator =
  | 'contains'
  | 'eq'
  | 'ne'
  | 'startsWith'
  | 'endsWith'
  | 'notContains'
  | 'isNull'
  | 'isNotNull'

export interface GridColumnFilter {
  operator: GridFilterOperator
  value: string
}

export interface DesignerListRuntimeState {
  columnWidths: Record<string, number>
  filters: Record<string, GridColumnFilter>
}

export function createDefaultRuntime(): DesignerListRuntimeState {
  return {
    columnWidths: {},
    filters: {},
  }
}

/** Operators that do not require a comparison value. */
export function operatorNeedsValue(operator: GridFilterOperator): boolean {
  return operator !== 'isNull' && operator !== 'isNotNull'
}

export function matchesColumnFilter(value: unknown, filter: GridColumnFilter): boolean {
  const text = value == null ? '' : String(value)
  const expected = filter.value ?? ''
  switch (filter.operator) {
    case 'eq':
      return text.toLowerCase() === expected.toLowerCase()
    case 'ne':
      return text.toLowerCase() !== expected.toLowerCase()
    case 'contains':
      return text.toLowerCase().includes(expected.toLowerCase())
    case 'notContains':
      return !text.toLowerCase().includes(expected.toLowerCase())
    case 'startsWith':
      return text.toLowerCase().startsWith(expected.toLowerCase())
    case 'endsWith':
      return text.toLowerCase().endsWith(expected.toLowerCase())
    case 'isNull':
      return text.trim() === ''
    case 'isNotNull':
      return text.trim() !== ''
    default:
      return false
  }
}

/** Active filters are those with a value, or the value-less isNull/isNotNull operators. */
export function activeFilterEntries(
  filters: Record<string, GridColumnFilter>,
): Array<[string, GridColumnFilter]> {
  return Object.entries(filters).filter(([, f]) => {
    if (!operatorNeedsValue(f.operator)) return true
    return (f.value ?? '').trim() !== ''
  })
}

const STORAGE_PREFIX = 'dw-designer-list:'

const VALID_OPERATORS = new Set<GridFilterOperator>([
  'contains',
  'eq',
  'ne',
  'startsWith',
  'endsWith',
  'notContains',
  'isNull',
  'isNotNull',
])

function isValidOperator(op: unknown): op is GridFilterOperator {
  return typeof op === 'string' && VALID_OPERATORS.has(op as GridFilterOperator)
}

/** Drop malformed session payloads instead of applying fail-open filters. */
export function sanitizeRuntimeState(raw: Partial<DesignerListRuntimeState>): DesignerListRuntimeState {
  if (!raw || typeof raw !== 'object') return createDefaultRuntime()

  const columnWidths: Record<string, number> = {}
  if (raw.columnWidths && typeof raw.columnWidths === 'object') {
    for (const [field, width] of Object.entries(raw.columnWidths)) {
      if (typeof width === 'number' && Number.isFinite(width)) {
        columnWidths[field] = clampColumnWidth(width)
      }
    }
  }

  const filters: Record<string, GridColumnFilter> = {}
  if (raw.filters && typeof raw.filters === 'object') {
    for (const [field, entry] of Object.entries(raw.filters)) {
      if (!entry || typeof entry !== 'object') continue
      const operator = (entry as GridColumnFilter).operator
      if (!isValidOperator(operator)) continue
      filters[field] = {
        operator,
        value: typeof (entry as GridColumnFilter).value === 'string'
          ? (entry as GridColumnFilter).value
          : '',
      }
    }
  }

  return { columnWidths, filters }
}

export function loadRuntimeFromSession(key: string): DesignerListRuntimeState {
  try {
    const raw = sessionStorage.getItem(`${STORAGE_PREFIX}${key}`)
    if (!raw) return createDefaultRuntime()
    const parsed = JSON.parse(raw) as Partial<DesignerListRuntimeState>
    return sanitizeRuntimeState(parsed)
  } catch {
    // FALLBACK(ux): corrupted session payload — reset grid prefs for this list
    return createDefaultRuntime()
  }
}

export function saveRuntimeToSession(key: string, state: DesignerListRuntimeState): void {
  try {
    sessionStorage.setItem(`${STORAGE_PREFIX}${key}`, JSON.stringify(state))
  } catch {
    /* ignore quota / serialization errors — persistence is best-effort */
  }
}
