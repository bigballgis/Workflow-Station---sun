import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, type CSSProperties, type MaybeRefOrGetter, type Ref, toValue } from 'vue'
import type { ListColumnKind } from './columnMeta'
import { clampColumnWidth, clampDisplayWidth } from './columnResizeCursor'
import { distributeDisplayWidths, headerFitColumnWidth, invertBaseWidth } from './columnWidthLayout'

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
  const gridViewportHeight = ref(0)
  let observer: ResizeObserver | null = null

  function applySize(el: HTMLElement) {
    gridViewportWidth.value = el.clientWidth
    gridViewportHeight.value = el.clientHeight
  }

  watch(gridScrollRef, (el, prev) => {
    if (prev) observer?.unobserve(prev)
    if (el) {
      applySize(el)
      observer?.observe(el)
    } else {
      gridViewportWidth.value = 0
      gridViewportHeight.value = 0
    }
  })

  onMounted(() => {
    if (typeof ResizeObserver === 'undefined') return
    observer = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect.width ?? 0
      const h = entries[0]?.contentRect.height ?? 0
      if (w > 0) gridViewportWidth.value = w
      if (h > 0) gridViewportHeight.value = h
    })
    if (gridScrollRef.value) observer.observe(gridScrollRef.value)
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
  })

  return { gridViewportWidth, gridViewportHeight }
}

/**
 * Host-owned column widths for shared ListColumnHeader's resize handle.
 * Session stores **base** widths; {@link widthOf} is the leftover-distributed
 * display width, except during an in-progress drag (preview of the active column).
 */
export function useListColumnLayout(opts: {
  storageKey: MaybeRefOrGetter<string>
  fields: MaybeRefOrGetter<string[]>
  extraWidth?: MaybeRefOrGetter<number>
  defaultWidthOf?: (field: string) => number
  labelOf?: (field: string) => string
  kindOf?: (field: string) => ListColumnKind | undefined
}) {
  const columnWidths = reactive<Record<string, number>>({})
  const dragPreview = reactive<{ field: string | null; displayWidth: number }>({
    field: null,
    displayWidth: 0,
  })
  const gridScrollRef = ref<HTMLElement | null>(null)
  const { gridViewportWidth, gridViewportHeight } = useGridViewport(gridScrollRef)
  const gridTableHeight = computed(() =>
    gridViewportHeight.value > 0 ? gridViewportHeight.value : undefined,
  )

  function defaultBaseOf(field: string): number {
    if (opts.defaultWidthOf) return opts.defaultWidthOf(field)
    return headerFitColumnWidth(opts.labelOf?.(field) ?? field, opts.kindOf?.(field))
  }

  function baseWidthOf(field: string): number {
    return clampColumnWidth(columnWidths[field] ?? defaultBaseOf(field))
  }

  function extra(): number {
    return toValue(opts.extraWidth) ?? 0
  }

  const displayWidths = computed(() => {
    const fields = toValue(opts.fields)
    const bases = fields.map((field) => baseWidthOf(field))
    const displays = distributeDisplayWidths(bases, gridViewportWidth.value, extra())
    const map: Record<string, number> = {}
    fields.forEach((field, index) => {
      map[field] = displays[index]
    })
    return map
  })

  function widthOf(field: string): number {
    if (dragPreview.field === field) return clampDisplayWidth(dragPreview.displayWidth)
    return displayWidths.value[field] ?? baseWidthOf(field)
  }

  function setWidth(field: string, displayWidth: number) {
    const fields = toValue(opts.fields)
    if (!fields.includes(field)) return
    dragPreview.field = field
    dragPreview.displayWidth = clampDisplayWidth(displayWidth)
  }

  function commitPreview() {
    const field = dragPreview.field
    if (!field) return
    const fields = toValue(opts.fields)
    const index = fields.indexOf(field)
    const displayWidth = dragPreview.displayWidth
    dragPreview.field = null
    if (index < 0) return
    const bases = fields.map((name) => baseWidthOf(name))
    columnWidths[field] = invertBaseWidth(
      displayWidth, index, bases, gridViewportWidth.value, extra(),
    )
  }

  function persistWidths() {
    commitPreview()
    writeStoredWidths(toValue(opts.storageKey), columnWidths)
  }

  const gridTotalColumnWidth = computed(() => {
    const fields = toValue(opts.fields)
    const data = fields.reduce((sum, field) => sum + widthOf(field), 0)
    return data + extra()
  })

  const gridFits = computed(() =>
    gridViewportWidth.value > 0 && gridTotalColumnWidth.value <= gridViewportWidth.value,
  )

  // Always the viewport width. Stretching this to the column-sum makes the
  // parent the horizontal scroller, so Element Plus `fixed="right"` (Action)
  // sticks to the table's far edge instead of the window.
  const gridInnerStyle = computed<CSSProperties>(() => ({
    width: '100%',
    minWidth: '100%',
  }))

  watch(() => toValue(opts.storageKey), (key) => {
    dragPreview.field = null
    replaceWidths(columnWidths, readStoredWidths(key))
  }, { immediate: true })

  return {
    gridScrollRef,
    gridFits,
    gridTableHeight,
    gridInnerStyle,
    widthOf,
    setWidth,
    persistWidths,
  }
}
