/**
 * Change-history rows about who holds a task: claims, releases, reassignments, hand-overs to a manager, and the end of
 * a single-task delegation. Values are i18n keys; `useChangeHistoryFormatting` translates them.
 */

/** Assignment changes the server makes itself are written with operator id `system` and a reason. */
export const SYSTEM_OPERATOR_ID = 'system'

const ASSIGNMENT_CHANGE_TYPES = new Set(['CLAIM', 'UNCLAIM', 'FORCE_UNCLAIM', 'REASSIGN', 'MANAGER_REASSIGN', 'DELEGATION_ENDED'])

export function isAssignmentChangeType(changeType: string | undefined): boolean {
  return !!changeType && ASSIGNMENT_CHANGE_TYPES.has(changeType)
}

/** Why the assignment changed (`reason`): the system's own changes, and a delegation ended by a change of holder. */
export const ASSIGNMENT_REASON_KEYS: Partial<Record<string, string>> = {
  POOL_MEMBERSHIP_LOST: 'changeHistory.releasedPoolMembershipLost',
  ASSIGNEE_DEPARTED: 'changeHistory.reassignedAssigneeDeparted',
  HOLDER_CHANGED: 'changeHistory.delegationEndedHolderChanged',
}

/** The field an assignment row changed: the delegation (`delegated_to`) or, otherwise, who holds the task. */
export function assignmentFieldKey(fieldName: string | undefined): string {
  return fieldName === 'delegated_to' ? 'changeHistory.delegatedTo' : 'changeHistory.claimedBy'
}

export const ASSIGNMENT_TYPE_LABEL_KEYS: Partial<Record<string, string>> = {
  CLAIM: 'changeHistory.claim',
  UNCLAIM: 'changeHistory.unclaim',
  FORCE_UNCLAIM: 'changeHistory.forceUnclaim',
  REASSIGN: 'changeHistory.reassign',
  MANAGER_REASSIGN: 'changeHistory.managerReassign',
  DELEGATION_ENDED: 'changeHistory.delegationEnded',
}

/** Tag colours; a type left out (DELEGATION_ENDED) shows as `info`. */
export const ASSIGNMENT_TYPE_TAGS: Partial<Record<string, 'success' | 'warning' | 'danger' | 'info'>> = {
  CLAIM: 'success',
  UNCLAIM: 'warning',
  FORCE_UNCLAIM: 'danger',
  REASSIGN: 'warning',
  MANAGER_REASSIGN: 'warning',
}
