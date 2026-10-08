import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { useSlaPolicies } from '../useSlaPolicies'
import type { SlaPolicyRow } from '@/api/slaPolicy'

const mocks = vi.hoisted(() => ({
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
  notifyConfirm: vi.fn(),
  hasPermission: vi.fn(),
  slaPolicyApi: {
    query: vi.fn(),
    update: vi.fn(),
    recalculate: vi.fn(),
    history: vi.fn(),
    jobs: vi.fn(),
    jobItems: vi.fn(),
  },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/utils/notify', () => ({
  notifySuccess: mocks.notifySuccess,
  notifyWarning: mocks.notifyWarning,
  notifyConfirm: mocks.notifyConfirm,
}))

vi.mock('@/utils/permission', () => ({
  hasPermission: mocks.hasPermission,
}))

vi.mock('@/api/slaPolicy', () => ({
  slaPolicyApi: mocks.slaPolicyApi,
}))

// The shared grid is exercised by its own tests; here it only has to accept a page.
vi.mock('@/composables/list/useAdminListGrid', () => ({
  useAdminListGrid: () => ({
    beginQuery: () => 1,
    isCurrentQuery: () => true,
    buildQuery: () => ({ page: 0, size: 20 }),
    applyPage: vi.fn(),
    gridInnerStyle: ref({}),
    gridTableHeight: ref(400),
  }),
}))

const row: SlaPolicyRow = {
  functionUnitCode: 'FU_CASES',
  functionUnitName: 'Cases',
  leadTimeDays: 30,
  version: 3,
  updatedBy: 'u-1',
  updatedAt: '2026-09-28T16:27:59Z',
  latestJobStatus: 'SUCCEEDED',
  latestJobId: 'job-3',
}

describe('useSlaPolicies', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.slaPolicyApi.query.mockResolvedValue({ columns: [], content: [], page: 0, size: 20, totalElements: 0 })
  })

  it('exposes edit rights from the sla:policy:edit permission', () => {
    mocks.hasPermission.mockImplementation((p: string) => p === 'sla:policy:edit')
    expect(useSlaPolicies().canEdit.value).toBe(true)
    mocks.hasPermission.mockReturnValue(false)
    expect(useSlaPolicies().canEdit.value).toBe(false)
  })

  it('saves a whole number of days with a trimmed reason, then reloads', async () => {
    mocks.slaPolicyApi.update.mockResolvedValue({ dispatchStatus: 'DISPATCHED' })
    const sla = useSlaPolicies()
    sla.showEditDialog(row)
    sla.editForm.leadTimeDays = 45
    sla.editForm.changeReason = '  statutory change  '

    await sla.submitEdit()

    expect(mocks.slaPolicyApi.update).toHaveBeenCalledWith('FU_CASES', {
      leadTimeDays: 45,
      changeReason: 'statutory change',
    })
    expect(sla.editDialogVisible.value).toBe(false)
    expect(mocks.notifySuccess).toHaveBeenCalledWith('sla.savedAndDispatched')
    expect(mocks.slaPolicyApi.query).toHaveBeenCalled()
  })

  it('warns, without leaking details, when the recalculation could not be started', async () => {
    mocks.slaPolicyApi.update.mockResolvedValue({
      dispatchStatus: 'DISPATCH_FAILED',
      dispatchError: 'PORTAL_DISPATCH_FAILED',
    })
    const sla = useSlaPolicies()
    sla.showEditDialog(row)
    sla.editForm.leadTimeDays = 45

    await sla.submitEdit()

    expect(mocks.notifyWarning).toHaveBeenCalledWith('sla.savedDispatchFailed')
    expect(mocks.notifySuccess).not.toHaveBeenCalled()
  })

  it.each([undefined, 0, 1.5, 3651])('never sends an invalid lead time (%s)', async (days) => {
    const sla = useSlaPolicies()
    sla.showEditDialog(row)
    sla.editForm.leadTimeDays = days

    await sla.submitEdit()

    expect(mocks.slaPolicyApi.update).not.toHaveBeenCalled()
  })

  it('opens the edit dialog empty for a Function Unit without a lead time', () => {
    const sla = useSlaPolicies()
    sla.showEditDialog({ ...row, leadTimeDays: null })
    expect(sla.editForm.leadTimeDays).toBeUndefined()
    expect(sla.editForm.currentDays).toBeNull()
  })

  it('recalculates only after the admin confirms', async () => {
    mocks.notifyConfirm.mockRejectedValueOnce('cancel')
    const sla = useSlaPolicies()

    await sla.recalculate(row)
    expect(mocks.slaPolicyApi.recalculate).not.toHaveBeenCalled()

    mocks.notifyConfirm.mockResolvedValueOnce(undefined)
    mocks.slaPolicyApi.recalculate.mockResolvedValue({ jobId: 'job-4' })
    await sla.recalculate(row)
    expect(mocks.slaPolicyApi.recalculate).toHaveBeenCalledWith('FU_CASES')
    expect(mocks.notifySuccess).toHaveBeenCalledWith('sla.recalcStarted')
  })

  it('loads history and jobs for the drawer, then the items of the selected job', async () => {
    mocks.slaPolicyApi.history.mockResolvedValue([{ id: 'h-1' }])
    mocks.slaPolicyApi.jobs.mockResolvedValue([{ id: 'job-3' }])
    mocks.slaPolicyApi.jobItems.mockResolvedValue([{ processInstanceId: 'pi-1', outcome: 'UPDATED' }])
    const sla = useSlaPolicies()

    await sla.showDetail(row)
    await sla.selectJob('job-3')

    expect(sla.history.value).toHaveLength(1)
    expect(sla.jobs.value).toHaveLength(1)
    expect(mocks.slaPolicyApi.jobItems).toHaveBeenCalledWith('FU_CASES', 'job-3')
    expect(sla.jobItems.value).toHaveLength(1)
  })
})
