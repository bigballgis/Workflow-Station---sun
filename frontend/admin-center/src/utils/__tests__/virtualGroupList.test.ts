import { describe, expect, it } from 'vitest'
import type { VirtualGroup } from '@/api/virtualGroup'
import {
  filterSortVirtualGroups,
  filterVirtualGroupsByType,
  paginateVirtualGroups,
  sortVirtualGroupsByName,
} from '../virtualGroupList'

function vg(partial: Partial<VirtualGroup> & Pick<VirtualGroup, 'id' | 'name' | 'type'>): VirtualGroup {
  return {
    code: partial.code || partial.id,
    status: 'ACTIVE',
    memberCount: 0,
    createdAt: '',
    updatedAt: '',
    ...partial,
  }
}

describe('virtualGroupList', () => {
  const sample = [
    vg({ id: '1', name: 'zeta', type: 'CUSTOM', code: 'Z' }),
    vg({ id: '2', name: 'Alpha', type: 'SYSTEM', code: 'A' }),
    vg({ id: '3', name: 'beta', type: 'DEVELOPER', code: 'B', adGroup: 'DevTeam' }),
    vg({ id: '4', name: 'Managers', type: 'CUSTOM', code: 'MANAGERS', boundRoleName: 'Dept Mgr' }),
  ]

  it('filters by type', () => {
    expect(filterVirtualGroupsByType(sample, 'SYSTEM').map((g) => g.id)).toEqual(['2'])
    expect(filterVirtualGroupsByType(sample, 'CUSTOM').map((g) => g.id)).toEqual(['1', '4'])
    expect(filterVirtualGroupsByType(sample, 'DEVELOPER').map((g) => g.id)).toEqual(['3'])
  })

  it('sorts by name case-insensitively', () => {
    const names = sortVirtualGroupsByName(sample, 'en').map((g) => g.name)
    expect(names).toEqual(['Alpha', 'beta', 'Managers', 'zeta'])
  })

  it('filters by keyword across name/code/adGroup/boundRole', () => {
    const r1 = filterSortVirtualGroups(sample, 'CUSTOM', 'manag', 'en')
    expect(r1.map((g) => g.id)).toEqual(['4'])
    const r2 = filterSortVirtualGroups(sample, 'DEVELOPER', 'devteam', 'en')
    expect(r2.map((g) => g.id)).toEqual(['3'])
  })

  it('paginates', () => {
    const sorted = sortVirtualGroupsByName(sample, 'en')
    expect(paginateVirtualGroups(sorted, 1, 2).map((g) => g.name)).toEqual(['Alpha', 'beta'])
    expect(paginateVirtualGroups(sorted, 2, 2).map((g) => g.name)).toEqual(['Managers', 'zeta'])
  })
})
