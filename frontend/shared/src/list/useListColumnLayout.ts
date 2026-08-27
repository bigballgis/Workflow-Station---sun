import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, type CSSProperties, type MaybeRefOrGetter, type Ref, toValue } from 'vue'
import { clampColumnWidth } from './columnResizeCursor'
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
 * Session stores **base** widths; {@link widthOf} is the leftover-distributed display width.
 */
export function useListColumnLayout(opts: {
  storageKey: MaybeRefOrGetter<string>
  fields: MaybeRefOrGetter<string[]>
  extraWidth?: MaybeRefOrGetter<number>
  defaultWidthOf?: (field: string) => number
  labelOf?: (field: string) => string
}) {
  const columnWidths = reactive<Record<string, number>>({})
  const gridScrollRef = ref<HTMLElement | null>(null)
  const gridViewportWidth = useGridViewport(gridScrollRef)

  function defaultBaseOf(field: string): number {
    if (opts.defaultWidthOf) return opts.defaultWidthOf(field)
    return headerFitColumnWidth(opts.labelOf?.(field) ?? field)
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
    return displayWidths.value[field] ?? baseWidthOf(field)
  }

  function setWidth(field: string, displayWidth: number) {
    const fields = toValue(opts.fields)
    const index = fields.indexOf(field)
    if (index < 0) return
    const bases = fields.map((name) => baseWidthOf(name))
    columnWidths[field] = invertBaseWidth(
      displayWidth, index, bases, gridViewportWidth.value, extra(),
    )
  }

  function persistWidths() {
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
    gridInnerStyle,
    widthOf,
    setWidth,
    persistWidths,
  }
}
