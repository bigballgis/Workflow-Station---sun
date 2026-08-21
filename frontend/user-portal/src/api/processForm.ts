import { request } from './request'
import type { RequestIdConfig } from '../utils/formFieldMeta'
import type { SensitiveMaskConfig } from '@/utils/sensitiveMask'

// --- TypeScript interfaces matching backend DTOs ---

export interface SubTableBindingData {
  bindingId: number
  tableName: string
  bindingType: string
  bindingMode: string
  columns: Array<Record<string, unknown>>
  data: Array<Record<string, unknown>>
  /** MI assignment contract (allowUser/allowRole/assigneeField/roleField/buField) parsed from BPMN. */
  assignmentConfig?: Record<string, unknown>
}

export interface ProcessFormData {
  processInstanceId: string
  formName: string
  formType: string
  configJson: Record<string, unknown>
  fieldValues: Record<string, unknown>
  subTableBindings: SubTableBindingData[]
  editable: boolean
  processState: string
  requestIdConfig?: RequestIdConfig | null
}

export interface TaskFormData {
  taskId: string
  taskDefinitionKey: string
  formName: string
  configJson: Record<string, unknown>
  fieldPermissions: Record<string, string>
  fieldValues: Record<string, unknown>
  subTableBindings: SubTableBindingData[]
  processFormRef: ProcessFormData
  formReadOnly?: boolean
  requestIdConfig?: RequestIdConfig | null
}

export interface TaskFormSnapshot {
  taskId: string
  taskDefinitionKey: string
  assignee: string
  completedAt: string
  fieldValues: Record<string, unknown>
}

export interface CompletedTaskFormData {
  snapshot: TaskFormSnapshot
  liveValues: Record<string, unknown>
  showLiveValues: boolean
  processFormRef: ProcessFormData
}

export interface TaskFormSubmitRequest {
  formData: Record<string, unknown>
  subTableData?: Record<string, Array<Record<string, unknown>>>
  baselineValues?: Record<string, unknown>
}

export interface ChangeHistoryRecord {
  id: number
  processInstanceId: string
  taskInstanceId: string | null
  stageId: string | null
  /** BPMN 任务名称（后端由任务历史解析） */
  stageName?: string | null
  userId: string
  userName: string
  timestamp: string
  fieldName: string
  fieldLabel: string | null
  fieldOrder?: number | null
  oldValue: string | null
  newValue: string | null
  changeType: string
  subTableName: string | null
  rowIdentifier: string | null
  concurrent: boolean
}

// --- API functions ---

export function getProcessFormData(processInstanceId: string) {
  return request.get<{ data: ProcessFormData }>(`/processes/${processInstanceId}/form`)
}

export function submitProcessFormUpdate(processInstanceId: string, data: Record<string, unknown>) {
  return request.put<{ data: void }>(`/processes/${processInstanceId}/form`, data)
}

export function getTaskFormData(taskId: string) {
  return request.get<{ data: TaskFormData }>(`/tasks/${taskId}/form-data`)
}

export function submitTaskForm(taskId: string, data: TaskFormSubmitRequest) {
  return request.post<{ data: void }>(`/tasks/${taskId}/submit`, data)
}

export function getCompletedTaskFormData(taskId: string) {
  return request.get<{ data: CompletedTaskFormData }>(`/tasks/${taskId}/completed-form`)
}

export function submitActionFormPopup(taskId: string, actionId: string, formData: Record<string, unknown>) {
  return request.post<{ data: void }>(`/tasks/${taskId}/actions/${actionId}/form-popup-submit`, { formData })
}

export function getChangeHistory(processInstanceId: string, rowIdentifier?: string, taskId?: string) {
  const query: string[] = []
  if (rowIdentifier) query.push('rowIdentifier=' + encodeURIComponent(rowIdentifier))
  if (taskId) query.push('taskId=' + encodeURIComponent(taskId))
  const url = `/processes/${processInstanceId}/change-history` + (query.length > 0 ? '?' + query.join('&') : '')
  return request.get<{ data: ChangeHistoryRecord[] }>(url)
}

/** Display-only mask configs for Change History (available whenever CH is). */
export function getChangeHistorySensitiveMasks(processInstanceId: string) {
  return request.get<{ data: Record<string, SensitiveMaskConfig> }>(
    `/processes/${processInstanceId}/change-history/sensitive-masks`,
  )
}
