export type BatchBusinessUnit = { id: string; name: string; children?: BatchBusinessUnit[] }
export type BatchUserOption = { id: string; username: string; name: string }
export type BatchRoleOption = { label: string; value: string }

/** Host-owned directory: live Portal endpoints or explicit, local Preview samples. */
export interface BatchAssignmentDirectory {
  error: { value: string }
  businessUnits: { value: BatchBusinessUnit[] }
  userOptions: Record<string, BatchUserOption[]>
  userLoading: Record<string, boolean>
  report: (cause: unknown) => void
  loadBusinessUnits: () => Promise<void>
  requireBusinessUnitCode: (id: string) => string
  requireBusinessUnitId: (code: string) => string
  loadRoles: (businessUnitId?: string) => Promise<BatchRoleOption[]>
  searchUsers: (key: string, query: string) => void
}
