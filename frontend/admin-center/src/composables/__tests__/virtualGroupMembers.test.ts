import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { useVirtualGroupMembers } from '../modules/useVirtualGroupMembers'
import type { VirtualGroup, VirtualGroupMember } from '@/api/virtualGroup'

const mocks = vi.hoisted(() => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
  notifyWarning: vi.fn(),
  notifyConfirm: vi.fn(),
  virtualGroupApi: {
    getMembers: vi.fn(),
    addMember: vi.fn(),
    removeMember: vi.fn()
  },
  userApi: {
    list: vi.fn()
  }
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key)
  })
}))

vi.mock('@/utils/notify', () => ({
  notifySuccess: mocks.notifySuccess,
  notifyError: mocks.notifyError,
  notifyWarning: mocks.notifyWarning,
  notifyConfirm: mocks.notifyConfirm
}))

vi.mock('@/api/virtualGroup', () => ({
  virtualGroupApi: mocks.virtualGroupApi
}))

vi.mock('@/api/user', () => ({
  userApi: mocks.userApi
}))

function group(overrides: Partial<VirtualGroup>): VirtualGroup {
  return {
    id: 'vg-1', name: 'Group', code: 'GROUP', type: 'CUSTOM', status: 'ACTIVE', memberCount: 0,
    createdAt: '', updatedAt: '', publicTeam: false, systemAdministrators: false, ...overrides
  }
}

function member(userId: string, listType: VirtualGroupMember['listType'],
  sources: VirtualGroupMember['sources']): VirtualGroupMember {
  return {
    id: `${userId}-${listType}`, groupId: 'vg-1', userId, username: userId, fullName: `Name ${userId}`,
    role: 'MEMBER', listType, sources, joinedAt: ''
  }
}

describe('virtual group members (platform access model 5.4 / 5.5)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.notifyConfirm.mockResolvedValue(undefined)
    mocks.virtualGroupApi.addMember.mockResolvedValue(undefined)
    mocks.virtualGroupApi.removeMember.mockResolvedValue(undefined)
  })

  it('shows a team as two lists and a custom group as one', async () => {
    mocks.virtualGroupApi.getMembers.mockResolvedValue([
      member('u1', 'DEVELOPER', ['AD', 'MANUAL']),
      member('u1', 'TECH_LEAD', ['MANUAL']),
      member('u2', 'DEVELOPER', ['AD'])
    ])
    const team = useVirtualGroupMembers(ref(group({ type: 'DEVELOPER' })))
    await team.loadMembers()

    expect(team.lists.value).toEqual(['DEVELOPER', 'TECH_LEAD'])
    expect(team.activeList.value).toBe('DEVELOPER')
    expect(team.listMembers.value.map(m => m.userId)).toEqual(['u1', 'u2'])
    expect(team.countOf('TECH_LEAD')).toBe(1)
    team.activeList.value = 'TECH_LEAD'
    expect(team.listMembers.value.map(m => m.userId)).toEqual(['u1'])

    const custom = useVirtualGroupMembers(ref(group({ type: 'CUSTOM' })))
    expect(custom.lists.value).toEqual(['MEMBER'])
  })

  it('adds a manual member to the chosen list', async () => {
    mocks.virtualGroupApi.getMembers.mockResolvedValue([])
    const team = useVirtualGroupMembers(ref(group({ type: 'DEVELOPER' })))
    await team.loadMembers()
    team.activeList.value = 'TECH_LEAD'
    team.openAddDialog()

    expect(team.newMember.listType).toBe('TECH_LEAD')
    team.newMember.userId = 'u3'
    await team.addMember()

    expect(mocks.notifyConfirm).not.toHaveBeenCalled()
    expect(mocks.virtualGroupApi.addMember).toHaveBeenCalledWith('vg-1', { userId: 'u3', listType: 'TECH_LEAD' })
  })

  it('revokes only the manual membership of the row list', async () => {
    mocks.virtualGroupApi.getMembers.mockResolvedValue([])
    const team = useVirtualGroupMembers(ref(group({ type: 'DEVELOPER' })))

    await team.revokeManual(member('u1', 'DEVELOPER', ['AD', 'MANUAL']))

    expect(mocks.notifyConfirm.mock.calls[0][0]).toContain('virtualGroup.revokeManualConfirm')
    expect(mocks.virtualGroupApi.removeMember).toHaveBeenCalledWith('vg-1', 'u1', 'DEVELOPER')
  })

  it('asks before adding to or revoking from System Administrators, and stops on cancel', async () => {
    mocks.virtualGroupApi.getMembers.mockResolvedValue([])
    const admins = useVirtualGroupMembers(ref(group({ type: 'SYSTEM', systemAdministrators: true })))
    admins.newMember.userId = 'u4'
    admins.userOptions.value = [{ id: 'u4', username: 'dana', fullName: 'Dana' } as never]

    mocks.notifyConfirm.mockRejectedValueOnce('cancel')
    await admins.addMember()
    expect(mocks.notifyConfirm.mock.calls[0][0]).toContain('virtualGroup.adminAddConfirm:{"name":"Dana"}')
    expect(mocks.virtualGroupApi.addMember).not.toHaveBeenCalled()

    await admins.addMember()
    expect(mocks.virtualGroupApi.addMember).toHaveBeenCalledWith('vg-1', { userId: 'u4', listType: 'MEMBER' })

    mocks.notifyConfirm.mockRejectedValueOnce('cancel')
    await admins.revokeManual(member('u4', 'MEMBER', ['MANUAL']))
    expect(mocks.notifyConfirm.mock.calls[2][0]).toContain('virtualGroup.adminRevokeConfirm')
    expect(mocks.virtualGroupApi.removeMember).not.toHaveBeenCalled()
  })
})
