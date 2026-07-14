export type PrimaryWorkTab = 'myRequests' | 'approvals'

function normalizePendingCount(count: number): number {
  return Math.max(0, count)
}

export function resolvePrimaryWorkTab(opts: {
  isApprover: boolean
  approvalPendingCount: number
}): PrimaryWorkTab {
  // Approvers always default to My approvals (pending count only drives banner).
  if (!opts.isApprover) return 'myRequests'
  return 'approvals'
}

export function shouldShowPendingApprovalsBanner(opts: {
  isApprover: boolean
  approvalPendingCount: number
}): boolean {
  return opts.isApprover && normalizePendingCount(opts.approvalPendingCount) > 0
}
