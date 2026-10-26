import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useBiAssignmentForm } from '../useBiAssignmentForm'
import { userApi, type User } from '@/api/user'
import { biManagementApi, type DashboardAssignmentResponse } from '@/api/biManagement'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/utils/notify', () => ({ notifyError: vi.fn(), notifyWarning: vi.fn(), notifySuccess: vi.fn() }))
vi.mock('@/api/user', () => ({ userApi: { list: vi.fn() } }))
vi.mock('@/api/role', () => ({ roleApi: {} }))
vi.mock('@/api/businessUnit', () => ({ businessUnitApi: {} }))
vi.mock('@/api/biManagement', () => ({ biManagementApi: {
  dashboard: { list: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }) },
  assignment: { update: vi.fn().mockResolvedValue({}), create: vi.fn().mockResolvedValue({}) },
} }))

describe('Audience target user completeness', () => {
  beforeEach(() => {
    vi.mocked(userApi.list).mockResolvedValue({ content: [], totalElements: 45, totalPages: 3, size: 20, number: 0 })
  })

  it('loads a normal first page without limiting eligibility by account status', async () => {
    const form = useBiAssignmentForm({ mode: ref('create'), initialRow: ref(null), onSuccess: vi.fn() })
    await form.searchUsers('')
    expect(userApi.list).toHaveBeenLastCalledWith({ page: 0, size: 20 })
  })

  it('loads subsequent matching pages and retains the selected user when searching', async () => {
    const user = (id: string): User => ({ id, fullName: id, username: id, email: '', status: 'LOCKED', createdAt: '' })
    vi.mocked(userApi.list).mockResolvedValueOnce({ content: [user('first')], totalElements: 2, totalPages: 2, size: 20, number: 0 })
      .mockResolvedValueOnce({ content: [user('second')], totalElements: 2, totalPages: 2, size: 20, number: 1 })
      .mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })
    const form = useBiAssignmentForm({ mode: ref('create'), initialRow: ref(null), onSuccess: vi.fn() })
    await form.searchUsers('match')
    expect(form.moreUsers.value).toBe(true)
    await form.loadMoreUsers()
    expect(userApi.list).toHaveBeenLastCalledWith({ keyword: 'match', page: 1, size: 20 })
    expect(form.targetSelectOptions.value.map(o => o.id)).toEqual(['first', 'second'])
    form.form.targetId = 'second'
    await form.searchUsers('new')
    expect(form.targetSelectOptions.value.map(o => o.id)).toEqual(['second'])
    expect(form.moreUsers.value).toBe(false)
  })

  it('ignores a stale search after a new search completes', async () => {
    let complete!: (value: Awaited<ReturnType<typeof userApi.list>>) => void
    vi.mocked(userApi.list).mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
      .mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })
    const form = useBiAssignmentForm({ mode: ref('create'), initialRow: ref(null), onSuccess: vi.fn() })
    const old = form.searchUsers('old')
    await form.searchUsers('new')
    complete({ content: [{ id: 'old', fullName: '', username: 'old', email: '', status: 'ACTIVE', createdAt: '' }], totalElements: 1, totalPages: 1, size: 20, number: 0 })
    await old
    expect(form.targetSelectOptions.value).toEqual([])
  })
  it('ignores a user response after the dialog cancels its search', async () => {
    let complete!: (value: Awaited<ReturnType<typeof userApi.list>>) => void
    vi.mocked(userApi.list).mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
    const form = useBiAssignmentForm({ mode: ref('create'), initialRow: ref(null), onSuccess: vi.fn() })
    const pending = form.searchUsers('closing')
    form.cancelUserSearch()
    complete({ content: [{ id: 'stale', fullName: '', username: 'stale', email: '', status: 'ACTIVE', createdAt: '' }], totalElements: 1, totalPages: 1, size: 20, number: 0 })
    await pending
    expect(form.targetSelectOptions.value).toEqual([])
  })

})

describe('Audience edit preserves independently changed enabled state', () => {
  const row = (enabled: boolean): DashboardAssignmentResponse => ({
    id: 'assignment', dashboardId: 'dashboard', dashboardTitle: 'Dashboard',
    targetType: 'USER', targetId: 'user', targetName: 'User', layoutMode: 'SINGLE',
    displayOrder: 0, isDefault: false, enabled, createdAt: '', updatedAt: '',
  })

  beforeEach(() => { vi.clearAllMocks() })

  it.each([true, false])('omits unchanged enabled=%s when saving other settings', async (enabled) => {
    const form = useBiAssignmentForm({ mode: ref('edit'), initialRow: ref(row(enabled)), onSuccess: vi.fn() })
    await form.initDialog()
    form.form.layoutMode = 'WIDGET'
    await form.handleSubmit()
    expect(biManagementApi.assignment.update).toHaveBeenCalledWith('assignment', expect.objectContaining({ layoutMode: 'WIDGET' }))
    expect(vi.mocked(biManagementApi.assignment.update).mock.calls[0][1]).not.toHaveProperty('enabled')
  })

  it.each([true, false])('sends an explicit enabled=%s change', async (enabled) => {
    const form = useBiAssignmentForm({ mode: ref('edit'), initialRow: ref(row(!enabled)), onSuccess: vi.fn() })
    await form.initDialog()
    form.form.enabled = enabled
    await form.handleSubmit()
    expect(biManagementApi.assignment.update).toHaveBeenCalledWith('assignment', expect.objectContaining({ enabled }))
  })

  it('continues sending the configured enabled state on creation', async () => {
    const form = useBiAssignmentForm({ mode: ref('create'), initialRow: ref(null), onSuccess: vi.fn() })
    form.form.enabled = false
    await form.handleSubmit()
    expect(biManagementApi.assignment.create).toHaveBeenCalledWith(expect.objectContaining({ enabled: false }))
  })
})
