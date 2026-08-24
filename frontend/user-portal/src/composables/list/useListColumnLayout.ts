import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, type CSSProperties, type MaybeRefOrGetter, type Ref, toValue } from 'vue'
import { clampColumnWidth, leftoverColumnWidth } from '@platform-shared/list/columnResizeCursor'

const DEFAULT_WIDTH = 120

function readStoredWidths(key: string): Record<string, number> {
  if (!key) return {}
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as { columnWidths?: Record<string, number> }
    if (parsed.columnWidths && typeof parsed.columnWidths === 'object') {
      return parsed.columnWidths
    }
    return {}
  } catch {
    // FALLBACK(ux): unreadable layout costs remembered widths only; the row data is unaffected.
    return {}
  }
}

function writeStoredWidths(key: string, columnWidths: Record<string, number>) {
  if (!key) return
  try {
    sessionStorage.setItem(key, JSON.stringify({ columnWidths: { ...columnWidths } }))
  } catch {
    // FALLBACK(ux): quota errors must not interrupt the resize the user just performed.
  }
}

function replaceWidths(target: Record<string, number>, next: Record<string, number>) {
  for (const key of Object.keys(target)) {
    delete target[key]
  }
  Object.assign(target, next)
}

function useGridViewport(gridScrollRef: Ref<HTMLElement | null>) {
  const gridViewportWidth = ref(0)
  let observer: ResizeObserver | null = null

  watch(gridScrollRef, (el, prev) => {
    if (prev) observer?.unobserve(prev)
    if (el) {
      gridViewportWidth.value = el.clientWidth
      observer?.observe(el)
    } else {
      gridViewportWidth.value = 0
    }
  })

  onMounted(() => {
    if (typeof ResizeObserver === 'undefined') return
    observer = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect.width ?? 0
      if (w > 0) gridViewportWidth.value = w
    })
    if (gridScrollRef.value) observer.observe(gridScrollRef.value)
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
  })

  return gridViewportWidth
}

/**
 * Host-owned column widths for shared ListColumnHeader's resize handle.
 * Persistence is session-only (same rule as Views); the shared components never touch storage.
 */
export function useListColumnLayout(opts: {
  storageKey: MaybeRefOrGetter<string>
  fields: MaybeRefOrGetter<string[]>
  extraWidth?: MaybeRefOrGetter<number>
  defaultWidthOf?: (field: string) => number
}) {
  const columnWidths = reactive<Record<string, number>>({})
  const gridScrollRef = ref<HTMLElement | null>(null)
  const gridViewportWidth = useGridViewport(gridScrollRef)
  const defaultWidthOf = opts.defaultWidthOf ?? (() => DEFAULT_WIDTH)

  function widthOf(field: string): number {
    return clampColumnWidth(columnWidths[field] ?? defaultWidthOf(field))
  }

  function setWidth(field: string, width: number) {
    columnWidths[field] = clampColumnWidth(width)
  }

  function persistWidths() {
    writeStoredWidths(toValue(opts.storageKey), columnWidths)
  }

  const gridTotalColumnWidth = computed(() => {
    const fields = toValue(opts.fields)
    const extra = toValue(opts.extraWidth) ?? 0
    return fields.reduce((sum, field) => sum + widthOf(field), 0) + extra
  })

  const gridFits = computed(() =>
    gridViewportWidth.value > 0 && gridTotalColumnWidth.value <= gridViewportWidth.value,
  )

  const leftoverWidth = computed(() =>
    leftoverColumnWidth(gridViewportWidth.value, gridTotalColumnWidth.value),
  )

  const gridInnerStyle = computed<CSSProperties>(() => (
    gridFits.value
      ? { width: '100%' }
      : { width: `${gridTotalColumnWidth.value}px`, minWidth: '100%' }
  ))

  watch(() => toValue(opts.storageKey), (key) => {
    replaceWidths(columnWidths, readStoredWidths(key))
  }, { immediate: true })

  return {
    gridScrollRef,
    gridFits,
    leftoverWidth,
    gridInnerStyle,
    widthOf,
    setWidth,
    persistWidths,
  }
}
