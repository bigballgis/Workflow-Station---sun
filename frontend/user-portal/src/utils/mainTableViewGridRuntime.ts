import type {
  MainTableViewDataRow, MainTableViewFieldColumn, MainTableViewGroup,
} from '@/api/mainTableView'
import { clampColumnWidth } from '@platform-shared/list/columnResizeCursor'
import type { ListColumnFilter, ListColumnMeta } from '@platform-shared/list/columnMeta'
import { formatMainTableViewCell } from '@/utils/mainTableViewCsvExport'

export { COLUMN_WIDTH_MIN, COLUMN_WIDTH_MAX, clampColumnWidth } from '@platform-shared/list/columnResizeCursor'

export type GridSortDirection = 'ASC' | 'DESC'

/** Column filters are the shared list's, so what the UI collects is what the backend receives. */
export type GridColumnFilter = ListColumnFilter

export interface GridRuntimeState {
  columnOrder: string[]
  columnWidths: Record<string, number>
  sort: { fieldName: string; direction: GridSortDirection } | null
  filters: Record<string, GridColumnFilter>
  groupBy: string | null
}

export type GridDisplayRow =
  | (MainTableViewDataRow & { _isGroupHeader?: false })
  | {
      _isGroupHeader: true
      _groupLabel: string
      _groupCount: number
      processInstanceId?: string
      values?: Record<string, unknown>
    }

export function createDefaultGridRuntime(): GridRuntimeState {
  return {
    columnOrder: [],
    columnWidths: {},
    sort: null,
    filters: {},
    groupBy: null,
  }
}

export function initColumnOrder(columns: MainTableViewFieldColumn[], state: GridRuntimeState): void {
  const names = columns.map(c => c.fieldName)
  if (!state.columnOrder.length) {
    state.columnOrder = [...names]
    return
  }
  const existing = new Set(state.columnOrder)
  const merged = state.columnOrder.filter(n => names.includes(n))
  for (const n of names) {
    if (!existing.has(n)) merged.push(n)
  }
  state.columnOrder = merged
}

export function orderedColumns(
  columns: MainTableViewFieldColumn[],
  state: GridRuntimeState,
): MainTableViewFieldColumn[] {
  if (!state.columnOrder.length) return columns
  const map = new Map(columns.map(c => [c.fieldName, c]))
  return state.columnOrder
    .map(name => map.get(name))
    .filter((c): c is MainTableViewFieldColumn => !!c)
}

export function columnWidth(
  col: MainTableViewFieldColumn,
  state: GridRuntimeState,
): number {
  return clampColumnWidth(state.columnWidths[col.fieldName] ?? col.columnWidth ?? 120)
}

export function setColumnWidth(
  state: GridRuntimeState,
  fieldName: string,
  width: number,
): void {
  state.columnWidths[fieldName] = clampColumnWidth(width)
}

function cellText(row: MainTableViewDataRow, fieldName: string): string {
  const v = row.values[fieldName]
  if (v == null) return ''
  // Use the same display formatting as cells so group labels show filenames (not upload URLs) and
  // lookup/FK display names (not "[object Object]"). formatMainTableViewCell returns '-' for empties.
  const formatted = formatMainTableViewCell(v)
  return formatted === '-' ? '' : formatted
}

/**
 * Slot a header in front of each run of rows sharing a group value.
 *
 * The page only holds part of the result set, so the count on a header cannot be derived from the
 * rows in hand — it comes from the backend's GROUP BY over the same predicate the page was drawn
 * from. A label the backend did not count means the two disagree about the grouping expression,
 * which would silently understate a group; that is an error, not something to paper over.
 */
export function insertGroupHeaders(
  rows: MainTableViewDataRow[],
  groupByField: string | null,
  groups: MainTableViewGroup[],
): GridDisplayRow[] {
  if (!groupByField) return rows

  const countByLabel = new Map(groups.map(g => [g.label ?? '', g.count]))
  const out: GridDisplayRow[] = []
  let currentLabel: string | null = null

  for (const row of rows) {
    const label = cellText(row, groupByField)
    if (label !== currentLabel) {
      const count = countByLabel.get(label)
      if (count === undefined) {
        throw new Error(
          `Group "${label}" on ${groupByField} was not counted by the server — the page and its group counts came from different queries`,
        )
      }
      out.push({
        _isGroupHeader: true,
        _groupLabel: label || '—',
        _groupCount: count,
      })
      currentLabel = label
    }
    out.push(row)
  }
  return out
}

/**
 * The shared list header reads a column declaration, not this app's column DTO. The capability
 * flags are the backend's answer about what the query can do, so they are passed through as-is.
 */
export function toListColumnMeta(col: MainTableViewFieldColumn): ListColumnMeta {
  return {
    field: col.fieldName,
    label: col.displayLabel,
    kind: col.kind,
    filterable: col.filterable,
    sortable: col.sortable,
    groupable: col.groupable,
    operators: col.operators,
  }
}

/**
 * Remove any runtime state (groupBy, sort, filters) that references a field not present in the given
 * columns. Prevents a prior view's grouping/sort/filter from mis-rendering against another view's data.
 */
export function pruneRuntimeToColumns(
  state: GridRuntimeState,
  columns: MainTableViewFieldColumn[],
): void {
  const valid = new Set(columns.map(c => c.fieldName))
  if (state.groupBy && !valid.has(state.groupBy)) {
    state.groupBy = null
  }
  if (state.sort && !valid.has(state.sort.fieldName)) {
    state.sort = null
  }
  for (const field of Object.keys(state.filters)) {
    if (!valid.has(field)) {
      delete state.filters[field]
    }
  }
}

export function moveColumn(
  state: GridRuntimeState,
  fieldName: string,
  direction: 'left' | 'right',
): void {
  const idx = state.columnOrder.indexOf(fieldName)
  if (idx < 0) return
  const target = direction === 'left' ? idx - 1 : idx + 1
  if (target < 0 || target >= state.columnOrder.length) return
  const next = [...state.columnOrder]
  ;[next[idx], next[target]] = [next[target], next[idx]]
  state.columnOrder = next
}

export function isGroupHeaderRow(row: GridDisplayRow): row is Extract<GridDisplayRow, { _isGroupHeader: true }> {
  return !!(row as { _isGroupHeader?: boolean })._isGroupHeader
}

/**
 * Only layout survives a reload — column order and widths.
 *
 * Filters, sort and grouping are questions the database answers, and it rejects a column the
 * current view no longer declares. Restoring them from an earlier session would therefore make
 * every load of a redesigned view fail with no way for the user to clear the offending state.
 * They are built fresh from the columns the backend just declared instead.
 */
type PersistedGridLayout = Pick<GridRuntimeState, 'columnOrder' | 'columnWidths'>

export function loadGridRuntimeFromSession(viewId: number): GridRuntimeState {
  try {
    const raw = sessionStorage.getItem(`portal-mtv-layout:${viewId}`)
    if (!raw) return createDefaultGridRuntime()
    const parsed = JSON.parse(raw) as PersistedGridLayout
    return {
      ...createDefaultGridRuntime(),
      columnOrder: parsed.columnOrder ?? [],
      columnWidths: parsed.columnWidths ?? {},
    }
  } catch {
    // FALLBACK(ux): unreadable layout costs the user their column widths, nothing else — the data
    // shown is unaffected. Throwing here would wedge the view until session storage is cleared.
    return createDefaultGridRuntime()
  }
}

export function saveGridRuntimeToSession(viewId: number, state: GridRuntimeState): void {
  const layout: PersistedGridLayout = {
    columnOrder: state.columnOrder,
    columnWidths: state.columnWidths,
  }
  try {
    sessionStorage.setItem(`portal-mtv-layout:${viewId}`, JSON.stringify(layout))
  } catch {
    // FALLBACK(ux): a storage quota error means widths are not remembered next visit; it must not
    // interrupt the resize the user just performed.
  }
}
