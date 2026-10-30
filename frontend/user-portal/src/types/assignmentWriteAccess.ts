import type { AssignmentClaimAccess } from './assignmentClaimAccess'

export interface AssignmentWriteAccess {
  canWrite: boolean
  evaluation: {
    contractVersion: number
    tracked: boolean | null
    exclusionAllowsWrite: boolean
    reason: string
    stamp: AssignmentClaimAccess['stamp']
  } | null
}

export interface AssignmentAccessTask {
  /** Client memory only: a qualification change retained the displayed local form. */
  assignmentLocalDraftRetained?: boolean
  assignmentWriteCheckPending?: boolean
  assignmentClaimAccess?: AssignmentClaimAccess | null
  assignmentWriteAccess?: AssignmentWriteAccess | null
}
