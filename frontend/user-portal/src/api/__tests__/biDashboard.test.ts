import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('../request', () => ({ request: requestMock }))

import { biDashboardApi } from '../biDashboard'

describe('biDashboardApi Portal backend boundary', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
    requestMock.post.mockReset()
  })

  it('loads dashboards through Portal and forwards only the active BU context', () => {
    biDashboardApi.getUserDashboards('bu-finance')

    expect(requestMock.get).toHaveBeenCalledWith('/bi/dashboards', {
      params: { activeBusinessUnitId: 'bu-finance' },
    })
  })

  it('requests the guest token through Portal without a client supplied userId', () => {
    biDashboardApi.getGuestToken({
      dashboardId: 'dashboard-1',
      activeBusinessUnitId: 'bu-finance',
    })

    expect(requestMock.post).toHaveBeenCalledWith('/bi/guest-token', {
      dashboardId: 'dashboard-1',
      activeBusinessUnitId: 'bu-finance',
    })
  })

  it('loads data-view dashboards through Portal', () => {
    biDashboardApi.getDataViewDashboards(42)

    expect(requestMock.get).toHaveBeenCalledWith(
      '/bi/data-view-assignments/views/42/dashboards',
    )
  })
})
