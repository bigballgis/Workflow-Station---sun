import axios, { AxiosRequestConfig } from 'axios'
import { TOKEN_KEY, USER_KEY, USER_ID_KEY } from './auth'

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
 */
const adminCenterService = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 60000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
})

let userId = localStorage.getItem(USER_ID_KEY)
  if (!userId) {
    const userStr = localStorage.getItem(USER_KEY)
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        userId = user.userId || user.id
      } catch (e) {
        // ignore
      }
    }
  }
  if (userId) {
    config.headers['X-User-Id'] = userId
  }
  return config
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
