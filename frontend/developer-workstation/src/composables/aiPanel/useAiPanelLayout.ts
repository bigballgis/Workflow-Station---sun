import { ref, reactive, computed } from 'vue'

/**
 * Composable for the AI panel's docked/detached layout: detach toggle, the
 * computed panel style, and the drag (header handle) + resize interactions for
 * the detached (pop-out) window.
 *
 * Docked mode is a full-screen takeover (positioning lives in AiPanel CSS);
 * only the detached window needs inline geometry.
 */
export function useAiPanelLayout() {
  // Detach / pop-out state（默认弹出小窗；全屏为切换态）
  const isDetached = ref(true)
  const detachedSize = reactive({ width: 1100, height: 720 })
  const dragPos = reactive({ x: 0, y: 0 })
  centerWindow()
  const isDragging = ref(false)
  const isResizing = ref(false)

  function centerWindow() {
    const vw = window.innerWidth
    const vh = window.innerHeight
    dragPos.x = Math.max(0, (vw - detachedSize.width) / 2)
    dragPos.y = Math.max(0, (vh - detachedSize.height) / 2)
  }

  const panelStyle = computed(() => {
    if (isDetached.value) {
      return {
        left: `${dragPos.x}px`,
        top: `${dragPos.y}px`,
        width: `${detachedSize.width}px`,
        height: `${detachedSize.height}px`
      }
    }
    return {}
  })

  function toggleDetach() {
    isDetached.value = !isDetached.value
    if (isDetached.value) {
      centerWindow()
    }
  }

  // Drag logic (header as handle)
  function onHeaderMouseDown(e: MouseEvent) {
    if (!isDetached.value) return
    // Don't drag if clicking a button
    if ((e.target as HTMLElement).closest('button, .el-button')) return
    isDragging.value = true
    const startX = e.clientX - dragPos.x
    const startY = e.clientY - dragPos.y

    function onMouseMove(ev: MouseEvent) {
      dragPos.x = ev.clientX - startX
      dragPos.y = ev.clientY - startY
    }
    function onMouseUp() {
      isDragging.value = false
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }

  // Resize logic
  function onResizeMouseDown(e: MouseEvent) {
    if (!isDetached.value) return
    e.preventDefault()
    isResizing.value = true
    const startX = e.clientX
    const startY = e.clientY
    const startW = detachedSize.width
    const startH = detachedSize.height

    function onMouseMove(ev: MouseEvent) {
      detachedSize.width = Math.max(600, startW + (ev.clientX - startX))
      detachedSize.height = Math.max(400, startH + (ev.clientY - startY))
    }
    function onMouseUp() {
      isResizing.value = false
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }

  return {
    isDetached,
    dragPos,
    detachedSize,
    isDragging,
    isResizing,
    panelStyle,
    toggleDetach,
    onHeaderMouseDown,
    onResizeMouseDown
  }
}
