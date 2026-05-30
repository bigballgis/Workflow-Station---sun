import { ref, computed, watch, nextTick, onUnmounted, type Ref } from 'vue'
import { syncDesignerHiddenFieldMarkers } from '@/utils/formDesignerCanvasChrome'

export interface UseFormDesignerCanvasChromeOptions {
  activeDesignerTab: Ref<string>
  designerRef: Ref<{ getRule?: () => unknown[] } | null | undefined>
  subDesignerRefs: Ref<Array<{ getRule?: () => unknown[] } | null | undefined>>
  designerSubBindings: Ref<Array<{ bindingId: number }>>
  hiddenBadgeLabel: Ref<string> | (() => string)
}

export function useFormDesignerCanvasChrome(options: UseFormDesignerCanvasChromeOptions) {
  const designerShowHidden = ref(false)
  const designerZoomPercent = ref(90)

  const designerZoomScale = computed(() => designerZoomPercent.value / 100)

  const designerZoomStyle = computed(() => ({
    '--fc-designer-zoom': String(designerZoomScale.value),
  }))

  let markerObserver: MutationObserver | null = null
  let markerPollTimer: ReturnType<typeof setInterval> | null = null
  let syncRaf = 0

  function resolveHiddenBadgeLabel(): string {
    const label = options.hiddenBadgeLabel
    return typeof label === 'function' ? label() : label.value
  }

  function getActiveDesignerInstance(): { getRule?: () => unknown[] } | null | undefined {
    if (options.activeDesignerTab.value === 'main') {
      return options.designerRef.value
    }
    const bindingId = Number(options.activeDesignerTab.value)
    if (!Number.isFinite(bindingId)) return null
    const index = options.designerSubBindings.value.findIndex((b) => b.bindingId === bindingId)
    if (index < 0) return null
    return options.subDesignerRefs.value[index]
  }

  function getActiveDesignerWrapperEl(): HTMLElement | null {
    const designer = getActiveDesignerInstance() as { $el?: HTMLElement } | null | undefined
    const root = designer?.$el as HTMLElement | null | undefined
    if (!root) return null

    const fromClosest = root.closest('.fc-designer-wrapper') as HTMLElement | null
    if (fromClosest) return fromClosest

    const zoomStage = root.closest('.fc-designer-zoom-stage') as HTMLElement | null
    const zoomParent = zoomStage?.parentElement
    if (zoomParent?.classList.contains('fc-designer-wrapper')) {
      return zoomParent as HTMLElement
    }

    return root
  }

  function syncHiddenMarkersNow() {
    const wrapper = getActiveDesignerWrapperEl()
    const designer = getActiveDesignerInstance()
    if (!wrapper || !designer?.getRule) return
    let rules: unknown[] = []
    try {
      rules = designer.getRule() || []
    } catch {
      return
    }
    syncDesignerHiddenFieldMarkers(
      wrapper,
      rules,
      designerShowHidden.value,
      resolveHiddenBadgeLabel(),
    )
  }

  function scheduleSyncHiddenMarkers() {
    if (syncRaf) cancelAnimationFrame(syncRaf)
    syncRaf = requestAnimationFrame(() => {
      syncRaf = 0
      // Basis "Hidden" writes activeRule._hidden before getRule() catches up
      nextTick(() => syncHiddenMarkersNow())
    })
  }

  function teardownMarkerObserver() {
    markerObserver?.disconnect()
    markerObserver = null
    if (markerPollTimer) {
      clearInterval(markerPollTimer)
      markerPollTimer = null
    }
  }

  function setupMarkerObserver() {
    teardownMarkerObserver()
    nextTick(() => {
      const wrapper = getActiveDesignerWrapperEl()
      if (!wrapper) return
      markerObserver = new MutationObserver(() => scheduleSyncHiddenMarkers())
      markerObserver.observe(wrapper, { childList: true, subtree: true })
      if (!markerPollTimer) {
        markerPollTimer = setInterval(() => scheduleSyncHiddenMarkers(), 800)
      }
      scheduleSyncHiddenMarkers()
    })
  }

  watch(designerShowHidden, () => scheduleSyncHiddenMarkers())
  watch(designerZoomPercent, () => scheduleSyncHiddenMarkers())
  watch(options.activeDesignerTab, () => {
    nextTick(() => {
      setupMarkerObserver()
      scheduleSyncHiddenMarkers()
    })
  })

  onUnmounted(() => {
    teardownMarkerObserver()
    if (syncRaf) cancelAnimationFrame(syncRaf)
  })

  return {
    designerShowHidden,
    designerZoomPercent,
    designerZoomStyle,
    scheduleSyncHiddenMarkers,
    setupMarkerObserver,
    teardownMarkerObserver,
  }
}
