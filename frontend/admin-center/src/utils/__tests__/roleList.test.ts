import { describe, expect, it } from 'vitest'
import type { Role } from '@/api/role'
import {
  SYSTEM_ROLE_LIST_CODES,
  filterSortRoles,
  isCustomRoleListEntry,
  isSystemRoleListEntry,
  paginateRoles,
} from '../roleList'

function role(partial: Partial<Role> & Pick<Role, 'id' | 'code' | 'name' | 'type'>): Role {
  return {
    status: 'ACTIVE',
    createdAt: '2026-01-01',
    isSystem: false,
    ...partial,
  }
}

describe('roleList', () => {
  const sample: Role[] = [
    role({ id: '1', code: 'SYS_ADMIN', name: 'System Administrator', type: 'ADMIN', isSystem: true }),
    role({ id: '2', code: 'AUDITOR', name: 'Auditor', type: 'AUDITOR', isSystem: true }),
    role({ id: '3', code: 'MANAGER', name: 'Department Manager', type: 'BU_BOUNDED', isSystem: false }),
    role({ id: '4', code: 'TECH_LEAD', name: 'Technical Lead', type: 'DEVELOPER', isSystem: true }),
    role({ id: '6', code: 'DEVELOPER', name: 'Developer', type: 'DEVELOPER', isSystem: true }),
    role({ id: '7', code: 'FU_VIEWER', name: 'Function Unit Viewer', type: 'DEVELOPER', isSystem: true }),
    role({ id: '8', code: 'CUSTOM_A', name: 'Custom A', type: 'BU_BOUNDED', isSystem: false }),
  ]

  it('system tab lists the four platform roles only, excludes FU_VIEWER', () => {
    const system = filterSortRoles(sample, 'SYSTEM', '')
    expect(system.map((r) => r.code).sort()).toEqual(['AUDITOR', 'DEVELOPER', 'SYS_ADMIN', 'TECH_LEAD'])
    expect(SYSTEM_ROLE_LIST_CODES.size).toBe(4)
    expect(sample.filter(isSystemRoleListEntry).some((r) => r.code === 'FU_VIEWER')).toBe(false)
  })

  it('custom tab is BU_BOUNDED non-system only, Department Manager included', () => {
    const custom = filterSortRoles(sample, 'CUSTOM', '')
    expect(custom.map((r) => r.code)).toEqual(['CUSTOM_A', 'MANAGER'])
    expect(sample.filter(isCustomRoleListEntry)).toHaveLength(2)
  })

  it('paginates after filter', () => {
    const filtered = filterSortRoles(sample, 'SYSTEM', '')
    expect(paginateRoles(filtered, 1, 2)).toHaveLength(2)
    expect(paginateRoles(filtered, 2, 2)).toHaveLength(2)
    expect(paginateRoles(filtered, 3, 2)).toHaveLength(0)
  })
})
