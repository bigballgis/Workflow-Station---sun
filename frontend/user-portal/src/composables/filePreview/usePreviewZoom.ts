import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ZOOM_STEP,
  clampZoomScale,
  scaleAfterWheel,
  scrollToKeepPoint,
} from '@/utils/filePreviewZoom'

/** CSS-only zoom: native backing store stays put; display size follows `scale`. */
export function usePreviewZoom() {
  const host = ref<HTMLElement | null>(null)
  const mode = ref<'fit' | 'custom'>('fit')
  const scale = ref(1)
  const nativeWidth = ref(0)
  const nativeHeight = ref(0)
  let observer: ResizeObserver | null = null

  function fitScale(): number {
    const el = host.value
    const w = nativeWidth.value
    const h = nativeHeight.value
    if (!el || w <= 0 || h <= 0) return 1
    const availW = Math.max(1, el.clientWidth - 8)
    const availH = Math.max(1, el.clientHeight - 8)
    return Math.min(availW / w, availH / h)
  }

  function applyFit() {
    if (mode.value === 'fit') scale.value = fitScale()
  }

  function setNativeSize(width: number, height: number) {
    nativeWidth.value = width
    nativeHeight.value = height
    applyFit()
  }

  function zoomToward(clientX: number, clientY: number, nextScale: number) {
    const el = host.value
    const prev = scale.value
    const next = clampZoomScale(nextScale)
    if (!el || next === prev) {
      mode.value = 'custom'
      scale.value = next
      return
    }
    const rect = el.getBoundingClientRect()
    const pointX = clientX - rect.left
    const pointY = clientY - rect.top
    const kept = scrollToKeepPoint(el.scrollLeft, el.scrollTop, pointX, pointY, prev, next)
    mode.value = 'custom'
    scale.value = next
    void nextTick(() => {
      el.scrollLeft = kept.scrollLeft
      el.scrollTop = kept.scrollTop
    })
  }

  function zoomFromCenter(nextScale: number) {
    const el = host.value
    if (!el) {
      mode.value = 'custom'
      scale.value = clampZoomScale(nextScale)
      return
    }
    const rect = el.getBoundingClientRect()
    zoomToward(rect.left + el.clientWidth / 2, rect.top + el.clientHeight / 2, nextScale)
  }

  function zoomIn() {
    zoomFromCenter(scale.value * ZOOM_STEP)
  }

  function zoomOut() {
    zoomFromCenter(scale.value / ZOOM_STEP)
  }

  function actualSize() {
    zoomFromCenter(1)
  }

  function fitWindow() {
    mode.value = 'fit'
    applyFit()
  }

  function onWheel(event: WheelEvent) {
    if (!event.ctrlKey && !event.metaKey) return
    event.preventDefault()
    zoomToward(event.clientX, event.clientY, scaleAfterWheel(scale.value, event.deltaY))
  }

  function bindHost(el: HTMLElement | null) {
    observer?.disconnect()
    observer = null
    if (!el) return
    el.addEventListener('wheel', onWheel, { passive: false })
    if (typeof ResizeObserver === 'undefined') return
    observer = new ResizeObserver(() => applyFit())
    observer.observe(el)
  }

  watch(
    host,
    (el, prev) => {
      if (prev) prev.removeEventListener('wheel', onWheel)
      bindHost(el)
    },
    { immediate: true },
  )

  const cssWidth = computed(() => `${Math.max(1, nativeWidth.value * scale.value)}px`)
  const cssHeight = computed(() => `${Math.max(1, nativeHeight.value * scale.value)}px`)
  const percent = computed(() => Math.round(scale.value * 100))

  onMounted(() => applyFit())
  onBeforeUnmount(() => {
    host.value?.removeEventListener('wheel', onWheel)
    observer?.disconnect()
    observer = null
  })

  return {
    host,
    scale,
    mode,
    percent,
    cssWidth,
    cssHeight,
    setNativeSize,
    zoomIn,
    zoomOut,
    actualSize,
    fitWindow,
  }
}
