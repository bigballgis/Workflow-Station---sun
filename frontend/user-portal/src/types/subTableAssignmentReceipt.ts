/** Only protected assignment endpoints return this versioned mutation receipt. */
export interface SubTableAssignmentReceipt {
  previousRowVersion?: number | null
  rowVersion?: number
  rowKey?: Record<string, unknown>
  storeKey?: string
  assigneeField?: string
}
