/** Informational claim restriction; existing role/workspace permission and final checks still apply. */
export interface AssignmentClaimAccess {
  taskId: string
  tracked: boolean | null
  state: 'LEGACY' | 'CURRENT' | 'RECONCILING' | 'SYNC_PENDING' | 'BLOCKED_NO_ELIGIBLE_USER'
    | 'NOT_ACTIVE' | 'CONTEXT_UNAVAILABLE' | 'DEPENDENCY_UNAVAILABLE' | 'ACTOR_INACTIVE'
  claim: { exclusionAllowsClaim: boolean; poolSource: string | null; holderSource: string | null;
    holding?: { userId: string | null } | null }
  stamp: { caseRevision: number | null; authorityRevision: number; validUntil: string | null } | null
}
