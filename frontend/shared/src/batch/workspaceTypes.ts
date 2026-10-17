/** Local workspace payloads; persistence and authorization belong to each host. */
export interface PreparedBatchRows {
  rows: Array<Record<string, unknown>>
  primaryFormDataPatch?: Record<string, unknown>
  parentRowPatch?: Record<string, unknown>
}

export interface BatchWorkspaceField {
  fieldName: string
  isPrimaryKey?: boolean
  isForeignKey?: boolean
  isComputed?: boolean
  requiresAllocation?: boolean
  nullable?: boolean
}

/** Dedicated, typed operation declaration; never an arbitrary form field map. */
export interface SubTableBatchChange {
  batchControlId: string
  bindingId: string
  operation: 'EDIT' | 'ASSIGN' | 'DELETE' | 'ADD'
  rowPatches: Array<{
    rowId: string
    rowKey: Record<string, unknown>
    fields?: string[]
    assignment?: {
      mode: 'PERSON' | 'ROLE'
      assigneeId?: string
      roleCode?: string
      businessUnitCode?: string
    }
  }>
}

export interface BatchAssignmentCollection {
  rows: Array<Record<string, unknown>>
  change?: SubTableBatchChange
}

export interface BatchAssignmentConfig {
  allowUser: boolean
  allowRole: boolean
  assigneeField?: string
  roleField?: string
  buField?: string
}

export interface BatchAssignmentCapability {
  batchControlId: string
  bindingId: string
  assignmentConfig?: BatchAssignmentConfig
}
