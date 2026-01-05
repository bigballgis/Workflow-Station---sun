// 通用业务组件导出
export { default as FormRenderer } from './FormRenderer.vue'
export { default as ProcessDiagram } from './ProcessDiagram.vue'
export { default as ProcessHistory } from './ProcessHistory.vue'
export { default as ActionButtons } from './ActionButtons.vue'
export { default as FileUploader } from './FileUploader.vue'
export { default as DraggableDashboard } from './DraggableDashboard.vue'

// 仪表盘小组件
export * from './widgets'

// 类型导出
export type { FormField } from './FormRenderer.vue'
export type { ProcessNode, ProcessFlow } from './ProcessDiagram.vue'
export type { HistoryRecord } from './ProcessHistory.vue'
export type { ActionButton } from './ActionButtons.vue'
export type { FileInfo } from './FileUploader.vue'
export type { DashboardWidget, WidgetType } from './DraggableDashboard.vue'
