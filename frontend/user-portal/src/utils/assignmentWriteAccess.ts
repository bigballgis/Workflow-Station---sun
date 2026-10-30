import type { AssignmentAccessTask } from '@/types/assignmentWriteAccess'

/** Additional UI restriction only; legacy permissions and server-side final checks still apply. */
export function isAssignmentWriteBlocked(task: AssignmentAccessTask): boolean {
  if (task.assignmentLocalDraftRetained || task.assignmentWriteCheckPending) return true
  if (task.assignmentWriteAccess) return task.assignmentWriteAccess.canWrite !== true
  const claim = task.assignmentClaimAccess
  return claim != null && claim.state !== 'LEGACY'
}
