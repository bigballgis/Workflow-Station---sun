import { ref } from 'vue'
import type { Ref } from 'vue'
import type { DashboardWidget } from './types'

interface DragResizeOptions {
  layoutWidgets: Ref<DashboardWidget[]>
  columns: Ref<number>
  rowHeight: Ref<number>
  gap: Ref<number>
  emitUpdate: () => void
}

// 部件拖拽与缩放：拖拽换位、鼠标拖拽缩放跨度
export function useWidgetDragResize(options: DragResizeOptions) {
  const { layoutWidgets, columns, rowHeight, gap, emitUpdate } = options

  // 拖拽状态
  const draggingId = ref<string | null>(null)
  const dragOverId = ref<string | null>(null)
  const resizingId = ref<string | null>(null)

  // 拖拽开始
  const handleDragStart = (e: DragEvent, widget: DashboardWidget) => {
    draggingId.value = widget.id
    if (e.dataTransfer) {
      e.dataTransfer.effectAllowed = 'move'
      e.dataTransfer.setData('text/plain', widget.id)
    }
  }

  // 拖拽结束
  const handleDragEnd = () => {
    draggingId.value = null
    dragOverId.value = null
  }

  // 拖拽经过
  const handleDragOver = (_e: DragEvent, widget: DashboardWidget) => {
    if (draggingId.value && draggingId.value !== widget.id) {
      dragOverId.value = widget.id
    }
  }

  // 放置
  const handleDrop = (_e: DragEvent, targetWidget: DashboardWidget) => {
    if (!draggingId.value || draggingId.value === targetWidget.id) return

    const sourceIndex = layoutWidgets.value.findIndex(w => w.id === draggingId.value)
    const targetIndex = layoutWidgets.value.findIndex(w => w.id === targetWidget.id)

    if (sourceIndex !== -1 && targetIndex !== -1) {
      // 交换位置
      const sourceWidget = layoutWidgets.value[sourceIndex]
      const tempCol = sourceWidget.col
      const tempRow = sourceWidget.row

      sourceWidget.col = targetWidget.col
      sourceWidget.row = targetWidget.row
      targetWidget.col = tempCol
      targetWidget.row = tempRow

      emitUpdate()
    }

    draggingId.value = null
    dragOverId.value = null
  }

  // 开始调整大小
  const startResize = (e: MouseEvent, widget: DashboardWidget) => {
    e.preventDefault()
    resizingId.value = widget.id

    const startX = e.clientX
    const startY = e.clientY
    const startColSpan = widget.colSpan
    const startRowSpan = widget.rowSpan

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const deltaX = moveEvent.clientX - startX
      const deltaY = moveEvent.clientY - startY

      const colDelta = Math.round(deltaX / (100 + gap.value))
      const rowDelta = Math.round(deltaY / (rowHeight.value + gap.value))

      widget.colSpan = Math.max(1, Math.min(columns.value - widget.col + 1, startColSpan + colDelta))
      widget.rowSpan = Math.max(1, Math.min(6, startRowSpan + rowDelta))
    }

    const handleMouseUp = () => {
      resizingId.value = null
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
      emitUpdate()
    }

    document.addEventListener('mousemove', handleMouseMove)
    document.addEventListener('mouseup', handleMouseUp)
  }

  return {
    draggingId,
    dragOverId,
    resizingId,
    handleDragStart,
    handleDragEnd,
    handleDragOver,
    handleDrop,
    startResize
  }
}
