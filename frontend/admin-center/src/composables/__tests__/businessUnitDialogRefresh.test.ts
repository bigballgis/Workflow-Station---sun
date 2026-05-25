import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { useBusinessUnitApprovers } from '../modules/useBusinessUnitApprovers'
import { useBusinessUnitMembers } from '../modules/useBusinessUnitMembers'

const mocks = vi.hoisted(() => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
  notifyConfirm: vi.fn(),
  businessUnitApi: {
    getApprovers: vi.fn(),
    addApprover: vi.fn(),
    removeApprover: vi.fn(),
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
    t: (key: string) => key
  })
}))

vi.mock('@/utils/notify', () => ({
  notifySuccess: mocks.notifySuccess,
  notifyError: mocks.notifyError,
  notifyConfirm: mocks.notifyConfirm
}))

vi.mock('@/api/businessUnit', () => ({
  businessUnitApi: mocks.businessUnitApi
}))

vi.mock('@/api/user', () => ({
  userApi: mocks.userApi
}))

describe('business unit dialog refresh composables', () => {
  const businessUnit = ref({
    id: 'bu-1',
    name: 'Finance',
    code: 'FIN',
    level: 1,
    sortOrder: 1,
    status: 'ACTIVE' as const,
    createdAt: '',
    updatedAt: ''
  })

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.notifyConfirm.mockResolvedValue(undefined)
  })

  it('approvers composable returns success flags after add/remove', async () => {
    mocks.businessUnitApi.getApprovers
      .mockResolvedValueOnce([{ id: 'ap-1', userId: 'user-1', userName: 'alice', userFullName: 'Alice' }])
      .mockResolvedValueOnce([{ id: 'ap-1', userId: 'user-1', userName: 'alice', userFullName: 'Alice' }])
      .mockResolvedValueOnce([])
    mocks.businessUnitApi.addApprover.mockResolvedValue(undefined)
    mocks.businessUnitApi.removeApprover.mockResolvedValue(undefined)

    const { approvers, selectedUserId, addApprover, removeApprover, fetchApprovers } =
      useBusinessUnitApprovers(businessUnit as any)

    await fetchApprovers()
    expect(approvers.value).toHaveLength(1)

    selectedUserId.value = 'user-1'
    await expect(addApprover()).resolves.toBe(true)
    expect(mocks.businessUnitApi.addApprover).toHaveBeenCalledWith('bu-1', 'user-1')

    await expect(removeApprover({ id: 'ap-1' } as any)).resolves.toBe(true)
    expect(mocks.businessUnitApi.removeApprover).toHaveBeenCalledWith('ap-1')
  })

  it('members composable returns success flags after add/remove', async () => {
    mocks.businessUnitApi.getMembers
      .mockResolvedValueOnce({ content: [{ id: 'user-2', username: 'bob', fullName: 'Bob' }] })
      .mockResolvedValueOnce({ content: [{ id: 'user-2', username: 'bob', fullName: 'Bob' }] })
      .mockResolvedValueOnce({ content: [] })
    mocks.businessUnitApi.addMember.mockResolvedValue(undefined)
    mocks.businessUnitApi.removeMember.mockResolvedValue(undefined)

    const { members, selectedUserId, addMember, removeMember, fetchMembers } =
      useBusinessUnitMembers(businessUnit as any)

    await fetchMembers()
    expect(members.value).toHaveLength(1)

    selectedUserId.value = 'user-2'
    await expect(addMember()).resolves.toBe(true)
    expect(mocks.businessUnitApi.addMember).toHaveBeenCalledWith('bu-1', 'user-2')

    await expect(removeMember({ id: 'user-2' })).resolves.toBe(true)
    expect(mocks.businessUnitApi.removeMember).toHaveBeenCalledWith('bu-1', 'user-2')
  })

  it('returns false when approver add fails', async () => {
    mocks.businessUnitApi.addApprover.mockRejectedValue(new Error('fail'))

    const { selectedUserId, addApprover } = useBusinessUnitApprovers(businessUnit as any)
    selectedUserId.value = 'user-3'

    await expect(addApprover()).resolves.toBe(false)
    expect(mocks.notifyError).toHaveBeenCalledTimes(1)
  })
})
