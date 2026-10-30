export type AssignmentBackupMode = 'BU_ROLE' | 'USERS'
export type AssignmentBackupBuSource = 'CASE_START_BU' | 'NODE_ASSIGNMENT_BU' | 'FIXED_BU'

/** Complete editor state; serialization omits inactive source fields. */
export interface AssignmentExclusionDraft {
  schemaVersion: 1
  excludeInitiator: boolean
  excludePreviousHandlers: boolean
  excludeSelectedNodeHandlers: boolean
  selectedNodeKeys: string[]
  backup: {
    enabled: boolean
    mode: AssignmentBackupMode
    buSource: AssignmentBackupBuSource
    businessUnitCode: string
    roleCodes: string[]
    userRefs: string[]
  }
}
