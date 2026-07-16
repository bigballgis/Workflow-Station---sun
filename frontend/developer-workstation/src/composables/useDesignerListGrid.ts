import { computed, reactive, ref, unref, watch, type ComputedRef, type Ref } from 'vue'
import {
  activeFilterEntries,
  clampColumnWidth,
  createDefaultRuntime,
  DWL_COLUMN_WIDTH_DEFAULT,
  loadRuntimeFromSession,
  matchesColumnFilter,
  operatorNeedsValue,
  saveRuntimeToSession,
  type DesignerListRuntimeState,
  type GridColumnFilter,
} from '@/utils/designerListGridRuntime'

/** A filterable/resizable column descriptor. */
export interface DesignerListColumn<T> {
  /** Stable key used for width/filter persistence; usually the column's `prop`. */
  key: string
  /** Default width in px when the user has not resized this column. */
  defaultWidth?: number
  /**
   * Extract the text used for filtering. Defaults to `row[key]`.
   * Provide this for slot-rendered columns whose displayed value differs from
   * the raw field (labels, tags, joined lists, formatted dates).
   */
  getValue?: (row: T) => unknown
}

/** Column config for DesignerListTable (extends grid column with display metadata). */
export interface DesignerListTableColumn<T> extends DesignerListColumn<T> {
  label: string
  prop?: string
  showOverflowTooltip?: boolean
}

export interface UseDesignerListGridOptions<T> {
  /** Persistence key (per Function Unit + list); state is remembered in sessionStorage. */
  storageKey: string | Ref<string>
  columns: DesignerListColumn<T>[]
  /** Source rows (a ref or getter). */
  rows: Ref<T[]> | (() => T[])
}

export interface UseDesignerListGridReturn<T> {
  displayRows: ComputedRef<T[]>
  columnWidth: (key: string, fallback?: number) => number
  hasFilter: (key: string) => boolean
  handleResize: (key: string, width: number) => void
  handleResizeEnd: () => void
  openColumnFilter: (key: string, label: string) => void
  applyColumnFilter: (filter: GridColumnFilter) => void
  clearColumnFilter: (key?: string) => void
  filterDialogVisible: Ref<boolean>
  filterColumnLabel: Ref<string>
  currentFilter: ComputedRef<GridColumnFilter | null>
}

export function useDesignerListGrid<T>(
  opts: UseDesignerListGridOptions<T>,
): UseDesignerListGridReturn<T> {
  const keyOf = () => unref(opts.storageKey)
  const colMap = new Map(opts.columns.map((c) => [c.key, c]))

  const state = reactive<DesignerListRuntimeState>(createDefaultRuntime())
  Object.assign(state, loadRuntimeFromSession(keyOf()))

  const rowsRef = computed<T[]>(() =>
    typeof opts.rows === 'function' ? opts.rows() : unref(opts.rows),
  )

  const displayRows = computed<T[]>(() => {
    const src = rowsRef.value ?? []
    const entries = activeFilterEntries(state.filters)
    if (!entries.length) return src
    return src.filter((row) =>
      entries.every(([key, filter]) => {
        const col = colMap.get(key)
        const value = col?.getValue ? col.getValue(row) : (row as Record<string, unknown>)[key]
        return matchesColumnFilter(value, filter)
      }),
    )
  })

  function persist() {
    saveRuntimeToSession(keyOf(), state)
  }

  function columnWidth(key: string, fallback?: number): number {
    return clampColumnWidth(
      state.columnWidths[key] ?? fallback ?? colMap.get(key)?.defaultWidth ?? DWL_COLUMN_WIDTH_DEFAULT,
    )
  }

  function hasFilter(key: string): boolean {
    return !!state.filters[key]
  }

  function handleResize(key: string, width: number) {
    state.columnWidths[key] = clampColumnWidth(width)
  }

  function handleResizeEnd() {
    persist()
  }

  // Filter dialog state
  const filterDialogVisible = ref(false)
  const filterColumnKey = ref<string | null>(null)
  const filterColumnLabel = ref('')

  const currentFilter = computed<GridColumnFilter | null>(() =>
    filterColumnKey.value ? state.filters[filterColumnKey.value] ?? null : null,
  )

  function openColumnFilter(key: string, label: string) {
    filterColumnKey.value = key
    filterColumnLabel.value = label
    filterDialogVisible.value = true
  }

  function applyColumnFilter(filter: GridColumnFilter) {
    const key = filterColumnKey.value
    if (!key) return
    if (operatorNeedsValue(filter.operator) && !filter.value.trim()) {
      delete state.filters[key]
    } else {
      state.filters[key] = { ...filter }
    }
    persist()
    filterDialogVisible.value = false
  }

  function clearColumnFilter(key?: string) {
    const target = key ?? filterColumnKey.value
    if (!target) return
    delete state.filters[target]
    persist()
    filterDialogVisible.value = false
  }

  // Reload persisted state if the storage key changes (e.g. switching Function Unit).
  watch(
    () => keyOf(),
    (next, prev) => {
      if (next === prev) return
      const loaded = loadRuntimeFromSession(next)
      state.columnWidths = loaded.columnWidths
      state.filters = loaded.filters
    },
  )

  return {
    displayRows,
    columnWidth,
    hasFilter,
    handleResize,
    handleResizeEnd,
    openColumnFilter,
    applyColumnFilter,
    clearColumnFilter,
    filterDialogVisible,
    filterColumnLabel,
    currentFilter,
  }
}
