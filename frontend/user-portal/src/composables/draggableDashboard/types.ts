// 可拖拽仪表盘共享类型定义
export interface DashboardWidget {
  id: string
  type: string
  title: string
  col: number
  row: number
  colSpan: number
  rowSpan: number
  props?: Record<string, any>
}

export interface WidgetType {
  type: string
  name: string
  description: string
  icon: any
  defaultColSpan: number
  defaultRowSpan: number
  component: any
}
