import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import DelegationRulesList from '@/components/delegations/DelegationRulesList.vue'
import DelegationAuditList from '@/components/delegations/DelegationAuditList.vue'
import { queryDelegationAudit, queryDelegationRules } from '@/api/delegation'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/delegation', () => ({
  queryDelegationRules: vi.fn(),
  queryDelegationAudit: vi.fn(),
  suspendDelegationRule: vi.fn(),
  resumeDelegationRule: vi.fn(),
  deleteDelegationRule: vi.fn(),
}))
vi.mock('@/composables/list/searchListFilterUsers', () => ({
  searchListFilterUsers: vi.fn(async () => []),
}))

const user = (field: string, label: string) =>
  ({ field, label, kind: 'USER', filterable: true, sortable: true, operators: ['eq'] })

let wrapper: VueWrapper | null = null

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

/** The Delegations page names people; a stored id shows only when no name is known. */
describe('delegation lists name the people', () => {
  it('My Delegations shows the delegate by name, or the id when unknown', async () => {
    vi.mocked(queryDelegationRules).mockResolvedValue({
      data: {
        columns: [user('delegateId', 'delegation.delegateTo')],
        content: [
          { id: 1, delegatorId: 'me', delegateId: 'user-e2e-zhangwei', delegateName: 'Zhang Wei', status: 'ACTIVE' },
          { id: 2, delegatorId: 'me', delegateId: 'gone-user', delegateName: null, status: 'ACTIVE' },
        ],
        totalElements: 2, page: 0, size: 20,
      },
    } as never)

    wrapper = mount(DelegationRulesList, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Zhang Wei')
    expect(text).not.toContain('user-e2e-zhangwei')
    expect(text).toContain('gone-user')
  })

  it('the audit names both parties and keeps a BU + Role target as it is', async () => {
    vi.mocked(queryDelegationAudit).mockResolvedValue({
      data: {
        columns: [user('delegatorId', 'delegation.delegator'), user('delegateId', 'delegation.delegate')],
        content: [
          { id: 1, delegatorId: 'me', delegatorName: 'Boss', delegateId: 'user-e2e-wangfang', delegateName: 'Wang Fang',
            operationType: 'DELEGATE_TASK', createdAt: '2026-11-02T09:00:00' },
          { id: 2, delegatorId: 'me', delegatorName: 'Boss', delegateId: 'BU_ROLE:FIN/CLERK', delegateName: null,
            operationType: 'DELEGATE_TASK', createdAt: '2026-11-01T09:00:00' },
        ],
        totalElements: 2, page: 0, size: 20,
      },
    } as never)

    wrapper = mount(DelegationAuditList, { global: { plugins: [ElementPlus] } })
    ;(wrapper.vm as unknown as { ensureLoaded: () => void }).ensureLoaded()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Wang Fang')
    expect(text).toContain('Boss')
    expect(text).not.toContain('user-e2e-wangfang')
    expect(text).toContain('BU_ROLE:FIN/CLERK')
  })
})
