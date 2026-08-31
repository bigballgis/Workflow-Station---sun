import { describe, it, expect } from 'vitest'
import { usePermissionFormatters } from '@/composables/permissions/usePermissionFormatters'
import type { PermissionRequestRecord } from '@/api/permission'

const { isBuJoinMembershipRequest, getMembershipTypeLabel, getRequestedRoleName } =
  usePermissionFormatters((key: string) => key)

function joinRow(overrides: Partial<PermissionRequestRecord> = {}): PermissionRequestRecord {
  return {
    id: '1',
    applicantId: 'u1',
    requestType: 'BUSINESS_UNIT_JOIN',
    targetId: 'bu-1',
    targetName: 'hase-hmdc',
    status: 'PENDING',
    createdAt: '2026-08-29T00:00:00Z',
    ...overrides,
  }
}

describe('usePermissionFormatters membership', () => {
  it('labels join requests as Member by default', () => {
    expect(getMembershipTypeLabel(joinRow({ membershipType: 'MEMBER' }))).toBe('permission.member')
    expect(getMembershipTypeLabel(joinRow({}))).toBe('permission.member')
  })

  it('labels Leader join requests', () => {
    expect(getMembershipTypeLabel(joinRow({ membershipType: 'LEADER' }))).toBe('permission.leader')
  })

  it('hides membership on exit and virtual-group requests', () => {
    expect(isBuJoinMembershipRequest(joinRow({ requestType: 'BUSINESS_UNIT_EXIT' }))).toBe(false)
    expect(getMembershipTypeLabel(joinRow({ requestType: 'VIRTUAL_GROUP_JOIN', membershipType: 'MEMBER' }))).toBe('-')
  })

  it('shows the requested role name', () => {
    expect(getRequestedRoleName(joinRow({ roleNames: ['HMDC_Approver_Role'] }))).toBe('HMDC_Approver_Role')
    expect(getRequestedRoleName(joinRow({ roleNames: [] }))).toBe('-')
  })
})
