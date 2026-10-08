import { request } from './request'

export interface UserDashboardResponse {
  dashboardId: string
  dashboardTitle: string
  description: string
  embedId: string
  layoutMode: 'SINGLE' | 'MULTI' | 'WIDGET'
  displayOrder: number
  isDefault: boolean
}

export interface GuestTokenResponse {
  token: string
  dashboardEmbedId: string
  supersetDomain?: string
}

export interface GuestTokenRequest {
  dashboardId: string
  /** Present for Data -> Views embeds; omitted for the legacy landing dashboard. */
  dataViewId?: number
  /** Workspace context used for BU and BU-scoped role audience assignments. */
  activeBusinessUnitId?: string
}

export interface DataViewDashboardResponse {
  dashboardId: string
  dashboardTitle: string
  description: string
  embedId: string
}

export const biDashboardApi = {
  getUserDashboards: (activeBusinessUnitId?: string) =>
    request.get<UserDashboardResponse[]>('/bi/dashboards', {
      params: activeBusinessUnitId ? { activeBusinessUnitId } : undefined,
    }),

  getGuestToken: (data: GuestTokenRequest) =>
    request.post<GuestTokenResponse>('/bi/guest-token', data),

  getDataViewDashboards: (viewId: number) =>
    request.get<DataViewDashboardResponse[]>(
      `/bi/data-view-assignments/views/${viewId}/dashboards`,
    ),
}
