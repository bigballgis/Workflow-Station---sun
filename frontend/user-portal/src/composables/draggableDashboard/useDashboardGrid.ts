import { ref } from 'vue'
import type { DashboardWidget } from './types'

// 网格配置：列数 / 行高 / 间距，以及部件栅格样式
export function useDashboardGrid() {
  // 网格配置
  const columns = ref(12)
  const rowHeight = ref(100)
  const gap = ref(15)

  // 获取组件样式
  const getWidgetStyle = (widget: DashboardWidget) => {
    return {
      gridColumn: `${widget.col} / span ${widget.colSpan}`,
      gridRow: `${widget.row} / span ${widget.rowSpan}`,
      minHeight: `${widget.rowSpan * rowHeight.value}px`
    }
  }

  return {
    columns,
    rowHeight,
    gap,
    getWidgetStyle
  }
}
