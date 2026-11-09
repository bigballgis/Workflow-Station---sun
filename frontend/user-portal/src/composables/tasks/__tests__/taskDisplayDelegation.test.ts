import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useTaskDisplay } from '@/composables/tasks/useTaskDisplay'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, named?: Record<string, unknown>) =>
      named ? `${key}:${Object.values(named).join(',')}` : key,
  }),
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ userInfo: { id: 'u-boss', username: 'boss' } }),
}))

describe('task page delegation status (D-55)', () => {
  it('keeps the delegate and says the single-task delegation is not in effect', () => {
    const task = ref<Record<string, unknown>>({
      assignee: 'u-boss',
      delegatedTargetType: 'USER',
      delegatedTo: 'u-deputy',
      delegationInactiveReason: 'DELEGATE_LOST_PORTAL_ENTRY',
    })
    const { getDelegationStatusDisplay } = useTaskDisplay(task)

    expect(getDelegationStatusDisplay()).toBe(
      'task.delegatedToUser:u-deputy · delegation.inactiveDelegateLostPortalEntry',
    )
  })

  it('shows the delegate alone while the delegation is in effect', () => {
    const task = ref<Record<string, unknown>>({ assignee: 'u-boss', delegatedTargetType: 'USER', delegatedTo: 'u-deputy' })
    const { getDelegationStatusDisplay } = useTaskDisplay(task)

    expect(getDelegationStatusDisplay()).toBe('task.delegatedToUser:u-deputy')
  })

  it('names the delegate when the task carries the name', () => {
    const task = ref<Record<string, unknown>>({
      assignee: 'u-boss', delegatedTargetType: 'USER', delegatedTo: 'user-e2e-wangfang', delegatedToName: 'Wang Fang',
    })
    const { getDelegationStatusDisplay } = useTaskDisplay(task)

    expect(getDelegationStatusDisplay()).toBe('task.delegatedToUser:Wang Fang')
  })

  it('names the business unit and role of a BU + Role delegation, codes when the names are unknown', () => {
    const named = ref<Record<string, unknown>>({
      assignee: 'u-boss', delegatedTargetType: 'BU_ROLE', delegatedBuCode: 'FIN', delegatedRoleCode: 'CLERK',
      delegatedBuName: 'Finance', delegatedRoleName: 'Clerk',
    })
    const unnamed = ref<Record<string, unknown>>({
      assignee: 'u-boss', delegatedTargetType: 'BU_ROLE', delegatedBuCode: 'FIN', delegatedRoleCode: 'CLERK',
    })

    expect(useTaskDisplay(named).getDelegationStatusDisplay()).toBe('task.delegatedToBuRole:Finance,Clerk')
    expect(useTaskDisplay(unnamed).getDelegationStatusDisplay()).toBe('task.delegatedToBuRole:FIN,CLERK')
  })
})
