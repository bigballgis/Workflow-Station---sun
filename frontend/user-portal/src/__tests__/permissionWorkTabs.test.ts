import { describe, it, expect } from 'vitest'
import {
  resolvePrimaryWorkTab,
  shouldShowPendingApprovalsBanner
} from '../utils/permissionWorkTabs'

describe('permissionWorkTabs', () => {
  it('non-approver always uses myRequests and hides banner', () => {
    const opts = { isApprover: false, approvalPendingCount: 5 }
    expect(resolvePrimaryWorkTab(opts)).toBe('myRequests')
    expect(shouldShowPendingApprovalsBanner(opts)).toBe(false)
  })

  it('approver with no pending defaults to approvals and hides banner', () => {
    const opts = { isApprover: true, approvalPendingCount: 0 }
    expect(resolvePrimaryWorkTab(opts)).toBe('approvals')
    expect(shouldShowPendingApprovalsBanner(opts)).toBe(false)
  })

  it('approver with pending defaults to approvals and shows banner', () => {
    const opts = { isApprover: true, approvalPendingCount: 3 }
    expect(resolvePrimaryWorkTab(opts)).toBe('approvals')
    expect(shouldShowPendingApprovalsBanner(opts)).toBe(true)
  })

  it('treats negative pending count as zero for banner only', () => {
    const opts = { isApprover: true, approvalPendingCount: -2 }
    expect(resolvePrimaryWorkTab(opts)).toBe('approvals')
    expect(shouldShowPendingApprovalsBanner(opts)).toBe(false)
  })
})
