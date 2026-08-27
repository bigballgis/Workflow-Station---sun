import type {
  MainTableViewDataRow, MainTableViewFieldColumn,
} from '@/api/mainTableView'
import { clampColumnWidth } from '@platform-shared/list/columnResizeCursor'
import { headerFitColumnWidth } from '@platform-shared/list/columnWidthLayout'
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
}

export type GridDisplayRow = MainTableViewDataRow

export function createDefaultGridRuntime(): GridRuntimeState {
  return {
    columnOrder: [],
    columnWidths: {},
    sort: null,
    filters: {},
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

/** Session drag → designer columnWidth → header-fit. This is the persisted **base**, not the leftover share. */
export function columnWidth(
  col: MainTableViewFieldColumn,
  state: GridRuntimeState,
): number {
  if (state.columnWidths[col.fieldName] != null) {
    return clampColumnWidth(state.columnWidths[col.fieldName])
  }
  if (col.columnWidth != null && col.columnWidth > 0) {
    return clampColumnWidth(col.columnWidth)
  }
  return headerFitColumnWidth(col.displayLabel, col.kind)
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
    operators: col.operators,
    options: col.options,
  }
}

/**
 * Remove any runtime state (sort, filters) that references a field not present in the given
 * columns. Prevents a prior view's sort/filter from mis-rendering against another view's data.
 */
export function pruneRuntimeToColumns(
  state: GridRuntimeState,
  columns: MainTableViewFieldColumn[],
): void {
  const valid = new Set(columns.map(c => c.fieldName))
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

/**
 * Only layout survives a reload — column order and widths.
 *
 * Filters and sort are questions the database answers, and it rejects a column the
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
