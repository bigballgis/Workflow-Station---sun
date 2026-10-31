import type { TaskInfo } from '@/api/task'
import type { AssignmentAccessTask } from '@/types/assignmentWriteAccess'
import type { TaskDetailCtx } from './context'
import { isAssignmentWriteBlocked } from '@/utils/assignmentWriteAccess'

/** Before any hydration/reset: preserve the mounted local form when the same task loses write eligibility. */
export function retainAssignmentDraft(ctx: TaskDetailCtx, incoming: Partial<TaskInfo> & Pick<AssignmentAccessTask, 'assignmentLocalDraftRetained'>,
  wasEditable = !ctx.taskForm.formReadOnly.value): boolean {
  const previous = ctx.taskInfo.value
  if (!ctx.formRenderReady.value || previous.taskId !== ctx.taskId || incoming.taskId !== ctx.taskId) return false
  const retained = previous.assignmentLocalDraftRetained === true
  if (!retained && (!wasEditable || !isAssignmentWriteBlocked(incoming))) return false
  ctx.taskForm.clearAutosaveTimer()
  ctx.taskForm.formReadOnly.value = true
  ctx.taskInfo.value = { ...incoming, variables: previous.variables, assignmentLocalDraftRetained: true }
  ctx.isCompletedTask.value = ctx.isCompletedTaskData(incoming) || ctx.hasCompletedSnapshotRoute()
  if (ctx.isCompletedTask.value) ctx.bpmn.currentNodeId.value = ''
  ctx.taskError.value = null
  return true
}

/** No new server decision exists on a failed read. Keep only the already mounted draft, under a client-only write veto. */
export function retainAssignmentDraftAfterReadFailure(ctx: TaskDetailCtx, wasEditable: boolean): boolean {
  return retainAssignmentDraft(ctx, { ...ctx.taskInfo.value, assignmentLocalDraftRetained: true }, wasEditable)
}

/** Normal reloads still clear MI/binding caches; retained drafts never enter this path. */
export function resetTaskDetailForHydration(ctx: TaskDetailCtx): void {
  ctx.loading.value = true
  ctx.formRenderReady.value = false
  ctx.fuFormSubTableFields.value = []
  ctx.detailUiPhase.value = 0
  ctx.diagramInViewport.value = false
  ctx.disconnectDiagramViewportObserver()
  ctx.deferredNodeFormMapContent = null
  ctx.nodeFormMap.value = new Map()
  ctx.previousForms.value = []
  ctx.subTableBindings.value = []
  ctx.taskError.value = null
  ctx.taskForm.formReadOnly.value = false
}
