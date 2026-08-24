import type {
  CompletedTaskFormData,
  ProcessFormData,
  TaskFormSnapshot,
} from '@/api/processForm'

const SNAPSHOT_PREFIX = '_snapshot_'

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

export function emptyProcessFormRef(processInstanceId: string): ProcessFormData {
  return {
    processInstanceId,
    formName: '',
    formType: 'PROCESS',
    configJson: {},
    fieldValues: {},
    subTableBindings: [],
    editable: false,
    processState: '',
  }
}

export function hasSnapshotFieldValues(fieldValues: Record<string, unknown> | undefined): boolean {
  if (!fieldValues) return false
  return Object.keys(fieldValues).some(key => key !== '__subTables__')
}

/**
 * Rebuild completed-form payload from process variables already returned to the client.
 * History snapshot links often 403 on `/completed-form` while `getProcessDetail` still
 * includes `_snapshot_{taskId}` — displaying that copy does not widen API access.
 */
export function extractCompletedFormFromVariables(
  variables: unknown,
  taskId: string,
  processFormRef: ProcessFormData,
): CompletedTaskFormData | null {
  if (!isPlainObject(variables) || !taskId) return null
  const raw = variables[SNAPSHOT_PREFIX + taskId]
  if (!isPlainObject(raw)) return null
  const fieldValues = isPlainObject(raw.fieldValues) ? raw.fieldValues : {}
  const liveValues: Record<string, unknown> = {}
  for (const key of Object.keys(fieldValues)) {
    if (Object.prototype.hasOwnProperty.call(variables, key)) {
      liveValues[key] = variables[key]
    }
  }
  const snapshot: TaskFormSnapshot = {
    taskId: typeof raw.taskId === 'string' ? raw.taskId : taskId,
    taskDefinitionKey: typeof raw.taskDefinitionKey === 'string' ? raw.taskDefinitionKey : '',
    assignee: typeof raw.assignee === 'string' ? raw.assignee : '',
    completedAt: typeof raw.completedAt === 'string' ? raw.completedAt : '',
    fieldValues,
  }
  return {
    snapshot,
    liveValues,
    showLiveValues: true,
    processFormRef,
  }
}
