import axios, { AxiosRequestConfig } from 'axios'

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
}

/**
 * Dedicated axios instance for admin-center BI APIs.
 * Routes through /api/v1/admin/ which is the Kong route to admin-center backend.
 * Auth via httpOnly cookie (withCredentials: true).
 */
const adminCenterService = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 60000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
})

adminCenterService.interceptors.response.use(
  (response) => response.data,
  (error) => Promise.reject(error)
)

export const biDashboardApi = {
  getUserDashboards: (userId: string, activeBusinessUnitId?: string) =>
    adminCenterService.get<any, UserDashboardResponse[]>(`/bi/assignments/user/${userId}`, {
      params: activeBusinessUnitId ? { activeBusinessUnitId } : undefined,
    }),

  getGuestToken: (data: GuestTokenRequest, userId?: string) =>
    adminCenterService.post<any, GuestTokenResponse>('/bi/guest-token', data, {
      headers: userId ? { 'X-User-Id': userId } : undefined,
    }),
}
